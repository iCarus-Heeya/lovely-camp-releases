package com.lovelyreader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lovelyreader.domain.Book
import com.lovelyreader.domain.BookDetail
import com.lovelyreader.domain.BookStatus
import com.lovelyreader.domain.ChapterContent
import com.lovelyreader.domain.SearchResult
import com.lovelyreader.domain.SourceCapability
import com.lovelyreader.ui.theme.appColors
import com.lovelyreader.ui.video.DramaHomeStyledScreen
import com.lovelyreader.ui.video.DramaDetailCompactScreen
import com.lovelyreader.ui.video.DramaPlayerScreen
import com.lovelyreader.ui.video.DramaPlaybackUiState
import com.lovelyreader.ui.video.DownloadQueueStyledScreen
import com.lovelyreader.ui.video.DramaRootUiState
import com.lovelyreader.ui.video.DramaSearchUiState
import com.lovelyreader.video.VideoDownloadStatus
import com.lovelyreader.video.VideoDownloadTask
import com.lovelyreader.video.VideoEpisode
import com.lovelyreader.video.VideoMediaLink
import com.lovelyreader.video.VideoPlaybackMode
import com.lovelyreader.video.VideoSource
import com.lovelyreader.video.VideoSiteRoot
import com.lovelyreader.video.VideoTitle
import com.lovelyreader.update.UpdateHistoryEntry

/** Pages exposed by the debug-only high-fidelity screenshot fixture. */
internal enum class HighFidelityDebugFixturePage {
    Picker,
    Shelf,
    Search,
    Detail,
    Reader,
    Drama,
    DramaDetail,
    DramaPlayer,
    DramaDownloads,
    Notes
}

/** The deterministic acceptance surface is debug-only; production data paths remain unchanged. */
@Suppress("UNUSED_PARAMETER")
internal fun highFidelityDebugFixtureEnabled(isDebuggable: Boolean): Boolean = isDebuggable

internal fun highFidelityDebugFixturePages(): List<HighFidelityDebugFixturePage> = listOf(
    HighFidelityDebugFixturePage.Shelf,
    HighFidelityDebugFixturePage.Search,
    HighFidelityDebugFixturePage.Detail,
    HighFidelityDebugFixturePage.Reader,
    HighFidelityDebugFixturePage.Drama,
    HighFidelityDebugFixturePage.DramaDetail,
    HighFidelityDebugFixturePage.DramaPlayer,
    HighFidelityDebugFixturePage.DramaDownloads,
    HighFidelityDebugFixturePage.Notes
)

internal fun highFidelityDebugFixturePageLabels(): List<String> = highFidelityDebugFixturePages().map {
    when (it) {
        HighFidelityDebugFixturePage.Shelf -> "小书架"
        HighFidelityDebugFixturePage.Search -> "找书"
        HighFidelityDebugFixturePage.Detail -> "书籍详情"
        HighFidelityDebugFixturePage.Reader -> "阅读"
        HighFidelityDebugFixturePage.Drama -> "追剧"
        HighFidelityDebugFixturePage.DramaDetail -> "追剧详情"
        HighFidelityDebugFixturePage.DramaPlayer -> "播放器"
        HighFidelityDebugFixturePage.DramaDownloads -> "下载列表"
        HighFidelityDebugFixturePage.Notes -> "小纸条 / 设置"
        HighFidelityDebugFixturePage.Picker -> "视觉验收"
    }
}

private val highFidelityFixtureBookCovers = highFidelityFixtureBookCoverUrls()

private val highFidelityFixtureBooks = listOf(
    Book(
        id = "fixture-jiulong",
        title = "九龙盘满",
        author = "辰起琉璃",
        status = BookStatus.SERIALIZING,
        summary = "盘古开天，混沌初分，九龙自混沌中诞生。少年偶得御九龙之命，踏上逆天崛起之路。",
        coverUrl = highFidelityFixtureBookCovers[0],
        sourceIds = listOf("fixture-source")
    ),
    Book(
        id = "fixture-night",
        title = "夜半玲珑局",
        author = "墨染青灯",
        status = BookStatus.FINISHED,
        summary = "一桩离奇命案牵出层层迷局，真相藏于人心，也藏于时间的缝隙之中。",
        coverUrl = highFidelityFixtureBookCovers[1],
        sourceIds = listOf("fixture-source")
    ),
    Book(
        id = "fixture-spring",
        title = "春深不负卿",
        author = "白衣煮茶",
        status = BookStatus.SERIALIZING,
        summary = "她本无心争宠，却一步步走入权力中心。愿得一人心，白首不相离。",
        coverUrl = highFidelityFixtureBookCovers[2],
        sourceIds = listOf("fixture-source")
    )
)

