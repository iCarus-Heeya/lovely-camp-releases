package com.lovelyreader.video

import android.app.DownloadManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.lovelyreader.network.isUnsafeNetworkAddress
import com.lovelyreader.source.HttpTextClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.Socket

internal class VideoHttpResponseException(message: String) : IOException(message)

/**
 * Mirrors the browser/update client's block-page guard without treating ordinary catalogue HTML
 * as an error. A non-2xx response is never handed to the HTML adapter as a successful page.
 */
internal fun shouldRetryVideoHttpResponse(responseCode: Int, body: String): Boolean {
    val trimmed = body.trimStart()
    val lower = trimmed.lowercase()
    val looksLikeJson = trimmed.startsWith("{") || trimmed.startsWith("[")
    val looksLikeHtmlBlock = trimmed.startsWith("<") && (
        lower.contains("access denied") ||
            lower.contains("request blocked") ||
            lower.contains("network policy") ||
            lower.contains("forbidden") ||
            lower.contains("error 403") ||
            lower.contains("error 451")
        )
    val plainBlockStatus = responseCode == 403 || responseCode == 451
    return responseCode in 200..499 && (looksLikeHtmlBlock || (plainBlockStatus && !looksLikeJson))
}

internal fun inspectVideoHttpResponse(responseCode: Int, body: String) {
    if (responseCode !in 200..299) {
        val reason = if (shouldRetryVideoHttpResponse(responseCode, body)) "疑似网络拦截页" else "响应异常"
        throw VideoHttpResponseException("追剧请求$reason：HTTP $responseCode")
    }
    if (shouldRetryVideoHttpResponse(responseCode, body)) {
        throw VideoHttpResponseException("追剧请求疑似网络拦截页")
    }
}

