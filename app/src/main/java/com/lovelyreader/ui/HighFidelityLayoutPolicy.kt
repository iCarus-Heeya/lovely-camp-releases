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
    val showsSharedAppChrome: Boolean = true,
    val showsBottomNavigation: Boolean = true,
    val showsPaperDecoration: Boolean = true
)

fun highFidelityBookLayout(page: BookPage): HighFidelityBookLayout = when (page) {
    BookPage.Shelf -> HighFidelityBookLayout(
        findBookEntryCount = 1,
        showsContinueReadingSection = true
    )
    BookPage.Search -> HighFidelityBookLayout(
        discoveryTabs = listOf("搜索", "首页精选", "随便看看")
    )
    BookPage.Detail -> HighFidelityBookLayout(
        scrollableContent = true,
        primaryActions = listOf("加入书架", "打开原站")
    )
    BookPage.Reader -> HighFidelityBookLayout(
        readerContentTopInsetDp = 78,
        readerContentBottomInsetDp = 144,
        showsSharedAppChrome = false,
        showsBottomNavigation = false
    )
}

fun highFidelitySettingsSectionOrder(): List<String> =
    listOf("应用更新", "阅读外观", "来源管理")

data class HighFidelitySettingsLayout(
    val showsRoseMark: Boolean,
    val showsUpdateCard: Boolean,
    val historyIsSeparateSection: Boolean
)

fun highFidelitySettingsLayout(): HighFidelitySettingsLayout = HighFidelitySettingsLayout(
    showsRoseMark = true,
    showsUpdateCard = true,
    historyIsSeparateSection = true
)

data class HighFidelityPlayerLayout(
    val darkSurface: Boolean,
    val showsProgressAndFullscreenControls: Boolean,
    val showsSourceAndEpisodeSurface: Boolean,
    val usesCustomControlSurface: Boolean = false
)

fun highFidelityPlayerLayout(): HighFidelityPlayerLayout = HighFidelityPlayerLayout(
    darkSurface = true,
    showsProgressAndFullscreenControls = true,
    showsSourceAndEpisodeSurface = true,
    usesCustomControlSurface = true
)

fun highFidelitySettingsTitle(): String = "应用更新"

fun highFidelityUpdateDescription(): String =
    "应用会在已验证的 Wi-Fi 或以太网下每天自动检查一次；下载与系统安装始终由你确认。"

fun highFidelityUpdateActionLabel(versionName: String?): String =
    versionName?.takeIf { it.isNotBlank() }?.let { "下载并安装 $it" } ?: "检查更新"

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
