package com.lovelyreader.ui

/** The two pieces of reader chrome that are independently hidden or shown. */
internal data class ReaderChromeState(
    val showChrome: Boolean,
    val showBottomMenu: Boolean
)

/**
 * A center tap is also the recovery affordance after a catalogue jump.
 * Catalogue selection hides the chrome while the reader moves to the target
 * page; the next center tap must make the toolbar and menu reachable again
 * instead of only toggling an invisible menu.
 */
internal fun readerChromeStateAfterCenterTap(state: ReaderChromeState): ReaderChromeState =
    if (!state.showChrome) {
        state.copy(showChrome = true, showBottomMenu = true)
    } else {
        state.copy(showBottomMenu = !state.showBottomMenu)
    }