class AndroidVideoPageFetcher(
    private val client: HttpTextClient = HttpTextClient(
        minimumIntervalMillis = 500,
        connectTimeoutMillis = 20_000,
        readTimeoutMillis = 75_000,
        responseInspector = ::inspectVideoHttpResponse
    ),
    private val requestDiagnostics: VideoRequestDiagnostics = VideoRequestDiagnostics(),
    private val searchFormPageCache: VideoSearchFormPageCache = VideoSearchFormPageCache(),
    private val connectivityProbe: VideoHostConnectivityProbe = NoopVideoHostConnectivityProbe,
    private val trustedDnsFallback: TrustedVideoDnsFallback = NoopTrustedVideoDnsFallback
) : VideoPageFetcher {
    override suspend fun get(url: String): String {
        requireTrustedVideoHttpsUrl(url)
        searchFormPageCache.takeFresh(url)?.let { cachedPage ->
            requestDiagnostics.record("GET", url, "复用刚验证的搜索首页")
            return cachedPage
        }
        return fetchWithTrustedDnsFallback(
            method = "GET",
            url = url,
            defaultRequest = { client.get(url) },
            systemAddressRequest = { addresses -> trustedDnsFallback.getUsingAddresses(url, addresses) },
            trustedRequest = { trustedDnsFallback.get(url) }
        ).also { page -> searchFormPageCache.store(url, page) }
    }

    override suspend fun postForm(url: String, fields: Map<String, String>): String {
        requireTrustedVideoHttpsUrl(url)
        return fetchWithTrustedDnsFallback(
            method = "POST",
            url = url,
            defaultRequest = { client.postForm(url, fields) },
            systemAddressRequest = { addresses -> trustedDnsFallback.postFormUsingAddresses(url, fields, addresses) },
            trustedRequest = { trustedDnsFallback.postForm(url, fields) }
        )
    }

    fun diagnostics(): List<VideoRequestDiagnostic> = requestDiagnostics.snapshot()

    private suspend fun fetch(
        method: String,
        url: String,
        request: suspend () -> String,
        retry: Boolean = true
    ): String = try {
        val result = if (retry) {
            retryVideoPageRequest(
                request = request,
                onRetry = { attempt, error ->
                    requestDiagnostics.record(
                        method,
                        url,
                        "网络连接较慢，正在重试（${attempt + 1}/3）：${error.javaClass.simpleName}"
                    )
                }
            )
        } else {
            request()
        }
        result.also { page -> requestDiagnostics.record(method, url, pageKind(page)) }
    } catch (error: Throwable) {
        recordVideoRequestFailureDiagnostics(
            diagnostics = requestDiagnostics,
            method = method,
            url = url,
            error = error,
            probe = connectivityProbe
        )
        throw error
    }

    private suspend fun fetchWithTrustedDnsFallback(
        method: String,
        url: String,
        defaultRequest: suspend () -> String,
        systemAddressRequest: suspend (List<InetAddress>) -> String,
        trustedRequest: suspend () -> String
    ): String {
        val preflight = try {
            trustedDnsFallback.preflightSelection(url)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            requestDiagnostics.record(method, url, "可信 DNS 预检失败：${error.javaClass.simpleName ?: "未知异常"}")
            null
        }

        suspend fun fetchTrustedFallback(trigger: String): String {
            requestDiagnostics.record(method, url, trigger)
            return try {
                fetch(method, url, trustedRequest).also {
                    requestDiagnostics.record(method, url, "可信 DNS 回退成功")
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                requestDiagnostics.record(
                    method,
                    url,
                    "可信 DNS 回退失败（${trustedVideoFailureStage(error)}）：${trustedVideoFailureType(error)}"
                )
                throw error
            }
        }

        // Keep the normal hostname URL stack when every system answer is usable. This is the
        // browser-compatible path: Android owns DNS address racing plus TLS SNI/verification.
        // Pin a transport address only when the system answer contained a rejected entry; that
        // is the narrow case where reusing the filtered answer is needed to bypass a poisoned
        // loopback/local route without changing the logical HTTPS origin.
        if (preflight?.usableAddresses?.isNotEmpty() == true && preflight.rejectedAddressCount > 0) {
            val rejected = preflight.rejectedAddressCount
            requestDiagnostics.record(
                method,
                url,
                if (rejected > 0) {
                    "系统 DNS 过滤 ${rejected} 个异常地址，优先使用有效地址"
                } else {
                    "复用系统 DNS 返回的 ${preflight.usableAddresses.size} 个有效地址"
                }
            )
            return try {
                fetch(method, url, request = { systemAddressRequest(preflight.usableAddresses) }, retry = false).also {
                    requestDiagnostics.record(method, url, "有效系统地址请求成功")
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (!isTransientVideoNetworkFailure(error)) throw error
                fetchTrustedFallback(
                    "有效系统地址请求失败：${error.javaClass.simpleName ?: "未知异常"}，正在使用可信 DNS 回退"
                )
            }
        }

        if (preflight?.fallbackReason != null) {
            val reason = when (preflight.fallbackReason) {
                TrustedDnsFallbackReason.UNREACHABLE_SYSTEM_ANSWER ->
                    "系统 DNS 地址均不可达，正在使用可信 DNS 回退"
                TrustedDnsFallbackReason.INVALID_SYSTEM_ANSWER ->
                    "系统 DNS 未返回可用公网地址，正在使用可信 DNS 回退"
            }
            return fetchTrustedFallback(reason)
        }

        if (preflight?.usableAddresses?.isNotEmpty() == true && preflight.rejectedAddressCount == 0) {
            requestDiagnostics.record(method, url, "系统 DNS 地址全部可用，保留主机名 HTTPS 路径")
        }

        return try {
            fetch(method, url, defaultRequest)
        } catch (error: Throwable) {
            if (!isTransientVideoNetworkFailure(error)) throw error
            fetchTrustedFallback("系统网络连接失败，正在使用可信 DNS 回退")
        }
    }

    private fun pageKind(page: String): String = when {
        page.isBlank() -> "收到空页面"
        Regex("(?is)<form\\b[^>]*>").containsMatchIn(page) -> "收到页面（含搜索表单）"
        page.contains("<html", ignoreCase = true) -> "收到 HTML 页面（未发现搜索表单）"
        else -> "收到非 HTML 页面"
    }
}

interface VideoHostConnectivityProbe {
    suspend fun inspect(url: String): List<String>
}

private const val MAX_PROBE_ADDRESS_ATTEMPTS = 4

/**
 * Keeps diagnostics read-only: special-use answers are reported only as a count and are never
 * used as TCP probe targets. IPv4 is preferred to avoid probing a broken IPv6 route first.
 */
internal fun selectVideoProbeAddresses(addresses: List<InetAddress>): List<InetAddress> =
    addresses
        .distinctBy { it.hostAddress }
        .filterNot(::isUnsafeNetworkAddress)
        .sortedWith(compareBy<InetAddress> { if (it is Inet4Address) 0 else 1 })
        .take(MAX_PROBE_ADDRESS_ATTEMPTS)

internal object NoopVideoHostConnectivityProbe : VideoHostConnectivityProbe {
    override suspend fun inspect(url: String): List<String> = emptyList()
}

/**
 * A bounded, read-only diagnostic snapshot. It never changes DNS, routing, TLS verification,
 * proxies, or the request itself; it only explains where a final connection failure occurred.
 */
internal class AndroidVideoHostConnectivityProbe(context: Context) : VideoHostConnectivityProbe {
    private val appContext = context.applicationContext

    override suspend fun inspect(url: String): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val host = URI(url).host ?: return@runCatching listOf("网络检测：无法识别目标主机")
            val manager = appContext.getSystemService(ConnectivityManager::class.java)
            val network = manager.activeNetwork
            val capabilities = manager.getNetworkCapabilities(network)
            val link = manager.getLinkProperties(network)
            val result = mutableListOf(networkSummary(capabilities))
            link?.let {
                val dns = it.dnsServers.joinToString("、") { address -> address.hostAddress ?: "未知" }
                result += "DNS：${dns.ifBlank { "未提供" }}"
                result += "代理：${it.httpProxy?.host ?: "未设置"}；私人 DNS：${privateDnsLabel(it)}"
            }
            val rawAddresses = (network?.getAllByName(host) ?: InetAddress.getAllByName(host)).toList()
            val distinctRawAddresses = rawAddresses.distinctBy { it.hostAddress }
            val unsafeAddressCount = distinctRawAddresses.count(::isUnsafeNetworkAddress)
            val addresses = selectVideoProbeAddresses(rawAddresses)
            if (addresses.isEmpty()) {
                return@runCatching result + if (distinctRawAddresses.isEmpty()) {
                    "域名解析：没有返回地址"
                } else {
                    "域名解析：已过滤 ${unsafeAddressCount} 个异常地址，未发现可用公网地址"
                }
            }
            if (unsafeAddressCount > 0) {
                result += "域名解析：已过滤 ${unsafeAddressCount} 个异常地址"
            }
            result += "域名解析：" + addresses.joinToString("、") { address -> "${familyOf(address)} ${address.hostAddress}" }
            addresses.forEach { address ->
                val outcome = runCatching {
                    val socket = network?.socketFactory?.createSocket() ?: Socket()
                    socket.use { it.connect(InetSocketAddress(address, 443), 3_000) }
                    "可达"
                }.getOrElse { failure -> "${failure.javaClass.simpleName ?: "失败"}" }
                result += "TCP 443：${familyOf(address)} ${address.hostAddress} — $outcome"
            }
            result
        }.getOrElse { error -> listOf("网络检测失败：${error.javaClass.simpleName ?: "未知异常"}") }
    }

    private fun networkSummary(capabilities: NetworkCapabilities?): String {
        val transport = when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "移动数据"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> "VPN"
            else -> "未知网络"
        }
        val validation = if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true) "已验证" else "未验证"
        return "网络快照：$transport（$validation）"
    }

    private fun privateDnsLabel(link: android.net.LinkProperties): String = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.P -> "系统版本未提供"
        !link.isPrivateDnsActive -> "未启用"
        !link.privateDnsServerName.isNullOrBlank() -> "已启用（${link.privateDnsServerName}）"
        else -> "已启用（机会模式）"
    }

    private fun familyOf(address: InetAddress): String = when (address) {
        is Inet4Address -> "IPv4"
        is Inet6Address -> "IPv6"
        else -> "IP"
    }
}

