package com.lovelyreader.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {
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
}
