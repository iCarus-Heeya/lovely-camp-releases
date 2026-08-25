package com.lovelyreader.ui.navigation

import com.lovelyreader.ui.Screen

/** Testable reader-experience route seam used by the system Back handler. */
fun nextReaderRoute(screen: Screen): Screen? = when (screen) {
    Screen.Shelf -> null
    Screen.Search, Screen.Settings, is Screen.Reader -> Screen.Shelf
    is Screen.Detail -> Screen.Search
}
