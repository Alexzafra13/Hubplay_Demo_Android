package com.alex.hubplay.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class LanProbeTest {

    @Test
    fun `udp reply builds url from source ip and advertised port`() {
        val server = LanProbe.parseUdpReply(
            """{"product":"hubplay","name":"Salón","version":"1.2","port":8097}""",
            fromHost = "192.168.1.100",
        )
        assertEquals("http://192.168.1.100:8097", server?.url)
        assertEquals("Salón · 192.168.1.100", server?.displayName)
    }

    @Test
    fun `udp reply without hubplay marker or port is rejected`() {
        assertNull(LanProbe.parseUdpReply("""{"product":"other","port":80}""", "10.0.0.2"))
        assertNull(LanProbe.parseUdpReply("""{"product":"hubplay"}""", "10.0.0.2"))
        assertNull(LanProbe.parseUdpReply("""{"product":"hubplay","port":8096}""", ""))
        assertNull(LanProbe.parseUdpReply("not json", "10.0.0.2"))
    }

    @Test
    fun `udp reply defaults the display name`() {
        val server = LanProbe.parseUdpReply("""{"product":"hubplay","port":8096}""", "10.0.0.5")
        assertEquals("HubPlay · 10.0.0.5", server?.displayName)
    }

    @Test
    fun `udp reply with explicit url wins over ip and port`() {
        val server = LanProbe.parseUdpReply(
            """{"product":"hubplay","name":"Casa","port":8096,"url":"https://hubplay.example.org/"}""",
            fromHost = "192.168.1.100",
        )
        assertEquals("https://hubplay.example.org", server?.url)
        assertNull(
            LanProbe.parseUdpReply("""{"product":"hubplay","port":8096,"url":"ftp://x"}""", "1.2.3.4")?.url
                ?.takeIf { it.startsWith("ftp") },
        )
    }

    @Test
    fun `health signature accepts marker and legacy body`() {
        assertTrue(LanProbe.looksLikeHubplayHealth("""{"product":"hubplay","status":"ok"}"""))
        assertTrue(
            LanProbe.looksLikeHubplayHealth(
                """{"active_streams":0,"database":"ok","ffmpeg":"/usr/bin/ffmpeg","status":"ok"}""",
            ),
        )
        assertFalse(LanProbe.looksLikeHubplayHealth("""{"status":"ok"}"""))
        assertFalse(LanProbe.looksLikeHubplayHealth("<html>"))
    }

    @Test
    fun `subnet hosts enumerates the slash24 without self network and broadcast`() {
        val hosts = LanProbe.subnetHosts(InetAddress.getByName("192.168.1.132"), 24)
        assertEquals(253, hosts.size)
        assertFalse(hosts.contains("192.168.1.132"))
        assertFalse(hosts.contains("192.168.1.0"))
        assertFalse(hosts.contains("192.168.1.255"))
        assertTrue(hosts.contains("192.168.1.100"))
        assertEquals("192.168.1.1", hosts.first())
    }

    @Test
    fun `subnet hosts refuses networks larger than a slash24`() {
        assertTrue(LanProbe.subnetHosts(InetAddress.getByName("10.0.0.7"), 16).isEmpty())
    }
}
