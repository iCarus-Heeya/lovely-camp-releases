package com.lovelyreader.video

import java.net.SocketTimeoutException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoNetworkRetryTest {
    @Test
    fun `video page timeout retries twice before succeeding`() = runTest {
        var attempts = 0
        val retries = mutableListOf<Int>()

        val page = retryVideoPageRequest(
            request = {
                attempts++
                if (attempts < 3) throw SocketTimeoutException("slow Wi-Fi")
                "<html>ok</html>"
            },
            onRetry = { attempt, _ -> retries += attempt },
            wait = {}
        )

        assertEquals("<html>ok</html>", page)
        assertEquals(3, attempts)
        assertEquals(listOf(1, 2), retries)
    }

    @Test
    fun `non transient video request failure is not retried`() = runTest {
        var attempts = 0

        var failed = false
        try {
            retryVideoPageRequest(
                request = { attempts++; throw IllegalArgumentException("bad page") },
                onRetry = { _, _ -> error("must not retry") },
                wait = {}
            )
        } catch (_: IllegalArgumentException) {
            failed = true
        }

        assertTrue(failed)
        assertEquals(1, attempts)
    }
}
