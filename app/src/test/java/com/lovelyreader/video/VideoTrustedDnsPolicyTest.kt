package com.lovelyreader.video

import java.net.InetAddress
import org.junit.Assert.assertEquals
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
}
