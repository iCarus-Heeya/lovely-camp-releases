package com.lovelyreader.ui.video

import com.lovelyreader.video.VideoMediaLink
import com.lovelyreader.video.VideoPlaybackMode
import com.lovelyreader.video.castMediaTarget

/** Small, deterministic rules shared by the compact episode and playback surfaces. */
internal fun compactEpisodeGridColumns(): Int = 4

internal fun canDownloadFromMedia(media: VideoMediaLink?): Boolean =
    media?.playbackMode == VideoPlaybackMode.DIRECT_MEDIA && !media.directMp4Url.isNullOrBlank()

internal fun canCastMedia(media: VideoMediaLink?): Boolean = castMediaTarget(media) != null

/** Provider-only playback has no public media URL to hand to a Cast receiver. */
internal fun shouldConfigureCast(media: VideoMediaLink?): Boolean = canCastMedia(media)
