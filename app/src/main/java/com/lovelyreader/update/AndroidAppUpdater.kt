package com.lovelyreader.update

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.core.content.FileProvider
import com.lovelyreader.download.DownloadConnection
import com.lovelyreader.download.DownloadTransferProgress
import com.lovelyreader.download.ResumableFileDownloader
import com.lovelyreader.network.NetworkRetryPolicy
import com.lovelyreader.network.retryNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object GitHubReleaseConfiguration {
    const val LATEST_RELEASE_URL = "https://api.github.com/repos/iCarus-Heeya/lovely-camp-releases/releases/latest"
    const val RELEASE_HISTORY_URL = "https://api.github.com/repos/iCarus-Heeya/lovely-camp-releases/releases"
}

sealed interface UpdateCheckResult {
    /** Kept for binary-safe UI compatibility; GitHub releases are always configured. */
    data object FeedNotConfigured : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Available(val manifest: UpdateManifest) : UpdateCheckResult
    data class Failed(val message: String) : UpdateCheckResult
}

class AndroidAppUpdater(private val context: Context) {
    private val automaticCheckPreferences = context.getSharedPreferences(
        AUTOMATIC_CHECK_PREFERENCES,
        Context.MODE_PRIVATE
    )

    /**
     * A best-effort background check for a normal app launch. It runs on any validated network,
     * including mobile data, but only once per day. It returns null when the network or daily
     * policy says to wait. Failures are intentionally left for the manual Settings action, so they
     * cannot interrupt the reading experience.
     */
    suspend fun checkAutomatically(nowMillis: Long = System.currentTimeMillis()): UpdateCheckResult? {
        if (!shouldRunAutomaticUpdateCheck(
                nowMillis = nowMillis,
                lastAutomaticAttemptMillis = lastAutomaticAttemptMillis(),
                isValidatedNetwork = hasValidatedNetwork()
            )
        ) return null

        automaticCheckPreferences.edit().putLong(AUTOMATIC_CHECK_ATTEMPT_KEY, nowMillis).apply()
        return check()
    }

    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val raw = getTextWithRetry(GitHubReleaseConfiguration.LATEST_RELEASE_URL)
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            when (val availability = parseGitHubLatestRelease(raw, packageInfo.longVersionCode)) {
                UpdateAvailability.UpToDate -> UpdateCheckResult.UpToDate
                is UpdateAvailability.Invalid -> UpdateCheckResult.Failed(availability.reason)
                is UpdateAvailability.Available -> UpdateCheckResult.Available(parseGitHubReleaseManifest(raw))
            }
        }.getOrElse { UpdateCheckResult.Failed("暂时无法检查更新，请稍后重试") }
    }

    suspend fun history(limit: Int = 20): Result<List<UpdateHistoryEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val address = "${GitHubReleaseConfiguration.RELEASE_HISTORY_URL}?per_page=${limit.coerceIn(1, 50)}"
            parseGitHubReleaseHistory(getTextWithRetry(address))
        }
    }

    suspend fun downloadAndPrepare(
        manifest: UpdateManifest,
        onProgress: suspend (UpdateDownloadProgress) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            ResumableFileDownloader.cleanupStalePartials(
                directory = context.cacheDir,
                olderThanMillis = 7L * 24L * 60L * 60L * 1_000L
            )
            val target = File(context.cacheDir, "update-${manifest.versionCode}.apk")
            val partial = File(target.parentFile, "${target.name}.part")
            if (target.isFile && sha256(target).equals(manifest.sha256, ignoreCase = true)) {
                onProgress(UpdateDownloadProgress(target.length(), target.length(), 0L, UpdateDownloadPhase.Ready))
                return@runCatching target
            }
            ResumableFileDownloader(
                open = { _, startByte -> openResumableConnection(manifest.apkUrl, startByte) }
            ).download(
                url = manifest.apkUrl,
                partial = partial,
                target = target
            ) { progress ->
                onProgress(progress.toUpdateProgress())
            }
            val downloadedBytes = target.length()
            val totalBytes = downloadedBytes.takeIf { it > 0L }
            onProgress(
                UpdateDownloadProgress(
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                    speedBytesPerSecond = 0L
                )
            )
            onProgress(
                UpdateDownloadProgress(
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                    speedBytesPerSecond = 0L,
                    phase = UpdateDownloadPhase.Verifying
                )
            )
            val actual = sha256(target)
            require(actual.equals(manifest.sha256, ignoreCase = true)) { "安装包校验失败" }
            onProgress(
                UpdateDownloadProgress(
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                    speedBytesPerSecond = 0L,
                    phase = UpdateDownloadPhase.Ready
                )
            )
            target
        }.onFailure { error ->
            if (error is CancellationException) throw error
        }
    }

    fun canRequestInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private suspend fun getTextWithRetry(address: String): String = retryNetwork(
        policy = NetworkRetryPolicy(maxAttempts = 3, initialDelayMillis = 600L, maxDelayMillis = 2_000L),
        request = { getText(address) },
        wait = { delay(it) }
    )

    private fun getText(address: String): String {
        val connection = openRawHttps(address)
        return try {
            when (val responseCode = connection.responseCode) {
                in 200..299 -> connection.inputStream.bufferedReader().use { it.readText() }
                in 500..599 -> throw IOException("更新服务响应异常：$responseCode")
                else -> error("更新服务响应异常：$responseCode")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun lastAutomaticAttemptMillis(): Long? {
        val value = automaticCheckPreferences.getLong(AUTOMATIC_CHECK_ATTEMPT_KEY, NO_AUTOMATIC_ATTEMPT)
        return value.takeIf { it != NO_AUTOMATIC_ATTEMPT }
    }

    private fun hasValidatedNetwork(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun openResumableConnection(address: String, startByte: Long): DownloadConnection {
        var current = address
        repeat(5) {
            val connection = openRawHttps(current).apply {
                if (startByte > 0L) setRequestProperty("Range", "bytes=$startByte-")
            }
            when (connection.responseCode) {
                in 200..299 -> return object : DownloadConnection {
                    override val responseCode: Int = connection.responseCode
                    override val contentLength: Long? = connection.contentLengthLong.takeIf { it >= 0L }
                    override val input = connection.inputStream
                    override fun close() = connection.disconnect()
                }
                in 300..399 -> {
                    val location = connection.getHeaderField("Location") ?: error("下载跳转地址缺失")
                    connection.disconnect()
                    current = URL(URL(current), location).toString()
                }
                else -> error("安装包下载失败")
            }
        }
        error("安装包跳转次数过多")
    }

    private fun openRawHttps(address: String): HttpURLConnection {
        val url = URL(address)
        require(url.protocol.equals("https", ignoreCase = true) && !url.host.isNullOrBlank()) { "更新地址必须是 HTTPS" }
        return (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 60_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "LovelyCamp-Android-Updater")
        }
    }

    private fun sha256(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(bytes)
            if (read < 0) break
            digest.update(bytes, 0, read)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun DownloadTransferProgress.toUpdateProgress(): UpdateDownloadProgress =
        UpdateDownloadProgress(
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            speedBytesPerSecond = speedBytesPerSecond
        )

    private companion object {
        const val AUTOMATIC_CHECK_PREFERENCES = "lovely_camp_update"
        const val AUTOMATIC_CHECK_ATTEMPT_KEY = "last_automatic_attempt_millis"
        const val NO_AUTOMATIC_ATTEMPT = -1L
    }
}
