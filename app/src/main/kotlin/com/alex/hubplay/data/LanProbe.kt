package com.alex.hubplay.data

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * Descubrimiento de servidores HubPlay en la LAN SIN multicast.
 *
 * mDNS ([LanDiscovery]) falla en el caso más común de instalación: el
 * servidor en Docker con `ports: "8097:8096"`. El anuncio multicast se
 * queda dentro del bridge y la TV nunca lo ve. Aquí van dos mecanismos
 * que sí llegan:
 *
 *  1. **Sondeo UDP por broadcast** ([udpProbe]): datagrama
 *     `HUBPLAY-DISCOVER/1` a 255.255.255.255 y a la dirección de
 *     broadcast de cada interfaz, puerto 41860. El backend
 *     (`internal/discovery`) responde con JSON `{product, name, port}` y
 *     la URL se construye con la IP de ORIGEN de la respuesta (con
 *     Docker, la del host) y `port` (el del host, que el backend recibe
 *     por `HUBPLAY_DISCOVERY_ADVERTISE_PORT`). Es el enfoque de Plex.
 *  2. **Barrido de la subred** ([subnetProbe]): último recurso para
 *     servidores antiguos sin respondedor UDP. GET `/api/v1/health` a
 *     cada host de la /24 en los puertos habituales; vale si el cuerpo
 *     lleva la marca `"product":"hubplay"` (o las claves del health
 *     antiguo). ~500 conexiones con timeout corto y 48 en paralelo:
 *     2-4 s en una red doméstica. Arranca con retardo para dar tiempo a
 *     mDNS/UDP, que son gratis.
 *
 * Todo corre en IO; cada método devuelve un Flow frío que termina solo.
 */
