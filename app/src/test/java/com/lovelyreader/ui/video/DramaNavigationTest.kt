package com.lovelyreader.ui.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DramaNavigationTest {
    @Test
    fun `system back returns drama subpages without leaving the app`() {
        assertEquals(DramaPage.Home, dramaBackDestination(DramaPage.Detail))
        assertEquals(DramaPage.Home, dramaBackDestination(DramaPage.Downloads))
        assertEquals(DramaPage.Detail, dramaBackDestination(DramaPage.Player))
        assertNull(dramaBackDestination(DramaPage.Home))
    }

    @Test
    fun `system back at drama home is handled by the shared experience host`() {
        assertEquals(true, shouldHandleDramaHomeSystemBack(DramaPage.Home))
        assertEquals(false, shouldHandleDramaHomeSystemBack(DramaPage.Detail))
    }

    @Test
    fun `drama home keeps the full experience switch while detail uses the compact switch`() {
        assertFalse(usesDetailExperienceSwitch(DramaPage.Home))
        assertTrue(usesDetailExperienceSwitch(DramaPage.Detail))
    }

    @Test
    fun `episode card selection and playback use separate explicit actions`() {
        assertEquals(DramaEpisodeAction.SelectForDownload, dramaEpisodeAction(DramaEpisodeTapTarget.Card))
        assertEquals(DramaEpisodeAction.OpenPlayer, dramaEpisodeAction(DramaEpisodeTapTarget.PlayButton))
    }
}
