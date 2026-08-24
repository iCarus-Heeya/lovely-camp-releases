package com.lovelyreader.ui.video

/**
 * The episode card has two different user intents. Keeping them explicit
 * prevents a full-size play surface from silently swallowing the batch
 * selection gesture.
 */
internal enum class DramaEpisodeTapTarget {
    Card,
    PlayButton
}

internal enum class DramaEpisodeAction {
    SelectForDownload,
    OpenPlayer
}

internal fun dramaEpisodeAction(target: DramaEpisodeTapTarget): DramaEpisodeAction = when (target) {
    DramaEpisodeTapTarget.Card -> DramaEpisodeAction.SelectForDownload
    DramaEpisodeTapTarget.PlayButton -> DramaEpisodeAction.OpenPlayer
}
