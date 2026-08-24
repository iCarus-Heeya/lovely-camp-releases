package com.lovelyreader.ui

/** The single source of truth for Android system Back outside the reader itself. */
internal fun readerBackDestination(screen: Screen): Screen? = when (screen) {
    Screen.Shelf -> null
    Screen.Search, Screen.Settings, is Screen.Reader -> Screen.Shelf
    is Screen.Detail -> Screen.Search
}

/**
 * The bookshelf is the app's visual root. Consume Android Back there instead
 * of allowing the activity to finish from a 9:16 phone gesture; child pages
 * continue to use [readerBackDestination].
 */
internal fun shouldConsumeRootSystemBack(screen: Screen): Boolean = screen == Screen.Shelf
