package com.lovelyreader.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuntimeMediaDiscoveryPolicyTest {

    @Test
    fun `loaded HLS manifest becomes a direct Cast candidate`() {
        val media = runtimeMediaFromLoadedUrl(
            "https://cdn.example.com/live/episode/master.m3u8?token=temporary"
        )

        assertEquals("https://cdn.example.com/live/episode/master.m3u8?token=temporary", media?.playbackUrl)
        assertEquals(VideoPlaybackMode.DIRECT_MEDIA, media?.playbackMode)
        assertEquals("application/x-mpegURL", castMediaTarget(media)?.contentType)
    }

    @Test
    fun `loaded MP4 becomes a direct Cast candidate`() {
        val media = runtimeMediaFromLoadedUrl("https://media.example.com/episode-2.mp4")

        assertEquals("https://media.example.com/episode-2.mp4", media?.playbackUrl)
        assertEquals("video/mp4", castMediaTarget(media)?.contentType)
    }

    @Test
    fun `non media and non public resources are not offered for Cast`() {
        listOf(
            "https://player.example.com/embed?id=123",
            "https://192.168.1.4/show.m3u8",
            "http://cdn.example.com/show.m3u8",
            "javascript:alert(1)"
        ).forEach { url ->
            assertNull(url, runtimeMediaFromLoadedUrl(url))
        }
    }
}
