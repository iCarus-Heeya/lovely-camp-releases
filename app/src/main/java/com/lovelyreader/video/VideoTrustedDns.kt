package com.lovelyreader.video

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory

enum class TrustedDnsFallbackReason { INVALID_SYSTEM_ANSWER }

/** Official AliDNS recursive resolver IPv4 bootstrap addresses; only used to reach dns.alidns.com over TLS. */
internal fun trustedDnsBootstrapAddresses(): List<String> = listOf("223.5.5.5", "223.6.6.6")

internal fun trustedDnsFallbackReason(host: String, addresses: List<InetAddress>): TrustedDnsFallbackReason? {
    if (host.isBlank() || addresses.isEmpty()) return TrustedDnsFallbackReason.INVALID_SYSTEM_ANSWER
    return if (addresses.any(::isUnsafePublicAddress)) TrustedDnsFallbackReason.INVALID_SYSTEM_ANSWER else null
}

private fun isUnsafePublicAddress(address: InetAddress): Boolean =
    address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
        address.isSiteLocalAddress || address.isMulticastAddress

/** Per-app fallback only. It cannot and does not change HarmonyOS/Android global DNS settings. */
interface TrustedVideoDnsFallback {
    suspend fun preflight(url: String): TrustedDnsFallbackReason?
    suspend fun get(url: String): String
    suspend fun postForm(url: String, fields: Map<String, String>): String
}

internal object NoopTrustedVideoDnsFallback : TrustedVideoDnsFallback {
    override suspend fun preflight(url: String): TrustedDnsFallbackReason? = null
    override suspend fun get(url: String): String = throw UnknownHostException("可信 DNS 回退未配置")
    override suspend fun postForm(url: String, fields: Map<String, String>): String = throw UnknownHostException("可信 DNS 回退未配置")
}

internal class AndroidTrustedVideoDnsFallback(context: Context) : TrustedVideoDnsFallback {
    private val appContext = context.applicationContext

    override suspend fun preflight(url: String): TrustedDnsFallbackReason? = withContext(Dispatchers.IO) {
        val host = URI(url).host ?: return@withContext TrustedDnsFallbackReason.INVALID_SYSTEM_ANSWER
        val network = appContext.getSystemService(ConnectivityManager::class.java).activeNetwork
        val addresses = runCatching { network?.getAllByName(host)?.toList() ?: InetAddress.getAllByName(host).toList() }
            .getOrDefault(emptyList())
        trustedDnsFallbackReason(host, addresses)
    }

    override suspend fun get(url: String): String = withTrustedClient(url) { it.get(url) }

    override suspend fun postForm(url: String, fields: Map<String, String>): String =
        withTrustedClient(url) { it.postForm(url, fields) }

    private suspend fun <T> withTrustedClient(url: String, action: suspend (com.lovelyreader.source.HttpTextClient) -> T): T {
        val host = URI(url).host ?: throw UnknownHostException("无效片源主机")
        val addresses = resolveWithAliDns(host)
        if (addresses.isEmpty()) throw UnknownHostException("可信 DNS 未返回可用地址")
        val client = com.lovelyreader.source.HttpTextClient(
            minimumIntervalMillis = 500,
            connectTimeoutMillis = 20_000,
            readTimeoutMillis = 75_000,
            connectionFactory = TrustedAddressHttpsConnectionFactory(host, addresses)
        )
        return action(client)
    }

    private suspend fun resolveWithAliDns(host: String): List<InetAddress> = withContext(Dispatchers.IO) {
        val encodedHost = URLEncoder.encode(host, "UTF-8")
        val resolverUrl = URL("https://dns.alidns.com/resolve?name=$encodedHost&type=A")
        val bootstrapAddresses = trustedDnsBootstrapAddresses().map(InetAddress::getByName)
        val connection = (TrustedAddressHttpsConnectionFactory("dns.alidns.com", bootstrapAddresses)(resolverUrl) as HttpsURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "LovelyCamp/1.0 trusted-dns")
        }
        try {
            if (connection.responseCode !in 200..299) return@withContext emptyList()
            val answer = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).optJSONArray("Answer")
                ?: return@withContext emptyList()
            buildList {
                for (index in 0 until answer.length()) {
                    val item = answer.optJSONObject(index) ?: continue
                    if (item.optInt("type") != 1) continue
                    val address = runCatching { InetAddress.getByName(item.optString("data")) }.getOrNull() ?: continue
                    if (!isUnsafePublicAddress(address)) add(address)
                }
            }.distinctBy { it.hostAddress }
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * Connects to a DNS address selected by the trusted resolver while preserving the URL hostname
 * for TLS SNI and the platform's default certificate/hostname verification.
 */
private class TrustedAddressHttpsConnectionFactory(
    private val hostname: String,
    private val addresses: List<InetAddress>
) : (URL) -> java.net.HttpURLConnection {
    override fun invoke(url: URL): java.net.HttpURLConnection {
        val connection = url.openConnection() as java.net.HttpURLConnection
        if (connection is HttpsURLConnection && url.host.equals(hostname, ignoreCase = true)) {
            connection.sslSocketFactory = AddressBoundSniSocketFactory(
                delegate = HttpsURLConnection.getDefaultSSLSocketFactory(),
                hostname = hostname,
                addresses = addresses
            )
        }
        return connection
    }
}

private class AddressBoundSniSocketFactory(
    private val delegate: SSLSocketFactory,
    private val hostname: String,
    private val addresses: List<InetAddress>
) : SSLSocketFactory() {
    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites
    override fun createSocket(): Socket = delegate.createSocket()
    override fun createSocket(host: String, port: Int): Socket = connectTrusted(port)
    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket = connectTrusted(port)
    override fun createSocket(host: InetAddress, port: Int): Socket = connectTrusted(port)
    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket = connectTrusted(port)
    override fun createSocket(socket: Socket, host: String, port: Int, autoClose: Boolean): Socket {
        runCatching { socket.close() }
        return connectTrusted(port)
    }

    private fun connectTrusted(port: Int): Socket {
        var lastError: IOException? = null
        addresses.forEach { address ->
            try {
                val plain = Socket()
                plain.connect(InetSocketAddress(address, port), 20_000)
                return delegate.createSocket(plain, hostname, port, true)
            } catch (error: IOException) {
                lastError = error
            }
        }
        throw lastError ?: UnknownHostException(hostname)
    }
}
