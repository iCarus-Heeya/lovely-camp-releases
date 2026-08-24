package com.lovelyreader.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckPolicyTest {
    @Test
    fun `automatic check runs on any validated network when no prior attempt exists`() {
        assertTrue(
            shouldRunAutomaticUpdateCheck(
                nowMillis = 1_000L,
                lastAutomaticAttemptMillis = null,
                isValidatedNetwork = true
            )
        )
    }

    @Test
    fun `automatic check waits one day after a prior attempt`() {
        assertFalse(
            shouldRunAutomaticUpdateCheck(
                nowMillis = UPDATE_AUTOMATIC_CHECK_INTERVAL_MILLIS - 1L,
                lastAutomaticAttemptMillis = 0L,
                isValidatedNetwork = true
            )
        )
        assertTrue(
            shouldRunAutomaticUpdateCheck(
                nowMillis = UPDATE_AUTOMATIC_CHECK_INTERVAL_MILLIS,
                lastAutomaticAttemptMillis = 0L,
                isValidatedNetwork = true
            )
        )
    }

    @Test
    fun `automatic check waits when the active network is not validated`() {
        assertFalse(
            shouldRunAutomaticUpdateCheck(
                nowMillis = Long.MAX_VALUE,
                lastAutomaticAttemptMillis = null,
                isValidatedNetwork = false
            )
        )
    }

    @Test
    fun `validated mobile network is eligible for startup discovery`() {
        assertTrue(
            shouldRunAutomaticUpdateCheck(
                nowMillis = 1_000L,
                lastAutomaticAttemptMillis = null,
                isValidatedNetwork = true
            )
        )
    }
}
