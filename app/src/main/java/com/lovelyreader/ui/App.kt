package com.lovelyreader.ui

import android.content.pm.ApplicationInfo
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.input.pointer.pointerInput
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
import com.lovelyreader.update.UpdateDownloadPhase
import com.lovelyreader.update.UpdateDownloadProgress
import com.lovelyreader.update.UpdateHistoryEntry
import com.lovelyreader.update.UpdateCheckResult
import com.lovelyreader.update.formatUpdateDownloadProgress
import androidx.compose.runtime.collectAsState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.lovelyreader.data.LibraryBackupCodec
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
    var updateDownloadProgress by remember { mutableStateOf<UpdateDownloadProgress?>(null) }
    var updateHistory by remember { mutableStateOf<List<UpdateHistoryEntry>>(emptyList()) }
    var updateHistoryMessage by rememberSaveable { mutableStateOf("") }
    var showUpdatePrompt by rememberSaveable { mutableStateOf(false) }
    var backupMessage by rememberSaveable { mutableStateOf("") }
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
    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        videoScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(LibraryBackupCodec.encode(viewModel.snapshotForBackup()).toByteArray(Charsets.UTF_8))
                    } ?: error("无法写入备份文件")
                }
            }.onSuccess { backupMessage = "备份已导出" }
                .onFailure { backupMessage = "备份导出失败：${it.message.orEmpty()}" }
        }
    }
    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        videoScope.launch {
            runCatching {
                val raw = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("无法读取备份文件")
                }
                LibraryBackupCodec.decode(raw).getOrThrow()
            }.onSuccess {
                viewModel.restoreBackup(it)
                backupMessage = "备份已恢复"
            }.onFailure { backupMessage = "备份恢复失败：${it.message.orEmpty()}" }
        }
    }
    var experience by rememberSaveable { mutableStateOf(AppExperience.Reader) }
    var showVisualFixture by rememberSaveable { mutableStateOf(false) }

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
    val searchSeed by viewModel.searchSeed.collectAsState()

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

    fun startUpdateDownload(manifest: com.lovelyreader.update.UpdateManifest) {
        updateMessage = "正在下载更新包…"
        updateDownloadProgress = UpdateDownloadProgress(
            downloadedBytes = 0L,
            totalBytes = null,
            speedBytesPerSecond = 0L,
            phase = UpdateDownloadPhase.Downloading
        )
        videoScope.launch {
            appUpdater.downloadAndPrepare(manifest) { progress ->
                withContext(Dispatchers.Main.immediate) {
                    updateDownloadProgress = progress
                }
            }.onSuccess { apk ->
                updateMessage = "下载完成，正在准备安装…"
                if (appUpdater.canRequestInstallPackages()) appUpdater.install(apk)
                else {
                    updateMessage = "请先允许本应用安装更新包，然后再次点击安装"
                    appUpdater.openInstallPermissionSettings()
                }
            }.onFailure {
                updateDownloadProgress = null
                updateMessage = it.message ?: "更新包下载失败"
            }
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
            compactWidth = highFidelityPhoneMetrics().shelfExperienceSwitchWidthDp.dp,
            compactHeight = 30.dp
        )
    }
    val renderInlineExperienceSwitch: @Composable () -> Unit = {
        AppExperienceSwitch(
            selected = experience,
            onSelected = { experience = it },
            compact = true,
            compactWidth = highFidelityPhoneMetrics().inlineExperienceSwitchWidthDp.dp,
            compactHeight = 30.dp
        )
    }
    val renderDetailToggle: @Composable () -> Unit = {
        AppExperienceSwitch(
            selected = experience,
            onSelected = { experience = it },
            compact = true,
            compactWidth = highFidelityDetailChrome().switchWidthDp.dp,
            compactHeight = 24.dp,
            showLabels = false
        )
    }
    val renderDramaHomeExperienceSwitch: @Composable () -> Unit = {
        AppExperienceSwitch(
            selected = experience,
            onSelected = { experience = it },
            compact = true,
            compactWidth = highFidelityPhoneMetrics().dramaHomeExperienceSwitchWidthDp.dp,
            compactHeight = 30.dp
        )
    }
    val renderDramaDetailExperienceSwitch: @Composable () -> Unit = {
        AppExperienceSwitch(
            selected = experience,
            onSelected = { experience = it },
            compact = true,
            compactWidth = highFidelityPhoneMetrics().dramaDetailExperienceSwitchWidthDp.dp,
            compactHeight = 30.dp,
            singleLabel = "追剧"
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
            initialQuery = searchSeed,
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
            bottomBar = {}
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
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(manifest.notes.ifBlank { "包含体验改进，可在设置中随时更新。" })
                        updateDownloadProgress?.let { progress ->
                            Text(
                                formatUpdateDownloadProgress(progress),
                                style = MaterialTheme.typography.bodyMedium,
                                color = appColors().roseDust
                            )
                            if (progress.phase == UpdateDownloadPhase.Downloading) {
                                progress.fraction?.let { fraction ->
                                    androidx.compose.material3.LinearProgressIndicator(
                                        progress = { fraction },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = appColors().roseDust,
                                        trackColor = appColors().almond.copy(alpha = .65f)
                                    )
                                } ?: androidx.compose.material3.LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = appColors().roseDust,
                                    trackColor = appColors().almond.copy(alpha = .65f)
                                )
                            }
                        }
                        updateMessage.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = appColors().cocoa.copy(alpha = .72f))
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        startUpdateDownload(manifest)
                    }, enabled = updateDownloadProgress?.phase != UpdateDownloadPhase.Downloading) {
                        Text(if (updateDownloadProgress?.phase == UpdateDownloadPhase.Ready) "再次下载" else "立即更新")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showUpdatePrompt = false }) { Text("稍后") }
                }
            )
        }
        Column(modifier = Modifier.fillMaxSize().background(appColors().cream)) {
        BackHandler(
            enabled = !showVisualFixture &&
                experience == AppExperience.Reader &&
                screen !is Screen.Reader &&
                (readerBackDestination(screen) != null || shouldConsumeRootSystemBack(screen))
        ) {
            viewModel.navigateBack()
        }
        Box(
            modifier = if (shouldShowSharedAppChrome(screen)) Modifier.fillMaxWidth().weight(1f)
            else Modifier.fillMaxSize()
        ) {
        if (showVisualFixture) {
            HighFidelityDebugFixtureScreen(
                onClose = { showVisualFixture = false },
                modifier = Modifier.fillMaxSize()
            )
        } else if (experience == AppExperience.Drama) {
            DramaScreen(
                viewModel = dramaViewModel,
                debugDiagnostics = if (isDebugBuild) videoPageFetcher::diagnostics else null,
                experienceSwitch = renderDramaHomeExperienceSwitch,
                detailExperienceSwitch = renderDramaDetailExperienceSwitch,
                onHomeBack = {
                    experience = AppExperience.Reader
                    viewModel.openShelf()
                }
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
            onAuthorClick = { author -> viewModel.openSearchFor(author) },
            onAddToShelf = {
                viewModel.startDownloadToShelf(current.result, selectedDetail?.book)
            },
            loadDetail = { viewModel.loadDetail(current.result) },
            // The v3 detail concept keeps the experience toggle compact in
            // the top chrome; the full-width switch is reserved for shelf and
            // drama home where it is the primary page control.
            experienceSwitch = renderDetailToggle,
            bottomBar = {}
            )
        }

        is Screen.Reader -> {
            val (initialFontSize, initialNightMode) = viewModel.readerPreferences()
            val initialLineSpacing = viewModel.readerLineSpacing()
            val (initialIndex, initialOffset) = viewModel.lastReadPositionFor(current.bookId)
            ReaderScreen(
                book = viewModel.bookById(current.bookId) ?: emptyBook(),
                chapterContent = readerChapter,
                isLoadingChapter = isLoadingChapter,
                chapterLoadAttempted = chapterLoadAttempted,
                initialFontSize = initialFontSize,
                initialLineSpacing = initialLineSpacing,
                initialNightMode = initialNightMode,
                initialScrollIndex = initialIndex,
                initialScrollOffset = initialOffset,
                loadChapter = { viewModel.loadChapter(current.bookId) },
                onBack = { viewModel.openShelf() },
                onPositionChanged = { percent, index, offset ->
                    viewModel.updateProgress(current.bookId, percent, index, offset)
                },
                onFontSizeChanged = { size -> viewModel.updateReaderFontSize(size) },
                onLineSpacingChanged = { spacing -> viewModel.updateReaderLineSpacing(spacing) },
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
            updateDownloadProgress = updateDownloadProgress,
            onCheckUpdate = {
                videoScope.launch {
                    updateAvailable = null
                    updateDownloadProgress = null
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
            onInstallUpdate = ::startUpdateDownload,
            updateHistory = updateHistory,
            updateHistoryMessage = updateHistoryMessage,
            onExportBackup = { exportBackupLauncher.launch("lovely-camp-backup.json") },
            onImportBackup = { importBackupLauncher.launch(arrayOf("application/json", "text/plain")) },
            backupMessage = backupMessage,
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
            },
            onOpenVisualFixture = if (highFidelityDebugFixtureEnabled(isDebugBuild)) {
                { showVisualFixture = true }
            } else null
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
    compactWidth: Dp = 220.dp,
    compactHeight: Dp = 24.dp,
    showLabels: Boolean = true,
    singleLabel: String? = null
) {
    UnifiedExperienceSwitch(
        selected = selected,
        onSelected = onSelected,
        width = if (compact) compactWidth else null,
        compact = compact,
        compactHeight = compactHeight,
        showLabels = showLabels,
        singleLabel = singleLabel
    )
}

@Composable
private fun UnifiedExperienceSwitch(
    selected: AppExperience,
    onSelected: (AppExperience) -> Unit,
    width: Dp?,
    compact: Boolean,
    compactHeight: Dp,
    showLabels: Boolean,
    singleLabel: String?
) {
    val height = if (compact) compactHeight else 56.dp
    Surface(
        modifier = (if (width != null) Modifier.width(width) else Modifier.fillMaxWidth().padding(horizontal = 24.dp))
            .height(height)
            .padding(vertical = if (compact) 0.dp else 4.dp),
        shape = RoundedCornerShape(height / 2),
        color = if (!showLabels) appColors().cocoa else appColors().warmWhite.copy(alpha = .58f),
        border = BorderStroke(1.dp, appColors().lineColor)
    ) {
        if (!showLabels) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(selected) {
                        detectTapGestures {
                            onSelected(if (selected == AppExperience.Reader) AppExperience.Drama else AppExperience.Reader)
                        }
                    },
                contentAlignment = if (selected == AppExperience.Reader) androidx.compose.ui.Alignment.CenterEnd else androidx.compose.ui.Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .padding(2.dp)
                        .size(height - 4.dp)
                        .background(Color.White, androidx.compose.foundation.shape.CircleShape)
                )
            }
            return@Surface
        }
        if (singleLabel != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(appColors().roseDust, RoundedCornerShape(height / 2))
                    .pointerInput(singleLabel) {
                        detectTapGestures { onSelected(AppExperience.Drama) }
                    },
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(singleLabel, color = Color.White, fontSize = if (compact) 14.sp else 17.sp)
            }
            return@Surface
        }
        Row(modifier = Modifier.fillMaxSize().padding(2.dp)) {
            listOf(AppExperience.Reader to "小书架", AppExperience.Drama to "追剧").forEach { (value, label) ->
                val active = selected == value
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (active) appColors().roseDust else Color.Transparent, RoundedCornerShape(height / 2))
                        .pointerInput(value, active) {
                            detectTapGestures(onTap = { if (!active) onSelected(value) })
                        },
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(label, color = if (active) Color.White else appColors().roseDust, fontSize = if (compact) 14.sp else 17.sp)
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
    onAuthorClick: (String) -> Unit,
    onAddToShelf: () -> Unit,
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
        onAuthorClick = onAuthorClick,
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
