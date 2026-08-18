package com.lovelyreader.video

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.delay

private const val VIDEO_REQUEST_MAX_ATTEMPTS = 3
private val videoRetryDelaysMillis = longArrayOf(1_200L, 2_500L)

/** Bounded retry for public video catalogue pages; never retries malformed content or parser errors. */
internal suspend fun <T> retryVideoPageRequest(
    request: suspend () -> T,
    onRetry: (attempt: Int, error: Throwable) -> Unit,
    wait: suspend (Long) -> Unit = { millis -> delay(millis) }
): T {
    repeat(VIDEO_REQUEST_MAX_ATTEMPTS) { index ->
        try {
            return request()
        } catch (error: Throwable) {
            val retryAttempt = index + 1
            if (!isTransientVideoNetworkFailure(error) || retryAttempt >= VIDEO_REQUEST_MAX_ATTEMPTS) throw error
            onRetry(retryAttempt, error)
            wait(videoRetryDelaysMillis[index])
        }
    }
    error("video retry loop terminated unexpectedly")
}

private fun isTransientVideoNetworkFailure(error: Throwable): Boolean = error is SocketTimeoutException ||
    error is ConnectException ||
    error is UnknownHostException
