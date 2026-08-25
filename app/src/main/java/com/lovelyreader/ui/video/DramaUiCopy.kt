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

/** Semantic icon identity kept separate from the visual glyph so tests can
 * verify that every download state remains understandable without color. */
internal fun downloadStatusIconName(status: com.lovelyreader.video.VideoDownloadStatus): String = when (status) {
    com.lovelyreader.video.VideoDownloadStatus.QUEUED -> "schedule"
    com.lovelyreader.video.VideoDownloadStatus.DOWNLOADING -> "cloud_download"
    com.lovelyreader.video.VideoDownloadStatus.COMPLETED -> "check_circle"
    com.lovelyreader.video.VideoDownloadStatus.FAILED -> "error_outline"
}

internal fun noPublicVideoDownloadMessage(): String = "所选剧集没有可下载的公开视频"

internal fun userFacingDownloadLocation(location: String?): String =
    location?.takeIf { it.isNotBlank() }?.let { "已保存到本地" } ?: "正在准备下载"

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

private val compactEpisodeNumberPattern = Regex(
    "^\\s*(?:(?:第\\s*)|(?:episode\\s*)|(?:ep\\s*))?(\\d+)(?:\\s*(?:集|话|期|回|episode|episodes|ep))?[^\\d]*$",
    RegexOption.IGNORE_CASE
)

/** Compact grid labels keep numeric episode identity readable at every count. */
internal fun dramaEpisodeDisplayLabel(raw: String): String {
    val trimmed = raw.trim()
    val number = compactEpisodeNumberPattern.matchEntire(trimmed)
        ?.groupValues
        ?.getOrNull(1)
        ?.toLongOrNull()
    return number?.toString()
        ?: trimmed.removePrefix("第").removeSuffix("集").trim()
}

/** Long editorial titles may ellipsize; numeric episode labels may not. */
internal fun dramaEpisodeLabelCanEllipsize(raw: String): Boolean =
    compactEpisodeNumberPattern.matchEntire(raw.trim()) == null
