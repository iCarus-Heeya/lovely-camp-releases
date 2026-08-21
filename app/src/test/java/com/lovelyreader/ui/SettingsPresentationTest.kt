package com.lovelyreader.ui

import org.junit.Assert.assertEquals
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

    @Test
    fun `high fidelity settings orders update as the visual hero and keeps user copy only`() {
        assertEquals(
            listOf("应用更新", "阅读外观", "来源管理"),
            highFidelitySettingsSectionOrder()
        )
        assertEquals("应用更新", highFidelitySettingsTitle())
        assertTrue(highFidelityUpdateDescription().contains("Wi-Fi"))
        assertFalse(highFidelityUpdateDescription().contains("APK"))
        assertFalse(highFidelityUpdateDescription().contains("SHA256"))
    }

    @Test
    fun `high fidelity update action never leaks delivery metadata`() {
        assertEquals("检查更新", highFidelityUpdateActionLabel(null))
        assertEquals("下载并安装 0.8.17", highFidelityUpdateActionLabel("0.8.17"))
        assertFalse(highFidelityUpdateActionLabel("0.8.17").contains("APK", ignoreCase = true))
        assertFalse(highFidelityUpdateActionLabel("0.8.17").contains("SHA", ignoreCase = true))
    }

    @Test
    fun `high fidelity update notes keep only user readable changes`() {
        val notes = highFidelityUserUpdateNotes(
            "阅读体验优化\n\nAPK: lovely-camp-v0.8.17.apk\nSHA256: abc\n测试：通过"
        )

        assertEquals("阅读体验优化", notes)
    }
}
