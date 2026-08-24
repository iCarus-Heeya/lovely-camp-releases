package com.lovelyreader.ui

/**
 * The page-level contract behind the 9:16 high-fidelity book flow.
 *
 * Keeping this small model outside Compose makes the important visual rules
 * executable in unit tests and prevents a future screen rewrite from
 * reintroducing duplicate entry points or global chrome in the reader.
 */
enum class BookPage {
    Shelf,
    Search,
    Detail,
    Reader
}

enum class HighFidelityChromePlacement {
    BelowHeader,
    HeaderTrailing,
    Hidden
}

/** A cover is artwork, so fixed cards must preserve the complete source image. */
enum class BookCoverScalePolicy {
    Fit
}

fun highFidelityBookCoverScalePolicy(): BookCoverScalePolicy = BookCoverScalePolicy.Fit

fun highFidelityShelfBookTitleMaxLines(): Int = 2

fun highFidelityShelfBookTitleSizeSp(): Int = 14

fun highFidelityChromePlacement(page: BookPage): HighFidelityChromePlacement = when (page) {
    BookPage.Shelf -> HighFidelityChromePlacement.BelowHeader
    BookPage.Search -> HighFidelityChromePlacement.HeaderTrailing
    // The v3 detail concept puts the experience switch in its own first row,
    // above the back/title row. Keeping this separate avoids compressing the
    // hero header and matches the 9:16 detail composition.
    BookPage.Detail -> HighFidelityChromePlacement.BelowHeader
    BookPage.Reader -> HighFidelityChromePlacement.Hidden
}

data class HighFidelityBookLayout(
    val findBookEntryCount: Int = 0,
    val showsContinueReadingSection: Boolean = false,
    val discoveryTabs: List<String> = emptyList(),
    val scrollableContent: Boolean = false,
    val primaryActions: List<String> = emptyList(),
    val readerContentTopInsetDp: Int = 0,
    val readerContentBottomInsetDp: Int = 0,
    val readerTextStartPaddingDp: Int = 18,
    val readerTextEndPaddingDp: Int = 28,
    val showsSharedAppChrome: Boolean = true,
    val showsBottomNavigation: Boolean = true,
    val showsPaperDecoration: Boolean = true
)

fun highFidelityBookLayout(page: BookPage): HighFidelityBookLayout = when (page) {
    BookPage.Shelf -> HighFidelityBookLayout(
        findBookEntryCount = 1,
        showsContinueReadingSection = true,
        showsBottomNavigation = true
    )
    BookPage.Search -> HighFidelityBookLayout(
        discoveryTabs = listOf("搜索", "首页精选", "随便看看"),
        showsBottomNavigation = false
    )
    BookPage.Detail -> HighFidelityBookLayout(
        scrollableContent = true,
        primaryActions = listOf("加入书架"),
        showsBottomNavigation = false
    )
    BookPage.Reader -> HighFidelityBookLayout(
        readerContentTopInsetDp = 60,
        readerContentBottomInsetDp = 24,
        readerTextStartPaddingDp = 36,
        readerTextEndPaddingDp = 36,
        showsSharedAppChrome = false,
        showsBottomNavigation = false
    )
}

data class HighFidelityDetailChrome(
    val aboveHeader: Boolean,
    val switchWidthDp: Int,
    val switchHeightDp: Int,
    val horizontalPaddingDp: Int
)

fun highFidelityDetailChrome(): HighFidelityDetailChrome = HighFidelityDetailChrome(
    aboveHeader = true,
    switchWidthDp = 40,
    switchHeightDp = 24,
    horizontalPaddingDp = 24
)

fun highFidelitySettingsSectionOrder(): List<String> =
    listOf("应用更新", "阅读外观", "来源管理")

data class HighFidelitySettingsLayout(
    val showsRoseMark: Boolean,
    val showsUpdateCard: Boolean,
    val historyIsSeparateSection: Boolean,
    val updateCoverWidthDp: Int,
    val updateCoverHeightDp: Int,
    val updateActionHeightDp: Int,
    val updateDescriptionTextSizeSp: Int,
    val updateDescriptionLineHeightSp: Int,
    val historyTopSpacingDp: Int
)