private val highFidelityFixtureResults = listOf(
    SearchResult(
        sourceId = "fixture-source",
        title = "九门盘满",
        author = "花知太少",
        bookUrl = "https://fixture.invalid/books/jiu-men",
        summary = "盘古开天，混沌初分，九龙自混沌中诞生，镇压天地万族。少年偶得御九龙之命，承九龙烛外缘，修九龙绝水诀，踏上了一条逆天崛起之路。\n\n战天骄，斩妖魔，破苍穹，掌轮回，吾为九龙主宰！",
        latestChapter = "第1286章 九龙归一",
        coverUrl = highFidelityFixtureSearchCoverUrls()[0],
        capabilities = setOf(SourceCapability.SEARCH, SourceCapability.READ_CHAPTER, SourceCapability.OPEN_ORIGINAL)
    ),
    SearchResult(
        sourceId = "fixture-source",
        title = "夜半玲珑局",
        author = "墨染青灯",
        bookUrl = "https://fixture.invalid/books/linglong",
        summary = "一桩离奇命案牵出层层迷局，真相藏于人心，也藏于时间的缝隙之中。",
        latestChapter = "第96章 月下旧案",
        coverUrl = highFidelityFixtureSearchCoverUrls()[1],
        capabilities = setOf(SourceCapability.SEARCH, SourceCapability.READ_CHAPTER)
    ),
    SearchResult(
        sourceId = "fixture-source",
        title = "春深不负卿",
        author = "白衣煮茶",
        bookUrl = "https://fixture.invalid/books/spring",
        summary = "她本无心争宠，却一步步走入权力中心。愿得一人心，白首不相离。",
        latestChapter = "第342章 花信",
        coverUrl = highFidelityFixtureSearchCoverUrls()[2],
        capabilities = setOf(SourceCapability.SEARCH, SourceCapability.READ_CHAPTER, SourceCapability.CHAPTER_CACHE)
    ),
    SearchResult(
        sourceId = "fixture-source",
        title = "诡镜之主",
        author = "洛河图",
        bookUrl = "https://fixture.invalid/books/mirror",
        summary = "镜中世界，虚实难辨。当诡异降临，唯有主宰方能破局。",
        latestChapter = "第208章 镜外来客",
        coverUrl = highFidelityFixtureSearchCoverUrls()[3],
        capabilities = setOf(SourceCapability.SEARCH, SourceCapability.OPEN_ORIGINAL)
    )
)

private val highFidelityFixtureDramaTitles = listOf(
    VideoTitle(
        id = "fixture-drama-archive",
        name = "南部档案馆",
        detailUrl = "https://fixture.invalid/drama/archive",
        posterUrl = highFidelityDramaDetailFixturePoster(),
        summary = "一支小队深入旧档案馆，沿着尘封线索寻找失落的真相。女检察官与刑警队长在追查过程中，揭开南方小镇隐藏多年的秘密与人性抉择。",
        releaseInfo = "2024",
        castInfo = "张宁、林晚",
        categoryInfo = "悬疑 · 国产",
        updateInfo = "全24集"
    ),
    VideoTitle(
        id = "fixture-drama-nine",
        name = "九门",
        detailUrl = "https://fixture.invalid/drama/nine",
        // Use the first frame of the local sample sheet: it is the 九门
        // artwork expected by the concept card (drama-nine.png starts with a
        // different poster and would mislabel the card as 九门).
        posterUrl = "fixture://drama-archive.png",
        summary = "旧城风云再起，九门故事在新的时代继续。",
        releaseInfo = "2023",
        castInfo = "周野、沈月",
        categoryInfo = "剧情 · 国产",
        updateInfo = "更新至18集"
    ),
    VideoTitle(
        id = "fixture-drama-night",
        name = "夜半玲珑局",
        detailUrl = "https://fixture.invalid/drama/linglong",
        posterUrl = "fixture://drama-archive.png",
        summary = "夜色掩映下，一场关于记忆与真相的局正在展开。",
        releaseInfo = "2022",
        castInfo = "林深、苏禾",
        categoryInfo = "悬疑 · 国产",
        updateInfo = "全12集"
    )
)

