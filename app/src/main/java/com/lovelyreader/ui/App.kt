package com.lovelyreader.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lovelyreader.data.AndroidLibraryPersistence
import com.lovelyreader.data.LibraryRepository
import com.lovelyreader.domain.Book
import com.lovelyreader.sync.ReadingLogSync
import com.lovelyreader.domain.BookDetail
import com.lovelyreader.domain.SearchResult
import com.lovelyreader.source.DiscoveryCatalog
import com.lovelyreader.ui.theme.LovelyReaderTheme
import com.lovelyreader.ui.theme.appColors
import com.lovelyreader.ui.video.DramaDownloadEnqueuer
import com.lovelyreader.ui.video.DramaRootResolver
import com.lovelyreader.ui.video.DramaScreen
import com.lovelyreader.ui.video.DramaViewModel
import com.lovelyreader.ui.video.VideoLibraryDramaStore
import com.lovelyreader.video.AndroidDownloadManagerGateway
import com.lovelyreader.video.AndroidVideoPageFetcher
import com.lovelyreader.video.AndroidVideoHostConnectivityProbe
import com.lovelyreader.video.AndroidTrustedVideoDnsFallback
import com.lovelyreader.video.AndroidVideoPersistence
import com.lovelyreader.video.AndroidVideoRootStore
import com.lovelyreader.video.DefaultVideoSiteAdapter
import com.lovelyreader.video.VideoDownloadCoordinator
import com.lovelyreader.video.VideoLibraryRepository
import com.lovelyreader.video.VideoSiteResolver
import com.lovelyreader.update.AndroidAppUpdater
import com.lovelyreader.update.UpdateHistoryEntry
import com.lovelyreader.update.UpdateCheckResult
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class Screen {
    data object Shelf : Screen()
    data object Search : Screen()
    data class Detail(val result: SearchResult) : Screen()
    data class Reader(val bookId: String) : Screen()
    data object Settings : Screen()
}

private enum class AppExperience { Reader, Drama }

