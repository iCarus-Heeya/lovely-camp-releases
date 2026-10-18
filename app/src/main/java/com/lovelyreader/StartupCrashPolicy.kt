package com.lovelyreader

/** Startup diagnostics must not turn a later network crash into a permanent launch gate. */
internal fun shouldPersistStartupCrash(appReady: Boolean): Boolean = !appReady
