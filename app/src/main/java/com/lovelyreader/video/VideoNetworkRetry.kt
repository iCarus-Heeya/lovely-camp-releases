package com.lovelyreader.video

import java.io.IOException
import com.lovelyreader.network.NetworkRetryPolicy
import com.lovelyreader.network.isTransientNetworkFailure
import com.lovelyreader.network.retryNetwork
import kotlinx.coroutines.delay

private val videoRetryPolicy = NetworkRetryPolicy(
    maxAttempts = 3,
    initialDelayMillis = 1_200L,
    maxDelayMillis = 2_500L
)

/** Bounded retry for public video catalogue pages; never retries malformed content or parser errors. */
internal suspend fun <T> retryVideoPageRequest(
    request: suspend () -> T,
    onRetry: (attempt: Int, error: Throwable) -> Unit,
    wait: suspend (Long) -> Unit = { millis -> delay(millis) }
): T {
    return retryNetwork(
        policy = videoRetryPolicy,
        request = request,
        onRetry = { attempt, _, error ->
            onRetry(attempt, error)
        },
        wait = wait
    )
}

/**
 * Transport failures can be wrapped differently by Android's URL stack. Keep the fallback
 * limited to IO failures (including DNS, routing, TLS and premature EOF) so parser/business
 * errors are still surfaced immediately.
 */
internal fun isTransientVideoNetworkFailure(error: Throwable): Boolean =
    isTransientNetworkFailure(error)
