package com.lovelyreader.video

import java.net.InetAddress
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
    fun `public host resolved to loopback is rejected before any site request`() {
        val reason = trustedDnsFallbackReason(
            host = "www.88ystv.com",
            addresses = listOf(InetAddress.getByName("::1"), InetAddress.getByName("221.228.32.13"))
        )

        assertEquals(TrustedDnsFallbackReason.INVALID_SYSTEM_ANSWER, reason)
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
}
