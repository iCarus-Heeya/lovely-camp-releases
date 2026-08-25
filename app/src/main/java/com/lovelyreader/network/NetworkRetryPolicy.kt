package com.lovelyreader.network

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

data class NetworkRetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMillis: Long = 1_200L,
    val maxDelayMillis: Long = 8_000L
) {
    init {
        require(maxAttempts > 0) { "maxAttempts must be positive" }
        require(initialDelayMillis >= 0L) { "initialDelayMillis must not be negative" }
        require(maxDelayMillis >= initialDelayMillis) { "maxDelayMillis must not be smaller than initialDelayMillis" }
    }

    fun delayFor(retryAttempt: Int): Long {
        require(retryAttempt >= 1) { "retryAttempt starts at one" }
        val multiplier = 1L shl (retryAttempt - 1).coerceAtMost(30)
        return (initialDelayMillis * multiplier).coerceAtMost(maxDelayMillis)
    }
}

suspend fun <T> retryNetwork(
    policy: NetworkRetryPolicy = NetworkRetryPolicy(),
    request: suspend () -> T,
    onRetry: (attempt: Int, delayMillis: Long, error: Throwable) -> Unit = { _, _, _ -> },
    wait: suspend (Long) -> Unit = { delay(it) }
): T {
    var attempt = 1
    while (true) {
        try {
            return request()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (!isTransientNetworkFailure(error) || attempt >= policy.maxAttempts) throw error
            val retryAttempt = attempt
            val delayMillis = policy.delayFor(retryAttempt)
            onRetry(retryAttempt, delayMillis, error)
            wait(delayMillis)
            attempt++
        }
    }
}

fun isTransientNetworkFailure(error: Throwable): Boolean =
    generateSequence(error) { it.cause }.any { it is IOException }