internal suspend fun recordVideoRequestFailureDiagnostics(
    diagnostics: VideoRequestDiagnostics,
    method: String,
    url: String,
    error: Throwable,
    probe: VideoHostConnectivityProbe
) {
    diagnostics.record(method, url, "请求失败：${error.javaClass.simpleName ?: "未知异常"}")
    if (isTransientVideoNetworkFailure(error)) {
        probe.inspect(url).forEach { detail -> diagnostics.record("网络检测", url, detail) }
    }
}

/** Debug-only UI consumes this in-memory, redacted and bounded request history. */
data class VideoRequestDiagnostic(
    val method: String,
    val safeUrl: String,
    val detail: String
) {
    val displayText: String get() = "$method $safeUrl - $detail"
}

fun videoDebugVersionLabel(versionName: String, versionCode: Long): String =
    "调试包 v$versionName ($versionCode)"

/**
 * Keeps diagnostics useful without copying playback credentials into logs or the debug clipboard.
 * Query strings, fragments, and all path segments are removed. Keeping only the origin prevents
 * short or encoded path tokens from leaking when a source uses an unconventional URL shape.
 */
internal fun redactVideoUrl(url: String): String = runCatching {
    val uri = URI(url)
    buildString {
        append(uri.scheme ?: "https")
        append("://")
        append(uri.host ?: "未知主机")
        if (uri.port != -1 && uri.port != 443) append(":${uri.port}")
    }
}.getOrDefault("无效地址")

