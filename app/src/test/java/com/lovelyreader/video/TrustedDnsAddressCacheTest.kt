package com.lovelyreader.video

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrustedDnsAddressCacheTest {
    @Test
    fun `trusted addresses are reused only inside the short TTL`() {
        var now = 100L
        val cache = TrustedDnsAddressCache(ttlMillis = 1_000L, nowMillis = { now })
        val address = InetAddress.getByName("23.224.113.227")
        cache.put("WWW.Example.com", listOf(address))

        assertEquals(listOf(address), cache.get("www.example.com"))
        now += 1_000L
        assertNull(cache.get("www.example.com"))
    }
}
