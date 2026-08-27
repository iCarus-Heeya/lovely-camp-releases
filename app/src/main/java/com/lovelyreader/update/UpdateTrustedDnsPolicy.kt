package com.lovelyreader.update

import android.content.Context
import android.net.ConnectivityManager
import com.lovelyreader.network.isUnsafeNetworkAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory

/** The update client never sends fallback traffic to arbitrary third-party hosts. */
internal val TRUSTED_UPDATE_HOSTS = setOf(
    "api.github.com",
    "github.com",
    "release-assets.githubusercontent.com"
)

internal val TRUSTED_UPDATE_ASSET_HOSTS = setOf(
    "github.com",
    "release-assets.githubusercontent.com"
)

internal enum class UpdateTrustedDnsFallbackReason {
    INVALID_SYSTEM_ANSWER
}

private const val UPDATE_SOCKET_CONNECT_TIMEOUT_MILLIS = 12_000
private const val TRUSTED_DNS_CONNECT_TIMEOUT_MILLIS = 8_000
private const val MAX_UPDATE_ADDRESS_ATTEMPTS = 4

internal fun isTrustedUpdateHost(host: String): Boolean =
    host.trim().trimEnd('.').lowercase() in TRUSTED_UPDATE_HOSTS

internal fun isAllowedUpdateAssetHost(host: String): Boolean =
    host.trim().trimEnd('.').lowercase() in TRUSTED_UPDATE_ASSET_HOSTS

/** Retry only responses that look like a network interception page, not GitHub JSON errors. */
internal fun shouldRetryTrustedUpdateResponse(responseCode: Int, body: String): Boolean {
    val trimmed = body.trimStart()
    val looksLikeJson = trimmed.startsWith("{") || trimmed.startsWith("[")
    val looksLikeHtml = trimmed.startsWith("<") ||
        trimmed.lowercase().contains("<html") ||
        trimmed.lowercase().contains("access denied")
    val looksLikePlainBlockPage = responseCode == 403 || responseCode == 451
    return responseCode in 200..499 && (looksLikeHtml || (looksLikePlainBlockPage && !looksLikeJson))
}

/** Official AliDNS recursive resolver IPv4 bootstrap addresses. */
internal fun updateTrustedDnsBootstrapAddresses(): List<String> = listOf("223.5.5.5", "223.6.6.6")

/**
 * Rewrites only the transport address. The caller restores the original Host header and TLS
 * identity, so the URL's HTTPS identity remains the GitHub hostname rather than the IP.
 */
internal fun updateTrustedAddressUrl(url: URL, address: InetAddress): URL {
    val host = address.hostAddress ?: throw UnknownHostException("可信 DNS 地址为空")
    val formattedHost = if (host.contains(':') && !host.startsWith('[')) "[$host]" else host
    return URL(url.protocol, formattedHost, url.port, url.file)
}

/** Parses only a literal IPv4 answer; resolver data must never trigger another system lookup. */
internal fun parseUpdateTrustedIpv4Address(raw: String): InetAddress? {
    val octets = raw.trim().split('.')
    if (octets.size != 4) return null
    val bytes = octets.map { it.toIntOrNull()?.takeIf { value -> value in 0..255 } ?: return null }
    return InetAddress.getByAddress(bytes.map(Int::toByte).toByteArray())
}

/** Parses an AliDNS JSON response without ever passing a hostname back to system DNS. */
internal fun parseUpdateTrustedDnsAnswer(body: String): List<InetAddress> {
    val answer = JSONObject(body).optJSONArray("Answer") ?: return emptyList()
    return buildList {
        for (index in 0 until answer.length()) {
            val item = answer.optJSONObject(index) ?: continue
            if (item.optInt("type") != 1) continue
            val address = parseUpdateTrustedIpv4Address(item.optString("data")) ?: continue
            if (!isUnsafeUpdateAddress(address)) add(address)
        }
    }.distinctBy { it.hostAddress }
}

internal fun updateTrustedDnsFallbackReason(
    host: String,
    addresses: List<InetAddress>
): UpdateTrustedDnsFallbackReason? {
    return selectUpdateTrustedDnsAddresses(host, addresses).fallbackReason
}

internal fun isUnsafeUpdateAddress(address: InetAddress): Boolean = isUnsafeNetworkAddress(address)

internal data class UpdateTrustedDnsAddressSelection(
    val usableAddresses: List<InetAddress>,
    val rejectedAddressCount: Int,
    val fallbackReason: UpdateTrustedDnsFallbackReason?
) {
    val shouldUseSystemAddresses: Boolean
        get() = usableAddresses.isNotEmpty()
}

