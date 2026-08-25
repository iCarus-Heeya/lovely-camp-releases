package com.lovelyreader.source

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceHealthTest {
    @Test
    fun `repeated failures cool a source down and success restores it`() {
        var now = 1_000L
        val ledger = SourceHealthLedger(
            failureThreshold = 2,
            cooldownMillis = 5_000L,
            nowMillis = { now }
        )

        ledger.recordFailure("qinkan", "timeout")
        assertTrue(ledger.canRequest("qinkan"))
        ledger.recordFailure("qinkan", "dns")
        assertFalse(ledger.canRequest("qinkan"))

        now += 5_000L
        assertTrue(ledger.canRequest("qinkan"))
        ledger.recordSuccess("qinkan")
        assertTrue(ledger.snapshot("qinkan").consecutiveFailures == 0)
    }

    @Test
    fun `cooldown message distinguishes provider failure from empty content`() {
        val ledger = SourceHealthLedger(failureThreshold = 1, cooldownMillis = 10_000L, nowMillis = { 10L })
        ledger.recordFailure("zxcs", "SocketTimeoutException")

        assertTrue(ledger.snapshot("zxcs").message.contains("暂时不可用"))
        assertTrue(ledger.snapshot("zxcs").message.contains("SocketTimeoutException"))
    }

    @Test
    fun `cooldown expiry resets the failure streak before the next request`() {
        var now = 0L
        val ledger = SourceHealthLedger(
            failureThreshold = 2,
            cooldownMillis = 1_000L,
            nowMillis = { now }
        )

        ledger.recordFailure("qisuwang", "timeout")
        ledger.recordFailure("qisuwang", "dns")
        assertFalse(ledger.canRequest("qisuwang"))

        now = 1_000L
        assertTrue(ledger.canRequest("qisuwang"))
        ledger.recordFailure("qisuwang", "timeout-again")
        assertTrue(ledger.canRequest("qisuwang"))
    }
}
