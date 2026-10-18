package com.lovelyreader.ui.video.ranking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.icu.text.Transliterator
import android.os.Build
import com.lovelyreader.ui.InkWashBackground
import com.lovelyreader.ui.SoftPanel
import com.lovelyreader.ui.theme.appColors
import com.lovelyreader.video.ranking.RankingBoardOption
import com.lovelyreader.video.ranking.RankingCapabilities
import com.lovelyreader.video.ranking.RankingEntry
import com.lovelyreader.video.ranking.RankingError
import com.lovelyreader.video.ranking.RankingFailureKind
import com.lovelyreader.video.ranking.RankingFilterOption
import com.lovelyreader.video.ranking.RankingFreshness
import com.lovelyreader.video.ranking.RankingMediaType
import com.lovelyreader.video.ranking.RankingRequest
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

private fun mediaTypeLabel(mediaType: RankingMediaType): String = when (mediaType) {
    RankingMediaType.ALL -> "全部"
    RankingMediaType.MOVIE -> "电影"
    RankingMediaType.SERIES -> "剧集"
    RankingMediaType.VARIETY -> "综艺"
}

private val rankingRegionLabels = mapOf(
    "US" to "美国", "GB" to "英国", "JP" to "日本", "KR" to "韩国",
    "CN" to "中国", "TH" to "泰国", "TW" to "中国台湾", "HK" to "中国香港",
    "CA" to "加拿大", "AU" to "澳大利亚", "FR" to "法国", "DE" to "德国",
    "IN" to "印度", "ES" to "西班牙", "IT" to "意大利", "BR" to "巴西",
    "NZ" to "新西兰", "IE" to "爱尔兰", "MX" to "墨西哥", "ID" to "印度尼西亚"
)

/** Region/year filters are the primary discovery path for the full catalogue. */
internal const val rankingFiltersExpandedByDefault: Boolean = true

internal fun rankingRegionLabel(value: String): String = rankingRegionLabels[value] ?: value

private val rankingGenreLabels = mapOf(
    "action" to "动作", "adventure" to "冒险", "animation" to "动画", "comedy" to "喜剧",
    "crime" to "犯罪", "documentary" to "纪录片", "drama" to "剧情", "family" to "家庭",
    "fantasy" to "奇幻", "history" to "历史", "horror" to "恐怖", "music" to "音乐",
    "mystery" to "悬疑", "news" to "新闻", "reality" to "真人秀", "romance" to "爱情",
    "sci-fi" to "科幻", "science-fiction" to "科幻", "sport" to "体育", "thriller" to "惊悚",
    "war" to "战争", "western" to "西部", "talk show" to "脱口秀", "game show" to "游戏节目",
    "kids" to "少儿", "short" to "短片", "adult" to "成人"
)

internal fun rankingGenreLabel(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isBlank() || trimmed.any { it in '\u3400'..'\u9fff' }) return simplifyRankingChinese(trimmed)
    return rankingGenreLabels[trimmed.lowercase().replace('_', '-')] ?: trimmed
}

internal fun rankingSourceLabel(value: String): String = when (value.lowercase()) {
    "tvmaze" -> "剧集评分"
    "imdb" -> "IMDb评分"
    else -> value
}

internal fun rankingBoardScoreLabel(boardId: String): String = when (boardId) {
    "source-imdb" -> "IMDb评分"
    "source-tvmaze" -> "剧集评分"
    else -> "评分"
}

/** Converts catalog provenance text into a short, readable synopsis. */
internal fun rankingDisplaySummary(summary: String?): String? {
    val cleaned = summary
        ?.trim()
        ?.removePrefix("公开资料概览：")
        ?.removeSuffix("；当前快照未收录剧情正文。")
        ?.removeSuffix("当前快照未收录剧情正文。")
        ?.trim(' ', '；', '。')
        .orEmpty()
    return cleaned
        .takeIf { it.isNotBlank() }
        ?.takeUnless { it.matches(Regex("《[^》]+》")) }
}

/**
 * Use a Chinese title whenever the immutable ranking row carries one in its
 * display field, original field, or aliases.  The source title remains in
 * the row for search fallback and auditability, but it must not win over a
 * verified Chinese alias in the user-facing card.
 */