internal fun selectUpdateTrustedDnsAddresses(
    host: String,
    addresses: List<InetAddress>
): UpdateTrustedDnsAddressSelection {
    if (!isTrustedUpdateHost(host)) return UpdateTrustedDnsAddressSelection(emptyList(), 0, null)
    if (addresses.isEmpty()) {
        return UpdateTrustedDnsAddressSelection(emptyList(), 0, UpdateTrustedDnsFallbackReason.INVALID_SYSTEM_ANSWER)
    }
    val distinct = addresses.distinctBy { it.hostAddress }
    val usable = distinct.filterNot(::isUnsafeUpdateAddress).take(MAX_UPDATE_ADDRESS_ATTEMPTS)
    return UpdateTrustedDnsAddressSelection(
        usableAddresses = usable,
        rejectedAddressCount = distinct.size - usable.size,
        fallbackReason = if (usable.isEmpty()) UpdateTrustedDnsFallbackReason.INVALID_SYSTEM_ANSWER else null
    )
}

/**
 * Per-app resolver/cache used only after the regular GitHub connection fails. It does not alter
 * Android's global DNS settings and it never disables TLS hostname or certificate checks.
 */
internal class AndroidTrustedUpdateDnsFallback(context: Context) {
    private val appContext = context.applicationContext
    private val addressCache = UpdateTrustedDnsAddressCache()

    suspend fun preflightSelection(url: URL): UpdateTrustedDnsAddressSelection = withContext(Dispatchers.IO) {
        val host = url.host.trimEnd('.').lowercase()
        if (!isTrustedUpdateHost(host)) return@withContext UpdateTrustedDnsAddressSelection(emptyList(), 0, null)
        val manager = appContext.getSystemService(ConnectivityManager::class.java)
        val network = manager?.activeNetwork
        val addresses = runCatching {
            network?.getAllByName(host)?.toList() ?: InetAddress.getAllByName(host).toList()
        }.getOrDefault(emptyList())
        selectUpdateTrustedDnsAddresses(host, addresses)
    }

    suspend fun shouldPreferFallback(url: URL): Boolean = preflightSelection(url).fallbackReason != null

    suspend fun openUsingAddresses(url: URL, addresses: List<InetAddress>): java.net.HttpURLConnection? =
        withContext(Dispatchers.IO) {
            require(url.protocol.equals("https", ignoreCase = true)) { "可信更新连接必须使用 HTTPS" }
            val host = url.host.trimEnd('.').lowercase()
            if (!isTrustedUpdateHost(host)) return@withContext null
            val usable = selectUpdateTrustedDnsAddresses(host, addresses).usableAddresses
            if (usable.isEmpty()) return@withContext null
            TrustedUpdateAddressHttpsConnectionFactory(
                hostname = host,
                addresses = usable,
                connectTimeoutMillis = UPDATE_SOCKET_CONNECT_TIMEOUT_MILLIS
            )(url)
        }

    suspend fun open(url: URL): java.net.HttpURLConnection? = withContext(Dispatchers.IO) {
        require(url.protocol.equals("https", ignoreCase = true)) { "可信更新连接必须使用 HTTPS" }
        val host = url.host.trimEnd('.').lowercase()
        if (!isTrustedUpdateHost(host)) return@withContext null
        val addresses = try {
            resolveWithAliDns(host)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            emptyList()
        }
        if (addresses.isEmpty()) return@withContext null
        TrustedUpdateAddressHttpsConnectionFactory(
            hostname = host,
            addresses = addresses.take(MAX_UPDATE_ADDRESS_ATTEMPTS),
            connectTimeoutMillis = UPDATE_SOCKET_CONNECT_TIMEOUT_MILLIS
        )(url)
    }

