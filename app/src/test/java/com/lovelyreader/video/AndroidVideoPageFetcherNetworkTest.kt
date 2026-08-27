package com.lovelyreader.video

import com.lovelyreader.source.HttpTextClient
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.SocketTimeoutException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidVideoPageFetcherNetworkTest {
    private val mixedSystemAnswer = TrustedDnsAddressSelection(
        usableAddresses = listOf(InetAddress.getByName("23.224.113.227")),
        rejectedAddressCount = 1,
        fallbackReason = null
    )

    private val allUnsafeAnswer = TrustedDnsAddressSelection(
        usableAddresses = emptyList(),
        rejectedAddressCount = 2,
        fallbackReason = TrustedDnsFallbackReason.INVALID_SYSTEM_ANSWER
    )

    private val allUsableAnswer = TrustedDnsAddressSelection(
        usableAddresses = listOf(InetAddress.getByName("23.224.113.227")),
        rejectedAddressCount = 0,
        fallbackReason = null
    )

    @Test
    fun `GET uses filtered system addresses before trusted DNS`() = runTest {
        val fallback = FakeTrustedVideoDnsFallback(mixedSystemAnswer)
        val fetcher = AndroidVideoPageFetcher(
            client = unusableDefaultClient(),
            trustedDnsFallback = fallback
        )

        assertTrue(fetcher.get("https://www.88ystv.com/").contains("<form"))
        assertEquals(1, fallback.systemGetCalls)
        assertEquals(0, fallback.trustedGetCalls)
        assertTrue(fallback.lastSystemAddresses.single().hostAddress == "23.224.113.227")
    }

    @Test
    fun `POST uses filtered system addresses before trusted DNS`() = runTest {
        val fallback = FakeTrustedVideoDnsFallback(mixedSystemAnswer)
        val fetcher = AndroidVideoPageFetcher(
            client = unusableDefaultClient(),
            trustedDnsFallback = fallback
        )

        assertTrue(fetcher.postForm("https://www.88ystv.com/search", mapOf("wd" to "南部档案")).contains("<form"))
        assertEquals(1, fallback.systemPostCalls)
        assertEquals(0, fallback.trustedPostCalls)
    }

    @Test
    fun `all usable preflight addresses keep the browser hostname request path`() = runTest {
        var defaultCalls = 0
        val fallback = FakeTrustedVideoDnsFallback(
            selection = allUsableAnswer,
            systemGetResult = { error("all-usable answers must not use the address-pinned path") },
            trustedGetResult = { error("default hostname request should succeed") }
        )
        val fetcher = AndroidVideoPageFetcher(
            client = successfulDefaultClient { defaultCalls++ },
            trustedDnsFallback = fallback
        )

        assertTrue(fetcher.get("https://www.88ystv.com/").contains("<form"))

        assertEquals(1, defaultCalls)
        assertEquals(0, fallback.systemGetCalls)
        assertEquals(0, fallback.trustedGetCalls)
        assertTrue(fetcher.diagnostics().any { it.detail.contains("保留主机名 HTTPS 路径") })
    }

    @Test
    fun `system address transport failure falls back to trusted DNS without repeating the same route`() = runTest {
        val fallback = FakeTrustedVideoDnsFallback(
            selection = mixedSystemAnswer,
            systemGetResult = { throw SocketTimeoutException("poisoned public route") },
            trustedGetResult = { "<html><form></form></html>" }
        )
        val fetcher = AndroidVideoPageFetcher(
            client = unusableDefaultClient(),
            trustedDnsFallback = fallback
        )

        assertTrue(fetcher.get("https://www.88ystv.com/").contains("<form"))
        assertEquals(1, fallback.systemGetCalls)
        assertEquals(1, fallback.trustedGetCalls)
        assertTrue(fetcher.diagnostics().any { it.detail.contains("有效系统地址请求失败") })
    }

    @Test
    fun `POST system address transport failure falls back to trusted DNS`() = runTest {
        val fallback = FakeTrustedVideoDnsFallback(
            selection = mixedSystemAnswer,
            systemPostResult = { throw SocketTimeoutException("poisoned public route") },
            trustedPostResult = { "<html><form></form></html>" }
        )
        val fetcher = AndroidVideoPageFetcher(
            client = unusableDefaultClient(),
            trustedDnsFallback = fallback
        )

        assertTrue(fetcher.postForm("https://www.88ystv.com/search", mapOf("wd" to "南部档案")).contains("<form"))
        assertEquals(1, fallback.systemPostCalls)
        assertEquals(1, fallback.trustedPostCalls)
    }

    @Test
    fun `all unsafe system addresses enter trusted DNS directly`() = runTest {
        val fallback = FakeTrustedVideoDnsFallback(
            selection = allUnsafeAnswer,
            trustedGetResult = { "<html><form></form></html>" }
        )
        val fetcher = AndroidVideoPageFetcher(
            client = unusableDefaultClient(),
            trustedDnsFallback = fallback
        )

        assertTrue(fetcher.get("https://www.88ystv.com/").contains("<form"))
        assertEquals(0, fallback.systemGetCalls)
        assertEquals(1, fallback.trustedGetCalls)
    }

    private fun unusableDefaultClient() = HttpTextClient(
        minimumIntervalMillis = 0,
        connectionFactory = { error("default request must not be selected in this test") }
    )

    private fun successfulDefaultClient(onOpen: () -> Unit) = HttpTextClient(
        minimumIntervalMillis = 0,
        connectionFactory = { url ->
            onOpen()
            object : HttpURLConnection(url) {
                override fun disconnect() = Unit
                override fun usingProxy(): Boolean = false
                override fun connect() = Unit
                override fun getResponseCode(): Int = 200
                override fun getInputStream() = ByteArrayInputStream("<html><form></form></html>".toByteArray())
            }
        }
    )

    private class FakeTrustedVideoDnsFallback(
        private val selection: TrustedDnsAddressSelection,
        private val systemGetResult: suspend (List<InetAddress>) -> String = { "<html><form></form></html>" },
        private val trustedGetResult: suspend () -> String = { "<html><form></form></html>" },
        private val systemPostResult: suspend (List<InetAddress>) -> String = { "<html><form></form></html>" },
        private val trustedPostResult: suspend () -> String = trustedGetResult
    ) : TrustedVideoDnsFallback {
        var systemGetCalls = 0
        var systemPostCalls = 0
        var trustedGetCalls = 0
        var trustedPostCalls = 0
        var lastSystemAddresses: List<InetAddress> = emptyList()

        override suspend fun preflight(url: String): TrustedDnsFallbackReason? = selection.fallbackReason

        override suspend fun preflightSelection(url: String): TrustedDnsAddressSelection = selection

        override suspend fun get(url: String): String {
            trustedGetCalls++
            return trustedGetResult()
        }

        override suspend fun postForm(url: String, fields: Map<String, String>): String {
            trustedPostCalls++
            return trustedPostResult()
        }

        override suspend fun getUsingAddresses(url: String, addresses: List<InetAddress>): String {
            systemGetCalls++
            lastSystemAddresses = addresses
            return systemGetResult(addresses)
        }

        override suspend fun postFormUsingAddresses(
            url: String,
            fields: Map<String, String>,
            addresses: List<InetAddress>
        ): String {
            systemPostCalls++
            lastSystemAddresses = addresses
            return systemPostResult(addresses)
        }
    }
}