internal fun rankingPreferredDisplayTitle(
    displayTitle: String,
    originalTitle: String?,
    aliases: List<String>
): String {
    val candidates = buildList {
        add(displayTitle)
        addAll(aliases)
        originalTitle?.let(::add)
    }
    val chineseCandidates = candidates.asSequence()
        .mapIndexedNotNull { index, candidate ->
            val chinese = rankingChineseTitlePart(candidate) ?: return@mapIndexedNotNull null
            val simplified = simplifyRankingChinese(chinese)
            RankingChineseCandidate(
                index = index,
                value = simplified,
                cjkCount = simplified.count { it in '\u3400'..'\u9fff' },
                traditionalCount = chinese.count { it in traditionalToSimplified }
            )
        }
        .toList()

    // The catalogue's display field is the reviewed primary identity.  Once
    // it is already a simplified Han title, an alias with more Han characters
    // must not displace it merely because the alias is Japanese (for example
    // ``照明店の客人たち``).  Traditional display fields still yield to a
    // verified simplified alias below.
    chineseCandidates.firstOrNull { candidate ->
        candidate.index == 0 && candidate.traditionalCount == 0
    }?.let { return it.value }

    return chineseCandidates.asSequence()
        .sortedWith(compareBy<RankingChineseCandidate> { value ->
            // A verified simplified alias is preferable to a traditional
            // display field.  The original order remains the final tie-break.
            value.traditionalCount
        }.thenByDescending { it.cjkCount }.thenBy { it.index })
        .firstOrNull()
        ?.value
        ?: displayTitle
}

private data class RankingChineseCandidate(
    val index: Int,
    val value: String,
    val cjkCount: Int,
    val traditionalCount: Int
)

/**
 * Android does not ship OpenCC.  Keep the high-frequency title characters in
 * one deterministic table so a traditional provider label cannot leak into
 * the simplified-Chinese client surface.  Unknown characters are preserved;
 * we never invent a translation for an English-only title.
 */
private val traditionalToSimplified: Map<Char, Char> = mapOf(
    '萬' to '万', '與' to '与', '國' to '国', '際' to '际', '戰' to '战', '劇' to '剧',
    '畫' to '画', '驚' to '惊', '懸' to '悬', '盜' to '盗', '黃' to '黄', '鑣' to '镖',
    '鏢' to '镖', '們' to '们', '辦' to '办', '搶' to '抢', '綠' to '绿', '蹟' to '迹',
    '飛' to '飞', '瘋' to '疯', '頂' to '顶', '對' to '对', '無' to '无', '間' to '间',
    '讓' to '让', '機' to '机', '開' to '开', '臺' to '台', '灣' to '湾', '學' to '学',
    '說' to '说', '話' to '话', '網' to '网', '關' to '关', '於' to '于', '電' to '电',
    '視' to '视', '樂' to '乐', '園' to '园', '後' to '后', '來' to '来', '實' to '实',
    '現' to '现', '經' to '经', '濟' to '济', '發' to '发', '問' to '问', '題' to '题',
    '時' to '时', '記' to '记', '風' to '风', '動' to '动', '戦' to '战', '戀' to '恋',
    '為' to '为', '這' to '这', '個' to '个', '進' to '进', '過' to '过',
    '還' to '还', '會' to '会', '從' to '从', '號' to '号', '門' to '门', '氣' to '气',
    '場' to '场', '長' to '长', '點' to '点', '車' to '车', '書' to '书', '醫' to '医',
    '龍' to '龙', '傳' to '传', '統' to '统', '線' to '线', '體' to '体', '種' to '种',
    '類' to '类', '區' to '区', '級' to '级', '產' to '产', '業' to '业', '務' to '务',
    '帶' to '带', '應' to '应', '該' to '该', '見' to '见', '難' to '难', '聽' to '听',
    '歡' to '欢', '團' to '团', '隊' to '队', '數' to '数', '據' to '据', '結' to '结',
    '樣' to '样', '觀' to '观', '眾' to '众', '優' to '优', '勢' to '势', '歸' to '归',
    '廣' to '广', '東' to '东', '亞' to '亚', '驗' to '验', '復' to '复', '偵' to '侦',
    '屍' to '尸', '殭' to '僵', '殺' to '杀', '喪' to '丧', '運' to '运', '銀' to '银',
    '紀' to '纪', '錄' to '录', '諜' to '谍', '諸' to '诸', '獄' to '狱', '壞' to '坏',
    '壓' to '压', '絕' to '绝', '擊' to '击', '師' to '师', '權' to '权', '遊' to '游', '戲' to '戏',
    '雙' to '双', '獵' to '猎', '惡' to '恶', '紅' to '红', '樓' to '楼', '異' to '异',
    '獸' to '兽', '鏡' to '镜', '別' to '别', '擁' to '拥', '愛' to '爱', '協' to '协',
    '議' to '议', '變' to '变', '稱' to '称', '夢' to '梦', '兩' to '两', '邊' to '边',
    '選' to '选', '擇' to '择', '簡' to '简', '讀' to '读', '錯' to '错', '識' to '识',
    '況' to '况', '準' to '准', '確' to '确', '實' to '实', '華' to '华', '質' to '质',
    '級' to '级', '頁' to '页', '顯' to '显', '顔' to '颜', '頭' to '头', '鬥' to '斗',
    '勝' to '胜', '敗' to '败', '熱' to '热', '點' to '点', '處' to '处', '聯' to '联',
    '絡' to '络', '導' to '导', '張' to '张', '維' to '维', '護' to '护', '專' to '专',
    '業' to '业', '標' to '标', '題' to '题', '態' to '态', '舊' to '旧', '續' to '续',
    '義' to '义', '編' to '编', '輯' to '辑', '節' to '节', '鏡' to '镜', '館' to '馆',
    '寶' to '宝', '貝' to '贝', '衛' to '卫', '鳳' to '凤', '麗' to '丽', '燈' to '灯',
    '島' to '岛', '巖' to '岩', '臉' to '脸', '類' to '类', '緊' to '紧', '傷' to '伤',
    '審' to '审', '查' to '查', '獨' to '独', '單' to '单', '戲' to '戏', '顧' to '顾',
    '頒' to '颁', '獎' to '奖', '禮' to '礼'
)

