package com.lovelyreader.update

import org.json.JSONObject
import java.net.URI

data class UpdateManifest(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val notes: String,
    val mandatory: Boolean
)

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
    validateManifest(manifest)
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
    val match = Regex("^v(\\d+\\.\\d+\\.\\d+)\\+(\\d+)$").matchEntire(json.getString("tag_name").trim())
        ?: throw IllegalArgumentException("发布标签必须是 v版本号+版本代码")
    val versionName = match.groupValues[1]
    val versionCode = match.groupValues[2].toLong()
    val expectedAsset = "lovely-camp-v$versionName.apk"
    val asset = json.getJSONArray("assets").let { assets ->
        (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name") == expectedAsset }
    } ?: throw IllegalArgumentException("未找到正式安装包")
    val digest = asset.optString("digest").trim().lowercase()
    require(digest.startsWith("sha256:")) { "发布安装包缺少 SHA-256 校验值" }
    return UpdateManifest(
        versionCode = versionCode,
        versionName = versionName,
        apkUrl = asset.getString("browser_download_url").trim(),
        sha256 = digest.removePrefix("sha256:"),
        notes = json.optString("body").trim(),
        mandatory = false
    ).also(::validateManifest)
}

private fun validateManifest(manifest: UpdateManifest) {
    require(manifest.versionCode > 0) { "版本号无效" }
    require(manifest.versionName.isNotBlank()) { "版本名称为空" }
    val uri = URI(manifest.apkUrl)
    require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()) { "安装包地址必须是 HTTPS" }
    require(manifest.sha256.matches(Regex("[0-9a-f]{64}"))) { "安装包校验值无效" }
}
