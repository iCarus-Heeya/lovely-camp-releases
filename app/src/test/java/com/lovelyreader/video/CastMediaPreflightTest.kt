package com.lovelyreader.video

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CastMediaPreflightTest {
    @Test
    fun `eligible public media is ready after a successful lightweight probe`() = runTest {
        val target = CastMediaTarget("https://cdn.example.com/episode.m3u8", "application/x-mpegURL")
        val preflight = CastMediaPreflight { ProbeResponse(204) }

        assertEquals(CastPreflightResult.Ready, preflight.check(target))
    }

    @Test
    fun `non success response is unavailable without handing media to a receiver`() = runTest {
        val target = CastMediaTarget("https://cdn.example.com/episode.mp4", "video/mp4")
        val preflight = CastMediaPreflight { ProbeResponse(403) }

        assertEquals(CastPreflightResult.Unavailable, preflight.check(target))
    }

    @Test
    fun `transport failure is unavailable`() = runTest {
        val target = CastMediaTarget("https://cdn.example.com/episode.webm", "video/webm")
        val preflight = CastMediaPreflight { throw java.io.IOException("offline") }

        assertEquals(CastPreflightResult.Unavailable, preflight.check(target))
    }
}