private val rankingTraditionalToSimplified: Transliterator? by lazy {
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Transliterator.getInstance("Traditional-Simplified")
        } else {
            null
        }
    }.getOrNull()
}

private fun simplifyRankingChinese(value: String): String {
    val fallback = value.map { traditionalToSimplified[it] ?: it }.joinToString("")
    return rankingTraditionalToSimplified?.let { transliterator ->
        runCatching { transliterator.transliterate(value) }.getOrDefault(fallback)
    } ?: fallback
}

/** The compact score chip stays numeric while accessibility keeps its meaning. */
internal fun rankingScoreTag(value: Double): String = formatRankingScore(value)

/** Keep the meaning available to TalkBack without adding words to the visual chip. */
private fun rankingScoreContentDescription(value: Double): String = "评分 ${formatRankingScore(value)}"

/** Keep only the CJK-bearing side of a bilingual provider label. */
private fun rankingChineseTitlePart(value: String): String? {
    val cleaned = value.trim().replace(Regex("\\s+"), " ")
    if (cleaned.isBlank() || !cleaned.any { it in '\u3400'..'\u9fff' }) return null
    val parts = cleaned.split(Regex("\\s*[/／|｜]\\s*"))
        .map(String::trim)
        .filter(String::isNotBlank)
    for (part in parts) {
        val bracketed = Regex("[\\(（\\[]([^\\)）\\]]+)[\\)）\\]]")
            .findAll(part)
            .map { it.groupValues[1].trim() }
            .filter { candidate -> candidate.any { it in '\u3400'..'\u9fff' } }
            .toList()
        val outside = part.replace(Regex("[\\(（\\[][^\\)）\\]]*[\\)）\\]]"), "").trim()
        if (bracketed.isNotEmpty() && !outside.any { it in '\u3400'..'\u9fff' }) {
            return bracketed.first().takeUnless(::rankingContainsNonChineseScript)
        }
        val withoutEnglishSuffix = part.replace(
            Regex("\\s*[\\(（\\[][^\\)）\\]]*[\\)）\\]]\\s*$"),
            ""
        ).trim()
        if (withoutEnglishSuffix.any { it in '\u3400'..'\u9fff' } && !rankingContainsNonChineseScript(withoutEnglishSuffix)) {
            return withoutEnglishSuffix
        }
        if (part.any { it in '\u3400'..'\u9fff' } && !rankingContainsNonChineseScript(part)) return part
    }
    return null
}

private fun rankingContainsNonChineseScript(value: String): Boolean = value.any { char ->
    char in '\u3040'..'\u30ff' ||
        char in '\u1100'..'\u11ff' ||
        char in '\u3130'..'\u318f' ||
        char in '\uac00'..'\ud7af'
}

/**
 * A stateless ranking surface. All visible content comes from [state]; all
 * navigation and network decisions stay with the drama host/ViewModel.
 */
