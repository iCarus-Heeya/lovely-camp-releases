package com.lovelyreader.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {
    @Test
    fun `parses stable release history and skips drafts and prereleases`() {
        val history = parseGitHubReleaseHistory(
            """[
                {"tag_name":"v0.8.16+79","name":"0.8.16","body":"阅读体验优化","published_at":"2026-08-21T03:00:00Z","draft":false,"prerelease":false},
                {"tag_name":"v0.8.17+80-rc1","name":"候选","body":"不应展示","published_at":"2026-08-21T04:00:00Z","draft":false,"prerelease":true},
                {"tag_name":"v0.8.15+78","name":"0.8.15","body":"下载体验优化","published_at":"2026-08-20T03:00:00Z","draft":false,"prerelease":false},
                {"tag_name":"v0.8.14+77","name":"0.8.14","body":"分类浏览","published_at":"2026-08-19T03:00:00Z","draft":false,"prerelease":false},
                {"tag_name":"v0.8.18+81","name":"草稿","body":"不应展示","published_at":null,"draft":true,"prerelease":false}
            ]""".trimIndent(),
            currentVersionCode = 76
        )

        assertEquals(listOf("0.8.16", "0.8.15", "0.8.14"), history.map { it.versionName })
        assertEquals("阅读体验优化", history.first().notes)
        assertEquals("2026-08-21T03:00:00Z", history.first().publishedAt)
    }

    @Test
    fun `a newer GitHub release supplies a verified APK update`() {
        val result = parseGitHubLatestRelease(
            """{
                "tag_name":"v0.8.6+69",
                "body":"自动发布测试",
                "prerelease":false,
                "assets":[{
                    "name":"lovely-camp-v0.8.6.apk",
                    "browser_download_url":"https://github.com/iCarus-Heeya/lovely-camp-releases/releases/download/v0.8.6%2B69/lovely-camp-v0.8.6.apk",
                    "digest":"sha256:${"b".repeat(64)}"
                }]
            }""".trimIndent(),
            currentVersionCode = 68
        )

        assertEquals(UpdateAvailability.Available("0.8.6"), result)
    }

    @Test
    fun `GitHub release without an asset digest is rejected`() {
        val result = parseGitHubLatestRelease(
            """{"tag_name":"v0.8.6+69","assets":[{"name":"lovely-camp-v0.8.6.apk","browser_download_url":"https://github.com/example/app.apk"}]}""",
            currentVersionCode = 68
        )

        assertTrue(result is UpdateAvailability.Invalid)
    }

    @Test
    fun `a newer HTTPS manifest with a sha256 is offered`() {
        val result = parseUpdateManifest(
            """{"versionCode":68,"versionName":"0.8.5","apkUrl":"https://updates.example.com/app.apk","sha256":"${"a".repeat(64)}","notes":"修复"}""",
            currentVersionCode = 67
        )

        assertEquals(UpdateAvailability.Available("0.8.5"), result)
    }

    @Test
    fun `equal version is not offered`() {
        val result = parseUpdateManifest(
            """{"versionCode":67,"versionName":"0.8.4","apkUrl":"https://updates.example.com/app.apk","sha256":"${"a".repeat(64)}"}""",
            currentVersionCode = 67
        )

        assertEquals(UpdateAvailability.UpToDate, result)
    }

    @Test
    fun `insecure APK URL is rejected`() {
        val result = parseUpdateManifest(
            """{"versionCode":68,"versionName":"0.8.5","apkUrl":"http://updates.example.com/app.apk","sha256":"${"a".repeat(64)}"}""",
            currentVersionCode = 67
        )

        assertTrue(result is UpdateAvailability.Invalid)
    }

    @Test
    fun `invalid hash is rejected`() {
        val result = parseUpdateManifest(
            """{"versionCode":68,"versionName":"0.8.5","apkUrl":"https://updates.example.com/app.apk","sha256":"bad"}""",
            currentVersionCode = 67
        )

        assertTrue(result is UpdateAvailability.Invalid)
    }

    @Test
    fun `download progress exposes real percent transfer and speed`() {
        val progress = UpdateDownloadProgress(
            downloadedBytes = 512L * 1024L,
            totalBytes = 1024L * 1024L,
            speedBytesPerSecond = 256L * 1024L
        )

        assertEquals(50, progress.percent)
        assertEquals("512 KB / 1 MB", formatUpdateDownloadTransfer(progress))
        assertEquals("256 KB/s", formatUpdateDownloadSpeed(progress.speedBytesPerSecond))
        assertEquals(
            "下载中 · 50% · 512 KB / 1 MB · 256 KB/s",
            formatUpdateDownloadProgress(progress)
        )
    }

    @Test
    fun `download progress stays indeterminate when server omits content length`() {
        val progress = UpdateDownloadProgress(
            downloadedBytes = 1536L,
            totalBytes = null,
            speedBytesPerSecond = 0L
        )

        assertEquals(null, progress.percent)
        assertEquals("1 KB", formatUpdateDownloadTransfer(progress))
        assertEquals("计算中…", formatUpdateDownloadSpeed(progress.speedBytesPerSecond))
    }
}
