package com.lovelyreader.ui

/** Search and detail share a call-site so browse state survives a detail round trip. */
internal fun shouldComposeBrowseSurface(screen: Screen): Boolean =
    screen is Screen.Search || screen is Screen.Detail

internal fun shouldShowSharedAppChrome(screen: Screen): Boolean = screen !is Screen.Reader
