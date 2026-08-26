package com.lovelyreader.update

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

internal fun updateTrustedDnsFallbackReason(
    host: String,
    addresses: List<InetAddress>
): UpdateTrustedDnsFallbackReason? {
    if (!isTrustedUpdateHost(host) || addresses.isEmpty()) {
        return UpdateTrustedDnsFallbackReason.INVALID_SYSTEM_ANSWER
    }
    return if (addresses.any(::isUnsafeUpdateAddress)) {
        UpdateTrustedDnsFallbackReason.INVALID_SYSTEM_ANSWER
    } else {
        null
    }
}

internal fun isUnsafeUpdateAddress(address: InetAddress): Boolean {
    if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
        address.isSiteLocalAddress || address.isMulticastAddress
    ) return true
    val bytes = address.address
    if (bytes.size != 4) return false
    val first = bytes[0].toInt() and 0xff
    val second = bytes[1].toInt() and 0xff
    val third = bytes[2].toInt() and 0xff
    return first == 0 ||
        (first == 100 && second in 64..127) ||
        (first == 192 && second == 0 && third == 0) ||
        (first == 192 && second == 0 && third == 2) ||
        (first == 192 && second == 88 && third == 99) ||
        (first == 198 && second == 18) ||
        (first == 198 && second == 19) ||
        (first == 198 && second == 51) ||
        (first == 203 && second == 0 && third == 113) ||
        first >= 240
}

/**
 * Per-app resolver/cache used only after the regular GitHub connection fails. It does not alter
 * Android's global DNS settings and it never disables TLS hostname or certificate checks.
 */
internal class AndroidTrustedUpdateDnsFallback(context: Context) {
    private val appContext = context.applicationContext
    private val addressCache = UpdateTrustedDnsAddressCache()

    suspend fun shouldPreferFallback(url: URL): Boolean = withContext(Dispatchers.IO) {
        val host = url.host.trimEnd('.').lowercase()
        if (!isTrustedUpdateHost(host)) return@withContext false
        val manager = appContext.getSystemService(ConnectivityManager::class.java)
        val network = manager?.activeNetwork
        val addresses = runCatching {
            network?.getAllByName(host)?.toList() ?: InetAddress.getAllByName(host).toList()
        }.getOrDefault(emptyList())
        updateTrustedDnsFallbackReason(host, addresses) != null
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
            addresses = addresses,
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
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "LovelyCamp/1.0 trusted-dns")
                }
                responseReceived = true
                if (connection.responseCode !in 200..299) return@forEach
                val answer = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    .optJSONArray("Answer") ?: return@forEach
                val resolved = buildList {
                    for (index in 0 until answer.length()) {
                        val item = answer.optJSONObject(index) ?: continue
                        if (item.optInt("type") != 1) continue
                        val address = parseUpdateTrustedIpv4Address(item.optString("data")) ?: continue
                        if (!isUnsafeUpdateAddress(address)) add(address)
                    }
                }.distinctBy { it.hostAddress }
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
        var lastError: IOException? = null
        addresses.forEach { address ->
            var plain: Socket? = null
            try {
                val socket = Socket()
                plain = socket
                socket.connect(InetSocketAddress(address, port), connectTimeoutMillis)
                return delegate.createSocket(socket, hostname, port, true)
            } catch (error: IOException) {
                runCatching { plain?.close() }
                lastError = error
            }
        }
        throw lastError ?: UnknownHostException(hostname)
    }
}
