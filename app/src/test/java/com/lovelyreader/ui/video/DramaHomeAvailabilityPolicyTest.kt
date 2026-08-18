package com.lovelyreader.ui.video

import com.lovelyreader.video.VideoRootResolutionStatus
import com.lovelyreader.video.VideoSiteRoot
import org.junit.Assert.assertEquals
import org.junit.Test

class DramaHomeAvailabilityPolicyTest {

    @Test
    fun `verified and cached roots stay silent on the drama home`() {
        val verified = DramaRootUiState(VideoSiteRoot("https://video.example", 1), false, false, "resolved")
        val cached = DramaRootUiState(VideoSiteRoot("https://video.example", 1), false, true, "cached")

        assertEquals(null, dramaHomeAvailabilityMessage(verified))
        assertEquals(null, dramaHomeAvailabilityMessage(cached))
    }

    @Test
    fun `only an unavailable root gives a user facing search hint`() {
        val unavailable = DramaRootUiState(root = null, isRefreshing = false, message = "unavailable")

        assertEquals("片源暂时连不上，请稍后再试", dramaHomeAvailabilityMessage(unavailable))
    }
}
