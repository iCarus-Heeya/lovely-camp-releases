package com.lovelyreader.ui

/** The single source of truth for Android system Back outside the reader itself. */
internal fun readerBackDestination(screen: Screen): Screen? = when (screen) {
    Screen.Shelf -> null
    Screen.Search, Screen.Settings, is Screen.Reader -> Screen.Shelf
    is Screen.Detail -> Screen.Search
}
