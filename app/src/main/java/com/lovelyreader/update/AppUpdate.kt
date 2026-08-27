package com.lovelyreader.update

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URL

data class UpdateManifest(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val notes: String,
    val mandatory: Boolean
)

data class UpdateHistoryEntry(
    val versionCode: Long,
    val versionName: String,
    val publishedAt: String?,
    val notes: String
)

enum class UpdateDownloadPhase {
    Connecting,
    Downloading,
    Verifying,
    Ready
}

data class UpdateDownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val speedBytesPerSecond: Long,
    val phase: UpdateDownloadPhase = UpdateDownloadPhase.Downloading
) {
    val percent: Int?
        get() = totalBytes
            ?.takeIf { it > 0L }
            ?.let { ((downloadedBytes.coerceAtLeast(0L) * 100L) / it).toInt().coerceIn(0, 100) }

    val fraction: Float?
        get() = totalBytes
            ?.takeIf { it > 0L }
            ?.let { (downloadedBytes.toFloat() / it.toFloat()).coerceIn(0f, 1f) }
}

internal fun formatUpdateDownloadTransfer(progress: UpdateDownloadProgress): String {
    val downloaded = formatUpdateDownloadBytes(progress.downloadedBytes)
    val total = progress.totalBytes?.takeIf { it > 0L }?.let(::formatUpdateDownloadBytes)
    return if (total == null) downloaded else "$downloaded / $total"
}

internal fun formatUpdateDownloadSpeed(bytesPerSecond: Long): String {
    if (bytesPerSecond <= 0L) return "计算中…"
    return "${formatUpdateDownloadBytes(bytesPerSecond)}/s"
}

internal fun formatUpdateDownloadProgress(progress: UpdateDownloadProgress): String {
    val percent = progress.percent?.let { "$it% · " }.orEmpty()
    val transfer = formatUpdateDownloadTransfer(progress)
    return when (progress.phase) {
        UpdateDownloadPhase.Connecting -> "正在连接下载源…"
        UpdateDownloadPhase.Downloading ->
            "下载中 · $percent$transfer · ${formatUpdateDownloadSpeed(progress.speedBytesPerSecond)}"
        UpdateDownloadPhase.Verifying -> "正在校验 · $transfer"
        UpdateDownloadPhase.Ready -> "已下载 · $transfer"
    }
}

internal fun isUpdateDownloadActive(progress: UpdateDownloadProgress?): Boolean =
    progress?.phase == UpdateDownloadPhase.Connecting ||
        progress?.phase == UpdateDownloadPhase.Downloading

internal fun formatUpdateFailureMessage(error: Throwable): String {
    val isNetworkFailure = generateSequence(error) { it.cause }
        .any { it is java.io.IOException }
    return if (isNetworkFailure) {
        "更新源暂时不可达，尚未收到安装包数据，请稍后重试"
    } else {
        error.message?.takeIf(String::isNotBlank) ?: "更新包下载失败，请稍后重试"
    }
}

private fun formatUpdateDownloadBytes(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    if (safeBytes < 1024L) return "$safeBytes B"
    if (safeBytes < 1024L * 1024L) return "${safeBytes / 1024L} KB"
    return "${safeBytes / (1024L * 1024L)} MB"
}

sealed interface UpdateAvailability {
    data object UpToDate : UpdateAvailability
    data class Available(val versionName: String) : UpdateAvailability
    data class Invalid(val reason: String) : UpdateAvailability
}

fun parseUpdateManifest(raw: String, currentVersionCode: Long): UpdateAvailability = runCatching {
    val json = JSONObject(raw)
    val manifest = UpdateManifest(
        versionCode = json.getLong("versionCode"),
        versionName = json.getString("versionName").trim(),
        apkUrl = json.getString("apkUrl").trim(),
        sha256 = json.getString("sha256").trim().lowercase(),
        notes = json.optString("notes").trim(),
        mandatory = json.optBoolean("mandatory", false)
    )
    validateUpdateManifest(manifest)
    if (manifest.versionCode <= currentVersionCode) UpdateAvailability.UpToDate
    else UpdateAvailability.Available(manifest.versionName)
}.getOrElse { UpdateAvailability.Invalid(it.message ?: "更新清单格式错误") }

/**
 * Converts GitHub's public latest-release response into the same safe update
 * decision used by the installer. Tags contain Android's monotonic versionCode:
 * v0.8.6+69.
 */
fun parseGitHubLatestRelease(raw: String, currentVersionCode: Long): UpdateAvailability = runCatching {
    val manifest = parseGitHubReleaseManifest(raw)
    if (manifest.versionCode <= currentVersionCode) UpdateAvailability.UpToDate
    else UpdateAvailability.Available(manifest.versionName)
}.getOrElse { UpdateAvailability.Invalid(it.message ?: "GitHub 发布信息格式错误") }

