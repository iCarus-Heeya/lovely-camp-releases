package com.lovelyreader.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RedirectPolicyTest {
    private val safety = SourceSafety(
        baseUrl = "https://ixdzs8.com",
        disallowedPrefixes = listOf("/down", "/download/")
    )

    @Test
    fun allowsSafeSameSourceRedirect() {
        val result = RedirectPolicy.follow(
            currentUrl = "https://ixdzs8.com/read/183714/",
            location = "/read/183714/p1.html",
            safety = safety
        )

        assertEquals("https://ixdzs8.com/read/183714/p1.html", result.getOrThrow())
    }

    @Test
    fun rejectsRedirectToDisallowedPath() {
        val result = RedirectPolicy.follow(
            currentUrl = "https://ixdzs8.com/read/183714/",
            location = "/download/183714.txt",
            safety = safety
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun rejectsRedirectToCrossSiteHost() {
        val result = RedirectPolicy.follow(
            currentUrl = "https://ixdzs8.com/read/183714/",
            location = "https://example.com/read/183714/",
            safety = safety
        )

        assertTrue(result.isFailure)
    }
}
