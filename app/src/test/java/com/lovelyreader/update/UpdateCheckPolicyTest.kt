package com.lovelyreader.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckPolicyTest {
    @Test
    fun `automatic check runs on unmetered network when no prior attempt exists`() {
        assertTrue(
            shouldRunAutomaticUpdateCheck(
                nowMillis = 1_000L,
                lastAutomaticAttemptMillis = null,
                isUnmetered = true
            )
        )
    }

    @Test
    fun `automatic check waits one day after a prior attempt`() {
        assertFalse(
            shouldRunAutomaticUpdateCheck(
                nowMillis = UPDATE_AUTOMATIC_CHECK_INTERVAL_MILLIS - 1L,
                lastAutomaticAttemptMillis = 0L,
                isUnmetered = true
            )
        )
        assertTrue(
            shouldRunAutomaticUpdateCheck(
                nowMillis = UPDATE_AUTOMATIC_CHECK_INTERVAL_MILLIS,
                lastAutomaticAttemptMillis = 0L,
                isUnmetered = true
            )
        )
    }

    @Test
    fun `automatic check never runs on a metered network`() {
        assertFalse(
            shouldRunAutomaticUpdateCheck(
                nowMillis = Long.MAX_VALUE,
                lastAutomaticAttemptMillis = null,
                isUnmetered = false
            )
        )
    }
}
