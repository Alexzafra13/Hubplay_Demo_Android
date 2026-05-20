package com.alex.hubplay.data

import android.util.Log
import java.net.Socket
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager

/**
 * `X509ExtendedTrustManager` that gives the user a "trust on first use"
 * escape hatch when the OS trust store rejects a server cert — the
 * standard case for self-hosted media servers on Android TV devices
 * with stale CA bundles (e.g. Android <14 doesn't know ISRG Root X2,
 * so Let's Encrypt ECDSA certs hard-fail despite being legitimate;
 * private CAs and self-signed certs always fail).
 *
 * Flow on every connect:
 *
 *   1. Defer to the platform trust manager first. If it accepts → done.
 *      The user never sees the dialog. This is the happy path for any
 *      modern Android with a Let's Encrypt RSA chain.
 *
 *   2. If the platform rejects, check the [pinStore]: if the same user
 *      already accepted this exact cert for this host, accept silently.
 *      Rotation breaks byte-equality on purpose — we want the user to
 *      re-review on a new cert.
 *
 *   3. Otherwise, classify the failure (expired / hostname / unknown
 *      issuer / other), publish a [CertChallenge] to the [bus] and let
 *      the platform exception propagate. The connection fails; the
 *      Login UI observes the bus, renders the dialog, and on accept
 *      pins the cert and auto-retries.
 *
 * We extend `X509ExtendedTrustManager` (not the plain `X509TrustManager`)
 * because only the extended variants carry the SSLEngine / Socket and
 * therefore the peer hostname — without that we can't key the pin
 * store nor present a meaningful dialog. The 2-arg overload (no socket
 * / engine) falls back to the system manager: we can't TOFU there.
 *
 * Modelled on Nextcloud Android's `AdvancedX509TrustManager`. The two
 * deliberate differences:
 *   - We classify failure reasons explicitly so the dialog can show
 *     "Caducado" vs "CA desconocida" instead of one generic blob.
 *   - We hand the cert to the bus instead of running a Compose-aware
 *     suspend inside the TrustManager — keeps this class trivially
 *     testable and threading-safe.
 */