@Composable
fun LovelyReaderApp(
    splashBitmap: android.graphics.Bitmap? = null,
    onSplashReady: () -> Unit = {}
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val isDebugBuild = appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val repository = LibraryRepository()
    val persistence = AndroidLibraryPersistence(context.applicationContext)
    val sync = ReadingLogSync(context.applicationContext)
    val bookDownloadScheduler = remember(appContext) { AndroidBookDownloadScheduler(appContext) }
    val viewModel: LibraryViewModel = viewModel {
        LibraryViewModel(repository, persistence, sync, bookDownloadScheduler)
    }
    val videoScope = rememberCoroutineScope()
    val videoPageFetcher = remember(appContext) {
        AndroidVideoPageFetcher(
            connectivityProbe = AndroidVideoHostConnectivityProbe(appContext),
            trustedDnsFallback = AndroidTrustedVideoDnsFallback(appContext)
        )
    }
    val videoRepository = remember(appContext) {
        VideoLibraryRepository(AndroidVideoPersistence(appContext), videoScope)
    }
    val videoCoordinator = remember(appContext) {
        VideoDownloadCoordinator(AndroidDownloadManagerGateway(appContext))
    }
    val appUpdater = remember(appContext) { AndroidAppUpdater(appContext) }
    var updateMessage by rememberSaveable { mutableStateOf("") }
    var updateAvailable by remember { mutableStateOf<com.lovelyreader.update.UpdateManifest?>(null) }
    var updateHistory by remember { mutableStateOf<List<UpdateHistoryEntry>>(emptyList()) }
    var updateHistoryMessage by rememberSaveable { mutableStateOf("") }
    var showUpdatePrompt by rememberSaveable { mutableStateOf(false) }
    val dramaViewModel: DramaViewModel = viewModel {
        DramaViewModel(
            rootResolver = DramaRootResolver {
                VideoSiteResolver(videoPageFetcher, AndroidVideoRootStore(appContext)).resolve()
            },
            siteAdapter = DefaultVideoSiteAdapter(videoPageFetcher),
            library = VideoLibraryDramaStore(videoRepository),
            downloadEnqueuer = DramaDownloadEnqueuer(videoCoordinator::download)
        )
    }
    var experience by rememberSaveable { mutableStateOf(AppExperience.Reader) }

    val screen by viewModel.screen.collectAsState()
    val shelfBooks by viewModel.shelfBooks.collectAsState()
    val shelfSortMode by viewModel.shelfSortMode.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val rankingResults by viewModel.rankingResults.collectAsState()
    val randomResults by viewModel.randomResults.collectAsState()
    val searchMessage by viewModel.searchMessage.collectAsState()
    val rankingMessage by viewModel.rankingMessage.collectAsState()
    val randomMessage by viewModel.randomMessage.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val isLoadingRanking by viewModel.isLoadingRanking.collectAsState()
    val isLoadingRandom by viewModel.isLoadingRandom.collectAsState()
    val isRandomExhausted by viewModel.isRandomExhausted.collectAsState()
    val selectedDetail by viewModel.selectedDetail.collectAsState()
    val readerChapter by viewModel.readerChapter.collectAsState()
    val isLoadingChapter by viewModel.isLoadingChapter.collectAsState()
    val chapterLoadAttempted by viewModel.chapterLoadAttempted.collectAsState()
    val lastReaderBookId by viewModel.lastReaderBookId.collectAsState()
    val downloadStatuses by viewModel.downloadStatuses.collectAsState()
    val downloadingBookIds by viewModel.downloadingBookIds.collectAsState()
    val appTheme by viewModel.appTheme.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()

    LaunchedEffect(appUpdater) {
        when (val result = appUpdater.checkAutomatically()) {
            is UpdateCheckResult.Available -> {
                updateAvailable = result.manifest
                updateMessage = "发现新版本 ${result.manifest.versionName}"
                showUpdatePrompt = true
            }
            else -> Unit
        }
    }

    val mainBottomBar: @Composable (MainTab) -> Unit = { selected ->
        MainBottomBar(
            selected = selected,
            onShelf = { viewModel.openShelf() },
            onSearch = { viewModel.openSearch() },
            onReader = { viewModel.openReader() },
            onNotes = { viewModel.openNotes() }
        )
    }
    val renderExperienceSwitch: @Composable () -> Unit = {
        AppExperienceSwitch(
            selected = experience,
            onSelected = { experience = it },
            compact = true,
            compactWidth = 220.dp
        )
    }
    val renderInlineExperienceSwitch: @Composable () -> Unit = {
        AppExperienceSwitch(
            selected = experience,
            onSelected = { experience = it },
            compact = true,
            compactWidth = 112.dp
        )
    }
    val browseScreen: @Composable () -> Unit = {
        SearchScreen(
            results = searchResults,
            rankingResults = rankingResults,
            randomResults = randomResults,
            categories = DiscoveryCatalog.primaryCategories,
            romanceCategories = DiscoveryCatalog.romanceCategories,
            message = searchMessage,
            isSearching = isSearching,
            isLoadingRanking = isLoadingRanking,
            isLoadingRandom = isLoadingRandom,
            isRandomExhausted = isRandomExhausted,
            rankingMessage = rankingMessage,
            randomMessage = randomMessage,
            searchHistory = searchHistory,
            onBack = { viewModel.openShelf() },
            onSearch = { query -> viewModel.performSearch(query) },
            onRankingChanged = { period, category -> viewModel.refreshRanking(period, category) },
            onRandomBrowse = { category -> viewModel.refreshRandomBrowse(category) },
            onRestartRandomBrowse = { category -> viewModel.restartRandomBrowse(category) },
            onSearchModeChanged = { category -> viewModel.refreshRandomBrowse(category) },
            onCancelDiscoveryLoads = { viewModel.cancelDiscoveryLoads() },
            onOpenResult = { result -> viewModel.navigateToDetail(result) },
            onAddResultToShelf = { result -> viewModel.startDownloadToShelf(result) },
            experienceSwitch = renderInlineExperienceSwitch,
            bottomBar = { mainBottomBar(MainTab.Search) }
        )
    }

    RandomSplash(
        splashBitmap = splashBitmap,
        onSplashReady = onSplashReady
    ) {
        LovelyReaderTheme(theme = appTheme) {
        updateAvailable?.takeIf { showUpdatePrompt }?.let { manifest ->
            AlertDialog(
                onDismissRequest = { showUpdatePrompt = false },
                title = { Text("发现新版本 ${manifest.versionName}") },
                text = { Text(manifest.notes.ifBlank { "包含体验改进，可在设置中随时更新。" }) },
                confirmButton = {
                    Button(onClick = {
                        showUpdatePrompt = false
                        videoScope.launch {
                            updateMessage = "正在下载更新包…"
                            appUpdater.downloadAndPrepare(manifest).onSuccess { apk ->
                                if (appUpdater.canRequestInstallPackages()) appUpdater.install(apk)
                                else {
                                    updateMessage = "请先允许本应用安装更新包，然后再次点击安装"
                                    appUpdater.openInstallPermissionSettings()
                                }
                            }.onFailure { updateMessage = it.message ?: "更新包下载失败" }
                        }
                    }) { Text("立即更新") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showUpdatePrompt = false }) { Text("稍后") }
                }
            )
        }
        Column(modifier = Modifier.fillMaxSize().background(appColors().cream)) {
        BackHandler(
            enabled = experience == AppExperience.Reader && screen !is Screen.Reader && readerBackDestination(screen) != null
        ) {
            viewModel.navigateBack()
        }
        Box(
            modifier = if (shouldShowSharedAppChrome(screen)) Modifier.fillMaxWidth().weight(1f)
            else Modifier.fillMaxSize()
        ) {
        if (experience == AppExperience.Drama) {
            DramaScreen(
                viewModel = dramaViewModel,
                debugDiagnostics = if (isDebugBuild) videoPageFetcher::diagnostics else null,
                experienceSwitch = renderExperienceSwitch
            )
        } else {
        // Keep this exact call-site for Search and Detail; branch-local calls recreate it.
        if (shouldComposeBrowseSurface(screen)) browseScreen()
        when (val current = screen) {
        Screen.Shelf -> BookshelfScreen(
            books = remember(shelfBooks, shelfSortMode) { viewModel.sortBooks(shelfBooks, shelfSortMode) },
            progressFor = { viewModel.progressFor(it) },
            downloadStatuses = downloadStatuses,
            sortMode = shelfSortMode,
            onSortModeChanged = { viewModel.setShelfSortMode(it) },
            onSearch = { viewModel.openSearch() },
            lastReaderBookId = lastReaderBookId,
            onOpenBook = { book ->
                when {
                    viewModel.isBookReady(book.id) -> viewModel.navigateToReader(book.id)
                    book.id in downloadingBookIds -> { /* 已在下载中，避免重复触发 */ }
                    else -> viewModel.retryDownload(book)
                }
            },
            onDeleteBook = { book -> viewModel.deleteBook(book.id) },
            onSettings = { viewModel.openNotes() },
            experienceSwitch = renderExperienceSwitch,
            bottomBar = { mainBottomBar(MainTab.Shelf) }
        )

        Screen.Search -> Unit

        is Screen.Detail -> {
            BookDetailScreenWrapper(
            result = current.result,
            detail = selectedDetail,
            onBack = { viewModel.openSearch() },
            onAddToShelf = {
                viewModel.startDownloadToShelf(current.result, selectedDetail?.book)
            },
            onOpenOriginal = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(current.result.bookUrl))
                    )
                }
            },
            loadDetail = { viewModel.loadDetail(current.result) },
            experienceSwitch = renderExperienceSwitch,
            bottomBar = { mainBottomBar(MainTab.Search) }
            )
        }

        is Screen.Reader -> {
            val (initialFontSize, initialNightMode) = viewModel.readerPreferences()
            val (initialIndex, initialOffset) = viewModel.lastReadPositionFor(current.bookId)
            ReaderScreen(
                book = viewModel.bookById(current.bookId) ?: emptyBook(),
                chapterContent = readerChapter,
                isLoadingChapter = isLoadingChapter,
                chapterLoadAttempted = chapterLoadAttempted,
                initialFontSize = initialFontSize,
                initialNightMode = initialNightMode,
                initialScrollIndex = initialIndex,
                initialScrollOffset = initialOffset,
                loadChapter = { viewModel.loadChapter(current.bookId) },
                onBack = { viewModel.openShelf() },
                onPositionChanged = { percent, index, offset ->
                    viewModel.updateProgress(current.bookId, percent, index, offset)
                },
                onFontSizeChanged = { size -> viewModel.updateReaderFontSize(size) },
                onNightModeChanged = { night -> viewModel.updateReaderNightMode(night) },
                bottomBar = {}
            )
        }

        Screen.Settings -> SettingsScreen(
            notes = viewModel.notes(),
            onBack = { viewModel.openShelf() },
            bottomBar = { mainBottomBar(MainTab.Notes) },
            currentTheme = appTheme,
            onThemeChanged = { viewModel.setAppTheme(it) },
            updateMessage = updateMessage,
            updateAvailable = updateAvailable,
            onCheckUpdate = {
                videoScope.launch {
                    updateAvailable = null
                    updateMessage = "正在检查更新…"
                    when (val result = appUpdater.check()) {
                        UpdateCheckResult.FeedNotConfigured -> updateMessage = "更新服务尚未发布，当前版本仍可正常使用"
                        UpdateCheckResult.UpToDate -> updateMessage = "已经是最新版本"
                        is UpdateCheckResult.Available -> {
                            updateAvailable = result.manifest
                            updateMessage = "发现新版本 ${result.manifest.versionName}"
                            showUpdatePrompt = true
                        }
                        is UpdateCheckResult.Failed -> updateMessage = result.message
                    }
                }
            },
            onInstallUpdate = { manifest ->
                videoScope.launch {
                    updateMessage = "正在下载更新包…"
                    appUpdater.downloadAndPrepare(manifest).onSuccess { apk ->
                        if (appUpdater.canRequestInstallPackages()) appUpdater.install(apk)
                        else {
                            updateMessage = "请先允许本应用安装更新包，然后再次点击安装"
                            appUpdater.openInstallPermissionSettings()
                        }
                    }.onFailure { updateMessage = it.message ?: "更新包下载失败" }
                }
            },
            updateHistory = updateHistory,
            updateHistoryMessage = updateHistoryMessage,
            onLoadUpdateHistory = {
                videoScope.launch {
                    updateHistoryMessage = "正在读取版本记录…"
                    appUpdater.history().onSuccess {
                        updateHistory = it
                        updateHistoryMessage = if (it.isEmpty()) "暂时没有可展示的版本记录" else ""
                    }.onFailure {
                        updateHistoryMessage = "版本记录暂不可用"
                    }
                }
            }
        )
        }
        }
        }
        }
        }
    }
}

