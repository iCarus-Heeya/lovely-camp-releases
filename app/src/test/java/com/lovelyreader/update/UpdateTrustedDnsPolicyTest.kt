package com.lovelyreader.update

import java.net.InetAddress
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateTrustedDnsPolicyTest {
    @Test
    fun `fallback is limited to the GitHub hosts used by the updater`() {
        assertTrue(isTrustedUpdateHost("api.github.com"))
        assertTrue(isTrustedUpdateHost("github.com"))
        assertTrue(isTrustedUpdateHost("release-assets.githubusercontent.com"))
        assertFalse(isTrustedUpdateHost("github.com.attacker.example"))
        assertFalse(isTrustedUpdateHost("example.com"))
    }

    @Test
    fun `asset redirects are limited to GitHub delivery hosts`() {
        assertTrue(isAllowedUpdateAssetHost("github.com"))
        assertTrue(isAllowedUpdateAssetHost("release-assets.githubusercontent.com"))
        assertFalse(isAllowedUpdateAssetHost("api.github.com"))
        assertFalse(isAllowedUpdateAssetHost("downloads.attacker.example"))
    }

    @Test
    fun `HTML interception responses trigger a trusted retry but GitHub JSON does not`() {
        assertTrue(shouldRetryTrustedUpdateResponse(200, "<html><body>blocked</body></html>"))
        assertTrue(shouldRetryTrustedUpdateResponse(403, "<!doctype html>access denied"))
        assertTrue(shouldRetryTrustedUpdateResponse(451, "Request blocked by network policy"))
        assertTrue(shouldRetryTrustedUpdateResponse(403, ""))
        assertFalse(shouldRetryTrustedUpdateResponse(403, "{\"message\":\"API rate limit exceeded\"}"))
        assertFalse(shouldRetryTrustedUpdateResponse(200, "{\"tag_name\":\"v0.9.1+88\"}"))
    }

    @Test
    fun `trusted resolver uses fixed public bootstrap addresses`() {
        assertEquals(listOf("223.5.5.5", "223.6.6.6"), updateTrustedDnsBootstrapAddresses())
    }

    @Test
    fun `unsafe system answers trigger fallback while normal public answers do not`() {
        assertEquals(
            null,
            updateTrustedDnsFallbackReason(
                "api.github.com",
                listOf(InetAddress.getByName("::1"), InetAddress.getByName("20.205.243.168"))
            )
        )
        assertEquals(
            null,
            updateTrustedDnsFallbackReason(
                "api.github.com",
                listOf(InetAddress.getByName("20.205.243.168"))
            )
        )
    }

    @Test
    fun `mixed update answers retain the public address and reject special use ranges`() {
        val selection = selectUpdateTrustedDnsAddresses(
            "api.github.com",
            listOf(
                InetAddress.getByName("::1"),
                InetAddress.getByName("fc00::1"),
                InetAddress.getByName("2001:db8::1"),
                InetAddress.getByName("20.205.243.168")
            )
        )

        assertEquals(listOf("20.205.243.168"), selection.usableAddresses.map { it.hostAddress })
        assertEquals(3, selection.rejectedAddressCount)
        assertEquals(null, selection.fallbackReason)
        assertTrue(selection.shouldUseSystemAddresses)
    }

    @Test
    fun `trusted transport URL preserves HTTPS request identity and path`() {
        val original = URL("https://api.github.com/repos/iCarus-Heeya/lovely-camp-releases/releases/latest")
        val direct = updateTrustedAddressUrl(original, InetAddress.getByName("20.205.243.168"))

        assertEquals("20.205.243.168", direct.host)
        assertEquals(original.protocol, direct.protocol)
        assertEquals(original.path, direct.path)
        assertEquals(original.query, direct.query)
    }

    @Test
    fun `trusted resolver accepts only literal public IPv4 answers`() {
        assertEquals("185.199.111.133", parseUpdateTrustedIpv4Address("185.199.111.133")?.hostAddress)
        assertEquals(null, parseUpdateTrustedIpv4Address("release-assets.githubusercontent.com"))
        assertEquals(null, parseUpdateTrustedIpv4Address("256.1.1.1"))
    }

    @Test
    fun `malformed trusted DNS JSON can be skipped before a later valid answer`() {
        val malformed = runCatching { parseUpdateTrustedDnsAnswer("<html>blocked</html>") }
        val valid = parseUpdateTrustedDnsAnswer(
            """{"Answer":[{"type":1,"data":"185.199.111.133"}]}"""
        )

        assertTrue(malformed.isFailure)
        assertEquals(listOf("185.199.111.133"), valid.map { it.hostAddress })
    }

    @Test
    fun `special use IPv4 ranges are rejected as trusted answers`() {
        assertTrue(isUnsafeUpdateAddress(InetAddress.getByName("100.64.0.1")))
        assertTrue(isUnsafeUpdateAddress(InetAddress.getByName("192.0.2.1")))
        assertTrue(isUnsafeUpdateAddress(InetAddress.getByName("198.51.100.2")))
        assertTrue(isUnsafeUpdateAddress(InetAddress.getByName("203.0.113.5")))
        assertFalse(isUnsafeUpdateAddress(InetAddress.getByName("20.205.243.168")))
    }

    @Test
    fun `special use IPv6 ranges are rejected as trusted answers`() {
        assertTrue(isUnsafeUpdateAddress(InetAddress.getByName("fc00::1")))
        assertTrue(isUnsafeUpdateAddress(InetAddress.getByName("2001:db8::1")))
        assertFalse(isUnsafeUpdateAddress(InetAddress.getByName("2001:4860:4860::8888")))
    }

    @Test
    fun `trusted update transport rejects HTTP before opening a socket`() {
        var failed = false
        try {
            TrustedUpdateAddressHttpsConnectionFactory(
                hostname = "github.com",
                addresses = listOf(InetAddress.getByName("20.205.243.168")),
                connectTimeoutMillis = 1_000
            )(URL("http://github.com/app.apk"))
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }
}
