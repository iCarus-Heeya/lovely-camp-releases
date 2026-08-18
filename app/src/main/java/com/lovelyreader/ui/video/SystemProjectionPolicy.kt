package com.lovelyreader.ui.video

import android.provider.Settings

/** Lets an OEM system (such as HarmonyOS) own its native screen-projection UX. */
internal fun systemProjectionSettingsAction(): String = Settings.ACTION_CAST_SETTINGS
