package com.lovelyreader.ui.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DramaNavigationTest {
    @Test
    fun `system back returns drama subpages without leaving the app`() {
        assertEquals(DramaPage.Home, dramaBackDestination(DramaPage.Detail))
        assertEquals(DramaPage.Home, dramaBackDestination(DramaPage.Downloads))
        assertEquals(DramaPage.Detail, dramaBackDestination(DramaPage.Player))
        assertNull(dramaBackDestination(DramaPage.Home))
    }
}