@Composable
fun RankingScreen(
    state: RankingUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRequest: (RankingRequest) -> Unit,
    onLoadNextPage: () -> Unit,
    onSearch: (RankingSearchIntent) -> Unit,
    onOpenSearch: () -> Unit = {},
    modifier: Modifier = Modifier,
    listState: LazyListState? = null
) {
    val fallbackListState = rememberLazyListState()
    val rankingListState = listState ?: fallbackListState
    val request = state.requestOrNull()
    var showMoreFilters by rememberSaveable { mutableStateOf(rankingFiltersExpandedByDefault) }

    InkWashBackground(modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            RankingTopBar(
                onBack = onBack,
                onRefresh = onRefresh,
                refreshing = state is RankingUiState.Refreshing
            )
            LazyColumn(
                state = rankingListState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("ranking-root"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val capabilities = state.capabilities
            val supportedBoards = capabilities.normalizedBoards()
            val supportedMediaTypes = capabilities.normalizedMediaTypes()
            val supportedPeriods = capabilities.normalizedPeriods()
            val supportedRegions = capabilities.normalizedRegions()
            val supportedYears = capabilities.normalizedYears()
            val supportedGenres = capabilities.normalizedGenres()
            val supportedLanguages = capabilities.normalizedLanguages()
            if (state !is RankingUiState.Loading && capabilities.canLoadRanking && request != null) {
                val selectedRequest = request
                if (supportedBoards.isNotEmpty()) item {
                    RankingFilterRow(
                        title = "榜单类型",
                        options = supportedBoards,
                        selectedId = selectedRequest.boardId,
                        onSelected = { board -> onRequest(selectedRequest.copy(boardId = board, cursor = null)) },
                        testTagPrefix = "ranking-board-tab"
                    )
                }
                if (supportedMediaTypes.isNotEmpty()) item {
                    RankingMediaFilterRow(
                        supported = supportedMediaTypes,
                        selected = selectedRequest.mediaType,
                        onSelected = { media -> onRequest(selectedRequest.copy(mediaType = media, cursor = null)) }
                    )
                }
                if (supportedPeriods.isNotEmpty()) item {
                    RankingNullableFilterRow(
                        title = "时间窗",
                        options = supportedPeriods,
                        selectedId = selectedRequest.period,
                        onSelected = { period -> onRequest(selectedRequest.copy(period = period, cursor = null)) },
                        testTagPrefix = "ranking-period"
                    )
                }
                val hasMoreFilters = supportedRegions.isNotEmpty() || supportedYears.isNotEmpty() ||
                    supportedGenres.isNotEmpty() || supportedLanguages.isNotEmpty()
                if (hasMoreFilters) item {
                    RankingMoreFiltersToggle(
                        expanded = showMoreFilters,
                        selectedCount = listOf(
                            selectedRequest.region,
                            selectedRequest.year,
                            selectedRequest.genre,
                            selectedRequest.language
                        ).count { value -> value != null && (value !is String || value.isNotBlank()) },
                        onToggle = { showMoreFilters = !showMoreFilters }
                    )
                }
                if (hasMoreFilters && showMoreFilters) item {
                    SoftPanel(
                        modifier = Modifier.testTag("ranking-more-filters-panel"),
                        innerPadding = 12.dp
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (supportedRegions.isNotEmpty()) {
                                RankingNullableFilterRow(
                                    title = "制作国家/地区",
                                    options = prioritizedRegionOptions(supportedRegions, selectedRequest.region),
                                    selectedId = selectedRequest.region,
                                    onSelected = { region -> onRequest(selectedRequest.copy(region = region, cursor = null)) },
                                    testTagPrefix = "ranking-region"
                                )
                            }
                            if (supportedYears.isNotEmpty()) {
                                RankingYearFilterRow(
                                    options = supportedYears,
                                    selected = selectedRequest.year,
                                    onSelected = { year -> onRequest(selectedRequest.copy(year = year, cursor = null)) }
                                )
                            }
                            if (supportedGenres.isNotEmpty()) {
                                RankingNullableFilterRow(
                                    title = "类型",
                                    options = supportedGenres,
                                    selectedId = selectedRequest.genre,
                                    onSelected = { genre -> onRequest(selectedRequest.copy(genre = genre, cursor = null)) },
                                    testTagPrefix = "ranking-genre"
                                )
                            }
                            if (supportedLanguages.isNotEmpty()) {
                                RankingNullableFilterRow(
                                    title = "语言",
                                    options = supportedLanguages,
                                    selectedId = selectedRequest.language,
                                    onSelected = { language -> onRequest(selectedRequest.copy(language = language, cursor = null)) },
                                    testTagPrefix = "ranking-language"
                                )
                            }
                        }
                    }
                }
            } else if (state !is RankingUiState.Loading) {
                item {
                    RankingCapabilitiesUnavailable(
                        state = state,
                        onRetry = onRefresh,
                        onOpenSearch = onOpenSearch
                    )
                }
            }
            when (state) {
                is RankingUiState.Loading -> item { RankingLoadingState() }
                is RankingUiState.Content -> items(state, onLoadNextPage, onSearch)
                is RankingUiState.Refreshing -> {
                    item {
                        RankingRefreshingBanner(
                            filterChanged = !state.snapshot.hasSameRankingContext(state.request)
                        )
                    }
                    items(state, onLoadNextPage, onSearch)
                }
                is RankingUiState.Partial -> {
                    item { RankingPartialBanner() }
                    items(state, onLoadNextPage, onSearch)
                }
                is RankingUiState.Stale -> {
                    item {
                        RankingStaleBanner(
                            onRetry = onRefresh,
                            canRetry = state.canRetry
                        )
                    }
                    items(state, onLoadNextPage, onSearch)
                }
                is RankingUiState.Empty -> if (capabilities.canLoadRanking || state.request != null) {
                    item { RankingEmptyState(state, request, onRefresh, onOpenSearch) }
                }
                is RankingUiState.Error -> item { RankingErrorState(state.error, state.canRetry, onRefresh, onBack, onOpenSearch) }
            }
        }
    }
}
}

