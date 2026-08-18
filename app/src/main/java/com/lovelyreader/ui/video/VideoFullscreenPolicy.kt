package com.lovelyreader.ui.video

internal data class VideoFullscreenBehavior(
    val hideSystemBars: Boolean,
    val preferLandscape: Boolean
)

internal fun videoFullscreenBehavior() = VideoFullscreenBehavior(
    hideSystemBars = true,
    preferLandscape = true
)