fun parseGitHubReleaseManifest(raw: String): UpdateManifest {
    val json = JSONObject(raw)
    require(!json.optBoolean("prerelease", false) && !json.optBoolean("draft", false)) { "预发布版本不能自动安装" }
    val version = parseReleaseVersionTag(json.getString("tag_name"))
        ?: throw IllegalArgumentException("发布标签必须是 v版本号+版本代码")
    val versionName = version.versionName
    val versionCode = version.versionCode
    val expectedAsset = releaseAssetName(versionName)
    val asset = json.getJSONArray("assets").let { assets ->
        (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name") == expectedAsset }
    } ?: throw IllegalArgumentException("未找到正式安装包")
    val digest = normalizeSha256(asset.optString("digest"))
        ?: throw IllegalArgumentException("发布安装包缺少 SHA-256 校验值")
    return UpdateManifest(
        versionCode = versionCode,
        versionName = versionName,
        apkUrl = asset.getString("browser_download_url").trim(),
        sha256 = digest,
        notes = json.optString("body").trim(),
        mandatory = false
    ).also {
        validateUpdateManifest(it)
        validateGitHubAssetHost(it)
    }
}

/** Parses stable GitHub releases into user-facing history without exposing delivery metadata. */
fun parseGitHubReleaseHistory(raw: String, currentVersionCode: Long? = null): List<UpdateHistoryEntry> {
    val releases = JSONArray(raw)
    return (0 until releases.length()).mapNotNull { index ->
        val release = releases.optJSONObject(index) ?: return@mapNotNull null
        if (release.optBoolean("draft", false) || release.optBoolean("prerelease", false)) {
            return@mapNotNull null
        }
        val match = Regex("^v(\\d+\\.\\d+\\.\\d+)\\+(\\d+)$")
            .matchEntire(release.optString("tag_name").trim())
            ?: return@mapNotNull null
        val versionCode = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
        if (currentVersionCode != null && versionCode <= currentVersionCode) return@mapNotNull null
        UpdateHistoryEntry(
            versionCode = versionCode,
            versionName = match.groupValues[1],
            publishedAt = release.optString("published_at").trim().takeIf(String::isNotBlank),
            notes = sanitizeUpdateNotes(release.optString("body"))
        )
    }.sortedByDescending(UpdateHistoryEntry::versionCode)
}

private fun sanitizeUpdateNotes(raw: String): String = raw.lines()
    .map(String::trim)
    .filter(String::isNotBlank)
    .filterNot { line ->
        val lower = line.lowercase()
        lower.contains("sha256") || lower.contains("apk") || lower.contains("构建") ||
            lower.contains("测试") || lower.contains("test") || lower.contains("build") ||
            lower.contains("校验")
    }
    .joinToString("\n")
    .ifBlank { "包含体验改进。" }

internal fun validateUpdateManifest(manifest: UpdateManifest) {
    require(manifest.versionCode > 0) { "版本号无效" }
    require(manifest.versionName.isNotBlank()) { "版本名称为空" }
    val uri = URI(manifest.apkUrl)
    require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()) { "安装包地址必须是 HTTPS" }
    require(uri.userInfo == null) { "安装包地址不允许携带用户信息" }
    require(uri.port == -1 || uri.port == 443) { "安装包地址端口不受支持" }
    require(manifest.sha256.matches(Regex("[0-9a-f]{64}"))) { "安装包校验值无效" }
}

internal fun validateGitHubAssetHost(manifest: UpdateManifest) {
    val uri = URI(manifest.apkUrl)
    val host = uri.host.orEmpty()
    require(isAllowedUpdateAssetHost(host)) { "安装包地址不是受信任的 GitHub 资产地址" }
    require(uri.scheme.equals("https", ignoreCase = true)) { "GitHub 安装包地址必须是 HTTPS" }
    require(uri.port == -1 || uri.port == 443) { "GitHub 安装包地址端口不受支持" }
}

internal fun requireAllowedUpdateAssetUrl(url: URL): URL {
    require(url.protocol.equals("https", ignoreCase = true)) { "GitHub 安装包地址必须是 HTTPS" }
    require(url.userInfo == null) { "GitHub 安装包地址不允许携带用户信息" }
    require(url.port == -1 || url.port == 443) { "GitHub 安装包地址端口不受支持" }
    require(isAllowedUpdateAssetHost(url.host)) { "下载跳转到未受信任的主机" }
    return url
}
