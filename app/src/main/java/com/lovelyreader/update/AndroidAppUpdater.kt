package com.lovelyreader.update

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.lovelyreader.download.DownloadConnection
import com.lovelyreader.download.DownloadContentRange
import com.lovelyreader.download.DownloadTransferProgress
import com.lovelyreader.download.ResumableFileDownloader
import com.lovelyreader.download.parseContentRange
import com.lovelyreader.network.NetworkRetryPolicy
import com.lovelyreader.network.isTransientNetworkFailure
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

private const val UPDATE_TRUSTED_DNS_BOOTSTRAP_TIMEOUT_MILLIS = 8_000

/**
 * Applies the updater's transport boundary to every connection, regardless of whether the
 * address came from the platform resolver or the trusted-DNS fallback.
 */
internal fun configureUpdateHttpConnection(connection: HttpURLConnection): HttpURLConnection =
    connection.apply {
        connectTimeout = 12_000
        readTimeout = 60_000
        instanceFollowRedirects = false
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "LovelyCamp-Android-Updater")
    }

/**
 * Applies the smaller, bounded transport policy used to reach the fixed AliDNS bootstrap IPs.
 * Bootstrap is still an HTTPS request and must never follow a redirect supplied by the network.
 */
internal fun configureUpdateTrustedDnsBootstrapConnection(connection: HttpURLConnection): HttpURLConnection =
    connection.apply {
        connectTimeout = UPDATE_TRUSTED_DNS_BOOTSTRAP_TIMEOUT_MILLIS
        readTimeout = UPDATE_TRUSTED_DNS_BOOTSTRAP_TIMEOUT_MILLIS
        instanceFollowRedirects = false
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "LovelyCamp/1.0 trusted-dns")
    }

/** A fallback response is parsed only after the same interception/status guard as the first path. */
internal fun validateTrustedUpdateResponse(responseCode: Int, body: String): String {
    if (shouldRetryTrustedUpdateResponse(responseCode, body)) {
        throw IOException("可信更新服务返回疑似网络拦截页")
    }
    if (responseCode !in 200..299) {
        throw IOException("可信更新服务响应异常：$responseCode")
    }
    return body
}

sealed interface UpdateCheckResult {
    /** Kept for binary-safe UI compatibility; GitHub releases are always configured. */
    data object FeedNotConfigured : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Available(val manifest: UpdateManifest) : UpdateCheckResult
    data class Failed(val message: String) : UpdateCheckResult
}

class AndroidAppUpdater(private val context: Context) {
    private val trustedDnsFallback = AndroidTrustedUpdateDnsFallback(context.applicationContext)
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
            when (val availability = parseGitHubLatestRelease(raw, PackageInfoCompat.getLongVersionCode(packageInfo))) {
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
            onProgress(
                UpdateDownloadProgress(
                    downloadedBytes = 0L,
                    totalBytes = null,
                    speedBytesPerSecond = 0L,
                    phase = UpdateDownloadPhase.Connecting
                )
            )
            ResumableFileDownloader.cleanupStalePartials(
                directory = context.cacheDir,
                olderThanMillis = 7L * 24L * 60L * 60L * 1_000L
            )
            validateUpdateManifest(manifest)
            validateGitHubAssetHost(manifest)
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

    private suspend fun getText(address: String): String {
        val connection = openConnectionWithFallback(address)
        return try {
            val responseCode = connection.responseCode
            val body = readResponseBody(connection, responseCode)
            if (shouldRetryTrustedUpdateResponse(responseCode, body)) {
                val fallbackConnection = trustedDnsFallback.open(URL(address))
                if (fallbackConnection != null) {
                    try {
                        connectCandidate(fallbackConnection) { }
                        val fallbackResponseCode = fallbackConnection.responseCode
                        val fallbackBody = readResponseBody(fallbackConnection, fallbackResponseCode)
                        return validateTrustedUpdateResponse(fallbackResponseCode, fallbackBody)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Throwable) {
                        // Fall through to a bounded retry with a clear network failure.
                    } finally {
                        fallbackConnection.disconnect()
                    }
                }
                throw IOException("更新服务返回疑似网络拦截页")
            }
            if (responseCode !in 200..299) throw IOException("更新服务响应异常：$responseCode")
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun readResponseBody(connection: HttpURLConnection, responseCode: Int): String {
        val stream = if (responseCode >= 400) {
            connection.errorStream ?: connection.inputStream
        } else {
            connection.inputStream
        }
        return stream.bufferedReader().use { it.readText() }
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

    private suspend fun openResumableConnection(address: String, startByte: Long): DownloadConnection {
        var current = address
        repeat(5) {
            val connection = openConnectionWithFallback(current) {
                if (startByte > 0L) it.setRequestProperty("Range", "bytes=$startByte-")
            }
            when (connection.responseCode) {
                in 200..299 -> return object : DownloadConnection {
                    override val responseCode: Int = connection.responseCode
                    override val contentLength: Long? = connection.contentLengthLong.takeIf { it >= 0L }
                    private val parsedContentRange: DownloadContentRange?
                        get() = parseContentRange(connection.getHeaderField("Content-Range"))
                    override val contentRangeStart: Long? get() = parsedContentRange?.start
                    override val contentRangeEnd: Long? get() = parsedContentRange?.end
                    override val contentRangeTotal: Long? get() = parsedContentRange?.total
                    override val input = connection.inputStream
                    override fun close() = connection.disconnect()
                }
                in 300..399 -> {
                    val location = connection.getHeaderField("Location") ?: error("下载跳转地址缺失")
                    connection.disconnect()
                    val next = requireAllowedUpdateAssetUrl(URL(URL(current), location))
                    current = next.toString()
                }
                else -> error("安装包下载失败")
            }
        }
        error("安装包跳转次数过多")
    }

    private suspend fun openConnectionWithFallback(
        address: String,
        configure: (HttpURLConnection) -> Unit = {}
    ): HttpURLConnection {
        val url = URL(address)
        require(isTrustedUpdateHost(url.host)) { "更新地址不是受信任的 GitHub 主机" }
        val selection = trustedDnsFallback.preflightSelection(url)
        var lastError: Throwable? = null

        if (selection.usableAddresses.isNotEmpty()) {
            try {
                trustedDnsFallback.openUsingAddresses(url, selection.usableAddresses)?.let {
                    return connectCandidate(it, configure)
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (!isTransientNetworkFailure(error)) throw error
                lastError = error
            }
        }

        val trusted = trustedDnsFallback.open(url)
        if (trusted != null) {
            try {
                return connectCandidate(trusted, configure)
            } catch (error: Throwable) {
                trusted.disconnect()
                if (error is CancellationException) throw error
                if (!isTransientNetworkFailure(error)) throw error
                lastError = lastError ?: error
            }
        }

        throw lastError ?: IOException("更新连接失败")
    }

    private fun connectCandidate(
        connection: HttpURLConnection,
        configure: (HttpURLConnection) -> Unit
    ): HttpURLConnection = connection.apply {
        try {
            // Apply the same safety boundary to system-address and trusted-DNS transports.
            // Keeping this here prevents a new connection path from accidentally inheriting
            // HttpURLConnection's redirect and timeout defaults.
            configureUpdateHttpConnection(this)
            configure(this)
            connect()
            responseCode
        } catch (error: Throwable) {
            disconnect()
            throw error
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
