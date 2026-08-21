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

data class HighFidelityBookLayout(
    val findBookEntryCount: Int = 0,
    val discoveryTabs: List<String> = emptyList(),
    val scrollableContent: Boolean = false,
    val primaryActions: List<String> = emptyList(),
    val showsSharedAppChrome: Boolean = true,
    val showsBottomNavigation: Boolean = true,
    val showsPaperDecoration: Boolean = true
)

fun highFidelityBookLayout(page: BookPage): HighFidelityBookLayout = when (page) {
    BookPage.Shelf -> HighFidelityBookLayout(
        findBookEntryCount = 1
    )
    BookPage.Search -> HighFidelityBookLayout(
        discoveryTabs = listOf("搜索", "首页精选", "随便看看")
    )
    BookPage.Detail -> HighFidelityBookLayout(
        scrollableContent = true,
        primaryActions = listOf("加入书架", "打开原站")
    )
    BookPage.Reader -> HighFidelityBookLayout(
        showsSharedAppChrome = false,
        showsBottomNavigation = false
    )
}

fun highFidelitySettingsSectionOrder(): List<String> =
    listOf("应用更新", "阅读外观", "来源管理")

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
