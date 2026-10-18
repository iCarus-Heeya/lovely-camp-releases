package com.lovelyreader.ui.video.ranking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlinx.coroutines.runBlocking

class RankingPosterPolicyTest {
    @Test
    fun allowsOnlyApprovedHttpsPosterHosts() {
        assertTrue(isSafeRankingRemotePoster("https://commons.wikimedia.org/wiki/Special:FilePath/poster.jpg"))
        assertTrue(isSafeRankingRemotePoster("https://upload.wikimedia.org/wikipedia/zh/a/ab/poster.jpg"))
        assertTrue(isSafeRankingRemotePoster("https://thumb.wikimedia.org/wikipedia/zh/thumb/a/ab/poster.jpg/330px-poster.jpg"))
        assertTrue(isSafeRankingRemotePoster("https://static.tvmaze.com/uploads/images/original_untouched/poster.jpg"))
        assertTrue(isSafeRankingRemotePoster("https://m.media-amazon.com/images/M/poster.jpg"))
        assertFalse(isSafeRankingRemotePoster("http://upload.wikimedia.org/poster.jpg"))
        assertFalse(isSafeRankingRemotePoster("https://example.com/poster.jpg"))
    }

    @Test
    fun resolvesOnlyAllowlistedRedirectTargets() {
        val current = "https://commons.wikimedia.org/wiki/Special:FilePath/poster.jpg"
        assertEquals(
            "https://commons.wikimedia.org/wiki/Special:FilePath/next.jpg",
            resolveSafeRankingPosterRedirect(current, "/wiki/Special:FilePath/next.jpg")
        )
        assertNull(resolveSafeRankingPosterRedirect(current, "https://example.com/poster.jpg"))
        assertNull(resolveSafeRankingPosterRedirect(current, "http://upload.wikimedia.org/poster.jpg"))
    }

    @Test
    fun poster_timeout_is_a_placeholder_failure_not_an_app_crash() = runBlocking {
        val result = loadRankingRemotePosterWith("https://static.tvmaze.com/poster-timeout.jpg") {
            object : HttpURLConnection(URL(it)) {
                override fun connect() = Unit
                override fun disconnect() = Unit
                override fun usingProxy() = false
                override fun getResponseCode(): Int = throw SocketTimeoutException("poster timeout")
            }
        }
        assertNull(result)
    }

    @Test
    fun blank_content_type_is_allowed_but_html_is_rejected() {
        assertTrue(rankingPosterContentTypeAllowed(null))
        assertTrue(rankingPosterContentTypeAllowed(""))
        assertTrue(rankingPosterContentTypeAllowed("image/jpeg; charset=binary"))
        assertFalse(rankingPosterContentTypeAllowed("text/html; charset=utf-8"))
    }
}
