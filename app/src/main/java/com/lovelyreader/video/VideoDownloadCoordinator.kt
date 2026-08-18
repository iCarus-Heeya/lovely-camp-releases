package com.lovelyreader.video

import java.net.URI

interface DownloadGateway {
    fun download(url: String): DownloadResult
}

sealed interface DownloadResult {
    data object Accepted : DownloadResult
    data object Rejected : DownloadResult
}

class VideoDownloadCoordinator(
    private val gateway: DownloadGateway
) {
    fun download(url: String): DownloadResult {
        val uri = runCatching { URI(url) }.getOrNull() ?: return DownloadResult.Rejected
        val path = uri.path ?: return DownloadResult.Rejected
        val lowerUrl = url.lowercase()

        if (uri.scheme?.equals("https", ignoreCase = true) != true ||
            !path.endsWith(".mp4", ignoreCase = true) ||
            lowerUrl.contains("blob:") ||
            lowerUrl.contains(".m3u8") ||
            lowerUrl.contains(".mpd") ||
            lowerUrl.contains("encrypted") ||
            lowerUrl.contains("encryption") ||
            lowerUrl.contains("drm")
        ) {
            return DownloadResult.Rejected
        }

        return gateway.download(url)
    }
}