class VideoRequestDiagnostics(private val capacity: Int = 30) {
    private val events = ArrayDeque<VideoRequestDiagnostic>()

    @Synchronized
    fun record(method: String, url: String, detail: String) {
        while (events.size >= capacity.coerceAtLeast(1)) events.removeFirst()
        events.addLast(VideoRequestDiagnostic(method, redactVideoUrl(url), detail))
    }

    @Synchronized
    fun snapshot(): List<VideoRequestDiagnostic> = events.toList()

}

class AndroidVideoRootStore(context: Context) : VideoRootStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): VideoSiteRoot? {
        val url = preferences.getString(KEY_URL, null)?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val validatedAtMillis = preferences.getLong(KEY_VALIDATED_AT, 0L)
        return VideoSiteRoot(url = url, validatedAtMillis = validatedAtMillis)
    }

    override fun save(root: VideoSiteRoot) {
        preferences.edit()
            .putString(KEY_URL, root.url)
            .putLong(KEY_VALIDATED_AT, root.validatedAtMillis)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "lovely_reader_video_root"
        const val KEY_URL = "url"
        const val KEY_VALIDATED_AT = "validated_at"
    }
}

class AndroidDownloadManagerGateway(context: Context) : DownloadGateway {
    private val appContext = context.applicationContext
    private val downloadManager = appContext.getSystemService(DownloadManager::class.java)

    override fun download(url: String): DownloadResult = runCatching {
        val fileName = "drama-${url.hashCode().toUInt().toString(16)}.mp4"
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("追剧下载")
            .setDescription("正在下载选中的剧集")
            .setMimeType("video/mp4")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_MOVIES, fileName)
        downloadManager.enqueue(request)
        DownloadResult.Accepted
    }.getOrDefault(DownloadResult.Rejected)
}