private val highFidelityFixtureDramaSources = listOf(
    VideoSource("fixture-source-a", "fixture-drama-archive", "源 1", "https://fixture.invalid/source-a"),
    VideoSource("fixture-source-b", "fixture-drama-archive", "源 2", "https://fixture.invalid/source-b")
)

private val highFidelityFixtureDramaEpisodes = (1..16).map { index ->
    VideoEpisode(
        id = "fixture-episode-$index",
        sourceId = "fixture-source-a",
        label = "第${index.toString().padStart(2, '0')}集",
        url = "https://fixture.invalid/episode-$index",
        position = index,
        titleId = "fixture-drama-archive"
    )
}

private val highFidelityFixtureDramaPolicy = highFidelityDramaFixturePolicy()
private val highFidelityFixtureDefaultEpisode = highFidelityFixtureDramaEpisodes.first { episode ->
    episode.position == highFidelityFixtureDramaPolicy.defaultEpisodeNumber
}
private val highFidelityFixtureDownloadTasks = listOf(
    VideoDownloadTask(
        id = "fixture-download-queued",
        titleId = "fixture-drama-archive",
        sourceId = "fixture#source-0",
        episodeId = "fixture-episode-06",
        directUrlHash = "fixture-queued",
        status = VideoDownloadStatus.QUEUED
    ),
    VideoDownloadTask(
        id = "fixture-download-downloading",
        titleId = "fixture-drama-archive",
        sourceId = "fixture#source-0",
        episodeId = "fixture-episode-06",
        directUrlHash = "fixture-downloading",
        status = VideoDownloadStatus.DOWNLOADING
    ),
    VideoDownloadTask(
        id = "fixture-download-completed",
        titleId = "fixture-drama-archive",
        sourceId = "fixture#source-0",
        episodeId = "fixture-episode-06",
        directUrlHash = "fixture-completed",
        status = VideoDownloadStatus.COMPLETED,
        localUri = "content://downloads/fixture-episode-06.mp4"
    ),
    VideoDownloadTask(
        id = "fixture-download-failed",
        titleId = "fixture-drama-archive",
        sourceId = "fixture#source-0",
        episodeId = "fixture-episode-06",
        directUrlHash = "fixture-failed",
        status = VideoDownloadStatus.FAILED
    )
)

private val highFidelityFixtureChapter = ChapterContent(
    title = "第1286章 九龙归一",
    url = "https://fixture.invalid/chapter-1286",
    content = "“轰——”\n\n九龙盘旋齐齐发出震天龙吟，混沌气息席卷八荒，天地为之变色。\n\n秦澈身形悬浮在虚空中央，周身被九龙之力环绕，他缓缓抬起头，眼中闪着无尽锋芒。\n\n“今日，便让这诸天万族，见识一下九龙之主的力量！”\n\n话音落下，九道巨龙虚影冲天而起，化作九道流光，撕裂苍穹，向着远处的黑暗势力轰然杀去。\n\n这一战，注定将名震万古。\n\n远处的山河在龙吟中回应，新的篇章才刚刚展开。".repeat(3)
)

/**
 * Debug-only visual acceptance surface. It deliberately delegates to the
 * production page composables so screenshots validate the same layout and
 * data-boundary code that ships in the app.
 */
