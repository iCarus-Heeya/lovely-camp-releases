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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lovelyreader.ui.InkWashBackground
import com.lovelyreader.ui.SoftPanel
import com.lovelyreader.ui.theme.appColors
import com.lovelyreader.ui.video.DramaPoster
import com.lovelyreader.video.ranking.RankingEntry
import com.lovelyreader.video.ranking.RankingError
import com.lovelyreader.video.ranking.RankingFailureKind
import com.lovelyreader.video.ranking.RankingFreshness
import com.lovelyreader.video.ranking.RankingMediaType
import com.lovelyreader.video.ranking.RankingRequest
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** A server capability, not a client-side list of guessed ranking choices. */
data class RankingBoardOption(val id: String, val label: String)

private fun mediaTypeLabel(mediaType: RankingMediaType): String = when (mediaType) {
    RankingMediaType.ALL -> "全部"
    RankingMediaType.MOVIE -> "电影"
    RankingMediaType.SERIES -> "剧集"
    RankingMediaType.VARIETY -> "综艺"
}

/** The first request is deliberately conservative until the server capabilities are known. */
internal fun defaultRankingRequest(): RankingRequest = RankingRequest(
    boardId = "aggregate",
    period = "week",
    mediaType = RankingMediaType.ALL,
    region = null,
    year = null,
    genre = null,
    language = null,
    limit = 20
)

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
    modifier: Modifier = Modifier,
    listState: LazyListState? = null,
    supportedBoards: List<RankingBoardOption> = emptyList(),
    supportedMediaTypes: List<RankingMediaType> = emptyList()
) {
    val fallbackListState = rememberLazyListState()
    val rankingListState = listState ?: fallbackListState
    val request = state.requestOrNull() ?: defaultRankingRequest()
    val colors = appColors()

    InkWashBackground(modifier.fillMaxSize()) {
        LazyColumn(
            state = rankingListState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("ranking-root"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
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
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("ranking-refresh")
                            .semantics { contentDescription = "刷新排行榜" }
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, tint = colors.roseDust)
                    }
                }
            }
            item {
                Text(
                    "排行榜数据服务接入后展示已验证内容，不把片源站点顺序当作全网热度。",
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ranking-source-note"),
                    color = colors.softGray,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
                )
            }
            if (supportedBoards.isEmpty() && supportedMediaTypes.isEmpty()) {
                item { RankingCapabilitiesUnavailable() }
            } else {
                if (supportedBoards.isNotEmpty()) item {
                    RankingFilterRow(
                        title = "榜单类型",
                        options = supportedBoards,
                        selectedId = request.boardId,
                        onSelected = { board -> onRequest(request.copy(boardId = board, cursor = null)) },
                        testTagPrefix = "ranking-board-tab"
                    )
                }
                if (supportedMediaTypes.isNotEmpty()) item {
                    RankingMediaFilterRow(
                        supported = supportedMediaTypes,
                        selected = request.mediaType,
                        onSelected = { media -> onRequest(request.copy(mediaType = media, cursor = null)) }
                    )
                }
            }
            when (state) {
                RankingUiState.Loading -> item { RankingLoadingState() }
                is RankingUiState.Content -> items(state, onLoadNextPage, onSearch)
                is RankingUiState.Refreshing -> {
                    item { RankingRefreshingBanner() }
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
                is RankingUiState.Empty -> item { RankingEmptyState(state, request, onRefresh) }
                is RankingUiState.Error -> item { RankingErrorState(state.error, state.canRetry, onRefresh, onBack) }
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
    val colors = appColors()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = colors.cocoa, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp))
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
    val colors = appColors()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("媒体类型", color = colors.cocoa, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp))
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
                Text("正在等待真实榜单快照，不会用占位内容代替。", color = colors.softGray, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun RankingCapabilitiesUnavailable() {
    val colors = appColors()
    SoftPanel(
        modifier = Modifier
            .testTag("ranking-capabilities-unavailable")
            .semantics { liveRegion = LiveRegionMode.Polite },
        innerPadding = 12.dp
    ) {
        Text("筛选待数据能力接入", color = colors.cocoa, style = MaterialTheme.typography.titleMedium)
        Text("当前没有已验证的榜单或媒体类型能力，暂不显示可选择的筛选项。", color = colors.softGray, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RankingRefreshingBanner() {
    val colors = appColors()
    SoftPanel(
        modifier = Modifier
            .testTag("ranking-refreshing")
            .semantics {
                contentDescription = "正在刷新排行榜"
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
                Text("正在刷新排行榜", color = colors.cocoa, style = MaterialTheme.typography.titleMedium)
                Text("当前内容仍可浏览，刷新完成后会整批更新。", color = colors.softGray, style = MaterialTheme.typography.bodyMedium)
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
        Text("部分来源暂不可用", color = colors.cocoa, style = MaterialTheme.typography.titleMedium)
        Text("以下内容只来自当前仍可验证的来源。", color = colors.softGray, style = MaterialTheme.typography.bodyMedium)
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
private fun RankingEmptyState(state: RankingUiState.Empty, request: RankingRequest, onRefresh: () -> Unit) {
    val colors = appColors()
    SoftPanel(
        modifier = Modifier
            .testTag("ranking-empty")
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Text("这个条件下暂时没有榜单内容", color = colors.cocoa, style = MaterialTheme.typography.titleMedium)
        Text(
            text = rankingFilterSummary(request),
            color = colors.softGray,
            style = MaterialTheme.typography.bodyMedium
        )
        if (state.canRetry) {
            Button(
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("ranking-empty-retry"),
                colors = ButtonDefaults.buttonColors(containerColor = colors.roseDust)
            ) { Text("重新加载") }
        }
    }
}

@Composable
private fun RankingErrorState(
    error: RankingError,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit
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
    items(state.items, key = { entry -> "${state.snapshot.snapshotId}:${entry.canonicalId}" }) { entry ->
        RankingCard(entry, onSearch)
    }
    item {
        state.lastError?.let { error ->
            Text(
                rankingErrorCopy(error),
                modifier = Modifier.fillMaxWidth().testTag("ranking-inline-error"),
                color = appColors().roseDust,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        state.nextCursor?.let {
            Button(
                onClick = onLoadNextPage,
                enabled = !state.isLoadingNextPage,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("ranking-pagination"),
                colors = ButtonDefaults.buttonColors(containerColor = appColors().roseDust),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                if (state.isLoadingNextPage) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp)
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
    val unavailable = state.snapshot.sourceStatuses.count { it.status.name != "AVAILABLE" }
    SoftPanel(modifier = Modifier.testTag("ranking-snapshot-meta"), innerPadding = 12.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("榜单快照", color = colors.cocoa, style = MaterialTheme.typography.titleMedium)
            Text(freshnessLabel(state.snapshot.freshness), color = colors.roseDust, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            buildString {
                append("来源覆盖 ")
                append(state.snapshot.coverage.coveredSourceIds.size)
                append("/")
                append(state.snapshot.coverage.sourceCount)
                if (unavailable > 0) append(" · $unavailable 个来源暂不可用")
            },
            color = colors.softGray,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "数据时间：${formatRankingTimestamp(state.snapshot.dataAsOf.epochMillis)}",
            color = colors.softGray,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
        )
    }
}

@Composable
private fun RankingCard(entry: RankingEntry, onSearch: (RankingSearchIntent) -> Unit) {
    val colors = appColors()
    val rankLabel = entry.rank?.let { "第 ${it} 名" } ?: "当前榜单"
    val metadata = buildList {
        entry.releaseYear?.let(::add)
        if (entry.countries.isNotEmpty()) add(entry.countries.joinToString("、"))
        if (entry.genres.isNotEmpty()) add(entry.genres.joinToString("、"))
    }.joinToString(" · ")
    val description = buildString {
        append(rankLabel)
        append("，")
        append(entry.displayTitle)
        if (metadata.isNotBlank()) {
            append("，")
            append(metadata)
        }
        append("，来源覆盖 ")
        append(entry.coverage.coveredSourceIds.size)
        append("/")
        append(entry.coverage.sourceCount)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ranking-card-${canonicalTestKey(entry.canonicalId)}")
            .semantics { contentDescription = description },
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
                    .heightIn(min = 112.dp)
                    .padding(end = 2.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    entry.rank?.toString() ?: "—",
                    color = colors.roseDust,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                    fontWeight = FontWeight.SemiBold
                )
            }
            Box(
                modifier = Modifier
                    .size(84.dp, 112.dp)
                    .clearAndSetSemantics { },
                contentAlignment = Alignment.Center
            ) {
                DramaPoster(
                    url = entry.posterUrl,
                    title = entry.displayTitle,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    entry.displayTitle,
                    color = colors.cocoa,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                    fontWeight = FontWeight.SemiBold
                )
                entry.originalTitle?.takeIf(String::isNotBlank)?.let {
                    Text(it, color = colors.softGray, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (metadata.isNotBlank()) {
                    Text(metadata, color = colors.softGray, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                entry.summary?.takeIf(String::isNotBlank)?.let {
                    Text(it, color = colors.softGray, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    "来源覆盖 ${entry.coverage.coveredSourceIds.size}/${entry.coverage.sourceCount}",
                    color = colors.roseDust,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                )
                TextButton(
                    onClick = {
                        onSearch(
                            RankingSearchIntent(
                                canonicalId = entry.canonicalId,
                                displayTitle = entry.searchTitle,
                                aliases = entry.aliases.toList()
                            )
                        )
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("ranking-search-${canonicalTestKey(entry.canonicalId)}"),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                ) { Text("去搜索", color = colors.roseDust, fontSize = 14.sp) }
            }
        }
    }
}

private fun RankingUiState.requestOrNull(): RankingRequest? = when (this) {
    RankingUiState.Loading -> null
    is RankingUiState.Content -> request
    is RankingUiState.Refreshing -> request
    is RankingUiState.Stale -> request
    is RankingUiState.Partial -> request
    is RankingUiState.Empty -> request
    is RankingUiState.Error -> request
}

private fun rankingFilterSummary(request: RankingRequest): String = buildList {
    add("榜单 ${request.boardId}")
    add(mediaTypeLabel(request.mediaType))
    request.period?.takeIf(String::isNotBlank)?.let { add(it) }
    request.region?.takeIf(String::isNotBlank)?.let { add(it) }
    request.year?.let { add(it.toString()) }
}.joinToString(" · ")

private fun rankingErrorCopy(error: RankingError): String = when (error.kind) {
    RankingFailureKind.RATE_LIMITED -> "请求过于频繁，请稍后再试。"
    RankingFailureKind.BAD_REQUEST -> "当前筛选条件暂不支持，请换一个榜单或媒体类型。"
    RankingFailureKind.NOT_FOUND -> "当前榜单暂不可用。"
    RankingFailureKind.SNAPSHOT_EXPIRED -> "榜单快照已更新，请重新加载。"
    RankingFailureKind.CONTENT_TYPE, RankingFailureKind.MALFORMED_RESPONSE -> "榜单返回内容异常，暂时无法读取。"
    RankingFailureKind.SERVICE_UNAVAILABLE -> "榜单服务暂未提供有效快照。"
    RankingFailureKind.INVALID_REQUEST -> "榜单筛选条件无效。"
    RankingFailureKind.NOT_MODIFIED_WITHOUT_CACHE -> "榜单缓存已失效，请重新加载。"
    RankingFailureKind.HTTP_ERROR, RankingFailureKind.NETWORK -> "榜单暂时连接不上，请检查网络后重试。"
}

private fun freshnessLabel(freshness: RankingFreshness): String = when (freshness) {
    RankingFreshness.FRESH -> "已更新"
    RankingFreshness.STALE -> "可能已变化"
    RankingFreshness.EXPIRED -> "已过期"
}

private fun formatRankingTimestamp(epochMillis: Long): String =
    java.time.Instant.ofEpochMilli(epochMillis).toString().replace('T', ' ').removeSuffix("Z")

private fun canonicalTestKey(canonicalId: String): String = MessageDigest
    .getInstance("SHA-256")
    .digest(canonicalId.toByteArray(StandardCharsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
