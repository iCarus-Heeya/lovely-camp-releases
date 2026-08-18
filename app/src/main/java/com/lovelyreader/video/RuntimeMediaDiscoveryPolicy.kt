package com.lovelyreader.video

/** Observes public media actually loaded by the browser; it never rewrites player data. */
fun runtimeMediaFromLoadedUrl(loadedUrl: String): VideoMediaLink? {
    val media = VideoMediaLink(playbackUrl = loadedUrl.trim())
    return media.takeIf { castMediaTarget(it) != null }
}
