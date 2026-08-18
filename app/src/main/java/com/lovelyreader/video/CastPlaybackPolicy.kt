package com.lovelyreader.video

import java.net.URI
import java.util.Locale

data class CastMediaTarget(
    val url: String,
    val contentType: String
)

/**
 * Returns a receiver-loadable public media target, or null when playback must
 * remain local. Cast receivers cannot access app-local URLs, and this app never
 * attempts to unwrap encrypted or otherwise protected streams.
 */
fun castMediaTarget(media: VideoMediaLink?): CastMediaTarget? {
    if (media == null || media.isEncrypted || media.playbackMode != VideoPlaybackMode.DIRECT_MEDIA) return null
    val url = media.playbackUrl.trim()
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    if (!uri.isAbsolute || !uri.scheme.equals("https", ignoreCase = true) ||
        uri.host.isNullOrBlank() || uri.userInfo != null || isNonPublicHost(uri.host)
    ) {
        return null
    }
    val contentType = when (uri.path.orEmpty().lowercase(Locale.ROOT).substringAfterLast('.', "")) {
        "m3u8" -> "application/x-mpegURL"
        "mpd" -> "application/dash+xml"
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        else -> return null
    }
    return CastMediaTarget(url = uri.toString(), contentType = contentType)
}

private fun isNonPublicHost(rawHost: String): Boolean {
    val host = rawHost.removePrefix("[").removeSuffix("]").lowercase(Locale.ROOT)
    if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) return true

    val ipv4 = host.split('.').takeIf { parts ->
        parts.size == 4 && parts.all { part -> part.toIntOrNull()?.let { it in 0..255 } == true }
    }?.map { it.toInt() }
    if (ipv4 != null) {
        val (first, second) = ipv4
        return first == 0 || first == 10 || first == 127 ||
            first == 169 && second == 254 ||
            first == 172 && second in 16..31 ||
            first == 192 && second == 168 ||
            first == 100 && second in 64..127 ||
            first >= 224
    }

    if (':' in host) {
        return host == "::" || host == "::1" || host.startsWith("fc") || host.startsWith("fd") ||
            host.startsWith("fe8") || host.startsWith("fe9") || host.startsWith("fea") || host.startsWith("feb")
    }
    return false
}