fun highFidelitySettingsLayout(): HighFidelitySettingsLayout = HighFidelitySettingsLayout(
    showsRoseMark = true,
    showsUpdateCard = true,
    historyIsSeparateSection = true,
    updateCoverWidthDp = 96,
    updateCoverHeightDp = 100,
    updateActionHeightDp = 44,
    updateDescriptionTextSizeSp = 12,
    updateDescriptionLineHeightSp = 20,
    historyTopSpacingDp = 180
)

data class HighFidelityPlayerLayout(
    val darkSurface: Boolean,
    val showsProgressAndFullscreenControls: Boolean,
    val showsSourceAndEpisodeSurface: Boolean,
    val usesCustomControlSurface: Boolean = false,
    val usesZoomedVideoSurface: Boolean = false,
    val usesPlainTextBackControl: Boolean = false,
    val keepsCastActionInline: Boolean = false,
    val showsFullscreenLabel: Boolean = false,
    val showsCastIcon: Boolean = false,
    val showsInfoIcon: Boolean = false,
    val posterPreviewShowsCastUnavailableNotice: Boolean = false,
    val previewMediaHeightDp: Int = 490
)

fun highFidelityPlayerLayout(): HighFidelityPlayerLayout = HighFidelityPlayerLayout(
    darkSurface = true,
    showsProgressAndFullscreenControls = true,
    showsSourceAndEpisodeSurface = true,
    usesCustomControlSurface = true,
    usesZoomedVideoSurface = true,
    usesPlainTextBackControl = true,
    keepsCastActionInline = true,
    showsFullscreenLabel = true,
    showsCastIcon = true,
    showsInfoIcon = true,
    posterPreviewShowsCastUnavailableNotice = true,
    previewMediaHeightDp = 490
)

/** Debug-only visual evidence may show the same poster and control geometry
 * while keeping the absence of a real media URL explicit. */
fun highFidelityPlayerFixtureUsesPosterPreview(): Boolean = true

enum class HighFidelityDebugControlPlacement {
    HeaderAction,
    MainContent
}

data class HighFidelityDramaHomeLayout(
    val sectionOrder: List<String>,
    val debugControlPlacement: HighFidelityDebugControlPlacement,
    val usesSeparateContinueHeading: Boolean,
    val showsSubtitle: Boolean
)

fun highFidelityDramaHomeLayout(): HighFidelityDramaHomeLayout = HighFidelityDramaHomeLayout(
    sectionOrder = listOf("标题", "搜索", "继续观看", "下载列表", "找到的剧集"),
    debugControlPlacement = HighFidelityDebugControlPlacement.HeaderAction,
    usesSeparateContinueHeading = true,
    showsSubtitle = false
)

fun highFidelityUsesUnifiedExperienceSwitch(): Boolean = true

/** Root bookshelf/settings concepts include the shared four-item navigation bar. */
fun highFidelityConceptUsesBottomNavigation(): Boolean = true

/**
 * Low-contrast decoration tokens for the shared paper shell.  Keeping these
 * values in the layout policy makes the subtle background a tested part of the
 * 9:16 visual contract instead of an unreviewed per-screen drawing detail.
 */
data class HighFidelityPaperDecoration(
    val branchAlpha: Float,
    val blossomAlpha: Float,
    val mountainAlpha: Float
)

enum class HighFidelityPaperSurface {
    BookShelf,
    Search,
    Detail,
    Reader,
    Drama,
    Settings,
    Downloads
}

enum class HighFidelityPaperDecorationStyle {
    Plum,
    Willow
}

/**
 * Approved 9:16 concept sheets used by the debug visual gate.  Keeping the
 * mapping in the shared policy makes the acceptance surface deterministic and
 * prevents a page from silently falling back to a different baseline image.
 */
enum class HighFidelityConceptPage {
    Shelf,
    Search,
    Detail,
    Reader,
    DramaHome,
    DramaDetail,
    Player,
    Downloads,
    Settings
}

