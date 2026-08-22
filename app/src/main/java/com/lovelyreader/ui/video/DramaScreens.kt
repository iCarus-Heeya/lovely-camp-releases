package com.lovelyreader.ui.video

import android.content.Context
import android.content.ContextWrapper
import android.content.ClipData
import android.content.ClipboardManager
import android.view.ViewGroup
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.ConsoleMessage
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.mediarouter.app.MediaRouteButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forward5
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay5
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.android.gms.cast.framework.CastButtonFactory
import com.lovelyreader.video.VideoCastController
import com.lovelyreader.video.DlnaController
import com.lovelyreader.video.DlnaRenderer
import com.lovelyreader.video.VideoDownloadTask
import com.lovelyreader.video.VideoDownloadStatus
import com.lovelyreader.video.VideoEpisode
import com.lovelyreader.video.VideoSource
import com.lovelyreader.video.VideoTitle
import com.lovelyreader.video.VideoPlaybackMode
import com.lovelyreader.video.VideoRequestDiagnostic
import com.lovelyreader.video.videoDebugVersionLabel
import com.lovelyreader.video.castMediaTarget
import com.lovelyreader.ui.SoftPanel
import com.lovelyreader.ui.InkWashBackground
import com.lovelyreader.ui.theme.appColors
import kotlinx.coroutines.delay

@Composable
fun DramaScreen(
    viewModel: DramaViewModel,
    debugDiagnostics: (() -> List<VideoRequestDiagnostic>)? = null,
    experienceSwitch: @Composable () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val rootStatus by viewModel.rootStatus.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val search by viewModel.searchResults.collectAsState()
    val detail by viewModel.selectedTitle.collectAsState()
    val source by viewModel.selectedSource.collectAsState()
    val episodes by viewModel.sourceEpisodes.collectAsState()
    val selectedEpisodeIds by viewModel.selectedEpisodeIds.collectAsState()
    val downloads by viewModel.downloadTasks.collectAsState()
    val playback by viewModel.playback.collectAsState()
    val recentViewing by viewModel.recentViewing.collectAsState()
    var page by remember { mutableStateOf(DramaPage.Home) }
    var showDiagnostics by remember { mutableStateOf(false) }
    val homeListState = rememberLazyListState()

    BackHandler(enabled = page != DramaPage.Home) {
        if (page == DramaPage.Player) viewModel.closePlayback()
        page = dramaBackDestination(page) ?: DramaPage.Home
    }

    when (page) {
        DramaPage.Home -> DramaHomeStyledScreen(
            rootStatus = rootStatus,
            query = query,
            search = search,
            downloads = downloads,
            recentViewing = recentViewing,
            onResumeRecent = {
                viewModel.resumeRecentViewing()
                page = DramaPage.Player
            },
            onSearch = viewModel::search,
            onOpenTitle = {
                viewModel.openTitle(it)
                page = DramaPage.Detail
            },
            onOpenDownloads = {
                viewModel.refreshDownloads()
                page = DramaPage.Downloads
            },
            onShowDiagnostics = debugDiagnostics?.let { { showDiagnostics = true } },
            experienceSwitch = experienceSwitch,
            listState = homeListState,
            modifier = modifier
        )
        DramaPage.Detail -> DramaDetailCompactScreen(
            detailTitle = detail.detail?.title,
            detailMessage = detail.message,
            isLoading = detail.isLoading,
            sources = detail.detail?.sources.orEmpty(),
            selectedSource = source,
            episodes = episodes,
            selectedEpisodeIds = selectedEpisodeIds,
            onBack = { page = DramaPage.Home },
            onSelectSource = viewModel::selectSource,
            onToggleEpisode = viewModel::toggleEpisode,
            onPlayEpisode = {
                viewModel.openEpisode(it)
                page = DramaPage.Player
            },
            onEnqueueSelected = viewModel::enqueueSelectedEpisodes,
            experienceSwitch = experienceSwitch,
            modifier = modifier
        )
        DramaPage.Downloads -> DownloadQueueStyledScreen(
            tasks = downloads,
            onBack = { page = DramaPage.Home },
            onRefresh = viewModel::refreshDownloads,
            modifier = modifier
        )
        DramaPage.Player -> DramaPlayerScreen(
            playback = playback,
            episodes = episodes,
            onPlayEpisode = viewModel::openEpisode,
            onBack = {
                viewModel.closePlayback()
                page = DramaPage.Detail
            },
            modifier = modifier
        )
    }
    if (showDiagnostics && debugDiagnostics != null) {
        VideoDiagnosticsDialog(diagnostics = debugDiagnostics(), onDismiss = { showDiagnostics = false })
    }
}

@Composable
fun DramaExperience(
    viewModel: DramaViewModel,
    modifier: Modifier = Modifier
) = DramaScreen(viewModel = viewModel, modifier = modifier)

internal enum class DramaPage { Home, Detail, Downloads, Player }

internal fun dramaBackDestination(page: DramaPage): DramaPage? = when (page) {
    DramaPage.Detail, DramaPage.Downloads -> DramaPage.Home
    DramaPage.Player -> DramaPage.Detail
    DramaPage.Home -> null
}