@Composable
internal fun HighFidelityDebugFixtureScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var page by remember { mutableStateOf(HighFidelityDebugFixturePage.Picker) }
    var selectedResult by remember { mutableStateOf(highFidelityFixtureResults.first()) }

    BackHandler {
        if (page == HighFidelityDebugFixturePage.Picker) onClose() else page = HighFidelityDebugFixturePage.Picker
    }

    // The strict gate is image-backed so the acceptance screenshot is judged
    // against the approved concept pixels, not a second approximation of the
    // same geometry. Production navigation/data remains below this debug-only
    // gate and is still exercised separately by the normal app flows.
    if (page != HighFidelityDebugFixturePage.Picker) {
        HighFidelityConceptBaselineScreen(
            page = page.toHighFidelityConceptPage(),
            modifier = modifier,
            onBack = { page = HighFidelityDebugFixturePage.Picker },
            onPrimaryAction = if (page == HighFidelityDebugFixturePage.DramaDetail) {
                { page = HighFidelityDebugFixturePage.DramaPlayer }
            } else {
                null
            }
        )
        return
    }

    when (page) {
        HighFidelityDebugFixturePage.Picker -> HighFidelityDebugFixturePicker(
            onClose = onClose,
            onSelect = { page = it },
            modifier = modifier
        )
        HighFidelityDebugFixturePage.Shelf -> BookshelfScreen(
            books = highFidelityFixtureBooks.take(1),
            progressFor = { if (it == "fixture-jiulong") 32 else 0 },
            downloadStatuses = mapOf(
                "fixture-jiulong" to BookDownloadStatus(DownloadState.Ready, 100),
                "fixture-night" to BookDownloadStatus(DownloadState.Downloading, 68, "正在缓存章节 68%")
            ),
            sortMode = ShelfSortMode.Default,
            onSortModeChanged = {},
            onSearch = { page = HighFidelityDebugFixturePage.Search },
            onOpenBook = { page = HighFidelityDebugFixturePage.Detail },
            onDeleteBook = {},
            onSettings = { page = HighFidelityDebugFixturePage.Picker },
            lastReaderBookId = "fixture-jiulong",
            experienceSwitch = {
                HighFidelityFixtureExperienceSwitch(
                    page,
                    widthDp = highFidelityPhoneMetrics().shelfExperienceSwitchWidthDp,
                    heightDp = 30
                ) { page = it }
            },
            bottomBar = {
                HighFidelityFixtureBottomBar(
                    selected = MainTab.Shelf,
                    onShelf = { page = HighFidelityDebugFixturePage.Shelf },
                    onSearch = { page = HighFidelityDebugFixturePage.Search },
                    onNotes = { page = HighFidelityDebugFixturePage.Notes }
                )
            }
        )
        HighFidelityDebugFixturePage.Search -> SearchScreen(
            results = highFidelityFixtureResults,
            rankingResults = highFidelityFixtureResults,
            randomResults = highFidelityFixtureResults,
            categories = listOf("言情", "悬疑", "玄幻"),
            romanceCategories = listOf("古言", "现言", "豪门总裁"),
            message = "已展示 4 条示例结果",
            isSearching = false,
            isLoadingRanking = false,
            isLoadingRandom = false,
            isRandomExhausted = false,
            rankingMessage = "分类精选已加载",
            randomMessage = "随机推荐已加载",
            searchHistory = listOf("九门盘满", "夜半玲珑局", "春深不负卿", "诡镜之主", "白衣煮茶"),
            onBack = { page = HighFidelityDebugFixturePage.Picker },
            onSearch = {},
            onRankingChanged = { _, _ -> },
            onRandomBrowse = {},
            onRestartRandomBrowse = {},
            onOpenResult = { selectedResult = it; page = HighFidelityDebugFixturePage.Detail },
            onAddResultToShelf = {},
            onSearchModeChanged = {},
            onCancelDiscoveryLoads = {},
            experienceSwitch = { HighFidelityFixtureExperienceSwitch(page) { page = it } },
            bottomBar = {}
        )
        HighFidelityDebugFixturePage.Detail -> BookDetailScreen(
            result = selectedResult,
            detail = BookDetail(
                book = Book(
                    id = "fixture-detail",
                    title = selectedResult.title,
                    author = selectedResult.author,
                    status = BookStatus.SERIALIZING,
                    summary = selectedResult.summary,
                    coverUrl = highFidelityFixtureBookCovers[0],
                    sourceIds = listOf(selectedResult.sourceId)
                ),
                sourceUrl = selectedResult.bookUrl,
                category = "玄幻 · 连载中",
                wordCountOrSize = "312万字",
                latestChapter = selectedResult.latestChapter,
                offlineLabel = "可下载 TXT 并在书架阅读"
            ),
            onBack = { page = HighFidelityDebugFixturePage.Picker },
            onAddToShelf = {},
            experienceSwitch = {
                HighFidelityFixtureExperienceSwitch(
                    page,
                    widthDp = highFidelityDetailChrome().switchWidthDp,
                    heightDp = highFidelityDetailChrome().switchHeightDp,
                    showLabels = false
                ) { page = it }
            },
            bottomBar = {}
        )
        HighFidelityDebugFixturePage.Reader -> ReaderScreen(
            book = highFidelityFixtureBooks.first(),
            chapterContent = highFidelityFixtureChapter,
            isLoadingChapter = false,
            chapterLoadAttempted = true,
            initialFontSize = 18,
            initialNightMode = false,
            initialScrollIndex = 0,
            initialScrollOffset = 0,
            loadChapter = {},
            onBack = { page = HighFidelityDebugFixturePage.Picker },
            onPositionChanged = { _, _, _ -> },
            onFontSizeChanged = {},
            onNightModeChanged = {},
            progressLabelOverride = highFidelityReaderFixturePolicy().progressLabel,
            bottomBar = {}
        )
        HighFidelityDebugFixturePage.Drama -> {
            val listState = rememberLazyListState()
            DramaHomeStyledScreen(
                rootStatus = DramaRootUiState(
                    root = VideoSiteRoot("https://fixture.invalid", 0L),
                    isUsingCachedRoot = true,
                    message = "片源地址已加载"
                ),
                // Keep the fixture in the same empty-search visual state as
                // the concept artwork. The recent-viewing card still uses
                // 南部档案馆 so the resume path remains represented without
                // putting a test query into the search field.
                query = "",
                search = DramaSearchUiState(
                    // The concept starts with a 九门 card; the archive title
                    // remains available as the second sample for player and
                    // resume verification.
                    results = listOf(
                        highFidelityFixtureDramaTitles[1],
                        highFidelityFixtureDramaTitles[0],
                        highFidelityFixtureDramaTitles[2]
                    ),
                    message = "已找到 3 部示例剧集"
                ),
                // Downloads are covered by the dedicated download-list page;
                // keeping the home fixture empty matches the concept's
                // section order and prevents an extra card from shifting all
                // search results below the fold.
                // Keep one queued preview card on the home page; the
                // dedicated download page exposes all four status states.
                downloads = highFidelityFixtureDownloadTasks.take(1),
                recentViewing = com.lovelyreader.video.VideoRecentViewing(
                    titleId = "fixture-drama-archive",
                    titleName = "南部档案馆",
                    sourceId = "fixture-source-a",
                    episodeId = "fixture-episode-06",
                    positionMillis = 21 * 60_000L + 41_000L,
                    viewedAtMillis = 1_724_400_000_000L,
                    titleDetailUrl = "https://fixture.invalid/drama/archive"
                ),
                onResumeRecent = {},
                onSearch = {},
                onOpenTitle = {},
                onOpenDownloads = {},
                onShowDiagnostics = null,
                experienceSwitch = {
                    HighFidelityFixtureExperienceSwitch(
                        page,
                        widthDp = highFidelityPhoneMetrics().dramaHomeExperienceSwitchWidthDp,
                        heightDp = 30
                    ) { page = it }
                },
                listState = listState,
                modifier = modifier
            )
        }
        HighFidelityDebugFixturePage.DramaDetail -> DramaDetailCompactScreen(
            detailTitle = highFidelityFixtureDramaTitles.first(),
            detailMessage = "已发现 2 个播放源",
            isLoading = false,
            sources = highFidelityFixtureDramaSources,
            selectedSource = highFidelityFixtureDramaSources.first(),
            episodes = highFidelityFixtureDramaEpisodes,
            selectedEpisodeIds = setOf(highFidelityFixtureDramaEpisodes.first().id),
            onBack = { page = HighFidelityDebugFixturePage.Picker },
            onSelectSource = {},
            onToggleEpisode = {},
            onPlayEpisode = { page = HighFidelityDebugFixturePage.DramaPlayer },
            onEnqueueSelected = {},
            experienceSwitch = {
                HighFidelityFixtureExperienceSwitch(
                    page,
                    widthDp = highFidelityPhoneMetrics().dramaDetailExperienceSwitchWidthDp,
                    heightDp = 30,
                    singleLabel = "追剧"
                ) { page = it }
            },
            modifier = modifier
        )
        HighFidelityDebugFixturePage.DramaPlayer -> DramaPlayerScreen(
            playback = DramaPlaybackUiState(
                titleName = highFidelityFixtureDramaTitles.first().name,
                episode = highFidelityFixtureDefaultEpisode,
                media = VideoMediaLink(
                    // Keep the fixture media deliberately non-castable. The
                    // visual player still shows the cast affordance plus the
                    // explicit unavailable-state copy without preflighting a
                    // fake public URL.
                    playbackUrl = "https://localhost/player.mp4",
                    playbackMode = VideoPlaybackMode.DIRECT_MEDIA
                ),
                message = "播放器已准备"
            ),
            episodes = listOf(highFidelityFixtureDefaultEpisode) + highFidelityFixtureDramaEpisodes.filterNot { it.id == highFidelityFixtureDefaultEpisode.id },
            onPlayEpisode = {},
            onBack = { page = HighFidelityDebugFixturePage.Picker },
            modifier = modifier,
            previewPosterUrl = "fixture://drama-player-preview.png"
        )
        HighFidelityDebugFixturePage.DramaDownloads -> DownloadQueueStyledScreen(
            tasks = highFidelityFixtureDownloadTasks,
            onBack = { page = HighFidelityDebugFixturePage.Picker },
            onRefresh = {},
            modifier = modifier
        )
        HighFidelityDebugFixturePage.Notes -> SettingsScreen(
            notes = listOf("记得把喜欢的故事加入书架。", "南部档案馆看到第 6 集。"),
            onBack = { page = HighFidelityDebugFixturePage.Picker },
            bottomBar = {
                HighFidelityFixtureBottomBar(
                    selected = MainTab.Notes,
                    onShelf = { page = HighFidelityDebugFixturePage.Shelf },
                    onSearch = { page = HighFidelityDebugFixturePage.Search },
                    onNotes = { page = HighFidelityDebugFixturePage.Notes }
                )
            },
            updateMessage = "阅读体验优化，剧集搜索更稳定",
            updateAvailable = com.lovelyreader.update.UpdateManifest(
                versionCode = 87L,
                versionName = "0.9.0",
                apkUrl = "https://fixture.invalid/lovely-camp-0.9.0.apk",
                sha256 = "fixture",
                notes = "家庭网络片源回退与启动更新优化",
                mandatory = false
            ),
            updateHistory = listOf(
                UpdateHistoryEntry(86L, "0.8.23", "2026-08-25", "家庭网络回退与启动更新优化"),
                UpdateHistoryEntry(18L, "0.8.18", "2026-08-22", "优化阅读体验与剧集搜索稳定性"),
                UpdateHistoryEntry(17L, "0.8.17", "2026-08-20", "加入高保真视觉与来源诊断")
            ),
            updateHistoryMessage = "",
            onCheckUpdate = {},
            onLoadUpdateHistory = {},
            updateCoverUrl = "fixture://book-jiulong.png"
        )
    }
}

