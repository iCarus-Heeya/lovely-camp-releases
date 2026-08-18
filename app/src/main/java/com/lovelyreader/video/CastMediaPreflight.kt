package com.lovelyreader.video

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Result of a bounded, header-only reachability check before a receiver is asked to fetch media. */
sealed interface CastPreflightResult {
    data object Ready : CastPreflightResult
    data object Unavailable : CastPreflightResult
}

data class ProbeResponse(val statusCode: Int)

fun interface CastMediaProbe {
    suspend fun probe(target: CastMediaTarget): ProbeResponse
}

class CastMediaPreflight(
    private val probe: CastMediaProbe = HttpCastMediaProbe
) {
    suspend fun check(target: CastMediaTarget?): CastPreflightResult {
        if (target == null) return CastPreflightResult.Unavailable
        val response = runCatching { probe.probe(target) }.getOrNull() ?: return CastPreflightResult.Unavailable
        return if (response.statusCode in 200..299) CastPreflightResult.Ready else CastPreflightResult.Unavailable
    }
}

private object HttpCastMediaProbe : CastMediaProbe {
    override suspend fun probe(target: CastMediaTarget): ProbeResponse = withContext(Dispatchers.IO) {
        val connection = (URL(target.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4_000
            readTimeout = 4_000
            requestMethod = "GET"
            setRequestProperty("Range", "bytes=0-0")
            setRequestProperty("Accept-Encoding", "identity")
        }
        try {
            ProbeResponse(connection.responseCode)
        } finally {
            connection.disconnect()
        }
    }
}

internal object CastReadinessCopy {
    const val CheckingMedia = "正在检查媒体地址…"
    const val MediaUnavailable = "媒体地址暂时无法访问，请稍后再试或切换片源。"
    const val ReceiverPreparing = "电视正在准备播放，请稍候。"
}
