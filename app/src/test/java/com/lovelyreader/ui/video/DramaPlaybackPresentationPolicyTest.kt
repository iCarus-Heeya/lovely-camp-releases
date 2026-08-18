package com.lovelyreader.ui.video

import com.lovelyreader.video.VideoMediaLink
import com.lovelyreader.video.VideoPlaybackMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DramaPlaybackPresentationPolicyTest {
    @Test
    fun `episode picker uses four compact columns`() {
        assertEquals(4, compactEpisodeGridColumns())
    }

    @Test
    fun `site player describes download and cast as unavailable rather than offering actions`() {
        val sitePlayer = VideoMediaLink(
            playbackUrl = "https://example.com/episode",
            playbackMode = VideoPlaybackMode.SITE_PLAYER
        )

        assertFalse(canDownloadFromMedia(sitePlayer))
        assertFalse(canCastMedia(sitePlayer))
    }

    @Test
    fun `public direct mp4 retains download and cast availability`() {
        val directMedia = VideoMediaLink(
            playbackUrl = "https://cdn.example/episode.mp4",
            directMp4Url = "https://cdn.example/episode.mp4"
        )

        assertTrue(canDownloadFromMedia(directMedia))
        assertTrue(canCastMedia(directMedia))
    }
}
