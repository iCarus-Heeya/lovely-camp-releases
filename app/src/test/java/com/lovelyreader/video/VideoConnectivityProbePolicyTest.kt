package com.lovelyreader.video

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoConnectivityProbePolicyTest {
    @Test
    fun `probe filters special use answers and keeps public addresses`() {
        val selected = selectVideoProbeAddresses(
            listOf(
                InetAddress.getByName("::1"),
                InetAddress.getByName("192.168.3.1"),
                InetAddress.getByName("23.224.113.227"),
                InetAddress.getByName("2001:4860:4860::8888")
            )
        )

        assertEquals(
            listOf("23.224.113.227", "2001:4860:4860:0:0:0:0:8888"),
            selected.map { it.hostAddress }
        )
    }

    @Test
    fun `probe returns no targets when DNS answers are all special use`() {
        val selected = selectVideoProbeAddresses(
            listOf(
                InetAddress.getByName("::1"),
                InetAddress.getByName("fc00::1"),
                InetAddress.getByName("192.168.3.1")
            )
        )

        assertTrue(selected.isEmpty())
    }
}