@Composable
private fun RankingFilterRow(
    title: String,
    options: List<RankingBoardOption>,
    selectedId: String,
    onSelected: (String) -> Unit,
    testTagPrefix: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RankingFilterSectionTitle(title, showScrollHint = options.size > 5)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().testTag("ranking-board-tabs"),
            contentPadding = PaddingValues(end = 8.dp)
        ) {
            items(options, key = { it.id }) { option ->
                FilterChip(
                    selected = option.id == selectedId,
                    onClick = { onSelected(option.id) },
                    label = { Text(option.label, fontSize = 14.sp, maxLines = 1) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("$testTagPrefix-${option.id}")
                        .semantics {
                            contentDescription = "${option.label}，榜单类型${if (option.id == selectedId) "，已选中" else ""}"
                        }
                )
            }
        }
    }
}

@Composable
private fun RankingMediaFilterRow(
    supported: List<RankingMediaType>,
    selected: RankingMediaType,
    onSelected: (RankingMediaType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RankingFilterSectionTitle("媒体类型", showScrollHint = supported.size > 5)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().testTag("ranking-media-tabs"),
            contentPadding = PaddingValues(end = 8.dp)
        ) {
            items(supported.distinct(), key = { it.name }) { mediaType ->
                FilterChip(
                    selected = mediaType == selected,
                    onClick = { onSelected(mediaType) },
                    label = { Text(mediaTypeLabel(mediaType), fontSize = 14.sp, maxLines = 1) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("ranking-media-tab-${mediaType.name.lowercase()}")
                        .semantics {
                            contentDescription = "${mediaTypeLabel(mediaType)}，媒体类型${if (mediaType == selected) "，已选中" else ""}"
                        }
                )
            }
        }
    }
}

@Composable
private fun RankingNullableFilterRow(
    title: String,
    options: List<RankingFilterOption>,
    selectedId: String?,
    onSelected: (String?) -> Unit,
    testTagPrefix: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RankingFilterSectionTitle(title, showScrollHint = options.size > 5)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().testTag("$testTagPrefix-tabs"),
            contentPadding = PaddingValues(end = 8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedId == null,
                    onClick = { onSelected(null) },
                    label = { Text("全部", fontSize = 14.sp, maxLines = 1) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("$testTagPrefix-all")
                        .semantics {
                            contentDescription = "全部，$title${if (selectedId == null) "，已选中" else ""}"
                        }
                )
            }
            items(options, key = { it.id }) { option ->
                FilterChip(
                    selected = option.id == selectedId,
                    onClick = { onSelected(option.id) },
                    label = { Text(option.label, fontSize = 14.sp, maxLines = 1) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("$testTagPrefix-${canonicalTestKey(option.id)}")
                        .semantics {
                            contentDescription = "$title${option.label}${if (option.id == selectedId) "，已选中" else ""}"
                        }
                )
            }
        }
    }
}

@Composable
private fun RankingYearFilterRow(
    options: List<Int>,
    selected: Int?,
    onSelected: (Int?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RankingFilterSectionTitle("年份", showScrollHint = options.size > 5)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().testTag("ranking-year-tabs"),
            contentPadding = PaddingValues(end = 8.dp)
        ) {
            item {
                FilterChip(
                    selected = selected == null,
                    onClick = { onSelected(null) },
                    label = { Text("全部", fontSize = 14.sp, maxLines = 1) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("ranking-year-all")
                        .semantics { contentDescription = "全部，年份${if (selected == null) "，已选中" else ""}" }
                )
            }
            items(options, key = { it }) { year ->
                FilterChip(
                    selected = year == selected,
                    onClick = { onSelected(year) },
                    label = { Text(year.toString(), fontSize = 14.sp, maxLines = 1) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("ranking-year-$year")
                        .semantics { contentDescription = "年份$year${if (year == selected) "，已选中" else ""}" }
                )
            }
        }
    }
}

@Composable
private fun RankingFilterSectionTitle(title: String, showScrollHint: Boolean) {
    val colors = appColors()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = colors.cocoa, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp))
        if (showScrollHint) {
            Text(
                "左右滑动查看更多",
                color = colors.softGray,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                modifier = Modifier.semantics { contentDescription = "${title}可横向滑动查看更多" }
            )
        }
    }
}

@Composable
private fun RankingTopBar(onBack: () -> Unit, onRefresh: () -> Unit, refreshing: Boolean) {
    val colors = appColors()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.cream
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("ranking-back")
                    .semantics { contentDescription = "返回追剧" }
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = colors.cocoa)
            }
            Text(
                text = "排行榜",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                color = colors.cocoa,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(
                onClick = onRefresh,
                enabled = !refreshing,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("ranking-refresh")
                    .semantics { contentDescription = if (refreshing) "正在刷新排行榜" else "刷新排行榜" }
            ) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = colors.roseDust,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, tint = colors.roseDust)
                }
            }
        }
    }
}

