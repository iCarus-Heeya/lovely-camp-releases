package com.lovelyreader.ui.video

import com.lovelyreader.video.VideoMediaLink

internal data class NativePlayerControls(
    val hideOnTouch: Boolean,
    val showTimeoutMs: Int,
    val showFullscreen: Boolean
)

/** Prefer a runtime-observed public stream so the app owns usable playback controls. */
internal fun playbackMedia(
    declared: VideoMediaLink?,
    discovered: VideoMediaLink?
): VideoMediaLink? = discovered ?: declared

internal fun nativePlayerControls() = NativePlayerControls(
    hideOnTouch = true,
    showTimeoutMs = 3_000,
    showFullscreen = true
)
