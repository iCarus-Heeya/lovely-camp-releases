package com.lovelyreader.ui

import com.lovelyreader.ui.navigation.nextReaderRoute

/** The single source of truth for Android system Back outside the reader itself. */
internal fun readerBackDestination(screen: Screen): Screen? = nextReaderRoute(screen)

/**
 * The bookshelf is the app's visual root. Consume Android Back there instead
 * of allowing the activity to finish from a 9:16 phone gesture; child pages
 * continue to use [readerBackDestination].
 */
internal fun shouldConsumeRootSystemBack(screen: Screen): Boolean = screen == Screen.Shelf
