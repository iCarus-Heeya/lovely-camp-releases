package com.lovelyreader.ui.video

import com.lovelyreader.video.VideoMediaLink
import com.lovelyreader.video.VideoPlaybackMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativePlaybackControlsPolicyTest {
    @Test
    fun `runtime public media replaces a site player for native playback controls`() {
        val sitePlayer = VideoMediaLink(
            playbackUrl = "https://example.com/episode",
            playbackMode = VideoPlaybackMode.SITE_PLAYER
        )
        val discovered = VideoMediaLink("https://cdn.example.com/episode/master.m3u8")

        assertEquals(discovered, playbackMedia(sitePlayer, discovered))
    }

    @Test
    fun `native player reveals controls on touch then lets them hide during viewing`() {
        val controls = nativePlayerControls()

        assertTrue(controls.hideOnTouch)
        assertEquals(3_000, controls.showTimeoutMs)
        assertTrue(controls.showFullscreen)
    }

    @Test
    fun `site player stays in web view until a public runtime media url is observed`() {
        val sitePlayer = VideoMediaLink(
            playbackUrl = "https://example.com/episode",
            playbackMode = VideoPlaybackMode.SITE_PLAYER
        )

        assertEquals(sitePlayer, playbackMedia(sitePlayer, null))
    }
}
