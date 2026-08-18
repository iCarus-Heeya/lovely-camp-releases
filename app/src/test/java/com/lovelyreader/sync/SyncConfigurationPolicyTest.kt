package com.lovelyreader.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncConfigurationPolicyTest {
    @Test
    fun `sync can only be enabled when the user supplies both GitHub credentials`() {
        assertFalse(canEnableSync(null, null))
        assertFalse(canEnableSync("token", ""))
        assertFalse(canEnableSync("", "gist"))
        assertTrue(canEnableSync("token", "gist"))
    }
}
