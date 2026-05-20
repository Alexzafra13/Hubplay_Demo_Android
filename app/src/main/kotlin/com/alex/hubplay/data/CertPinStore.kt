package com.alex.hubplay.data

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Locale

/**
 * User-accepted server certificate pins, keyed by hostname.
 *
 * Modelled on Nextcloud Android's `getKnownServersStore` — we persist
 * the leaf cert's DER bytes per host so a returning user doesn't see the
 * "trust this cert?" dialog again until the server rotates its cert
 * (which intentionally re-triggers the prompt: the user should review a
 * new cert, not auto-trust whatever shows up).
 *
 * **Not a secret.** A cert fingerprint is public information from any
 * TLS handshake; we don't need EncryptedFile / Keystore-backed crypto
 * here. Plain JSON in `filesDir` is the right blast radius.
 */
class CertPinStore(context: Context) {

    private val file  = File(context.applicationContext.filesDir, FILE_NAME)
    private val mutex = Mutex()
    private val _pins = MutableStateFlow(loadFromDisk())
    val pins: StateFlow<Map<String, Pin>> = _pins.asStateFlow()

    data class Pin(
        val host:        String,
        /** SHA-256 of the DER, formatted as colon-separated uppercase hex. */
        val fingerprint: String,
        /** Leaf cert DER, base64 — used to verify byte-exact match next visit. */
        val derBase64:   String,
        val subject:     String,
        val issuer:      String,
        val notBefore:   Long,
        val notAfter:    Long,
        val addedAt:     Long,
    )

    fun get(host: String): Pin? = _pins.value[normalize(host)]

    /** True iff [cert]'s DER equals the pinned bytes for [host]. */
    fun matches(host: String, cert: X509Certificate): Boolean {
        val pin = get(host) ?: return false
        val stored = runCatching { Base64.decode(pin.derBase64, Base64.NO_WRAP) }.getOrNull()
            ?: return false
        return stored.contentEquals(cert.encoded)
    }

    suspend fun put(host: String, cert: X509Certificate): Unit = mutex.withLock {
        val der = cert.encoded
        val pin = Pin(
            host        = normalize(host),
            fingerprint = sha256Hex(der),
            derBase64   = Base64.encodeToString(der, Base64.NO_WRAP),
            subject     = cert.subjectX500Principal?.name.orEmpty(),
            issuer      = cert.issuerX500Principal?.name.orEmpty(),
            notBefore   = cert.notBefore?.time ?: 0L,
            notAfter    = cert.notAfter?.time ?: 0L,
            addedAt     = System.currentTimeMillis(),
        )
        val next = _pins.value + (pin.host to pin)
        _pins.value = next
        writeToDisk(next)
    }

    suspend fun delete(host: String): Unit = mutex.withLock {
        val next = _pins.value - normalize(host)
        _pins.value = next
        writeToDisk(next)
    }

    private fun loadFromDisk(): Map<String, Pin> = runCatching {
        if (!file.exists()) return@runCatching emptyMap<String, Pin>()
        val json = JSONArray(file.readText())
        buildMap {
            for (i in 0 until json.length()) {
                val o = json.getJSONObject(i)
                val host = o.getString("host")
                put(host, Pin(
                    host        = host,
                    fingerprint = o.getString("fingerprint"),
                    derBase64   = o.getString("der_base64"),
                    subject     = o.optString("subject"),
                    issuer      = o.optString("issuer"),
                    notBefore   = o.optLong("not_before"),
                    notAfter    = o.optLong("not_after"),
                    addedAt     = o.optLong("added_at"),
                ))
            }
        }
    }.onFailure { Log.w(TAG, "load pin store failed", it) }.getOrDefault(emptyMap())

    private fun writeToDisk(map: Map<String, Pin>) {
        val arr = JSONArray()
        for (pin in map.values) {
            arr.put(JSONObject().apply {
                put("host",        pin.host)
                put("fingerprint", pin.fingerprint)
                put("der_base64",  pin.derBase64)
                put("subject",     pin.subject)
                put("issuer",      pin.issuer)
                put("not_before",  pin.notBefore)
                put("not_after",   pin.notAfter)
                put("added_at",    pin.addedAt)
            })
        }
        runCatching { file.writeText(arr.toString()) }
            .onFailure { Log.w(TAG, "write pin store failed", it) }
    }

    companion object {
        private const val TAG       = "CertPinStore"
        private const val FILE_NAME = "cert-pins.json"

        internal fun normalize(host: String): String = host.lowercase(Locale.ROOT)

        internal fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return digest.joinToString(":") { byte ->
                String.format(Locale.ROOT, "%02X", byte)
            }
        }
    }
}
