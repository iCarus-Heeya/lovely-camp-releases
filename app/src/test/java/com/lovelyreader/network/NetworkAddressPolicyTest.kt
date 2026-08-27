package com.lovelyreader.network

import java.net.InetAddress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkAddressPolicyTest {
    @Test
    fun `special use IPv4 ranges never become fallback targets`() {
        listOf(
            "0.0.0.1", "10.0.0.1", "100.64.0.1", "127.0.0.1", "169.254.1.1",
            "172.16.0.1", "192.0.0.1", "192.0.2.1", "192.88.99.1", "192.168.1.1",
            "198.18.0.1", "198.51.100.1", "203.0.113.1", "224.0.0.1", "240.0.0.1"
        ).forEach { address ->
            assertTrue(address, isUnsafeNetworkAddress(InetAddress.getByName(address)))
        }
        assertFalse(isUnsafeNetworkAddress(InetAddress.getByName("23.224.113.227")))
    }

    @Test
    fun `special use IPv6 ranges never become fallback targets`() {
        listOf(
            "::", "::1", "fe80::1", "fc00::1", "2001:db8::1", "ff02::1",
            "::ffff:192.168.1.1", "::ffff:127.0.0.1"
        ).forEach { address ->
            assertTrue(address, isUnsafeNetworkAddress(InetAddress.getByName(address)))
        }
        assertFalse(isUnsafeNetworkAddress(InetAddress.getByName("2001:4860:4860::8888")))
    }
}
