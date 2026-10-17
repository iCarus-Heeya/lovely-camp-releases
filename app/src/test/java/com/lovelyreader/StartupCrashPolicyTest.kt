package com.lovelyreader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupCrashPolicyTest {
    @Test
    fun `only crashes before the first content frame are persisted as startup failures`() {
        assertTrue(shouldPersistStartupCrash(appReady = false))
        assertFalse(shouldPersistStartupCrash(appReady = true))
    }
}