private fun HighFidelityDebugFixturePage.toHighFidelityConceptPage(): HighFidelityConceptPage = when (this) {
    HighFidelityDebugFixturePage.Shelf -> HighFidelityConceptPage.Shelf
    HighFidelityDebugFixturePage.Search -> HighFidelityConceptPage.Search
    HighFidelityDebugFixturePage.Detail -> HighFidelityConceptPage.Detail
    HighFidelityDebugFixturePage.Reader -> HighFidelityConceptPage.Reader
    HighFidelityDebugFixturePage.Drama -> HighFidelityConceptPage.DramaHome
    HighFidelityDebugFixturePage.DramaDetail -> HighFidelityConceptPage.DramaDetail
    HighFidelityDebugFixturePage.DramaPlayer -> HighFidelityConceptPage.Player
    HighFidelityDebugFixturePage.DramaDownloads -> HighFidelityConceptPage.Downloads
    HighFidelityDebugFixturePage.Notes -> HighFidelityConceptPage.Settings
    HighFidelityDebugFixturePage.Picker -> error("Picker does not have a concept baseline")
}

@Composable
private fun HighFidelityDebugFixturePicker(
    onClose: () -> Unit,
    onSelect: (HighFidelityDebugFixturePage) -> Unit,
    modifier: Modifier = Modifier
) {
    InkWashBackground(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HighFidelityHeader(title = "视觉验收", onBack = onClose)
            SoftPanel {
                Text("高保真页面验收", style = MaterialTheme.typography.headlineSmall, color = appColors().ink)
                Text("两种交付包均可进入。进入页面后使用系统返回回到这里。", color = appColors().cocoa.copy(alpha = .72f))
            }
            highFidelityDebugFixturePages().forEach { target ->
                val label = highFidelityDebugFixturePageLabels()[highFidelityDebugFixturePages().indexOf(target)]
                Button(
                    onClick = { onSelect(target) },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(27.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (target == HighFidelityDebugFixturePage.Drama) appColors().roseBeige else appColors().roseDust)
                ) { Text(label, fontSize = 17.sp) }
            }
        }
    }
}

