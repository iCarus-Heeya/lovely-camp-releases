package com.lovelyreader.ui.video

import com.lovelyreader.video.VideoMediaLink
import com.lovelyreader.video.VideoPlaybackMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CastPresentationPolicyTest {
    @Test
    fun `does not configure Cast for a provider-only player`() {
        val provider = VideoMediaLink(
            playbackUrl = "https://example.com/episode",
            playbackMode = VideoPlaybackMode.SITE_PLAYER
        )

        assertFalse(shouldConfigureCast(provider))
    }

    @Test
    fun `configures Cast when public direct media is eligible`() {
        assertTrue(shouldConfigureCast(VideoMediaLink("https://cdn.example/episode.m3u8")))
    }
}
