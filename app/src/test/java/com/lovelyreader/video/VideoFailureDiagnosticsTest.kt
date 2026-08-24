package com.lovelyreader.video

import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFailureDiagnosticsTest {
    @Test
    fun `final timeout appends the device network probe to debug diagnostics`() = runTest {
        val diagnostics = VideoRequestDiagnostics()
        val probe = object : VideoHostConnectivityProbe {
            override suspend fun inspect(url: String): List<String> = listOf(
                "网络快照：Wi-Fi（已验证）",
                "TCP 443：IPv4 23.224.113.227 — 超时"
            )
        }

        recordVideoRequestFailureDiagnostics(
            diagnostics = diagnostics,
            method = "GET",
            url = "https://www.88ystv.com/",
            error = SocketTimeoutException(),
            probe = probe
        )

        val messages = diagnostics.snapshot().map { it.displayText }
        assertTrue(messages.any { it.contains("请求失败：SocketTimeoutException") })
        assertTrue(messages.any { it.contains("网络快照：Wi-Fi（已验证）") })
        assertTrue(messages.any { it.contains("TCP 443：IPv4 23.224.113.227 — 超时") })
    }

    @Test
    fun `TLS transport failure also appends the device network probe`() = runTest {
        val diagnostics = VideoRequestDiagnostics()
        var inspected = false
        val probe = object : VideoHostConnectivityProbe {
            override suspend fun inspect(url: String): List<String> {
                inspected = true
                return listOf("网络快照：Wi-Fi（已验证）")
            }
        }

        recordVideoRequestFailureDiagnostics(
            diagnostics = diagnostics,
            method = "GET",
            url = "https://www.88ystv.com/",
            error = SSLHandshakeException("TLS blocked by gateway"),
            probe = probe
        )

        assertTrue(inspected)
        assertTrue(diagnostics.snapshot().any { it.detail.contains("SSLHandshakeException") })
    }
}
