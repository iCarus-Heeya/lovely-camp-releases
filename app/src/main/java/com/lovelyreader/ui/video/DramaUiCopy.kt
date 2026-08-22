package com.lovelyreader.ui.video

internal enum class DramaStatus {
    RootUnavailable,
    Searching,
    NoSearchResults,
    CastTargetUnavailable
}

/** App-owned drama status copy stays Chinese even when a provider returns English metadata. */
internal fun dramaStatusCopy(status: DramaStatus): String = when (status) {
    DramaStatus.RootUnavailable -> "片源暂时连不上，请稍后再试"
    DramaStatus.Searching -> "正在查找剧集…"
    DramaStatus.NoSearchResults -> "没有找到匹配的剧集"
    DramaStatus.CastTargetUnavailable -> "当前片源暂不支持投屏，仍可在手机上观看"
}

internal fun selectedEpisodeDownloadLabel(count: Int): String = "下载已选 ${count.coerceAtLeast(0)} 集"

internal fun userFacingDownloadLocation(location: String?): String =
    location?.takeIf { it.isNotBlank() } ?: "正在准备下载"

/** Download metadata stores opaque provider IDs; never render their URL-bearing prefix. */
internal fun downloadSourceDisplayLabel(sourceId: String): String {
    val sourceKey = sourceId.substringAfterLast("#source-", "")
    val index = sourceKey.substringAfterLast('-').toIntOrNull()
    return index?.let { "片源 ${it + 1}" } ?: "当前片源"
}

/** Episode IDs are internal routing keys and can contain provider URLs; never render them directly. */
internal fun recentEpisodeDisplayLabel(episodeId: String): String {
    val number = Regex("""(?:num-|episode-)(\d+)(?:\D|$)""", RegexOption.IGNORE_CASE)
        .find(episodeId)
        ?.groupValues
        ?.getOrNull(1)
    return number?.let { "第${it.toIntOrNull() ?: it}集" } ?: "上次选集"
}
