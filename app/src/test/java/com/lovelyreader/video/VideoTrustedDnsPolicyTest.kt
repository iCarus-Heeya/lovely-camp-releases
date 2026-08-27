package com.lovelyreader.video

import java.net.InetAddress
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoTrustedDnsPolicyTest {
    @Test
    fun `trusted resolver uses official bootstrap addresses without consulting the affected system DNS`() {
        assertEquals(listOf("223.5.5.5", "223.6.6.6"), trustedDnsBootstrapAddresses())
    }

    @Test
    fun `trusted DNS bootstrap transport disables redirects and uses bounded headers`() {
        val connection = RecordingHttpURLConnection(URL("https://dns.alidns.com/resolve?name=www.88ystv.com&type=A"))

        configureVideoTrustedDnsBootstrapConnection(connection)

        assertEquals(8_000, connection.connectTimeout)
        assertEquals(8_000, connection.readTimeout)
        assertTrue(!connection.instanceFollowRedirects)
        assertEquals("application/json", connection.getRequestProperty("Accept"))
        assertEquals("LovelyCamp/1.0 trusted-dns", connection.getRequestProperty("User-Agent"))
    }

    @Test
    fun `mixed system answer filters loopback and keeps usable public IPv4`() {
        val selection = selectTrustedDnsAddresses(
            host = "www.88ystv.com",
            addresses = listOf(InetAddress.getByName("::1"), InetAddress.getByName("23.224.113.227"))
        )

        assertEquals(null, selection.fallbackReason)
        assertEquals(listOf("23.224.113.227"), selection.usableAddresses.map { it.hostAddress })
        assertEquals(1, selection.rejectedAddressCount)
        assertTrue(selection.shouldPreferSystemAddresses)
    }

    @Test
    fun `only unsafe system answers still require trusted DNS fallback`() {
        val selection = selectTrustedDnsAddresses(
            host = "www.88ystv.com",
            addresses = listOf(InetAddress.getByName("::1"), InetAddress.getByName("192.168.3.1"))
        )

        assertEquals(TrustedDnsFallbackReason.INVALID_SYSTEM_ANSWER, selection.fallbackReason)
        assertTrue(selection.usableAddresses.isEmpty())
        assertEquals(2, selection.rejectedAddressCount)
        assertTrue(!selection.shouldPreferSystemAddresses)
    }

    @Test
    fun `usable addresses prefer IPv4 before public IPv6`() {
        val selection = selectTrustedDnsAddresses(
            host = "www.88ystv.com",
            addresses = listOf(InetAddress.getByName("2001:4860:4860::8888"), InetAddress.getByName("23.224.113.227"))
        )

        assertEquals(
            listOf("23.224.113.227", "2001:4860:4860:0:0:0:0:8888"),
            selection.usableAddresses.map { it.hostAddress }
        )
    }

    @Test
    fun `special use IPv6 answers are rejected`() {
        val selection = selectTrustedDnsAddresses(
            host = "www.88ystv.com",
            addresses = listOf(
                InetAddress.getByName("fc00::1"),
                InetAddress.getByName("2001:db8::1"),
                InetAddress.getByName("23.224.113.227")
            )
        )

        assertEquals(listOf("23.224.113.227"), selection.usableAddresses.map { it.hostAddress })
        assertEquals(2, selection.rejectedAddressCount)
        assertEquals(null, selection.fallbackReason)
    }

    @Test
    fun `trusted DNS and target failures retain distinct diagnostic stages`() {
        assertEquals(
            "可信 DNS 查询阶段",
            trustedVideoFailureStage(java.io.IOException("outer", TrustedDnsResolutionException("dns")))
        )
        assertEquals(
            "目标 HTTPS 阶段",
            trustedVideoFailureStage(java.io.IOException("outer", TrustedVideoTargetException("target")))
        )
        assertEquals(
            "SocketTimeoutException",
            trustedVideoFailureType(
                TrustedDnsResolutionException("dns", java.net.SocketTimeoutException("slow resolver"))
            )
        )
    }

    @Test
    fun `normal public CDN addresses keep the system DNS path`() {
        val reason = trustedDnsFallbackReason(
            host = "www.88ystv.com",
            addresses = listOf(InetAddress.getByName("23.224.113.227"), InetAddress.getByName("23.224.113.228"))
        )

        assertEquals(null, reason)
    }

    @Test
    fun `trusted connection rewrites only the transport address while retaining the request host`() {
        val original = URL("https://www.88ystv.com/resolve?name=example.com&type=A")
        val direct = trustedAddressUrl(original, InetAddress.getByName("23.224.113.227"))

        assertEquals("23.224.113.227", direct.host)
        assertEquals(original.path, direct.path)
        assertEquals(original.query, direct.query)
    }

    @Test
    fun `trusted connection brackets IPv6 transport addresses`() {
        val original = URL("https://www.88ystv.com/")
        val direct = trustedAddressUrl(original, InetAddress.getByName("2001:db8::1"))

        assertTrue(direct.host.contains(":"))
        assertTrue(direct.authority.startsWith("[") && direct.authority.endsWith("]"))
    }

    @Test
    fun `trusted resolver accepts only literal public IPv4 answers`() {
        assertEquals("23.224.113.227", parseTrustedIpv4Address("23.224.113.227")?.hostAddress)
        assertEquals(null, parseTrustedIpv4Address("www.88ystv.com"))
        assertEquals(null, parseTrustedIpv4Address("256.1.1.1"))
    }

    @Test
    fun `non HTTPS video URLs are rejected before any request`() {
        var failed = false
        try {
            requireTrustedVideoHttpsUrl("http://www.88ystv.com/")
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }

    private class RecordingHttpURLConnection(url: URL) : HttpURLConnection(url) {
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun connect() = Unit
    }
}
