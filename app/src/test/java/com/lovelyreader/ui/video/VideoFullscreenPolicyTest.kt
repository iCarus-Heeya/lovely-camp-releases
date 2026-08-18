package com.lovelyreader.ui.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFullscreenPolicyTest {
    @Test
    fun `web player fullscreen uses immersive landscape presentation`() {
        val behavior = videoFullscreenBehavior()

        assertTrue(behavior.hideSystemBars)
        assertTrue(behavior.preferLandscape)
    }
}
