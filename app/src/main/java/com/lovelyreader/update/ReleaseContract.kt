package com.lovelyreader.update

data class ReleaseVersionContract(
    val versionName: String,
    val versionCode: Long
)

private val releaseTagPattern = Regex("^v(\\d+\\.\\d+\\.\\d+)\\+(\\d+)$")

fun parseReleaseVersionTag(tag: String): ReleaseVersionContract? {
    val match = releaseTagPattern.matchEntire(tag.trim()) ?: return null
    val code = match.groupValues[2].toLongOrNull() ?: return null
    return ReleaseVersionContract(match.groupValues[1], code).takeIf { it.versionCode > 0L }
}

fun normalizeSha256(raw: String): String? {
    val normalized = raw.trim().lowercase().removePrefix("sha256:")
    return normalized.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
}

fun releaseAssetName(versionName: String): String = "lovely-camp-v$versionName.apk"
