package com.lovelyreader.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPresentationTest {
    @Test
    fun `ordinary settings does not expose sync credentials`() {
        val labels = ordinarySettingsSectionLabels()
        assertTrue(labels.contains("阅读外观"))
        assertTrue(labels.contains("应用更新"))
        assertTrue(labels.contains("来源管理"))
        assertFalse(labels.any { it.contains("同步") || it.contains("Token") || it.contains("Gist") })
    }
}
