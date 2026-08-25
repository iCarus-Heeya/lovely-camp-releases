package com.lovelyreader.network

import java.io.IOException
import java.util.concurrent.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NetworkRetryPolicyTest {
    @Test
    fun `retries transient failures with capped exponential delays`() = runTest {
        val waits = mutableListOf<Long>()
        val retryAttempts = mutableListOf<Int>()
        var attempts = 0
        val result = retryNetwork(
            policy = NetworkRetryPolicy(maxAttempts = 4, initialDelayMillis = 100, maxDelayMillis = 250),
            request = {
                attempts++
                if (attempts < 4) throw IOException("route")
                "ok"
            },
            onRetry = { attempt, _, _ -> retryAttempts += attempt },
            wait = { waits += it }
        )

        assertEquals("ok", result)
        assertEquals(4, attempts)
        assertEquals(listOf(1, 2, 3), retryAttempts)
        assertEquals(listOf(100L, 200L, 250L), waits)
    }

    @Test
    fun `does not retry cancellation or non transient failures`() = runTest {
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.test.runTest {
                retryNetwork(
                    policy = NetworkRetryPolicy(),
                    request = { throw CancellationException("cancel") },
                    wait = {}
                )
            }
        }
    }
}
