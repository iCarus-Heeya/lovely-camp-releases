package com.lovelyreader.ui.video

/** Keeps alternative playback sources reachable on narrow phone screens. */
internal enum class SourceSelectorLayout {
    STATIC,
    HORIZONTALLY_SCROLLABLE
}

internal fun sourceSelectorLayoutFor(sourceCount: Int): SourceSelectorLayout =
    if (sourceCount > 1) SourceSelectorLayout.HORIZONTALLY_SCROLLABLE else SourceSelectorLayout.STATIC
