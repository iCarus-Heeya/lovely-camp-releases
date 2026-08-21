package com.lovelyreader.video

private fun String?.orIfBlank(fallback: String?): String? = this?.takeIf(String::isNotBlank) ?: fallback?.takeIf(String::isNotBlank)

/** Keeps detail-page truth while filling fields the detail page did not expose from the search card. */
fun mergeVideoTitleMetadata(search: VideoTitle, detail: VideoTitle): VideoTitle = detail.copy(
    posterUrl = detail.posterUrl.orIfBlank(search.posterUrl),
    summary = detail.summary.orIfBlank(search.summary),
    releaseInfo = detail.releaseInfo.orIfBlank(search.releaseInfo),
    castInfo = detail.castInfo.orIfBlank(search.castInfo),
    categoryInfo = detail.categoryInfo.orIfBlank(search.categoryInfo),
    updateInfo = detail.updateInfo.orIfBlank(search.updateInfo)
)