class PinnedCertTrustManager(
    private val pinStore: CertPinStore,
    private val bus:      CertChallengeBus,
) : X509ExtendedTrustManager() {

    private val systemTm: X509ExtendedTrustManager = run {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        factory.trustManagers.filterIsInstance<X509ExtendedTrustManager>().firstOrNull()
            ?: error("no X509ExtendedTrustManager from system TrustManagerFactory")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = systemTm.acceptedIssuers

    // ─── client-cert path: delegate verbatim, we don't do mTLS ────────────
    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) =
        systemTm.checkClientTrusted(chain, authType)
    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String, socket: Socket?) =
        systemTm.checkClientTrusted(chain, authType, socket)
    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String, engine: SSLEngine?) =
        systemTm.checkClientTrusted(chain, authType, engine)

    // ─── server-cert path: the interesting bit ────────────────────────────
    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        // The hostname-less overload is only invoked when no SNI/peer
        // info is available — we can't safely TOFU without a host to
        // key the pin against. Defer to the system manager unmodified.
        systemTm.checkServerTrusted(chain, authType)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String, socket: Socket?) {
        evaluate(chain, peerHostFrom(socket)) {
            systemTm.checkServerTrusted(chain, authType, socket)
        }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String, engine: SSLEngine?) {
        evaluate(chain, peerHostFrom(engine)) {
            systemTm.checkServerTrusted(chain, authType, engine)
        }
    }

    /**
     * Common branch for the socket / engine overloads. Try the system
     * manager first; on failure consult the pin store and otherwise
     * publish a challenge before re-raising.
     */
    private inline fun evaluate(
        chain:    Array<out X509Certificate>,
        peerHost: String?,
        delegate: () -> Unit,
    ) {
        try {
            delegate()
            return
        } catch (systemFailure: CertificateException) {
            val leaf = chain.firstOrNull() ?: throw systemFailure
            val host = peerHost?.takeIf { it.isNotBlank() } ?: throw systemFailure

            if (pinStore.matches(host, leaf)) {
                Log.d(TAG, "accepting pinned cert for $host")
                return
            }

            val reason = classify(systemFailure, leaf)
            Log.w(TAG, "unpinned cert for $host — reason=$reason, publishing challenge")
            bus.publish(CertChallenge(
                host        = host,
                fingerprint = CertPinStore.sha256Hex(leaf.encoded),
                subject     = leaf.subjectX500Principal?.name.orEmpty(),
                issuer      = leaf.issuerX500Principal?.name.orEmpty(),
                notBefore   = leaf.notBefore?.time ?: 0L,
                notAfter    = leaf.notAfter?.time ?: 0L,
                reason      = reason,
                cert        = leaf,
            ))
            throw systemFailure
        }
    }

    private fun classify(failure: CertificateException, leaf: X509Certificate): CertFailureReason {
        // Walk the cause chain — the exception we catch is usually a
        // generic CertificateException wrapping a CertPathValidator or
        // CertificateExpired further down.
        var cur: Throwable? = failure
        val seen = mutableSetOf<Throwable>()
        while (cur != null && seen.add(cur)) {
            val msg  = cur.message?.lowercase().orEmpty()
            val name = cur::class.java.name
            when {
                cur is CertificateExpiredException                       -> return CertFailureReason.Expired
                cur is CertificateNotYetValidException                   -> return CertFailureReason.NotYetValid
                "expired" in msg                                          -> return CertFailureReason.Expired
                "not yet valid" in msg                                    -> return CertFailureReason.NotYetValid
                "hostname" in msg || "subject alternative" in msg         -> return CertFailureReason.HostnameMismatch
                "trust anchor" in msg ||
                "chain validation" in msg ||
                "unable to find valid certification path" in msg          -> return CertFailureReason.UnknownIssuer
                "CertPath"     in name ||
                "TrustAnchor"  in name                                    -> return CertFailureReason.UnknownIssuer
            }
            cur = cur.cause
        }
        // Last-ditch: ask the cert itself. The JDK sometimes folds an
        // expired cert into a generic "validation failed" without the
        // expired class deeper down.
        try {
            leaf.checkValidity()
        } catch (e: CertificateExpiredException)   { return CertFailureReason.Expired }
        catch  (e: CertificateNotYetValidException) { return CertFailureReason.NotYetValid }
        return CertFailureReason.Other
    }

    private fun peerHostFrom(socket: Socket?): String? = when (socket) {
        is SSLSocket -> socket.handshakeSession?.peerHost
            ?: socket.session?.peerHost
            ?: socket.inetAddress?.hostName
        else         -> socket?.inetAddress?.hostName
    }

    private fun peerHostFrom(engine: SSLEngine?): String? =
        engine?.peerHost ?: engine?.handshakeSession?.peerHost

    companion object {
        private const val TAG = "PinnedTrustMgr"
    }
}

/**
 * Pairs with [PinnedCertTrustManager]: the OS hostname verifier rejects
 * certs whose SAN/CN doesn't match the URL the user typed. For a pinned
 * host we trust the user's earlier explicit accept, so we let the byte-
 * equal pin override the default verifier just like the trust manager
 * does. Same security model — pin is per-host, mismatch means re-prompt.
 */
class PinnedHostnameVerifier(private val pinStore: CertPinStore) : HostnameVerifier {
    private val default = HttpsURLConnection.getDefaultHostnameVerifier()

    override fun verify(host: String, session: SSLSession): Boolean {
        if (default.verify(host, session)) return true
        val leaf = session.peerCertificates.firstOrNull() as? X509Certificate ?: return false
        return pinStore.matches(host, leaf)
    }
}