    private suspend fun resolveWithAliDns(host: String): List<InetAddress> = withContext(Dispatchers.IO) {
        addressCache.get(host)?.let { return@withContext it }
        val encodedHost = URLEncoder.encode(host, "UTF-8")
        val resolverUrl = URL("https://dns.alidns.com/resolve?name=$encodedHost&type=A")
        var responseReceived = false
        var lastError: IOException? = null
        updateTrustedDnsBootstrapAddresses().mapNotNull(::parseUpdateTrustedIpv4Address).forEach { bootstrapAddress ->
            var connection: HttpsURLConnection? = null
            try {
                connection = (TrustedUpdateAddressHttpsConnectionFactory(
                    hostname = "dns.alidns.com",
                    addresses = listOf(bootstrapAddress),
                    connectTimeoutMillis = TRUSTED_DNS_CONNECT_TIMEOUT_MILLIS
                )(resolverUrl) as HttpsURLConnection).apply {
                    configureUpdateTrustedDnsBootstrapConnection(this)
                }
                responseReceived = true
                if (connection.responseCode !in 200..299) return@forEach
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val resolved = try {
                    parseUpdateTrustedDnsAnswer(body)
                } catch (error: JSONException) {
                    // A captive portal or gateway can return HTTP 200 with non-JSON content.
                    // Treat that bootstrap as failed and continue with the second fixed address.
                    lastError = IOException(
                        "可信 DNS 引导地址 ${bootstrapAddress.hostAddress} 返回畸形响应",
                        error
                    )
                    return@forEach
                }
                if (resolved.isNotEmpty()) {
                    addressCache.put(host, resolved)
                    return@withContext resolved
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                lastError = IOException(
                    "可信 DNS 引导地址 ${bootstrapAddress.hostAddress} 查询失败",
                    error
                )
            } finally {
                connection?.disconnect()
            }
        }
        if (responseReceived) emptyList() else throw (lastError ?: IOException("可信 DNS 服务不可达"))
    }
}

internal class UpdateTrustedDnsAddressCache(
    private val ttlMillis: Long = 30_000L,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private data class Entry(val addresses: List<InetAddress>, val expiresAtMillis: Long)
    private val entries = mutableMapOf<String, Entry>()

    @Synchronized
    fun get(host: String): List<InetAddress>? {
        val key = host.lowercase()
        val entry = entries[key] ?: return null
        if (nowMillis() >= entry.expiresAtMillis) {
            entries.remove(key)
            return null
        }
        return entry.addresses
    }

    @Synchronized
    fun put(host: String, addresses: List<InetAddress>) {
        if (addresses.isEmpty()) return
        entries[host.lowercase()] = Entry(
            addresses = addresses.distinctBy { it.hostAddress },
            expiresAtMillis = nowMillis() + ttlMillis
        )
    }
}

/** Connects to an address selected by trusted DNS while retaining HTTPS SNI and verification. */
internal class TrustedUpdateAddressHttpsConnectionFactory(
    private val hostname: String,
    private val addresses: List<InetAddress>,
    private val connectTimeoutMillis: Int
) : (URL) -> java.net.HttpURLConnection {
    override fun invoke(url: URL): java.net.HttpURLConnection {
        require(url.protocol.equals("https", ignoreCase = true)) { "可信地址连接必须使用 HTTPS" }
        require(url.userInfo == null) { "可信地址连接不允许携带用户信息" }
        require(url.port == -1 || url.port == 443) { "可信地址连接端口不受支持" }
        val transportUrl = addresses.firstOrNull()?.let { updateTrustedAddressUrl(url, it) } ?: url
        val connection = transportUrl.openConnection() as java.net.HttpURLConnection
        connection.setRequestProperty("Host", hostname)
        if (connection is HttpsURLConnection) {
            connection.sslSocketFactory = UpdateAddressBoundSniSocketFactory(
                delegate = HttpsURLConnection.getDefaultSSLSocketFactory(),
                hostname = hostname,
                addresses = addresses,
                connectTimeoutMillis = connectTimeoutMillis
            )
            val platformVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
            connection.hostnameVerifier = HostnameVerifier { _, session ->
                platformVerifier.verify(hostname, session)
            }
        }
        return connection
    }
}

private class UpdateAddressBoundSniSocketFactory(
    private val delegate: SSLSocketFactory,
    private val hostname: String,
    private val addresses: List<InetAddress>,
    private val connectTimeoutMillis: Int
) : SSLSocketFactory() {
    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites
    override fun createSocket(): Socket = delegate.createSocket()
    override fun createSocket(host: String, port: Int): Socket = connectTrusted(port)
    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        connectTrusted(port)
    override fun createSocket(host: InetAddress, port: Int): Socket = connectTrusted(port)
    override fun createSocket(
        address: InetAddress,
        port: Int,
        localAddress: InetAddress,
        localPort: Int
    ): Socket = connectTrusted(port)

    override fun createSocket(socket: Socket, host: String, port: Int, autoClose: Boolean): Socket {
        runCatching { socket.close() }
        return connectTrusted(port)
    }

    private fun connectTrusted(port: Int): Socket {
        var lastError: Throwable? = null
        addresses.take(MAX_UPDATE_ADDRESS_ATTEMPTS).forEach { address ->
            var plain: Socket? = null
            try {
                val socket = Socket()
                plain = socket
                socket.connect(InetSocketAddress(address, port), connectTimeoutMillis)
                return delegate.createSocket(socket, hostname, port, true)
            } catch (error: Throwable) {
                runCatching { plain?.close() }
                if (error is CancellationException) throw error
                if (error is IOException) {
                    lastError = error
                } else {
                    throw error
                }
            }
        }
        throw lastError ?: UnknownHostException(hostname)
    }
}