@Composable
private fun RankingMoreFiltersToggle(expanded: Boolean, selectedCount: Int, onToggle: () -> Unit) {
    val colors = appColors()
    OutlinedButton(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("ranking-more-filters")
            .clearAndSetSemantics {
                contentDescription = "更多筛选，${if (expanded) "已展开" else "已收起"}${if (selectedCount > 0) "，已选 $selectedCount 项" else ""}"
            },
        border = BorderStroke(1.dp, colors.roseBeige),
        contentPadding = PaddingValues(vertical = 10.dp)
    ) {
        val suffix = if (selectedCount > 0) " · 已选 $selectedCount 项" else ""
        Text(if (expanded) "收起更多筛选$suffix" else "更多筛选$suffix", color = colors.roseDust, fontSize = 14.sp)
    }
}

/** Keep the user-requested regional catalogue discoverable before the long tail. */
private fun prioritizedRegionOptions(
    options: List<RankingFilterOption>,
    selectedId: String?
): List<RankingFilterOption> {
    val priority = listOf("CN", "KR", "TH", "TW", "HK", "US", "GB", "JP", "IN")
    val byId = options.associateBy { it.id }
    val seen = mutableSetOf<String>()
    val ordered = buildList {
        selectedId?.let { byId[it] }?.let { option -> if (seen.add(option.id)) add(option) }
        priority.mapNotNull { byId[it] }.forEach { option -> if (seen.add(option.id)) add(option) }
        options.forEach { option -> if (seen.add(option.id)) add(option) }
    }
    return ordered
}

@Composable
private fun RankingLoadingState() {
    val colors = appColors()
    SoftPanel(
        modifier = Modifier
            .testTag("ranking-loading")
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = colors.roseBeige, strokeWidth = 2.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("正在加载排行榜", color = colors.cocoa, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun RankingCapabilitiesUnavailable(
    state: RankingUiState,
    onRetry: () -> Unit,
    onOpenSearch: () -> Unit
) {
    val colors = appColors()
    SoftPanel(
        modifier = Modifier
            .testTag("ranking-capabilities-unavailable")
            .semantics { liveRegion = LiveRegionMode.Polite },
        innerPadding = 12.dp
    ) {
        Text("排行榜暂不可用", color = colors.cocoa, style = MaterialTheme.typography.titleMedium)
        Text(
            "暂时无法加载榜单，请稍后再试。",
            color = colors.softGray,
            style = MaterialTheme.typography.bodyMedium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (state is RankingUiState.Empty && state.canRetry) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("ranking-capabilities-retry"),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.roseDust)
                ) { Text("重试") }
            }
            OutlinedButton(
                onClick = onOpenSearch,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("ranking-open-search-unavailable"),
                border = BorderStroke(1.dp, colors.roseBeige)
            ) { Text("去追剧搜索", color = colors.roseDust) }
        }
    }
}