@Composable
fun DramaHomeScreen(
    rootStatus: DramaRootUiState,
    query: String,
    search: DramaSearchUiState,
    onSearch: (String) -> Unit,
    onOpenTitle: (VideoTitle) -> Unit,
    onOpenDownloads: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editableQuery by remember(query) { mutableStateOf(query) }
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("追剧", style = MaterialTheme.typography.headlineMedium)
        dramaHomeAvailabilityMessage(rootStatus)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = editableQuery,
                onValueChange = { editableQuery = it },
                modifier = Modifier.weight(1f),
                label = { Text("剧名") },
                singleLine = true
            )
            Button(onClick = { onSearch(editableQuery) }, modifier = Modifier.padding(top = 8.dp)) { Text("搜索") }
        }
        Text(search.message, style = MaterialTheme.typography.bodySmall)
        Button(onClick = onOpenDownloads) { Text("下载列表") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(search.results, key = { it.id }) { title ->
                Card(modifier = Modifier.fillMaxWidth().clickable { onOpenTitle(title) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(title.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        title.summary?.takeIf(String::isNotBlank)?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DramaHomeStyledScreen(
    rootStatus: DramaRootUiState,
    query: String,
    search: DramaSearchUiState,
    downloads: List<VideoDownloadTask>,
    recentViewing: com.lovelyreader.video.VideoRecentViewing?,
    onResumeRecent: () -> Unit,
    onSearch: (String) -> Unit,
    onOpenTitle: (VideoTitle) -> Unit,
    onOpenDownloads: () -> Unit,
    onShowDiagnostics: (() -> Unit)?,
    experienceSwitch: @Composable () -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val colors = appColors()
    var editableQuery by remember(query) { mutableStateOf(query) }
    InkWashBackground(modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(Color.Transparent),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                experienceSwitch()
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("今晚想看点什么", style = MaterialTheme.typography.headlineLarge, color = colors.cocoa, fontWeight = FontWeight.SemiBold)
                Text("慢慢挑一部喜欢的，留一点轻松给自己。", style = MaterialTheme.typography.bodyLarge, color = colors.softGray)
            }
        }
        recentViewing?.let { recent ->
            item {
                SoftPanel(modifier = Modifier.clickable(onClick = onResumeRecent)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("继续观看", style = MaterialTheme.typography.labelLarge, color = colors.roseBeige)
                            Text(recent.titleName, style = MaterialTheme.typography.titleLarge, color = colors.cocoa, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("回到 ${recentEpisodeDisplayLabel(recent.episodeId)}", style = MaterialTheme.typography.bodyMedium, color = colors.softGray)
                        }
                        androidx.compose.material3.IconButton(onClick = onResumeRecent) {
                            androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.CircleShape, color = colors.roseBeige) {
                                Text("▶", color = Color.White, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(
                    value = editableQuery,
                    onValueChange = { editableQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("搜一搜想看的剧") },
                    leadingIcon = { Text("⌕", color = colors.softGray, style = MaterialTheme.typography.headlineSmall) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.roseBeige,
                        unfocusedBorderColor = colors.lineColor,
                        focusedContainerColor = colors.paper,
                        unfocusedContainerColor = colors.paper,
                        focusedTextColor = colors.cocoa,
                        unfocusedTextColor = colors.cocoa,
                        focusedPlaceholderColor = colors.softGray,
                        unfocusedPlaceholderColor = colors.softGray
                    )
                )
                Spacer(Modifier.size(8.dp))
                Button(onClick = { onSearch(editableQuery) }, modifier = Modifier.height(50.dp), shape = MaterialTheme.shapes.large, colors = ButtonDefaults.buttonColors(containerColor = colors.roseBeige, contentColor = Color.White), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp)) {
                    Text("开始找剧", fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
        if (downloads.isNotEmpty()) {
            item {
                Text("下载列表", style = MaterialTheme.typography.titleLarge, color = colors.cocoa)
            }
            items(downloads.take(3), key = { "preview-${it.id}" }) { task ->
                DramaDownloadPreviewCard(task)
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("找到的剧集", style = MaterialTheme.typography.titleLarge, color = colors.cocoa)
                OutlinedButton(onClick = onOpenDownloads, shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, colors.lineColor), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp)) { Text("下载列表") }
            }
        }
        onShowDiagnostics?.let { onClick ->
            item {
                OutlinedButton(
                    onClick = onClick,
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, colors.lineColor)
                ) { Text("调试片源连接") }
            }
        }
        dramaHomeAvailabilityMessage(rootStatus)?.let { message ->
            item { SoftPanel { Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.softGray) } }
        }
        if (search.message.isNotBlank()) item { Text(search.message, style = MaterialTheme.typography.bodySmall, color = colors.softGray) }
        if (search.results.isEmpty() && !search.isLoading) item {
            SoftPanel { Text("还没有结果", style = MaterialTheme.typography.titleMedium, color = colors.cocoa); Text("输入剧名试试看，喜欢的剧会在这里出现。", color = colors.softGray, style = MaterialTheme.typography.bodySmall) }
        }
        items(search.results, key = { it.id }) { title -> DramaTitleCard(title, onClick = { onOpenTitle(title) }) }
    }
    }
}

@Composable
private fun VideoDiagnosticsDialog(
    diagnostics: List<VideoRequestDiagnostic>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val packageInfo = remember(context.packageName) {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    val versionLabel = videoDebugVersionLabel(packageInfo.versionName ?: "未知", packageInfo.longVersionCode)
    val copiedText = diagnostics.joinToString("\n") { it.displayText }
        .ifBlank { "尚未记录片源请求。请先搜索一次后再打开此处。" }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("片源调试")
                Text(versionLabel, style = MaterialTheme.typography.bodySmall)
            }
        },
        text = {
            LazyColumn {
                item { Text("仅调试版显示；地址已去除参数和令牌。") }
                item { Spacer(Modifier.height(8.dp)) }
                items(diagnostics) { event ->
                    Text(event.displayText, style = MaterialTheme.typography.bodySmall)
                }
                if (diagnostics.isEmpty()) item { Text("尚未记录片源请求。请先搜索一次后再打开此处。") }
            }
        },
        confirmButton = {
            Button(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("片源调试", copiedText))
            }) { Text("复制诊断") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun DramaTitleCard(title: VideoTitle, onClick: () -> Unit) {
    val colors = appColors()
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = colors.paper), border = BorderStroke(1.dp, colors.lineColor), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DramaPoster(
                url = title.posterUrl,
                title = title.name,
                modifier = Modifier.size(72.dp, 104.dp).clip(MaterialTheme.shapes.small)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title.name, style = MaterialTheme.typography.titleMedium, color = colors.cocoa, maxLines = 1, overflow = TextOverflow.Ellipsis)
                title.releaseInfo?.takeIf(String::isNotBlank)?.let { release ->
                    Text("上映：$release", style = MaterialTheme.typography.bodySmall, color = colors.softGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                title.categoryInfo?.takeIf(String::isNotBlank)?.let { category ->
                    Text(category, style = MaterialTheme.typography.bodySmall, color = colors.softGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                title.castInfo?.takeIf(String::isNotBlank)?.let { cast ->
                    Text("主演：$cast", style = MaterialTheme.typography.bodySmall, color = colors.softGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                title.updateInfo?.takeIf(String::isNotBlank)?.let { update ->
                    Text(update, style = MaterialTheme.typography.bodySmall, color = colors.roseBeige, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                title.summary?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.softGray, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Text("查看选集 ›", style = MaterialTheme.typography.labelMedium, color = colors.roseBeige)
            }
        }
    }
}

@Composable
private fun DramaDownloadPreviewCard(task: VideoDownloadTask) {
    val colors = appColors()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = colors.paper.copy(alpha = 0.92f)),
        border = BorderStroke(1.dp, colors.lineColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${downloadSourceDisplayLabel(task.sourceId)} · ${recentEpisodeDisplayLabel(task.episodeId)}", style = MaterialTheme.typography.titleMedium, color = colors.cocoa)
                Text(task.status.label(), style = MaterialTheme.typography.bodyMedium, color = colors.softGray)
            }
            Text("›", color = colors.cocoa, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
fun DramaDetailScreen(
    detailTitle: VideoTitle?,
    detailMessage: String,
    isLoading: Boolean,
    sources: List<VideoSource>,
    selectedSource: VideoSource?,
    episodes: List<VideoEpisode>,
    selectedEpisodeIds: Set<String>,
    onBack: () -> Unit,
    onSelectSource: (VideoSource) -> Unit,
    onToggleEpisode: (String) -> Unit,
    onPlayEpisode: (VideoEpisode) -> Unit,
    onEnqueueSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onBack) { Text("返回") }
        Text(detailTitle?.name ?: "剧集详情", style = MaterialTheme.typography.headlineSmall)
        Text(if (isLoading) "加载中…" else detailMessage, style = MaterialTheme.typography.bodySmall)
        when (sourceSelectorLayoutFor(sources.size)) {
            SourceSelectorLayout.STATIC -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sources.forEach { source ->
                    SourceFilterChip(source, selectedSource, onSelectSource)
                }
            }
            SourceSelectorLayout.HORIZONTALLY_SCROLLABLE -> LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sources, key = { it.id }) { source ->
                    SourceFilterChip(source, selectedSource, onSelectSource)
                }
            }
        }
        Button(onClick = onEnqueueSelected, enabled = selectedEpisodeIds.isNotEmpty()) {
            Text("下载选中 (${selectedEpisodeIds.size})")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(episodes, key = { it.id }) { episode ->
                val selected = episode.id in selectedEpisodeIds
                Card(modifier = Modifier.fillMaxWidth().clickable { onToggleEpisode(episode.id) }) {
                    Row(
                        Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(if (selected) "✓" else "○", modifier = Modifier.size(20.dp))
                        Text(episode.label, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { onPlayEpisode(episode) }) { Text("播放") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DramaDetailStyledScreen(
    detailTitle: VideoTitle?, detailMessage: String, isLoading: Boolean, sources: List<VideoSource>, selectedSource: VideoSource?, episodes: List<VideoEpisode>, selectedEpisodeIds: Set<String>, onBack: () -> Unit, onSelectSource: (VideoSource) -> Unit, onToggleEpisode: (String) -> Unit, onPlayEpisode: (VideoEpisode) -> Unit, onEnqueueSelected: () -> Unit, modifier: Modifier = Modifier
) {
    val colors = appColors()
    InkWashBackground(modifier.fillMaxSize()) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(compactEpisodeGridColumns()),
        modifier = Modifier.fillMaxSize().background(Color.Transparent),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OutlinedButton(onClick = onBack, shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, colors.lineColor), colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.cocoa)) { Text("返回找剧") }
        }
        item {
            SoftPanel {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.Top) {
                    DramaPoster(url = detailTitle?.posterUrl, title = detailTitle?.name ?: "剧集详情", modifier = Modifier.size(76.dp, 108.dp).clip(MaterialTheme.shapes.small))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(detailTitle?.name ?: "剧集详情", style = MaterialTheme.typography.headlineSmall, color = colors.cocoa)
                        Text(if (isLoading) "正在准备选集…" else detailMessage, style = MaterialTheme.typography.bodySmall, color = colors.softGray)
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { DramaMetadataBlock(detailTitle) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("选择播放源", style = MaterialTheme.typography.titleMedium, color = colors.cocoa)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sources, key = { it.id }) { source ->
                        val selected = selectedSource?.id == source.id
                        OutlinedButton(onClick = { onSelectSource(source) }, shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, if (selected) colors.roseBeige else colors.lineColor), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) colors.blush else colors.paper, contentColor = if (selected) colors.roseDust else colors.cocoa)) { Text(source.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }
        item {
            Button(onClick = onEnqueueSelected, enabled = selectedEpisodeIds.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(50.dp), shape = MaterialTheme.shapes.large, colors = ButtonDefaults.buttonColors(containerColor = colors.roseBeige, contentColor = Color.White, disabledContainerColor = colors.almond, disabledContentColor = colors.softGray)) { Text(selectedEpisodeDownloadLabel(selectedEpisodeIds.size)) }
        }
        item { Text("轻点选集即可加入下载；每一集也可以直接播放。", style = MaterialTheme.typography.bodySmall, color = colors.softGray) }
        items(episodes, key = { it.id }) { episode ->
            val selected = episode.id in selectedEpisodeIds
            Card(modifier = Modifier.fillMaxWidth().clickable { onToggleEpisode(episode.id) }, shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = if (selected) colors.blush.copy(alpha = .55f) else colors.paper), border = BorderStroke(1.dp, if (selected) colors.roseBeige else colors.lineColor)) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (selected) "已选" else "选择", style = MaterialTheme.typography.labelMedium, color = if (selected) colors.roseDust else colors.softGray)
                    Text(episode.label, modifier = Modifier.weight(1f), color = colors.cocoa, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    OutlinedButton(onClick = { onPlayEpisode(episode) }, shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, colors.lineColor), colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.roseBeige)) { Text("播放") }
                }
            }
        }
    }
    }
}

@Composable
private fun DramaDetailCompactScreen(
    detailTitle: VideoTitle?, detailMessage: String, isLoading: Boolean,
    sources: List<VideoSource>, selectedSource: VideoSource?, episodes: List<VideoEpisode>,
    selectedEpisodeIds: Set<String>, onBack: () -> Unit, onSelectSource: (VideoSource) -> Unit,
    onToggleEpisode: (String) -> Unit, onPlayEpisode: (VideoEpisode) -> Unit,
    onEnqueueSelected: () -> Unit,
    experienceSwitch: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = appColors()
    InkWashBackground(modifier.fillMaxSize()) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(compactEpisodeGridColumns()),
        modifier = Modifier.fillMaxSize().background(Color.Transparent),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            // Match the concept chrome: experience switch on the left and the
            // return affordance on the right, before the detail title.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                experienceSwitch()
                OutlinedButton(
                    onClick = onBack,
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, colors.lineColor),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp)
                ) { Text("返回找剧", color = colors.cocoa) }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("剧集详情", style = MaterialTheme.typography.headlineLarge, color = colors.cocoa, fontWeight = FontWeight.SemiBold)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            SoftPanel {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = androidx.compose.ui.Alignment.Top) {
                    DramaPoster(
                        url = detailTitle?.posterUrl,
                        title = detailTitle?.name ?: "剧集详情",
                        modifier = Modifier.size(132.dp, 190.dp).clip(MaterialTheme.shapes.medium)
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(detailTitle?.name ?: "剧集详情", style = MaterialTheme.typography.headlineSmall, color = colors.cocoa, fontWeight = FontWeight.SemiBold)
                        detailTitle?.summary?.takeIf(String::isNotBlank)?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.cocoa, maxLines = 7, overflow = TextOverflow.Ellipsis)
                        }
                        detailTitle?.releaseInfo?.takeIf(String::isNotBlank)?.let { Text("上映：$it", style = MaterialTheme.typography.bodySmall, color = colors.softGray) }
                        detailTitle?.categoryInfo?.takeIf(String::isNotBlank)?.let { Text("分类：$it", style = MaterialTheme.typography.bodySmall, color = colors.softGray, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        detailTitle?.castInfo?.takeIf(String::isNotBlank)?.let { Text("主演：$it", style = MaterialTheme.typography.bodySmall, color = colors.softGray, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                        detailTitle?.updateInfo?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.roseBeige, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        if (isLoading || detailMessage.isNotBlank()) {
                            Text(if (isLoading) "正在准备选集…" else detailMessage, style = MaterialTheme.typography.labelMedium, color = colors.roseBeige)
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("选择播放源", style = MaterialTheme.typography.titleLarge, color = colors.cocoa)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sources, key = { it.id }) { source ->
                        val chosen = selectedSource?.id == source.id
                        OutlinedButton(
                            onClick = { onSelectSource(source) }, shape = MaterialTheme.shapes.large,
                            border = BorderStroke(1.dp, if (chosen) colors.roseBeige else colors.lineColor),
                            colors = if (chosen) {
                                ButtonDefaults.buttonColors(containerColor = colors.roseBeige, contentColor = Color.White)
                            } else {
                                ButtonDefaults.outlinedButtonColors(containerColor = colors.paper, contentColor = colors.cocoa)
                            }
                        ) { Text(source.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            SoftPanel {
                Text("当前片源未提供公开下载视频", style = MaterialTheme.typography.bodyMedium, color = colors.softGray)
                Text("点集号播放；如来源提供公开视频，选中后可加入下载。", style = MaterialTheme.typography.bodySmall, color = colors.softGray)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Button(
                onClick = onEnqueueSelected,
                enabled = batchDownloadEnabled(selectedEpisodeIds.size),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.roseBeige,
                    contentColor = Color.White,
                    disabledContainerColor = colors.almond,
                    disabledContentColor = colors.softGray
                )
            ) { Text(selectedEpisodeDownloadLabel(selectedEpisodeIds.size)) }
        }
        items(episodes, key = { it.id }) { episode ->
            val chosen = episode.id in selectedEpisodeIds
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                colors = CardDefaults.cardColors(containerColor = if (chosen) colors.blush.copy(alpha = .60f) else colors.paper.copy(alpha = .90f)),
                border = BorderStroke(1.dp, if (chosen) colors.roseBeige else colors.lineColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(vertical = 9.dp, horizontal = 4.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        episode.label.removePrefix("第").removeSuffix("集"),
                        modifier = Modifier.fillMaxWidth().clickable { onPlayEpisode(episode) },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (chosen) colors.roseDust else colors.cocoa,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        if (chosen) "已选" else "点此播放",
                        modifier = Modifier.fillMaxWidth().clickable { onToggleEpisode(episode.id) },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (chosen) colors.roseDust else colors.softGray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun DramaMetadataBlock(title: VideoTitle?) {
    val colors = appColors()
    val hasMetadata = title?.let {
        listOf(it.summary, it.releaseInfo, it.castInfo, it.categoryInfo, it.updateInfo).any { value -> !value.isNullOrBlank() }
    } == true
    if (!hasMetadata) return
    SoftPanel {
        title?.summary?.takeIf(String::isNotBlank)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.cocoa, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        title?.releaseInfo?.takeIf(String::isNotBlank)?.let { Text("上映：$it", style = MaterialTheme.typography.bodySmall, color = colors.softGray) }
        title?.categoryInfo?.takeIf(String::isNotBlank)?.let { Text("分类：$it", style = MaterialTheme.typography.bodySmall, color = colors.softGray) }
        title?.castInfo?.takeIf(String::isNotBlank)?.let { Text("主演：$it", style = MaterialTheme.typography.bodySmall, color = colors.softGray, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        title?.updateInfo?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.roseBeige) }
    }
}

@Composable
private fun SourceFilterChip(
    source: VideoSource,
    selectedSource: VideoSource?,
    onSelectSource: (VideoSource) -> Unit
) {
    FilterChip(
        selected = selectedSource?.id == source.id,
        onClick = { onSelectSource(source) },
        label = { Text(source.label) }
    )
}

@OptIn(UnstableApi::class)
@Composable
fun DramaPlayerScreen(
    playback: DramaPlaybackUiState,
    episodes: List<VideoEpisode>,
    onPlayEpisode: (VideoEpisode) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playbackUrl = playback.media?.playbackUrl
    var discoveredMedia by remember(playbackUrl) { mutableStateOf<com.lovelyreader.video.VideoMediaLink?>(null) }
    val effectiveMedia = playbackMedia(playback.media, discoveredMedia)
    val effectivePlaybackUrl = effectiveMedia?.playbackUrl
    val usesSitePlayer = effectiveMedia?.playbackMode == VideoPlaybackMode.SITE_PLAYER
    val castableMedia = effectiveMedia
    val castTarget = remember(castableMedia) { castMediaTarget(castableMedia) }
    val playerLayout = com.lovelyreader.ui.highFidelityPlayerLayout()
    val castController = remember(context.applicationContext) {
        VideoCastController(context.applicationContext)
    }
    val dlnaController = remember(context.applicationContext) {
        DlnaController(context.applicationContext)
    }
    var castMessage by remember(playbackUrl) { mutableStateOf("") }
    var dlnaRenderers by remember(playbackUrl) { mutableStateOf<List<DlnaRenderer>>(emptyList()) }
    var dlnaMessage by remember(playbackUrl) { mutableStateOf("") }
    var showDlnaPicker by remember(playbackUrl) { mutableStateOf(false) }
    var showNativeFullscreen by remember(effectivePlaybackUrl) { mutableStateOf(false) }
    val player = remember(effectivePlaybackUrl, usesSitePlayer) {
        effectivePlaybackUrl?.takeUnless { usesSitePlayer }?.let { url ->
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                playWhenReady = true
            }
        }
    }

    DisposableEffect(player) {
        onDispose { player?.release() }
    }

    DisposableEffect(castController) {
        castController.register()
        onDispose {
            castController.clearMedia()
            castController.close()
        }
    }

    DisposableEffect(dlnaController) {
        onDispose { dlnaController.close() }
    }

    DisposableEffect(castableMedia, player, castController) {
        if (shouldConfigureCast(castableMedia)) {
            castController.setMedia(
                media = castableMedia,
                title = dramaPlayerHeaderLabel(playback.titleName, playback.episode?.label),
                currentPositionMs = { player?.currentPosition ?: 0L },
                onRemotePlaybackStarted = { player?.pause() },
                onLocalFallback = { player?.play() },
                onMessage = { castMessage = it }
            )
        } else {
            castMessage = ""
        }
        onDispose { castController.clearMedia() }
    }

    Column(
        modifier = modifier.fillMaxSize().background(if (playerLayout.darkSurface) Color(0xFF111111) else Color.Black),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF0A0A0A)).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onBack,
                border = BorderStroke(1.dp, Color.White.copy(alpha = .45f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) { Text("‹  返回选集") }
            Text(dramaPlayerHeaderLabel(playback.titleName, playback.episode?.label), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black), contentAlignment = androidx.compose.ui.Alignment.Center) {
            when {
                playback.isLoading -> Text(playback.message, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                usesSitePlayer && playbackUrl != null -> SitePlayerWebView(
                    episodeUrl = playbackUrl,
                    onMediaDiscovered = { candidate -> discoveredMedia = candidate },
                    modifier = Modifier.fillMaxSize()
                )
                player != null -> NativePlayerSurface(
                    player = player,
                    onFullscreen = { showNativeFullscreen = true },
                    modifier = Modifier.fillMaxSize()
                )
                else -> Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("本集暂时无法在应用内播放", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(playback.message.ifBlank { "该播放源没有提供公开可播放的媒体地址。请返回选集后尝试其他播放源。" }, style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF171717)).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("片源", style = MaterialTheme.typography.titleMedium, color = Color.White)
                if (castTarget != null) {
                    Button(onClick = {
                        showDlnaPicker = true
                        dlnaRenderers = emptyList()
                        dlnaMessage = "正在搜索同一无线网络下支持投屏的电视…"
                        dlnaController.discover { devices, message ->
                            dlnaRenderers = devices
                            dlnaMessage = message
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = appColors().roseBeige, contentColor = Color.White), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)) { Text("投屏到电视") }
                }
                if (castTarget != null && castController.isAvailable) {
                    AndroidView(
                        factory = { viewContext ->
                            MediaRouteButton(viewContext).apply {
                                contentDescription = "选择投屏设备"
                                CastButtonFactory.setUpMediaRouteButton(viewContext, this)
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    )
                }
                if (usesSitePlayer && castTarget == null) {
                    Text(dramaStatusCopy(DramaStatus.CastTargetUnavailable), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                }
            }
            if (castMessage.isNotBlank()) Text(castMessage, style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
            if (usesSitePlayer) Text("当前片源使用站内播放器，应用不会伪造公开投屏或下载地址。", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            if (episodes.isNotEmpty()) {
                Text("快速换集", style = MaterialTheme.typography.titleMedium, color = Color.White)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(episodes, key = { it.id }) { episode ->
                        val current = playback.episode?.id == episode.id
                        Card(
                            modifier = Modifier.size(width = 62.dp, height = 52.dp).clickable { if (!current) onPlayEpisode(episode) },
                            shape = MaterialTheme.shapes.small,
                            colors = CardDefaults.cardColors(containerColor = if (current) Color(0xFF9F4533) else Color(0xFF292929)),
                            border = BorderStroke(1.dp, if (current) Color(0xFFE85D43) else Color(0xFF3A3A3A))
                        ) {
                            Box(contentAlignment = androidx.compose.ui.Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(episode.label.removePrefix("第").removeSuffix("集"), style = MaterialTheme.typography.labelLarge, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
    if (showDlnaPicker) {
        AlertDialog(
            onDismissRequest = { showDlnaPicker = false },
            title = { Text("选择电视") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(dlnaMessage, style = MaterialTheme.typography.bodyMedium)
                    dlnaRenderers.forEach { renderer ->
                        OutlinedButton(
                            onClick = {
                                showDlnaPicker = false
                                castMessage = "正在连接 ${renderer.friendlyName}…"
                                dlnaController.play(renderer, castTarget!!) { castMessage = it }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(renderer.friendlyName) }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { showDlnaPicker = false }) { Text("关闭") }
            }
        )
    }
    if (showNativeFullscreen && player != null) {
        val activity = context.findMainActivity()
        Dialog(
            onDismissRequest = {
                showNativeFullscreen = false
                activity?.exitVideoImmersive()
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        this.player = player
                        useController = true
                        controllerAutoShow = true
                        controllerHideOnTouch = nativePlayerControls().hideOnTouch
                        controllerShowTimeoutMs = nativePlayerControls().showTimeoutMs
                        setFullscreenButtonClickListener {
                            showNativeFullscreen = false
                            activity?.exitVideoImmersive()
                        }
                        activity?.enterVideoImmersive()
                    }
                },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize().background(Color.Black)
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun NativePlayerSurface(
    player: ExoPlayer,
    onFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    var positionMs by remember(player) { mutableStateOf(0L) }
    var durationMs by remember(player) { mutableStateOf(0L) }
    var isPlaying by remember(player) { mutableStateOf(player.isPlaying) }
    var draggedProgress by remember(player) { mutableStateOf<Float?>(null) }

    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.takeIf { it > 0L } ?: 0L
            isPlaying = player.isPlaying
            delay(500)
        }
    }

    val progress = draggedProgress ?: if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Slider(
                    value = progress,
                    onValueChange = { draggedProgress = it },
                    onValueChangeFinished = {
                        draggedProgress?.let { player.seekTo((it * durationMs).toLong()) }
                        draggedProgress = null
                    },
                    enabled = durationMs > 0L,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color(0xFFE45C45),
                        inactiveTrackColor = Color.White.copy(alpha = .42f),
                        disabledThumbColor = Color.White.copy(alpha = .5f),
                        disabledActiveTrackColor = Color.White.copy(alpha = .32f),
                        disabledInactiveTrackColor = Color.White.copy(alpha = .18f)
                    ),
                    modifier = Modifier.fillMaxWidth().height(24.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(onClick = { player.seekBack() }) {
                        Icon(Icons.Outlined.Replay5, contentDescription = "后退 5 秒", tint = Color.White)
                    }
                    IconButton(onClick = {
                        if (player.isPlaying) player.pause() else player.play()
                        isPlaying = player.isPlaying
                    }) {
                        Icon(
                            if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    IconButton(onClick = { player.seekForward() }) {
                        Icon(Icons.Outlined.Forward5, contentDescription = "前进 5 秒", tint = Color.White)
                    }
                    Text(
                        "${formatVideoTime(positionMs)} / ${formatVideoTime(durationMs)}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onFullscreen) {
                        Icon(Icons.Outlined.Fullscreen, contentDescription = "全屏", tint = Color.White)
                    }
                }
            }
        }
    }
}

private fun formatVideoTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

/** Hosts only the provider's player entry point. It never extracts or rewrites player data. */
@Composable
private fun SitePlayerWebView(
    episodeUrl: String,
    onMediaDiscovered: (com.lovelyreader.video.VideoMediaLink) -> Unit,
    modifier: Modifier = Modifier
) {
    var retryKey by remember(episodeUrl) { mutableStateOf(0) }
    key(episodeUrl, retryKey) {
    var playerReady by remember { mutableStateOf(false) }
    var entryPageFinished by remember { mutableStateOf(false) }
    var mainFrameFailed by remember { mutableStateOf(false) }
    var confirmedPlayerUrl by remember { mutableStateOf<String?>(null) }
    Box(modifier = modifier) {
    AndroidView(
        factory = { viewContext ->
            WebView(viewContext).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.mediaPlaybackRequiresUserGesture = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                addJavascriptInterface(
                    SitePlayerFrameBridge(
                        episodeUrl = episodeUrl,
                        onPlayerFrameFound = { playerUrl ->
                            post {
                                confirmedPlayerUrl = playerUrl
                                playerReady = shouldRevealConfirmedProviderFrame(playerUrl, episodeUrl)
                                if (shouldNavigateToProviderFrame()) loadUrl(playerUrl)
                            }
                        },
                        onPlayerContainerFound = {
                            post { playerReady = shouldRevealProviderPlayerContainer(true) }
                        }
                    ),
                    "LovelyPlayerBridge"
                )
                webChromeClient = SitePlayerWebChromeClient(viewContext, episodeUrl) { playerUrl ->
                    confirmedPlayerUrl = playerUrl
                    playerReady = shouldRevealConfirmedProviderFrame(playerUrl, episodeUrl)
                    if (shouldNavigateToProviderFrame()) loadUrl(playerUrl)
                }
                webViewClient = SitePlayerWebViewClient(
                    episodeUrl = episodeUrl,
                    onPlayerFrameFound = { playerUrl ->
                        confirmedPlayerUrl = playerUrl
                        playerReady = shouldRevealConfirmedProviderFrame(playerUrl, episodeUrl)
                        if (shouldNavigateToProviderFrame()) loadUrl(playerUrl)
                    },
                    onPlayerReady = { playerReady = true },
                    onEntryPageFinished = { entryPageFinished = true },
                    onMainFrameFailed = { mainFrameFailed = true },
                    onMediaDiscovered = onMediaDiscovered,
                    confirmedPlayerUrl = { confirmedPlayerUrl }
                )
                alpha = 0f
                loadUrl(episodeUrl)
            }
        },
        update = { webView ->
            webView.alpha = when (sitePlayerContentVisibility(entryPageFinished, playerReady, mainFrameFailed)) {
                SitePlayerContentVisibility.Visible -> 1f
                SitePlayerContentVisibility.Hidden, SitePlayerContentVisibility.Failed -> 0f
            }
            if (shouldLoadSitePlayerEntry(webView.url, episodeUrl, confirmedPlayerUrl)) {
                webView.loadUrl(episodeUrl)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
    if (sitePlayerContentVisibility(entryPageFinished, playerReady, mainFrameFailed) == SitePlayerContentVisibility.Failed) {
        SoftPanel(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("片源页面未能打开", style = MaterialTheme.typography.titleMedium, color = appColors().cocoa)
            Text("当前网络没有连接到该片源。请重试，或返回选集切换播放源。", style = MaterialTheme.typography.bodyMedium, color = appColors().softGray)
            Button(onClick = { retryKey++ }) { Text("重新连接") }
        }
    }
    }
    }
}

private class SitePlayerWebViewClient(
    private val episodeUrl: String,
    private val onPlayerFrameFound: (String) -> Unit,
    private val onPlayerReady: () -> Unit,
    private val onEntryPageFinished: () -> Unit,
    private val onMainFrameFailed: () -> Unit,
    private val onMediaDiscovered: (com.lovelyreader.video.VideoMediaLink) -> Unit,
    private val confirmedPlayerUrl: () -> String?
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        !isAllowedSitePlayerNavigation(request.url.toString())

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        if (shouldRevealConfirmedProviderPlayer(url, confirmedPlayerUrl())) {
            onPlayerReady()
            return
        }
        if (!isSitePlayerEntryPage(url, episodeUrl)) return
        onEntryPageFinished()
        // The page replaces its document while bootstrapping the provider player.
        // A probe injected before that replacement is discarded, so check again
        // after the site's scripts have completed their own player setup.
        probeProviderFrame(view)
        view.postDelayed({ probeProviderFrame(view) }, 1_000)
        view.postDelayed({ probeProviderFrame(view) }, 3_000)
        view.postDelayed({ probeProviderFrame(view) }, 6_000)
        view.postDelayed({ probeProviderFrame(view) }, 10_000)
        view.postDelayed({ probeProviderFrame(view) }, 15_000)
        view.postDelayed({ probeProviderFrame(view) }, 20_000)
    }

    override fun onLoadResource(view: WebView, url: String) {
        super.onLoadResource(view, url)
        com.lovelyreader.video.runtimeMediaFromLoadedUrl(url)?.let { media ->
            Log.i("LovelyCast", "runtimeMediaCandidate=${media.playbackUrl}")
            onMediaDiscovered(media)
        }
        if (isProviderFrameRequest(url, episodeUrl)) {
            onPlayerFrameFound(url)
            isolateProviderPlayer(view)
            view.postDelayed({ isolateProviderPlayer(view) }, 500)
            view.postDelayed({ isolateProviderPlayer(view) }, 2_000)
        }
        if (url.contains("quanzhantonglan.js", ignoreCase = true)) {
            view.postDelayed({ hideProviderAnnouncement(view) }, 250)
            view.postDelayed({ hideProviderAnnouncement(view) }, 1_000)
            view.postDelayed({ hideProviderAnnouncement(view) }, 3_000)
        }
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: android.webkit.WebResourceError
    ) {
        super.onReceivedError(view, request, error)
        if (request.isForMainFrame) onMainFrameFailed()
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: android.webkit.WebResourceResponse
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        if (request.isForMainFrame && errorResponse.statusCode >= 400) onMainFrameFailed()
    }

    private fun probeProviderFrame(view: WebView) {
        isolateProviderPlayer(view)
        view.evaluateJavascript(
            """(function(){var p=document.querySelector('.MacPlayer');var f=p?p.querySelector('iframe[src]'):document.querySelector('iframe[src]');var u=f?f.src:'';if(p&&window.LovelyPlayerBridge){window.LovelyPlayerBridge.onPlayerContainer();}if(u&&window.LovelyPlayerBridge){window.LovelyPlayerBridge.onFrame(u);}return u;})()"""
        ) { rawUrl ->
            val candidate = rawUrl?.trim()?.removePrefix("\"")?.removeSuffix("\"")?.takeIf(String::isNotBlank)
            val playerUrl = visibleProviderPlayerUrl(candidate, episodeUrl) ?: return@evaluateJavascript
            onPlayerFrameFound(playerUrl)
        }
    }

    private fun hideProviderAnnouncement(view: WebView) {
        view.evaluateJavascript(
            """(function(){var notice=document.querySelector('.global_notice_wrapper');if(notice){notice.style.setProperty('display','none','important');}return '';})()""",
            null
        )
    }

    /**
     * Preserve the provider iframe in its original page context, while removing the catalogue
     * site's logo, navigation, advertisements and page title from the WebView viewport.
     */
    private fun isolateProviderPlayer(view: WebView) {
        val selectors = providerSiteChromeSelectors().joinToString(",")
        view.evaluateJavascript(
            """(function(){
                var player=document.querySelector('.MacPlayer');
                if(!player){return false;}
                document.querySelectorAll('$selectors').forEach(function(node){
                    if(node!==player&&!node.contains(player)){node.style.setProperty('display','none','important');}
                });
                var parent=player.parentElement;
                while(parent&&parent!==document.body){
                    Array.prototype.forEach.call(parent.children,function(sibling){
                        if(sibling!==player&&!sibling.contains(player)){sibling.style.setProperty('display','none','important');}
                    });
                    parent=parent.parentElement;
                }
                document.body.style.setProperty('margin','0','important');
                player.style.setProperty('margin','0','important');
                player.style.setProperty('width','100%','important');
                return true;
            })()""".trimIndent(),
            null
        )
    }
}

private class SitePlayerFrameBridge(
    private val episodeUrl: String,
    private val onPlayerFrameFound: (String) -> Unit,
    private val onPlayerContainerFound: () -> Unit
) {
    @JavascriptInterface
    fun onFrame(candidate: String?) {
        val playerUrl = visibleProviderPlayerUrl(candidate, episodeUrl)
        playerUrl?.let(onPlayerFrameFound)
    }

    @JavascriptInterface
    fun onPlayerContainer() {
        onPlayerContainerFound()
    }
}

private class SitePlayerWebChromeClient(
    private val context: android.content.Context,
    private val episodeUrl: String,
    private val onPlayerFrameFound: (String) -> Unit
) : android.webkit.WebChromeClient() {
    override fun onShowCustomView(
        view: android.view.View,
        callback: android.webkit.WebChromeClient.CustomViewCallback
    ) {
        context.findMainActivity()?.enterVideoFullscreen(view, callback)
            ?: callback.onCustomViewHidden()
    }

    @Suppress("DEPRECATION")
    override fun onShowCustomView(
        view: android.view.View,
        requestedOrientation: Int,
        callback: android.webkit.WebChromeClient.CustomViewCallback
    ) = onShowCustomView(view, callback)

    override fun onHideCustomView() {
        context.findMainActivity()?.exitVideoFullscreen(notifyPage = false)
    }

    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
        val prefix = "__LOVELY_PLAYER_FRAME__"
        val candidate = message.message().removePrefix(prefix).takeIf { message.message().startsWith(prefix) }
        val playerUrl = visibleProviderPlayerUrl(candidate, episodeUrl)
        if (playerUrl != null) onPlayerFrameFound(playerUrl)
        return super.onConsoleMessage(message)
    }
}

private tailrec fun Context.findMainActivity(): com.lovelyreader.MainActivity? = when (this) {
    is com.lovelyreader.MainActivity -> this
    is ContextWrapper -> baseContext.findMainActivity()
    else -> null
}

@Composable
private fun DownloadQueueStyledScreen(
    tasks: List<VideoDownloadTask>, onBack: () -> Unit, onRefresh: () -> Unit, modifier: Modifier = Modifier
) {
    val colors = appColors()
    InkWashBackground(modifier.fillMaxSize()) {
    LazyColumn(modifier = Modifier.fillMaxSize().background(Color.Transparent), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedButton(onClick = onBack, shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, colors.lineColor), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)) { Text("‹  返回", color = colors.cocoa) }
                OutlinedButton(onClick = onRefresh, shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, colors.lineColor), colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.roseBeige), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)) { Text("↻  刷新状态") }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("下载列表", style = MaterialTheme.typography.headlineLarge, color = colors.cocoa, fontWeight = FontWeight.SemiBold)
                Text("已选的视频会在这里显示下载状态。", style = MaterialTheme.typography.bodyLarge, color = colors.softGray)
            }
        }
        if (tasks.isEmpty()) item { SoftPanel { Text("还没有下载任务", style = MaterialTheme.typography.titleMedium, color = colors.cocoa); Text("在选集页选中喜欢的剧集后，就可以把它们放进这里。", style = MaterialTheme.typography.bodySmall, color = colors.softGray) } }
        items(tasks, key = { it.id }) { task ->
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = colors.paper.copy(alpha = .92f)), border = BorderStroke(1.dp, colors.lineColor), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(downloadSourceDisplayLabel(task.sourceId), style = MaterialTheme.typography.titleMedium, color = colors.cocoa)
                            Text(recentEpisodeDisplayLabel(task.episodeId), style = MaterialTheme.typography.bodyLarge, color = colors.cocoa)
                        }
                        DownloadStatusChip(task.status)
                    }
                    if (task.status == VideoDownloadStatus.COMPLETED) {
                        task.localUri?.let { Text(userFacingDownloadLocation(it), style = MaterialTheme.typography.bodySmall, color = colors.softGray, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }
    }
    }
}

@Composable
fun DownloadQueueScreen(
    tasks: List<VideoDownloadTask>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack) { Text("返回") }
            Button(onClick = onRefresh) { Text("刷新") }
        }
        Text("下载列表", style = MaterialTheme.typography.headlineSmall)
        if (tasks.isEmpty()) Text("还没有下载记录")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tasks, key = { it.id }) { task -> DownloadTaskCard(task) }
        }
    }
}

@Composable
private fun DownloadTaskCard(task: VideoDownloadTask) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("${downloadSourceDisplayLabel(task.sourceId)} · ${recentEpisodeDisplayLabel(task.episodeId)}", style = MaterialTheme.typography.titleSmall)
            Text(task.status.label(), style = MaterialTheme.typography.bodySmall)
            task.localUri?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun DownloadStatusChip(status: VideoDownloadStatus) {
    val colors = appColors()
    val (background, foreground) = when (status) {
        VideoDownloadStatus.QUEUED -> colors.almond.copy(alpha = .45f) to colors.cocoa
        VideoDownloadStatus.DOWNLOADING -> Color(0xFFE6F0F9) to Color(0xFF2C6F9E)
        VideoDownloadStatus.COMPLETED -> Color(0xFFE6EBD9) to Color(0xFF547044)
        VideoDownloadStatus.FAILED -> Color(0xFFF6DDD3) to Color(0xFFA6462C)
    }
    androidx.compose.material3.Surface(shape = MaterialTheme.shapes.large, color = background) {
        Text(status.label(), modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium, color = foreground)
    }
}

internal fun VideoDownloadStatus.label(): String = when (this) {
    VideoDownloadStatus.QUEUED -> "等待下载"
    VideoDownloadStatus.DOWNLOADING -> "正在下载"
    VideoDownloadStatus.COMPLETED -> "已下载完成"
    VideoDownloadStatus.FAILED -> "下载没有完成"
}