fun highFidelityConceptAsset(page: HighFidelityConceptPage): String = when (page) {
    HighFidelityConceptPage.Shelf -> "concept/bookshelf-home-9x16-1080x1920.png"
    HighFidelityConceptPage.Search -> "concept/book-search-9x16-1080x1920.png"
    HighFidelityConceptPage.Detail -> "concept/book-detail-9x16-1080x1920.png"
    HighFidelityConceptPage.Reader -> "concept/reader-9x16-1080x1920.png"
    HighFidelityConceptPage.DramaHome -> "concept/drama-home-9x16-1080x1920.png"
    HighFidelityConceptPage.DramaDetail -> "concept/drama-detail-9x16-1080x1920.png"
    HighFidelityConceptPage.Player -> "concept/video-player-9x16-1080x1920.png"
    HighFidelityConceptPage.Downloads -> "concept/drama-downloads-9x16-1080x1920.png"
    HighFidelityConceptPage.Settings -> "concept/settings-update-9x16-1080x1920.png"
}

/** Source-pixel crop used to keep the concept header inside the app viewport. */
fun highFidelityConceptCropTopPx(page: HighFidelityConceptPage): Int = when (page) {
    // The reader's compact chrome starts above the normal page content; keep
    // its title visible while the real Android status bar owns the top 48 px.
    HighFidelityConceptPage.Reader,
    HighFidelityConceptPage.Player -> 48
    else -> 72
}

fun highFidelityPaperDecorationStyle(surface: HighFidelityPaperSurface): HighFidelityPaperDecorationStyle = when (surface) {
    HighFidelityPaperSurface.Settings,
    HighFidelityPaperSurface.Downloads -> HighFidelityPaperDecorationStyle.Willow
    else -> HighFidelityPaperDecorationStyle.Plum
}

fun highFidelityPaperDecoration(): HighFidelityPaperDecoration = HighFidelityPaperDecoration(
    branchAlpha = 0.10f,
    blossomAlpha = 0.14f,
    mountainAlpha = 0.22f
)

/**
 * Measurements shared by the 9:16 concept pages.  Values are expressed in
 * dp/sp so the 1080x1920 artwork and the 720x1280 MuMu viewport resolve to
 * the same phone-width composition at different device densities.
 */
data class HighFidelityPhoneMetrics(
    val pageHorizontalPaddingDp: Int,
    val sectionGapDp: Int,
    val cardCornerRadiusDp: Int,
    val bottomNavigationHeightDp: Int,
    val searchResultCoverWidthDp: Int,
    val searchResultCoverHeightDp: Int,
    val searchResultCardHeightDp: Int,
    val detailCoverWidthDp: Int,
    val detailCoverHeightDp: Int,
    val dramaDetailPosterHeightDp: Int,
    val compactExperienceSwitchWidthDp: Int,
    val shelfExperienceSwitchWidthDp: Int,
    val shelfExperienceSwitchStartPaddingDp: Int,
    val dramaHomeExperienceSwitchWidthDp: Int,
    val dramaDetailExperienceSwitchWidthDp: Int,
    val inlineExperienceSwitchWidthDp: Int,
    val displayTitleSizeSp: Int,
    val bodyTextSizeSp: Int,
    val bodyLineHeightMultiplier: Float,
    val usesSharedPaperShell: Boolean
)

fun highFidelityPhoneMetrics(): HighFidelityPhoneMetrics = HighFidelityPhoneMetrics(
    pageHorizontalPaddingDp = 16,
    sectionGapDp = 18,
    cardCornerRadiusDp = 18,
    bottomNavigationHeightDp = 68,
    searchResultCoverWidthDp = 90,
    searchResultCoverHeightDp = 112,
    searchResultCardHeightDp = 132,
    // The 1080x1920 detail concept uses a roughly 444x660px hero cover;
    // at the 720x1280 MuMu scale this resolves to about 148x220dp.
    detailCoverWidthDp = 148,
    detailCoverHeightDp = 220,
    dramaDetailPosterHeightDp = 236,
    compactExperienceSwitchWidthDp = 132,
    shelfExperienceSwitchWidthDp = 228,
    shelfExperienceSwitchStartPaddingDp = 56,
    dramaHomeExperienceSwitchWidthDp = 210,
    dramaDetailExperienceSwitchWidthDp = 65,
    inlineExperienceSwitchWidthDp = 132,
    displayTitleSizeSp = 24,
    bodyTextSizeSp = 14,
    bodyLineHeightMultiplier = 1.42f,
    usesSharedPaperShell = true
)

/**
 * Text hierarchy used by the shelf's concept sort row. The primary sort is
 * kept on the left and the two alternate sort actions stay on the right.
 */
