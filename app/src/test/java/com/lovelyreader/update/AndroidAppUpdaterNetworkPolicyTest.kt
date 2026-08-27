package com.lovelyreader.update

import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAppUpdaterNetworkPolicyTest {
    @Test
    fun `every updater transport applies bounded timeouts and disables redirects`() {
        val connection = RecordingHttpURLConnection(URL("https://api.github.com/releases/latest"))

        configureUpdateHttpConnection(connection)

        assertEquals(12_000, connection.connectTimeout)
        assertEquals(60_000, connection.readTimeout)
        assertFalse(connection.instanceFollowRedirects)
        assertEquals("application/vnd.github+json", connection.getRequestProperty("Accept"))
        assertEquals("LovelyCamp-Android-Updater", connection.getRequestProperty("User-Agent"))
    }

    @Test
    fun `trusted DNS bootstrap transport also disables redirects and uses bounded headers`() {
        val connection = RecordingHttpURLConnection(URL("https://dns.alidns.com/resolve?name=api.github.com&type=A"))

        configureUpdateTrustedDnsBootstrapConnection(connection)

        assertEquals(8_000, connection.connectTimeout)
        assertEquals(8_000, connection.readTimeout)
        assertFalse(connection.instanceFollowRedirects)
        assertEquals("application/json", connection.getRequestProperty("Accept"))
        assertEquals("LovelyCamp/1.0 trusted-dns", connection.getRequestProperty("User-Agent"))
    }

    @Test
    fun `trusted fallback response is revalidated before release parsing`() {
        var failed = false
        try {
            validateTrustedUpdateResponse(200, "<html><body>access denied</body></html>")
        } catch (_: java.io.IOException) {
            failed = true
        }
        assertTrue(failed)
        assertEquals(
            "{\"tag_name\":\"v0.9.1+88\"}",
            validateTrustedUpdateResponse(200, "{\"tag_name\":\"v0.9.1+88\"}")
        )
    }

    private class RecordingHttpURLConnection(url: URL) : HttpURLConnection(url) {
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun connect() = Unit
    }
}
