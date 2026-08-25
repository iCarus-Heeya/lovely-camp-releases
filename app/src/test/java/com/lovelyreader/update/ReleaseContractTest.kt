package com.lovelyreader.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseContractTest {
    @Test
    fun `parses a version tag with monotonic code`() {
        assertEquals(
            ReleaseVersionContract("0.9.0", 87L),
            parseReleaseVersionTag("v0.9.0+87")
        )
    }

    @Test
    fun `rejects tags without an Android version code`() {
        assertNull(parseReleaseVersionTag("v0.9.0"))
        assertNull(parseReleaseVersionTag("release-0.9.0+87"))
    }

    @Test
    fun `normalizes and validates SHA256 values from GitHub`() {
        val digest = "A".repeat(64)
        assertEquals("a".repeat(64), normalizeSha256("sha256:$digest"))
        assertEquals("b".repeat(64), normalizeSha256("  ${"b".repeat(64)}  "))
        assertNull(normalizeSha256("sha256:bad"))
    }
}
