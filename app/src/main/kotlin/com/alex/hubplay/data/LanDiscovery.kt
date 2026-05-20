package com.alex.hubplay.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * mDNS discovery of HubPlay servers on the local network.
 *
 * The Go backend (internal/mdns/) advertises itself as a `_http._tcp`
 * service with hostname `hubplay.local` (configurable). We listen for
 * that service type, resolve each instance to its host/port, and emit
 * a [LanServer] each time a NEW (host:port) pair is observed.
 *
 * Why a callback Flow rather than a Repository with snapshots? Discovery
 * is inherently push-driven (NsdManager fires events when devices come
 * online / go offline). A cold Flow ties the lifecycle to the collector,
 * so when the user navigates off the login screen `awaitClose` runs and
 * we stop the discovery — no background battery hit.
 *
 * Caveats this code is paranoid about:
 *
 *  - **API 30 vs older**: `registerServiceInfoCallback` only exists from
 *    API 34. Below that we keep using `resolveService`, which has a
 *    documented "one resolve at a time" footgun — we serialise resolves
 *    with a simple in-flight set.
 *  - **Duplicate emissions**: the framework re-fires `onServiceFound`
 *    on Wi-Fi reconnect. We de-dupe on `(name, host, port)`.
 *  - **Invalid services**: anything without a host (resolve failed) or
 *    a port <= 0 is dropped silently.
 *  - **stopServiceDiscovery throwing**: framework occasionally throws
 *    `IllegalArgumentException` if the discovery already stopped due to
 *    Wi-Fi flapping. Swallow the exception so the Flow tears down cleanly.
 */
class LanDiscovery(private val context: Context) {

    fun discover(): Flow<LanServer> = callbackFlow {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        if (nsd == null) {
            close()
            return@callbackFlow
        }

        val seen = mutableSetOf<String>()
        // Older devices: avoid concurrent resolves (single-threaded API).
        val resolving = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                resolving.remove(serviceInfo.serviceName)
                Log.d(TAG, "resolve failed ${serviceInfo.serviceName} err=$errorCode")
            }
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                resolving.remove(serviceInfo.serviceName)
                val host = serviceInfo.host?.hostAddress ?: return
                val port = serviceInfo.port
                if (port <= 0) return
                val url = "http://$host:$port"
                val key = "${serviceInfo.serviceName}|$host:$port"
                if (seen.add(key)) {
                    trySend(LanServer(
                        displayName = serviceInfo.serviceName.ifBlank { "HubPlay" },
                        url         = url,
                    ))
                }
            }
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "discovery started: $serviceType")
            }
            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "discovery stopped: $serviceType")
            }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "start failed $serviceType err=$errorCode")
                close()
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "stop failed $serviceType err=$errorCode")
            }
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                // Heuristic: filter to entries whose name looks like ours.
                // The backend registers with `cfg.MDNS.Hostname` (default
                // "hubplay"), so any service name containing "hubplay" is
                // a candidate. We don't hard-fail if the operator chose a
                // different hostname — they can always type the URL.
                val name = serviceInfo.serviceName.orEmpty()
                if (!name.contains("hubplay", ignoreCase = true)) return
                if (!resolving.add(name)) return
                @Suppress("DEPRECATION")
                nsd.resolveService(serviceInfo, resolveListener)
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "service lost ${serviceInfo.serviceName}")
            }
        }

        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (t: Throwable) {
            Log.w(TAG, "discoverServices threw", t)
            close(t)
            return@callbackFlow
        }

        awaitClose {
            try {
                nsd.stopServiceDiscovery(discoveryListener)
            } catch (_: IllegalArgumentException) {
                // Discovery already torn down by the framework — fine.
            } catch (t: Throwable) {
                Log.w(TAG, "stopServiceDiscovery threw", t)
            }
        }
    }

    companion object {
        private const val TAG = "LanDiscovery"
        // Matches the Go backend (internal/mdns/server.go): it registers
        // _http._tcp so any web service (not just HubPlay) on the LAN
        // shows up here — we filter by service name above. mDNS service
        // types are case-insensitive but the framework expects the
        // trailing dot only on some API levels; the form below works on
        // API 26+.
        private const val SERVICE_TYPE = "_http._tcp."
    }
}

data class LanServer(
    val displayName: String,
    val url:         String,
)
