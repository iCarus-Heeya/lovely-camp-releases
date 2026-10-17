package com.lovelyreader.ui.video.ranking

import com.lovelyreader.video.ranking.RankingEntry

/**
 * Describes which user-visible fields are actually usable on a ranking card.
 * Missing metadata is deliberately surfaced instead of being represented by
 * an empty block or an invented poster/summary.
 */
internal data class RankingMetadataQuality(
    val hasChineseTitle: Boolean,
    val hasSummary: Boolean,
    val hasPoster: Boolean,
    val hasRegion: Boolean,
    val hasRating: Boolean
) {
    val isComplete: Boolean
        get() = hasChineseTitle && hasSummary && hasPoster && hasRegion && hasRating

    val missingLabels: List<String>
        get() = buildList {
            if (!hasChineseTitle) add("中文名待补充")
            if (!hasSummary) add("简介待补充")
            if (!hasPoster) add("海报待补充")
            if (!hasRegion) add("地区待补充")
            if (!hasRating) add("评分待补充")
        }

    val displayLabel: String
        get() = if (isComplete) "资料完整" else "资料待补：${missingLabels.joinToString("、") { it.removeSuffix("待补充") }}"
}

internal data class RankingPageMetadataCoverage(
    val total: Int,
    val chineseTitleCount: Int,
    val summaryCount: Int,
    val posterCount: Int,
    val regionCount: Int,
    val ratingCount: Int
) {
    val shortLabel: String
        get() = "中文名 $chineseTitleCount/$total · 简介 $summaryCount/$total · 海报 $posterCount/$total"
}

/** Fixed visual contract used by the card and its regression tests. */
internal data class RankingVisualContract(
    val posterWidthDp: Int = 84,
    val posterHeightDp: Int = 112,
    val minimumTouchTargetDp: Int = 48,
    val titleMaxLines: Int = 3,
    val summaryMaxLines: Int = 4
)

internal val rankingVisualContract = RankingVisualContract()

internal fun rankingMetadataQuality(
    entry: RankingEntry,
    preferredTitle: String = rankingPreferredDisplayTitle(
        entry.displayTitle,
        entry.originalTitle,
        entry.aliases + entry.searchTitle
    )
): RankingMetadataQuality = RankingMetadataQuality(
    hasChineseTitle = hasSimplifiedChineseTitle(preferredTitle),
    hasSummary = hasUsableChineseSummary(entry.summary),
    hasPoster = !entry.posterUrl.isNullOrBlank(),
    hasRegion = entry.countries.isNotEmpty(),
    hasRating = entry.aggregateScore != null || entry.sourceRatings.any { it.value != null }
)

internal fun rankingPageMetadataCoverage(entries: List<RankingEntry>): RankingPageMetadataCoverage {
    var chineseTitles = 0
    var summaries = 0
    var posters = 0
    var regions = 0
    var ratings = 0
    entries.forEach { entry ->
        val quality = rankingMetadataQuality(entry)
        if (quality.hasChineseTitle) chineseTitles += 1
        if (quality.hasSummary) summaries += 1
        if (quality.hasPoster) posters += 1
        if (quality.hasRegion) regions += 1
        if (quality.hasRating) ratings += 1
    }
    return RankingPageMetadataCoverage(entries.size, chineseTitles, summaries, posters, regions, ratings)
}

internal fun rankingCardContentDescription(
    entry: RankingEntry,
    preferredTitle: String,
    aggregateScoreLabel: String
): String = buildString {
    append(entry.rank?.let { "第 $it 名" } ?: "当前榜单")
    append("，《$preferredTitle》")
    entry.rankingScore?.let { append("，排名分 ${rankingScoreTag(it)}") }
    entry.aggregateScore?.let { append("，$aggregateScoreLabel ${rankingScoreTag(it)}/10") }
    val quality = rankingMetadataQuality(entry, preferredTitle)
    if (quality.missingLabels.isNotEmpty()) {
        append("，${quality.missingLabels.joinToString("、")}")
    }
}

private fun hasSimplifiedChineseTitle(value: String): Boolean {
    if (!value.any { it in '\u3400'..'\u9fff' }) return false
    val hasJapaneseKana = value.any { it in '\u3040'..'\u30ff' }
    val hasKorean = value.any { it in '\u1100'..'\u11ff' || it in '\u3130'..'\u318f' || it in '\uac00'..'\ud7af' }
    return !hasJapaneseKana && !hasKorean
}

private fun hasUsableChineseSummary(summary: String?): Boolean {
    val value = summary?.trim().orEmpty()
    if (value.isBlank() || value.startsWith("公开资料概览")) return false
    return value.any { it in '\u3400'..'\u9fff' }
}