@Composable
private fun RankingRefreshingBanner(
    filterChanged: Boolean
) {
    val colors = appColors()
    SoftPanel(
        modifier = Modifier
            .testTag("ranking-refreshing")
            .semantics {
                contentDescription = if (filterChanged) "正在应用排行榜筛选" else "正在刷新排行榜"
                liveRegion = LiveRegionMode.Polite
            },
        innerPadding = 12.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = colors.roseBeige,
                strokeWidth = 2.dp
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (filterChanged) "正在应用筛选" else "正在刷新排行榜",
                    color = colors.cocoa,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    if (filterChanged) {
                        "正在更新筛选结果"
                    } else {
                        "正在更新榜单"
                    },
                    color = colors.softGray,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun RankingPartialBanner() {
    val colors = appColors()
    SoftPanel(
        modifier = Modifier
            .testTag("ranking-partial")
            .semantics { liveRegion = LiveRegionMode.Polite },
        innerPadding = 12.dp
    ) {
        Text("部分内容暂不可用", color = colors.cocoa, style = MaterialTheme.typography.titleMedium)
        Text("部分内容暂时无法显示。", color = colors.softGray, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RankingStaleBanner(onRetry: () -> Unit, canRetry: Boolean) {
    val colors = appColors()
    SoftPanel(
        modifier = Modifier
            .testTag("ranking-stale")
            .semantics { liveRegion = LiveRegionMode.Polite },
        innerPadding = 12.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("正在显示上次数据", color = colors.cocoa, style = MaterialTheme.typography.titleMedium)
                Text("内容可能已变化，请重新更新。", color = colors.softGray, style = MaterialTheme.typography.bodyMedium)
            }
            if (canRetry) {
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("ranking-stale-retry"),
                    border = BorderStroke(1.dp, colors.roseBeige),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) { Text("重试", color = colors.roseDust) }
            }
        }
    }
}

@Composable
private fun RankingEmptyState(
    state: RankingUiState.Empty,
    request: RankingRequest?,
    onRefresh: () -> Unit,
    onOpenSearch: () -> Unit
) {
    val colors = appColors()
    SoftPanel(
        modifier = Modifier
            .testTag("ranking-empty")
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Text(
            if (request == null) "暂时没有榜单内容" else "暂无符合条件的内容",
            color = colors.cocoa,
            style = MaterialTheme.typography.titleMedium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (state.canRetry) {
                Button(
                    onClick = onRefresh,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("ranking-empty-retry"),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.roseDust)
                ) { Text("重新加载") }
            }
            OutlinedButton(
                onClick = onOpenSearch,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("ranking-empty-search"),
                border = BorderStroke(1.dp, colors.lineColor)
            ) { Text("去追剧搜索", color = colors.roseDust) }
        }
    }
}

@Composable
private fun RankingErrorState(
    error: RankingError,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onOpenSearch: () -> Unit
) {
    val colors = appColors()
    SoftPanel(
        modifier = Modifier
            .testTag("ranking-error")
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Text("排行榜暂时无法读取", color = colors.cocoa, style = MaterialTheme.typography.titleMedium)
        Text(rankingErrorCopy(error), color = colors.softGray, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (canRetry) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("ranking-retry"),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.roseDust)
                ) { Text("重试") }
            }
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("ranking-error-back"),
                border = BorderStroke(1.dp, colors.lineColor)
            ) { Text("返回追剧", color = colors.roseDust) }
        }
        TextButton(
            onClick = onOpenSearch,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("ranking-error-search")
        ) { Text("去追剧搜索", color = colors.roseDust) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.items(
    state: RankingSnapshotUiState,
    onLoadNextPage: () -> Unit,
    onSearch: (RankingSearchIntent) -> Unit
) {
    item {
        RankingSnapshotMeta(state)
    }
    val aggregateScoreLabel = rankingBoardScoreLabel(state.request.boardId)
    items(state.items, key = { entry -> "${state.snapshot.snapshotId}:${entry.canonicalId}" }) { entry ->
        RankingCard(entry, onSearch, aggregateScoreLabel)
    }
    item {
        state.lastError?.let { error ->
            Text(
                rankingErrorCopy(error),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ranking-inline-error")
                    .semantics { liveRegion = LiveRegionMode.Polite },
                color = appColors().roseDust,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        state.nextCursor?.let {
            Button(
                onClick = onLoadNextPage,
                enabled = !state.isLoadingNextPage,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("ranking-pagination")
                    .semantics { liveRegion = LiveRegionMode.Polite },
                colors = ButtonDefaults.buttonColors(containerColor = appColors().roseDust),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                if (state.isLoadingNextPage) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp)
                        Text("正在加载更多")
                    }
                } else {
                    Text("加载更多")
                }
            }
        }
    }
}

