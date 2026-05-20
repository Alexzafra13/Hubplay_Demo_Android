package com.alex.hubplay.ui.login

import com.google.common.truth.Truth.assertThat
import org.junit.Test

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
}
