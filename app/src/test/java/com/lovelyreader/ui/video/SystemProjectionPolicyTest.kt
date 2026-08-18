package com.lovelyreader.ui.video

import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Test

class SystemProjectionPolicyTest {
    @Test
    fun `projection fallback opens the system cast route`() {
        assertEquals(Settings.ACTION_CAST_SETTINGS, systemProjectionSettingsAction())
    }
}