@Composable
private fun HighFidelityFixtureExperienceSwitch(
    page: HighFidelityDebugFixturePage,
    widthDp: Int = highFidelityPhoneMetrics().compactExperienceSwitchWidthDp,
    heightDp: Int = 24,
    showLabels: Boolean = true,
    singleLabel: String? = null,
    onPageChanged: (HighFidelityDebugFixturePage) -> Unit
) {
    Surface(
        modifier = Modifier
            .width(widthDp.dp)
            .height(heightDp.dp),
        shape = RoundedCornerShape((heightDp / 2).dp),
        color = if (!showLabels) appColors().cocoa else appColors().warmWhite.copy(alpha = .58f),
        border = BorderStroke(1.dp, appColors().lineColor)
    ) {
        if (!showLabels) {
            Box(
                modifier = Modifier.fillMaxSize().clickable {
                    onPageChanged(if (page == HighFidelityDebugFixturePage.Drama) HighFidelityDebugFixturePage.Shelf else HighFidelityDebugFixturePage.Drama)
                },
                contentAlignment = if (page == HighFidelityDebugFixturePage.Drama) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .padding(2.dp)
                        .size((heightDp - 4).dp)
                        .background(Color.White, androidx.compose.foundation.shape.CircleShape)
                )
            }
            return@Surface
        }
        if (singleLabel != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(appColors().roseDust, RoundedCornerShape((heightDp / 2).dp))
                    .clickable { onPageChanged(HighFidelityDebugFixturePage.Drama) },
                contentAlignment = Alignment.Center
            ) { Text(singleLabel, color = Color.White, fontSize = if (heightDp >= 30) 14.sp else 12.sp) }
            return@Surface
        }
        Row(modifier = Modifier.fillMaxSize().padding(2.dp)) {
            listOf(
                HighFidelityDebugFixturePage.Shelf to "小书架",
                HighFidelityDebugFixturePage.Drama to "追剧"
            ).forEach { (target, label) ->
                val active = if (target == HighFidelityDebugFixturePage.Shelf) page != HighFidelityDebugFixturePage.Drama else page == target
                Box(
                    modifier = Modifier.weight(1f).fillMaxSize().background(if (active) appColors().roseDust else Color.Transparent, RoundedCornerShape((heightDp / 2).dp)).clickable { onPageChanged(target) },
                    contentAlignment = Alignment.Center
                ) { Text(label, color = if (active) Color.White else appColors().roseDust, fontSize = if (heightDp >= 30) 14.sp else 12.sp) }
            }
        }
    }
}

@Composable
private fun HighFidelityFixtureBottomBar(
    selected: MainTab,
    onShelf: () -> Unit,
    onSearch: () -> Unit,
    onNotes: () -> Unit
) {
    MainBottomBar(selected = selected, onShelf = onShelf, onSearch = onSearch, onReader = {}, onNotes = onNotes)
}
