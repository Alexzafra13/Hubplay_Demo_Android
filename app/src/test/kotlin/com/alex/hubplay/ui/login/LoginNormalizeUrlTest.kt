package com.alex.hubplay.ui.login

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * URL normalisation runs on every Continue tap and has to accept the
 * grab-bag of shapes a non-technical user might type: with/without scheme,
 * with/without port, with/without a trailing slash, sometimes a path. If
 * we get this wrong the BaseUrlInterceptor swap mints a half-broken URL
 * and every request returns "host not found".
 */
class LoginNormalizeUrlTest {

    @Test
    fun `bare hostname gets https scheme`() {
        assertThat(normalizeServerUrl("hubplay.duckdns.org"))
            .isEqualTo("https://hubplay.duckdns.org")
    }

    @Test
    fun `http URL with explicit port survives intact`() {
        assertThat(normalizeServerUrl("http://192.168.1.50:8096"))
            .isEqualTo("http://192.168.1.50:8096")
    }

    @Test
    fun `trailing slash is stripped so BaseUrlInterceptor swaps cleanly`() {
        assertThat(normalizeServerUrl("https://hubplay.local/"))
            .isEqualTo("https://hubplay.local")
    }

    @Test
    fun `https URL kept as-is`() {
        assertThat(normalizeServerUrl("https://hubplay.example.com:8443"))
            .isEqualTo("https://hubplay.example.com:8443")
    }

    @Test
    fun `whitespace gets trimmed before parsing`() {
        assertThat(normalizeServerUrl("   hubplay.local   "))
            .isEqualTo("https://hubplay.local")
    }

    @Test
    fun `blank input returns null`() {
        assertThat(normalizeServerUrl("")).isNull()
        assertThat(normalizeServerUrl("   ")).isNull()
    }

    // ─── friendlyConnectError ─────────────────────────────────────────────
    //
    // The raw JDK / OkHttp exception messages ("Chain validation failed",
    // "Trust anchor for certification path not found") are unintelligible to
    // a non-technical TV user. These tests pin the mapping so a regression
    // doesn't quietly bring back the gobbledygook on the login surface.

    @Test
    fun `chain validation TLS error gets a self-hosted-friendly message`() {
        val err = SSLPeerUnverifiedException("Chain validation failed")
        val msg = friendlyConnectError(err, "https://hubplay.duckdns.org")
        assertThat(msg).contains("certificado")
        assertThat(msg).doesNotContain("Chain validation failed")
    }

    @Test
    fun `wrapped CertPathValidator cause is walked from outer IO exception`() {
        val inner = SSLPeerUnverifiedException("Trust anchor for certification path not found")
        val outer = IOException("network", inner)
        val msg = friendlyConnectError(outer, "https://hubplay.example.com")
        assertThat(msg).contains("certificado")
    }

    @Test
    fun `SSL handshake failure surfaces a generic secure-connection message`() {
        val err = SSLHandshakeException("handshake aborted")
        val msg = friendlyConnectError(err, "https://hubplay.example.com")
        assertThat(msg).contains("conexión segura")
    }

    @Test
    fun `unknown host is called out so the user re-checks the URL`() {
        val err = UnknownHostException("hubplay.notreal")
        val msg = friendlyConnectError(err, "https://hubplay.notreal")
        assertThat(msg).contains("host")
    }

    @Test
    fun `connection refused suggests checking the server is up`() {
        val err = ConnectException("Connection refused")
        val msg = friendlyConnectError(err, "http://192.168.1.50:8096")
        assertThat(msg).contains("servidor")
    }

    @Test
    fun `unknown errors fall back to the raw message rather than blanking`() {
        val err = RuntimeException("something exotic")
        val msg = friendlyConnectError(err, "https://hubplay.example.com")
        assertThat(msg).isEqualTo("something exotic")
    }
}
