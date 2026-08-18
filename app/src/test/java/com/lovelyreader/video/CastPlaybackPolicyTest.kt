package com.lovelyreader.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CastPlaybackPolicyTest {

    @Test
    fun `ordinary HTML episode pages are never Cast targets`() {
        assertNull(
            castMediaTarget(
                VideoMediaLink(playbackUrl = "https://88ystv.example/vod-play-123.html")
            )
        )
    }
    @Test
    fun `public HTTPS HLS playback is eligible for Cast`() {
        val target = castMediaTarget(
            VideoMediaLink(playbackUrl = "https://cdn.example.com/shows/episode-1/master.m3u8?token=public")
        )

        assertEquals("https://cdn.example.com/shows/episode-1/master.m3u8?token=public", target?.url)
        assertEquals("application/x-mpegURL", target?.contentType)
    }

    @Test
    fun `public HTTPS MP4 playback is eligible for Cast`() {
        val target = castMediaTarget(
            VideoMediaLink(playbackUrl = "https://media.example.com/episode-2.MP4")
        )

        assertEquals("video/mp4", target?.contentType)
    }

    @Test
    fun `encrypted media is never handed to Cast`() {
        val target = castMediaTarget(
            VideoMediaLink(
                playbackUrl = "https://cdn.example.com/protected/master.m3u8",
                isEncrypted = true
            )
        )

        assertNull(target)
    }

    @Test
    fun `non HTTPS and non public playback URLs are rejected`() {
        val rejected = listOf(
            "http://cdn.example.com/episode.mp4",
            "file:///storage/emulated/0/episode.mp4",
            "https://localhost/episode.mp4",
            "https://127.0.0.1/episode.mp4",
            "https://192.168.1.10/episode.mp4",
            "https://user:password@cdn.example.com/episode.mp4"
        )

        rejected.forEach { url ->
            assertNull("Expected Cast to reject $url", castMediaTarget(VideoMediaLink(url)))
        }
    }

    @Test
    fun `unrecognized media resources remain local playback only`() {
        assertNull(
            castMediaTarget(VideoMediaLink("https://cdn.example.com/watch?id=episode-3"))
        )
    }
}
