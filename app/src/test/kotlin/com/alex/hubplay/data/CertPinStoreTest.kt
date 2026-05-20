package com.alex.hubplay.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

/**
 * Pure-logic guardrails for [CertPinStore]. The full round-trip
 * (file → reload) lives in instrumentation tests because it needs an
 * Android Context — what we pin here are the static helpers behaving
 * deterministically across rebuilds, since they end up serialised into
 * the user's persisted pin file and any drift would invalidate every
 * previously-accepted pin.
 */
class CertPinStoreTest {

    @Test
    fun `normalize lowercases so a typed URL with mixed case hits the same pin`() {
        // Pin store is keyed by host — if the user types Hubplay.duckdns.org
        // one day and hubplay.duckdns.org the next, the same pin must
        // match. Hostnames are case-insensitive in DNS but Kotlin strings
        // aren't, so we lowercase at store boundaries.
        assertThat(CertPinStore.normalize("Hubplay.DuckDNS.org"))
            .isEqualTo("hubplay.duckdns.org")
    }

    @Test
    fun `normalize uses ROOT locale so Turkish I doesn't break hostname matching`() {
        // The classic Locale.getDefault().toLowerCase() footgun: "I" in
        // Turkish lowercases to "ı" (dotless), not "i". A user on a TR
        // device would pin "ISTANBUL" → "ıstanbul" and never match
        // "istanbul.example" again. Locale.ROOT keeps it ASCII-safe.
        val turkishHostname = "ISTANBUL.example"
        val normalised      = CertPinStore.normalize(turkishHostname)
        assertThat(normalised).isEqualTo("istanbul.example")
        assertThat(normalised).isNotEqualTo(turkishHostname.lowercase(Locale("tr", "TR")))
    }

    @Test
    fun `sha256Hex emits colon-separated uppercase hex for human matching`() {
        // The dialog shows this string verbatim; users will be comparing
        // against the fingerprint OpenSSL prints (`openssl x509 -fingerprint
        // -sha256`), which is colon-separated uppercase hex. Format drift
        // means the user can't visually confirm against the server.
        val empty  = CertPinStore.sha256Hex(ByteArray(0))
        // SHA-256 of empty input is e3b0c442…b855 — pin the first byte
        // to confirm uppercase + colon formatting.
        assertThat(empty).startsWith("E3:B0:C4:42")
        assertThat(empty.split(":")).hasSize(32)
        assertThat(empty).matches("([0-9A-F]{2}:){31}[0-9A-F]{2}")
    }

    @Test
    fun `sha256Hex of identical bytes returns identical strings`() {
        val a = byteArrayOf(1, 2, 3, 4, 5)
        val b = byteArrayOf(1, 2, 3, 4, 5)
        assertThat(CertPinStore.sha256Hex(a)).isEqualTo(CertPinStore.sha256Hex(b))
    }
}