@Composable
private fun RankingSnapshotMeta(state: RankingSnapshotUiState) {
    val colors = appColors()
    if (state.snapshot.totalCount != null || state.snapshot.freshness != RankingFreshness.FRESH) {
        SoftPanel(
            modifier = Modifier
                .testTag("ranking-snapshot-meta")
                .semantics { liveRegion = LiveRegionMode.Polite },
            innerPadding = 12.dp
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                state.snapshot.totalCount?.let { total ->
                    Text("共 $total 条", color = colors.cocoa, style = MaterialTheme.typography.titleMedium)
                }
                if (state.snapshot.freshness != RankingFreshness.FRESH) {
                    Text(freshnessLabel(state.snapshot.freshness), color = colors.roseDust, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun RankingCard(
    entry: RankingEntry,
    onSearch: (RankingSearchIntent) -> Unit,
    aggregateScoreLabel: String
) {
    val colors = appColors()
    val preferredTitle = rankingPreferredDisplayTitle(
        entry.displayTitle,
        entry.originalTitle,
        entry.aliases + entry.searchTitle
    )
    val rankLabel = entry.rank?.let { "第 ${it} 名" } ?: "当前榜单"
    val metadata = buildList {
        entry.releaseYear?.let(::add)
        if (entry.countries.isNotEmpty()) {
            val labels = entry.countries.map(::rankingRegionLabel)
            add(if (labels.size > 4) labels.take(4).joinToString("、") + "等" else labels.joinToString("、"))
        }
        if (entry.genres.isNotEmpty()) add(entry.genres.take(3).map(::rankingGenreLabel).joinToString("、"))
    }.joinToString(" · ")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ranking-card-${canonicalTestKey(entry.canonicalId)}")
            .semantics {
                contentDescription = rankingCardContentDescription(entry, preferredTitle, aggregateScoreLabel)
            },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = colors.warmWhite.copy(alpha = .94f)),
        border = BorderStroke(1.dp, colors.lineColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .widthIn(min = 34.dp)
                    .wrapContentWidth()
                    .heightIn(min = rankingVisualContract.posterHeightDp.dp)
                    .padding(end = 2.dp)
                    .semantics { contentDescription = rankLabel },
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    entry.rank?.toString() ?: "—",
                    color = colors.roseDust,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clearAndSetSemantics { }
                )
            }
            Box(
                modifier = Modifier
                    .size(rankingVisualContract.posterWidthDp.dp, rankingVisualContract.posterHeightDp.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "$preferredTitle ${if (entry.posterUrl == null) "封面占位" else "封面"}"
                    },
                contentAlignment = Alignment.Center
            ) {
                RankingPoster(
                    url = entry.posterUrl,
                    title = preferredTitle,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    entry.rankingScore?.let { score ->
                        Surface(
                            modifier = Modifier
                                .testTag("ranking-score-${canonicalTestKey(entry.canonicalId)}")
                                .semantics { contentDescription = rankingScoreContentDescription(score) },
                            shape = MaterialTheme.shapes.small,
                            color = colors.roseDust.copy(alpha = .14f),
                            contentColor = colors.roseDust
                        ) {
                            Text(
                                rankingScoreTag(score),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                    Text(
                        preferredTitle,
                        modifier = Modifier.weight(1f),
                        color = colors.cocoa,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = rankingVisualContract.titleMaxLines,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (metadata.isNotBlank()) {
                    Text(metadata, color = colors.softGray, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                rankingDisplaySummary(entry.summary)?.let {
                    Text(it, color = colors.softGray, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), maxLines = rankingVisualContract.summaryMaxLines, overflow = TextOverflow.Ellipsis)
                }
                if (entry.aggregateScore != null) {
                    Text(
                        "$aggregateScoreLabel ${formatRankingScore(entry.aggregateScore)}/10",
                        color = colors.roseDust,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        "暂无$aggregateScoreLabel",
                        color = colors.roseDust,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        fontWeight = FontWeight.Medium
                    )
                }
                TextButton(
                    onClick = {
                        onSearch(
                            RankingSearchIntent(
                                canonicalId = entry.canonicalId,
                                displayTitle = preferredTitle,
                                aliases = entry.aliases.toList()
                            )
                        )
                    },
                    modifier = Modifier
                        .heightIn(min = rankingVisualContract.minimumTouchTargetDp.dp)
                        .testTag("ranking-search-${canonicalTestKey(entry.canonicalId)}")
                        .semantics { contentDescription = "搜索《$preferredTitle》" },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                ) { Text("去搜索", color = colors.roseDust, fontSize = 14.sp) }
            }
        }
    }
}

private fun RankingUiState.requestOrNull(): RankingRequest? = when (this) {
    is RankingUiState.Loading -> null
    is RankingUiState.Content -> request
    is RankingUiState.Refreshing -> request
    is RankingUiState.Stale -> request
    is RankingUiState.Partial -> request
    is RankingUiState.Empty -> request
    is RankingUiState.Error -> request
}

private fun com.lovelyreader.video.ranking.RankingSnapshot.hasSameRankingContext(request: RankingRequest): Boolean =
    boardId == request.boardId && filters.period == request.period &&
        filters.mediaType == request.mediaType && filters.region == request.region &&
        filters.year == request.year && filters.genre == request.genre &&
        filters.language == request.language && filters.limit == request.limit

private fun rankingErrorCopy(error: RankingError): String = when (error.kind) {
    RankingFailureKind.RATE_LIMITED -> "请求过于频繁，请稍后再试。"
    RankingFailureKind.BAD_REQUEST -> "当前筛选条件暂不支持，请调整筛选后重试。"
    RankingFailureKind.NOT_FOUND -> "当前榜单暂不可用。"
    RankingFailureKind.SNAPSHOT_EXPIRED -> "榜单已更新，请重新加载。"
    RankingFailureKind.CONTENT_TYPE, RankingFailureKind.MALFORMED_RESPONSE -> "暂时无法读取榜单，请稍后再试。"
    RankingFailureKind.SERVICE_UNAVAILABLE -> "榜单暂时不可用，请稍后再试。"
    RankingFailureKind.INVALID_REQUEST -> "榜单筛选条件无效。"
    RankingFailureKind.NOT_MODIFIED_WITHOUT_CACHE -> "榜单缓存已失效，请重新加载。"
    RankingFailureKind.HTTP_ERROR, RankingFailureKind.NETWORK -> "榜单暂时连接不上，请检查网络后重试。"
}

private fun freshnessLabel(freshness: RankingFreshness): String = when (freshness) {
    RankingFreshness.FRESH -> "已更新"
    RankingFreshness.STALE -> "可能已变化"
    RankingFreshness.EXPIRED -> "已过期"
}

private fun formatRankingScore(value: Double): String =
    String.format(java.util.Locale.ROOT, "%.1f", value)

private fun canonicalTestKey(canonicalId: String): String = MessageDigest
    .getInstance("SHA-256")
    .digest(canonicalId.toByteArray(StandardCharsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