class LanProbe(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(PROBE_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(PROBE_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build(),
    private val interfaces: () -> List<InterfaceAddress> = ::localIpv4Interfaces,
) {

    /** Sondeo UDP: emite un [LanServer] por cada respuesta válida. */
    fun udpProbe(): Flow<LanServer> = channelFlow {
        val socket = runCatching {
            DatagramSocket().apply {
                broadcast = true
                soTimeout = UDP_READ_TIMEOUT_MS
            }
        }
            .getOrElse {
                Log.w(TAG, "udp socket", it)
                return@channelFlow
            }
        socket.use { sock ->
            val payload = PROBE_MAGIC.toByteArray()
            val targets = broadcastTargets()
            val buf = ByteArray(UDP_MAX_REPLY)
            repeat(UDP_ROUNDS) { round ->
                targets.forEach { addr ->
                    runCatching { sock.send(DatagramPacket(payload, payload.size, addr, DISCOVERY_PORT)) }
                        .onFailure { Log.d(TAG, "udp send to $addr failed: ${it.message}") }
                }
                val until = System.currentTimeMillis() + UDP_ROUND_MS
                while (System.currentTimeMillis() < until) {
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        sock.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    val body = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                    parseUdpReply(body, packet.address.hostAddress.orEmpty())?.let { send(it) }
                }
                if (round < UDP_ROUNDS - 1) delay(UDP_GAP_MS)
            }
        }
    }.flowOn(Dispatchers.IO)

    /** Barrido HTTP de la subred /24 de cada interfaz IPv4. */
    fun subnetProbe(): Flow<LanServer> = channelFlow {
        delay(SUBNET_START_DELAY_MS)
        val ifaces = interfaces().filter { it.networkPrefixLength >= MIN_PREFIX_FOR_SCAN }
        if (ifaces.isEmpty()) return@channelFlow
        val gate = Semaphore(SUBNET_PARALLELISM)
        coroutineScope {
            ifaces.flatMap { iface -> subnetHosts(iface.address, iface.networkPrefixLength.toInt()) }
                .distinct()
                .flatMap { host -> PROBE_PORTS.map { port -> host to port } }
                .map { (host, port) ->
                    async { gate.withPermit { probeHttp(host, port) } }
                }
                .awaitAll()
                .filterNotNull()
                .forEach { send(it) }
        }
    }.flowOn(Dispatchers.IO)

    private fun probeHttp(host: String, port: Int): LanServer? {
        val url = "http://$host:$port"
        val request = Request.Builder().url("$url/api/v1/health").get().build()
        return runCatching {
            httpClient.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful && looksLikeHubplayHealth(body)) {
                    LanServer(displayName = "HubPlay · $host:$port", url = url)
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun broadcastTargets(): List<InetAddress> {
        val directed = interfaces().mapNotNull { it.broadcast }
        val global = runCatching { InetAddress.getByName(GLOBAL_BROADCAST) }.getOrNull()
        return (directed + listOfNotNull(global)).distinct()
    }

    companion object {
        private const val TAG = "LanProbe"

        /** Mismo puerto y magic que `internal/discovery` del backend. */
        const val DISCOVERY_PORT = 41860
        const val PROBE_MAGIC = "HUBPLAY-DISCOVER/1"
        private const val GLOBAL_BROADCAST = "255.255.255.255"

        private const val UDP_ROUNDS = 3
        private const val UDP_ROUND_MS = 700L
        private const val UDP_GAP_MS = 300L
        private const val UDP_READ_TIMEOUT_MS = 250
        private const val UDP_MAX_REPLY = 1024

        /** Puertos HTTP habituales: nativo (8096) y Docker por defecto (8097). */
        val PROBE_PORTS = listOf(8096, 8097)
        private const val SUBNET_START_DELAY_MS = 1_500L
        private const val SUBNET_PARALLELISM = 48
        private const val PROBE_CONNECT_TIMEOUT_MS = 400L
        private const val PROBE_READ_TIMEOUT_MS = 1_000L

        /** Solo barremos redes de hasta 256 hosts (/24 o más pequeñas). */
        private const val MIN_PREFIX_FOR_SCAN = 24
        private const val IPV4_BITS = 32
        private const val OCTET_MASK = 0xFF
        private const val OCTET_BITS = 8

        private val moshi: Moshi by lazy { Moshi.Builder().build() }
        private val mapAdapter by lazy {
            moshi.adapter<Map<String, Any?>>(
                Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java),
            )
        }

        /**
         * Respuesta del respondedor UDP → [LanServer], o null si no es
         * una respuesta HubPlay válida. `fromHost` es la IP de origen del
         * datagrama; es la que vale, no ninguna que venga en el cuerpo.
         */
        fun parseUdpReply(body: String, fromHost: String): LanServer? {
            val map = runCatching { mapAdapter.fromJson(body) }.getOrNull() ?: return null
            if (map["product"] != "hubplay") return null
            val port = (map["port"] as? Number)?.toInt() ?: return null
            if (port <= 0 || fromHost.isBlank()) return null
            val name = (map["name"] as? String)?.takeIf { it.isNotBlank() } ?: "HubPlay"
            // URL explícita (servidor detrás de proxy TLS): manda sobre ip:port.
            val explicit = (map["url"] as? String)?.trim()
                ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?.trimEnd('/')
            return when (explicit) {
                null -> LanServer(displayName = "$name · $fromHost", url = "http://$fromHost:$port")
                else -> LanServer(displayName = "$name · $explicit", url = explicit)
            }
        }

        /** ¿Es este cuerpo el `/api/v1/health` de un HubPlay? */
        fun looksLikeHubplayHealth(body: String): Boolean {
            val map = runCatching { mapAdapter.fromJson(body) }.getOrNull() ?: return false
            if (map["product"] == "hubplay") return true
            // Servidores anteriores a la marca `product`: el health de
            // HubPlay siempre ha llevado estas dos claves juntas.
            return map.containsKey("active_streams") && map.containsKey("ffmpeg")
        }

        /** Todas las IPs de la /24 (o menor) de `address`, salvo la propia. */
        fun subnetHosts(address: InetAddress, prefix: Int): List<String> {
            val self = address as? Inet4Address ?: return emptyList()
            if (prefix < MIN_PREFIX_FOR_SCAN || prefix >= IPV4_BITS) return emptyList()
            val ip = self.address.fold(0) { acc, b -> (acc shl OCTET_BITS) or (b.toInt() and OCTET_MASK) }
            val hostBits = IPV4_BITS - prefix
            val network = ip ushr hostBits shl hostBits
            val count = 1 shl hostBits
            return (1 until count - 1)
                .map { network or it }
                .filter { it != ip }
                .map { v -> dottedQuad(v) }
        }

        private fun dottedQuad(v: Int): String =
            (0 until IPV4_BITS / OCTET_BITS).reversed()
                .joinToString(".") { i -> (v ushr (i * OCTET_BITS) and OCTET_MASK).toString() }

        private fun localIpv4Interfaces(): List<InterfaceAddress> =
            runCatching {
                NetworkInterface.getNetworkInterfaces().toList()
                    .filter { it.isUp && !it.isLoopback }
                    .flatMap { it.interfaceAddresses }
                    .filter { it.address is Inet4Address }
            }.getOrDefault(emptyList())
    }
}
