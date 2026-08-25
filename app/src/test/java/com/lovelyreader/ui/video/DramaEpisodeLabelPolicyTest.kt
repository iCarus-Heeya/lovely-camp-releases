package com.lovelyreader.ui.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DramaEpisodeLabelPolicyTest {
    @Test
    fun numericEpisodeLabelsNeverUseEllipsis() {
        assertEquals("1", dramaEpisodeDisplayLabel("第01集"))
        assertEquals("10", dramaEpisodeDisplayLabel("第10集"))
        assertEquals("100", dramaEpisodeDisplayLabel("第100集"))
        assertEquals("11", dramaEpisodeDisplayLabel("第11话"))
        assertEquals("16", dramaEpisodeDisplayLabel("episode 16"))
        assertFalse(dramaEpisodeLabelCanEllipsize("第10集"))
        assertFalse(dramaEpisodeLabelCanEllipsize("第100集"))
    }

    @Test
    fun nonNumericEpisodeTitlesKeepTheirMeaningAndMayEllipsize() {
        assertEquals("特别篇", dramaEpisodeDisplayLabel("特别篇"))
        assertTrue(dramaEpisodeLabelCanEllipsize("特别篇：幕后花絮"))
    }
}
