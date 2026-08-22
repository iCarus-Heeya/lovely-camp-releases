package com.lovelyreader.ui.video

import com.lovelyreader.video.VideoMediaLink
import com.lovelyreader.video.VideoPlaybackMode
import com.lovelyreader.video.castMediaTarget

/** Small, deterministic rules shared by the compact episode and playback surfaces. */
/** Five columns match the narrow episode tiles in the 9:16 detail concept. */
internal fun compactEpisodeGridColumns(): Int = 5

/** The compact detail page keeps the batch action disabled until selection exists. */
internal fun batchDownloadEnabled(selectedEpisodeCount: Int): Boolean = selectedEpisodeCount > 0

/** Keep the drama name visible when the player is reached from a resume card or episode grid. */
internal fun dramaPlayerHeaderLabel(titleName: String?, episodeLabel: String?): String =
    listOfNotNull(
        titleName?.takeIf(String::isNotBlank),
        episodeLabel?.takeIf(String::isNotBlank)
    ).joinToString(" · ").ifBlank { "视频播放" }

internal fun canDownloadFromMedia(media: VideoMediaLink?): Boolean =
    media?.playbackMode == VideoPlaybackMode.DIRECT_MEDIA && !media.directMp4Url.isNullOrBlank()

internal fun canCastMedia(media: VideoMediaLink?): Boolean = castMediaTarget(media) != null

/** Provider-only playback has no public media URL to hand to a Cast receiver. */
internal fun shouldConfigureCast(media: VideoMediaLink?): Boolean = canCastMedia(media)