data class HighFidelityShelfLayout(
    val primarySortLabel: String,
    val secondarySortLabels: List<String>,
    val progressLabel: String,
    val readyDownloadLabel: String
)

fun highFidelityShelfLayout(): HighFidelityShelfLayout = HighFidelityShelfLayout(
    primarySortLabel = "默认",
    secondarySortLabels = listOf("进度", "书名"),
    progressLabel = "阅读进度",
    readyDownloadLabel = "已下载"
)

/** Number of empty add-book cells needed to complete the concept's 3-column grid. */
fun highFidelityShelfPlaceholderSlots(bookCount: Int, columns: Int = 3): Int {
    if (columns <= 0 || bookCount <= 0) return if (bookCount == 0) 0 else 0
    return (columns - (bookCount % columns)) % columns
}

data class HighFidelityReaderFixturePolicy(
    val progressLabel: String,
    val keepInitialContentVisible: Boolean
)

/** The concept captures the reader at 88.7% while keeping the opening copy visible. */
fun highFidelityReaderFixturePolicy(): HighFidelityReaderFixturePolicy =
    HighFidelityReaderFixturePolicy(progressLabel = "88.7%", keepInitialContentVisible = true)

/**
 * The result card keeps its two user-facing actions short enough to match
 * the 9:16 reference card while the actual capability text remains in the
 * body of the card.
 */
fun highFidelitySearchResultActionLabels(): List<String> = listOf("能力状态", "查看详情")

/** Keep result actions in the content column so they never cover the portrait cover. */
fun highFidelitySearchResultActionsInline(): Boolean = true

/**
 * Debug fixture cover identities are intentionally stable and distinct. The
 * last three assets may fall back to the deterministic local cover renderer
 * when the binary artwork is not bundled, but their URLs must not alias the
 * first cover or each other; otherwise a screenshot falsely suggests that
 * every result is the same book.
 */
fun highFidelityFixtureBookCoverUrls(): List<String> = listOf(
    "fixture://book-jiulong.png",
    "fixture://book-night.png",
    "fixture://book-spring.png",
    "fixture://book-mirror.png"
)

/** Debug fixture artwork is cropped from the approved 9:16 concept sheets. */
fun highFidelityFixtureSearchCoverUrls(): List<String> = listOf(
    "fixture://book-search-nine.png",
    "fixture://book-search-night.png",
    "fixture://book-search-spring.png",
    "fixture://book-search-mirror.png"
)

fun highFidelityDramaDetailFixturePoster(): String = "fixture://drama-detail-cover.png"

data class HighFidelityDramaFixturePolicy(
    val defaultEpisodeNumber: Int,
    val detailEpisodeNumbers: List<Int>,
    val previewHidesUnavailableMediaMessage: Boolean,
    val keepsCastEntry: Boolean,
    val downloadStatusLabels: List<String>
)

fun highFidelityDramaFixturePolicy(): HighFidelityDramaFixturePolicy = HighFidelityDramaFixturePolicy(
    defaultEpisodeNumber = 6,
    detailEpisodeNumbers = listOf(1, 2, 3, 4, 5),
    previewHidesUnavailableMediaMessage = true,
    keepsCastEntry = true,
    downloadStatusLabels = listOf("等待下载", "正在下载", "已下载完成", "下载没有完成")
)

fun highFidelitySettingsTitle(): String = "应用更新"

fun highFidelityUpdateDescription(): String =
    "应用会在已验证网络下每天自动检查一次；下载与安装始终由你确认"

fun highFidelityUpdateActionLabel(versionName: String?): String =
    versionName?.takeIf { it.isNotBlank() }?.let { "下载并安装 $it" } ?: "检查更新"

fun highFidelityUpdateCardUsesCover(coverUrl: String?): Boolean = !coverUrl.isNullOrBlank()

internal fun highFidelityUserUpdateNotes(raw: String): String = raw.lines()
    .map(String::trim)
    .filter(String::isNotBlank)
    .filterNot { line ->
        val lower = line.lowercase()
        lower.contains("apk") || lower.contains("sha256") || lower.contains("sha-256") ||
            lower.contains("测试") || lower.contains("test") || lower.contains("构建") ||
            lower.contains("build") || lower.contains("校验")
    }
    .joinToString("\n")
    .ifBlank { "包含体验改进。" }
