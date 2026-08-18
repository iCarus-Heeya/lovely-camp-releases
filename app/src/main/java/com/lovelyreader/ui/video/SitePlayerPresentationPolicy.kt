package com.lovelyreader.ui.video

internal enum class SitePlayerContentVisibility { Hidden, Visible, Failed }

/** Avoid a permanently transparent WebView when an external provider is slow or filtered. */
internal fun sitePlayerContentVisibility(
    entryPageFinished: Boolean,
    providerReady: Boolean,
    mainFrameFailed: Boolean
): SitePlayerContentVisibility = when {
    mainFrameFailed -> SitePlayerContentVisibility.Failed
    providerReady || entryPageFinished -> SitePlayerContentVisibility.Visible
    else -> SitePlayerContentVisibility.Hidden
}