@Composable
private fun AppExperienceSwitch(
    selected: AppExperience,
    onSelected: (AppExperience) -> Unit,
    compact: Boolean = false,
    compactWidth: Dp = 220.dp
) {
    Surface(
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        if (compact) {
            CompactExperienceSwitch(
                selected = selected,
                onSelected = onSelected,
                width = compactWidth
            )
        } else Row(
            modifier = if (compact) {
                Modifier.width(compactWidth).padding(vertical = 4.dp)
            } else {
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)
            },
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (selected == AppExperience.Reader) {
                Button(
                    onClick = {},
                    modifier = Modifier.weight(1f).height(if (compact) 40.dp else 48.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(containerColor = appColors().roseDust)
                ) { Text("小书架", fontSize = if (compact) 14.sp else 16.sp) }
            } else {
                OutlinedButton(
                    onClick = { onSelected(AppExperience.Reader) },
                    modifier = Modifier.weight(1f).height(if (compact) 40.dp else 48.dp),
                    shape = MaterialTheme.shapes.large
                ) { Text("小书架", fontSize = if (compact) 14.sp else 16.sp) }
            }
            if (selected == AppExperience.Drama) {
                Button(
                    onClick = {},
                    modifier = Modifier.weight(1f).height(if (compact) 40.dp else 48.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(containerColor = appColors().roseDust)
                ) { Text("追剧", fontSize = if (compact) 14.sp else 16.sp) }
            } else {
                OutlinedButton(
                    onClick = { onSelected(AppExperience.Drama) },
                    modifier = Modifier.weight(1f).height(if (compact) 40.dp else 48.dp),
                    shape = MaterialTheme.shapes.large
                ) { Text("追剧", fontSize = if (compact) 14.sp else 16.sp) }
            }
        }
    }
}

@Composable
private fun CompactExperienceSwitch(
    selected: AppExperience,
    onSelected: (AppExperience) -> Unit,
    width: Dp
) {
    Row(
        modifier = Modifier.width(width).height(40.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val itemWidth = (width - 6.dp) / 2
        listOf(AppExperience.Reader to "小书架", AppExperience.Drama to "追剧").forEach { (value, label) ->
            val active = selected == value
            Surface(
                modifier = Modifier.width(itemWidth).fillMaxHeight().clickable(enabled = !active) { onSelected(value) },
                shape = RoundedCornerShape(22.dp),
                color = if (active) appColors().roseDust else Color.Transparent,
                border = if (active) null else BorderStroke(1.dp, appColors().roseBeige)
            ) {
                Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text(label, color = if (active) Color.White else appColors().roseDust, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun RandomSplash(
    splashBitmap: android.graphics.Bitmap?,
    onSplashReady: () -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var showSplash by rememberSaveable { mutableStateOf(true) }
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // Use the bitmap pre-decoded on the main thread before setContent when available;
    // fall back to decoding a random asset if the caller did not supply one.
    LaunchedEffect(splashBitmap) {
        imageBitmap = splashBitmap?.asImageBitmap()
            ?: withContext(Dispatchers.IO) {
                val files = context.assets.list("splash")?.filter {
                    it.endsWith(".png", ignoreCase = true) ||
                        it.endsWith(".jpg", ignoreCase = true) ||
                        it.endsWith(".jpeg", ignoreCase = true)
                } ?: emptyList()
                val path = files.randomOrNull()?.let { "splash/$it" }
                runCatching {
                    path?.let {
                        context.assets.open(it).use { stream ->
                            BitmapFactory.decodeStream(stream)?.asImageBitmap()
                        }
                    }
                }.getOrNull()
            }
    }

    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (imageBitmap == null && System.currentTimeMillis() - start < 3000) {
            delay(50)
        }
        val elapsed = System.currentTimeMillis() - start
        if (elapsed < 400) delay(400 - elapsed)
        showSplash = false
        onSplashReady()
    }

    if (showSplash) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
            imageBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    } else {
        content()
    }
}

@Composable
private fun BookDetailScreenWrapper(
    result: SearchResult,
    detail: BookDetail?,
    onBack: () -> Unit,
    onAddToShelf: () -> Unit,
    onOpenOriginal: () -> Unit,
    loadDetail: suspend () -> Unit,
    experienceSwitch: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {}
) {
    LaunchedEffect(result.bookUrl) {
        loadDetail()
    }
    BookDetailScreen(
        result = result,
        detail = detail,
        onBack = onBack,
        onAddToShelf = onAddToShelf,
        onOpenOriginal = onOpenOriginal,
        experienceSwitch = experienceSwitch,
        bottomBar = bottomBar
    )
}

private fun emptyBook(): Book {
    return Book(
        id = "empty",
        title = "还没有选中的书",
        author = "老公的小书架",
        summary = "先去帮老婆找一本喜欢的故事吧。"
    )
}
