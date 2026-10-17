package com.lovelyreader.video

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.lovelyreader.network.isUnsafeNetworkAddress
import org.json.JSONObject
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

enum class TrustedDnsFallbackReason {
    INVALID_SYSTEM_ANSWER,
    UNREACHABLE_SYSTEM_ANSWER
}

private const val MAX_ADDRESS_ATTEMPTS = 4
private const val TRUSTED_DNS_CONNECT_TIMEOUT_MILLIS = 8_000

/** The fixed AliDNS bootstrap must stay HTTPS-only, bounded, and redirect-free. */
internal fun configureVideoTrustedDnsBootstrapConnection(
    connection: java.net.HttpURLConnection
): java.net.HttpURLConnection = connection.apply {
    connectTimeout = TRUSTED_DNS_CONNECT_TIMEOUT_MILLIS
    readTimeout = TRUSTED_DNS_CONNECT_TIMEOUT_MILLIS
    instanceFollowRedirects = false
    setRequestProperty("Accept", "application/json")
    setRequestProperty("User-Agent", "LovelyCamp/1.0 trusted-dns")
}

internal class TrustedDnsResolutionException(message: String, cause: Throwable? = null) : IOException(message, cause)

internal class TrustedVideoTargetException(message: String, cause: Throwable? = null) : IOException(message, cause)

internal fun trustedVideoFailureStage(error: Throwable): String = when {
    generateSequence(error) { it.cause }.any { it is TrustedDnsResolutionException } -> "可信 DNS 查询阶段"
    generateSequence(error) { it.cause }.any { it is TrustedVideoTargetException } -> "目标 HTTPS 阶段"
    else -> "目标 HTTPS 阶段"
}

internal fun trustedVideoFailureType(error: Throwable): String =
    generateSequence(error) { it.cause }.lastOrNull()?.javaClass?.simpleName ?: "未知异常"

/** Official AliDNS recursive resolver IPv4 bootstrap addresses; only used to reach dns.alidns.com over TLS. */
internal fun trustedDnsBootstrapAddresses(): List<String> = listOf("223.5.5.5", "223.6.6.6")

/**
 * Builds the transport URL for a trusted address. The request host is restored as the Host
 * header and TLS SNI/verification below, so the system resolver is never consulted for the
 * affected host while the public URL identity remains unchanged to the server.
 */
internal fun trustedAddressUrl(url: URL, address: InetAddress): URL {
    val host = address.hostAddress ?: throw UnknownHostException("可信 DNS 地址为空")
    val formattedHost = if (host.contains(':') && !host.startsWith('[')) "[$host]" else host
    return URL(url.protocol, formattedHost, url.port, url.file)
}

/** Parses only a literal IPv4 answer; a resolver payload must never trigger another system DNS lookup. */
internal fun parseTrustedIpv4Address(raw: String): InetAddress? {
    val octets = raw.trim().split('.')
    if (octets.size != 4) return null
    val bytes = octets.map { it.toIntOrNull()?.takeIf { value -> value in 0..255 } ?: return null }
    return InetAddress.getByAddress(bytes.map(Int::toByte).toByteArray())
}

/**
 * The provider's edge accepts TLS 1.2 but can abort a TLS 1.3 negotiation with
 * `SSLProtocolException`. Browsers retry with a compatible protocol; the address-pinned fallback
 * has no browser retry layer, so constrain only this fallback socket to TLS 1.2. Certificate
 * verification and hostname/SNI handling remain unchanged.
 */
internal fun configureTrustedVideoTlsSocket(socket: java.net.Socket): java.net.Socket {
    if (socket is SSLSocket) {
        val supported = socket.supportedProtocols.toSet()
        if ("TLSv1.2" in supported) {
            socket.enabledProtocols = arrayOf("TLSv1.2")
        }
    }
    return socket
}

/**
 * The system resolver may return a mixture of unusable and public addresses. Browsers normally
 * discard loopback/local answers and continue with the usable set. IPv4 is ordered first as a
 * conservative choice for networks that advertise a broken IPv6 route. Mixed answers are
 * additionally checked for TCP reachability before they are reused; this is not a parallel Happy
 * Eyeballs race, but a bounded, deterministic fallback.
 */
data class TrustedDnsAddressSelection(
    val usableAddresses: List<InetAddress>,
    val rejectedAddressCount: Int,
    val fallbackReason: TrustedDnsFallbackReason?
) {
    val shouldPreferSystemAddresses: Boolean
        get() = usableAddresses.isNotEmpty() && rejectedAddressCount > 0
}

internal fun selectTrustedDnsAddresses(host: String, addresses: List<InetAddress>): TrustedDnsAddressSelection {
    if (host.isBlank() || addresses.isEmpty()) {
        return TrustedDnsAddressSelection(
            usableAddresses = emptyList(),
            rejectedAddressCount = addresses.size,
            fallbackReason = TrustedDnsFallbackReason.INVALID_SYSTEM_ANSWER
        )
    }
    val distinctAddresses = addresses.distinctBy { it.hostAddress }
    val usable = distinctAddresses
        .filterNot(::isUnsafePublicAddress)
        .sortedWith(compareBy<InetAddress> { if (it is Inet4Address) 0 else 1 })
        .take(MAX_ADDRESS_ATTEMPTS)
    return TrustedDnsAddressSelection(
        usableAddresses = usable,
        rejectedAddressCount = distinctAddresses.size - usable.size,
        fallbackReason = if (usable.isEmpty()) TrustedDnsFallbackReason.INVALID_SYSTEM_ANSWER else null
    )
}

/**
 * Applies the bounded TCP reachability result to a system DNS answer. A public-looking address
 * is not enough to be selected as a transport target: captive/poisoned Wi-Fi answers can still
 * time out on port 443. The probe is deliberately supplied by the caller so the policy remains
 * deterministic and unit-testable without opening sockets from JVM tests.
 */
internal fun selectReachableTrustedDnsAddresses(
    host: String,
    addresses: List<InetAddress>,
    isReachable: (InetAddress) -> Boolean
): TrustedDnsAddressSelection {
    val base = selectTrustedDnsAddresses(host, addresses)
    if (base.usableAddresses.isEmpty()) return base
    val reachable = base.usableAddresses.filter(isReachable)
    if (reachable.size == base.usableAddresses.size) return base
    val distinct = addresses.distinctBy { it.hostAddress }
    return TrustedDnsAddressSelection(
        usableAddresses = reachable,
        rejectedAddressCount = distinct.size - reachable.size,
        fallbackReason = if (reachable.isEmpty()) {
            TrustedDnsFallbackReason.UNREACHABLE_SYSTEM_ANSWER
        } else {
            null
        }
    )
}

internal fun trustedDnsFallbackReason(host: String, addresses: List<InetAddress>): TrustedDnsFallbackReason? {
    return selectTrustedDnsAddresses(host, addresses).fallbackReason
}

private fun isUnsafePublicAddress(address: InetAddress): Boolean = isUnsafeNetworkAddress(address)

internal fun requireTrustedVideoHttpsUrl(url: String): URL {
    val parsed = URI(url)
    require(parsed.scheme.equals("https", ignoreCase = true) && !parsed.host.isNullOrBlank()) {
        "追剧请求必须使用 HTTPS"
    }
    require(parsed.userInfo == null) { "追剧请求不允许携带用户信息" }
    require(parsed.port == -1 || parsed.port == 443) { "追剧请求端口不受支持" }
    return URL(url)
}

/** Per-app fallback only. It cannot and does not change HarmonyOS/Android global DNS settings. */
interface TrustedVideoDnsFallback {
    suspend fun preflight(url: String): TrustedDnsFallbackReason?

    /**
     * Returns the filtered system answer when the platform supplied one. The default keeps the
     * original interface behavior for test doubles and non-Android implementations.
     */
    suspend fun preflightSelection(url: String): TrustedDnsAddressSelection =
        TrustedDnsAddressSelection(emptyList(), 0, preflight(url))

    suspend fun get(url: String): String
    suspend fun postForm(url: String, fields: Map<String, String>): String

    /** Uses already-resolved system addresses while preserving the original host for TLS. */
    suspend fun getUsingAddresses(url: String, addresses: List<InetAddress>): String = get(url)

    /** Uses already-resolved system addresses while preserving the original host for TLS. */
    suspend fun postFormUsingAddresses(
        url: String,
        fields: Map<String, String>,
        addresses: List<InetAddress>
    ): String = postForm(url, fields)
}

internal object NoopTrustedVideoDnsFallback : TrustedVideoDnsFallback {
    override suspend fun preflight(url: String): TrustedDnsFallbackReason? = null
    override suspend fun get(url: String): String = throw UnknownHostException("可信 DNS 回退未配置")
    override suspend fun postForm(url: String, fields: Map<String, String>): String = throw UnknownHostException("可信 DNS 回退未配置")
}

/**
 * Resolves a host through fixed AliDNS bootstrap addresses first, then through the same HTTPS
 * hostname as a last-resort transport. The latter is still certificate-verified and redirect-free;
 * it only helps networks that block direct connections to the resolver's published bootstrap IPs.
 */
internal class TrustedVideoDnsResolver(
    private val openBootstrapConnection: (URL, InetAddress) -> java.net.HttpURLConnection,
    private val openHostnameConnection: (URL) -> java.net.HttpURLConnection,
    private val bootstrapAddresses: List<String> = trustedDnsBootstrapAddresses()
) {
    suspend fun resolve(host: String): List<InetAddress> = withContext(Dispatchers.IO) {
        require(host.isNotBlank()) { "可信 DNS 主机为空" }
        val encodedHost = URLEncoder.encode(host, "UTF-8")
        val resolverUrl = URL("https://dns.alidns.com/resolve?name=$encodedHost&type=A")
        var responseReceived = false
        var lastError: IOException? = null

        for (rawBootstrapAddress in bootstrapAddresses) {
            val bootstrapAddress = parseTrustedIpv4Address(rawBootstrapAddress) ?: continue
            var connection: java.net.HttpURLConnection? = null
            try {
                connection = openBootstrapConnection(resolverUrl, bootstrapAddress).apply {
                    configureVideoTrustedDnsBootstrapConnection(this)
                }
                val responseCode = connection.responseCode
                responseReceived = true
                if (responseCode !in 200..299) {
                    throw IOException("可信 DNS 引导响应 HTTP $responseCode")
                }
                val resolved = parseResolverResponse(connection)
                if (resolved.isNotEmpty()) return@withContext resolved
                throw IOException("可信 DNS 引导未返回可用 A 记录")
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                lastError = IOException(
                    "可信 DNS 引导地址 ${bootstrapAddress.hostAddress} 查询失败",
                    error
                )
            } finally {
                connection?.disconnect()
            }
        }

        // Some Wi-Fi/ISP paths block direct HTTPS connections to 223.5.5.5/223.6.6.6 while
        // allowing the resolver's normal hostname route. Keep this as a final, bounded fallback;
        // the URL remains a fixed HTTPS endpoint with platform certificate verification.
        var hostnameConnection: java.net.HttpURLConnection? = null
        try {
            hostnameConnection = openHostnameConnection(resolverUrl).apply {
                configureVideoTrustedDnsBootstrapConnection(this)
            }
            val responseCode = hostnameConnection.responseCode
            responseReceived = true
            if (responseCode !in 200..299) {
                throw IOException("可信 DNS 主机名回退响应 HTTP $responseCode")
            }
            val resolved = parseResolverResponse(hostnameConnection)
            if (resolved.isNotEmpty()) return@withContext resolved
            throw IOException("可信 DNS 主机名回退未返回可用 A 记录")
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            lastError = IOException("可信 DNS 主机名回退失败", error)
        } finally {
            hostnameConnection?.disconnect()
        }

        if (responseReceived) {
            throw TrustedDnsResolutionException("可信 DNS 未返回可用 A 记录", lastError)
        }
        throw TrustedDnsResolutionException("可信 DNS 服务不可达", lastError)
    }

    private fun parseResolverResponse(connection: java.net.HttpURLConnection): List<InetAddress> {
        val answer = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            .optJSONArray("Answer")
            ?: return emptyList()
        return buildList {
            for (index in 0 until answer.length()) {
                val item = answer.optJSONObject(index) ?: continue
                if (item.optInt("type") != 1) continue
                val address = parseTrustedIpv4Address(item.optString("data")) ?: continue
                if (!isUnsafePublicAddress(address)) add(address)
            }
        }.distinctBy { it.hostAddress }
    }
}

internal class AndroidTrustedVideoDnsFallback(context: Context) : TrustedVideoDnsFallback {
    private val appContext = context.applicationContext
    private val addressCache = TrustedDnsAddressCache()
    private val dnsResolver = TrustedVideoDnsResolver(
        openBootstrapConnection = { resolverUrl, bootstrapAddress ->
            TrustedAddressHttpsConnectionFactory(
                "dns.alidns.com",
                listOf(bootstrapAddress),
                TRUSTED_DNS_CONNECT_TIMEOUT_MILLIS,
                plainSocketFactory = ::createActiveNetworkSocket
            )(resolverUrl)
        },
        openHostnameConnection = { resolverUrl ->
            resolverUrl.openConnection() as java.net.HttpURLConnection
        }
    )

    override suspend fun preflight(url: String): TrustedDnsFallbackReason? = preflightSelection(url).fallbackReason

    override suspend fun preflightSelection(url: String): TrustedDnsAddressSelection = withContext(Dispatchers.IO) {
        val host = URI(url).host ?: return@withContext TrustedDnsAddressSelection(
            usableAddresses = emptyList(),
            rejectedAddressCount = 0,
            fallbackReason = TrustedDnsFallbackReason.INVALID_SYSTEM_ANSWER
        )
        val network = appContext.getSystemService(ConnectivityManager::class.java).activeNetwork
        val addresses = runCatching { network?.getAllByName(host)?.toList() ?: InetAddress.getAllByName(host).toList() }
            .getOrDefault(emptyList())
        val initial = selectTrustedDnsAddresses(host, addresses)
        if (initial.usableAddresses.isEmpty() || initial.rejectedAddressCount == 0) {
            return@withContext initial
        }

        // Only mixed answers need an active reachability check. Fully public answers stay on the
        // platform hostname/TLS path, while a mixed answer must not pin a dead public address.
        val reachable = selectReachableTrustedDnsAddresses(host, addresses) { address ->
            runCatching {
                val socket = network?.socketFactory?.createSocket() ?: Socket()
                socket.use {
                    it.connect(
                        InetSocketAddress(address, 443),
                        SYSTEM_PREFLIGHT_CONNECT_TIMEOUT_MILLIS
                    )
                }
                true
            }.getOrDefault(false)
        }
        reachable
    }

    override suspend fun get(url: String): String = withTrustedClient(url) { it.get(url) }

    override suspend fun postForm(url: String, fields: Map<String, String>): String =
        withTrustedClient(url) { it.postForm(url, fields) }

    override suspend fun getUsingAddresses(url: String, addresses: List<InetAddress>): String =
        withSystemAddressClient(url, addresses) { it.get(url) }

    override suspend fun postFormUsingAddresses(
        url: String,
        fields: Map<String, String>,
        addresses: List<InetAddress>
    ): String = withSystemAddressClient(url, addresses) { it.postForm(url, fields) }

    private suspend fun <T> withTrustedClient(url: String, action: suspend (com.lovelyreader.source.HttpTextClient) -> T): T {
        val host = requireTrustedVideoHttpsUrl(url).toURI().host ?: throw UnknownHostException("无效片源主机")
        val addresses = resolveWithAliDns(host)
        if (addresses.isEmpty()) throw TrustedDnsResolutionException("可信 DNS 未返回可用地址")
        return withAddressClient(
            url = url,
            addresses = addresses,
            connectTimeoutMillis = TRUSTED_TARGET_CONNECT_TIMEOUT_MILLIS,
            readTimeoutMillis = TRUSTED_TARGET_READ_TIMEOUT_MILLIS,
            plainSocketFactory = ::createActiveNetworkSocket,
            action = action
        )
    }

    private suspend fun <T> withSystemAddressClient(
        url: String,
        addresses: List<InetAddress>,
        action: suspend (com.lovelyreader.source.HttpTextClient) -> T
    ): T {
        val host = requireTrustedVideoHttpsUrl(url).toURI().host ?: throw UnknownHostException("无效片源主机")
        val usableAddresses = selectTrustedDnsAddresses(host, addresses).usableAddresses
        if (usableAddresses.isEmpty()) throw UnknownHostException("系统 DNS 未返回可用地址")
        return withAddressClient(
            url = url,
            addresses = usableAddresses,
            connectTimeoutMillis = SYSTEM_TARGET_CONNECT_TIMEOUT_MILLIS,
            readTimeoutMillis = SYSTEM_TARGET_READ_TIMEOUT_MILLIS,
            plainSocketFactory = ::createActiveNetworkSocket,
            action = action
        )
    }

    private suspend fun <T> withAddressClient(
        url: String,
        addresses: List<InetAddress>,
        connectTimeoutMillis: Int,
        readTimeoutMillis: Int,
        plainSocketFactory: () -> Socket,
        action: suspend (com.lovelyreader.source.HttpTextClient) -> T
    ): T {
        val host = URI(url).host ?: throw UnknownHostException("无效片源主机")
        val orderedAddresses = addresses
            .distinctBy { it.hostAddress }
            .sortedWith(compareBy<InetAddress> { if (it is Inet4Address) 0 else 1 })
            .take(MAX_ADDRESS_ATTEMPTS)
        val client = com.lovelyreader.source.HttpTextClient(
            minimumIntervalMillis = 500,
            connectTimeoutMillis = connectTimeoutMillis,
            readTimeoutMillis = readTimeoutMillis,
            connectionFactory = TrustedAddressHttpsConnectionFactory(
                hostname = host,
                addresses = orderedAddresses,
                connectTimeoutMillis = connectTimeoutMillis,
                plainSocketFactory = plainSocketFactory
            ),
            responseInspector = ::inspectVideoHttpResponse
        )
        return try {
            action(client)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (!isTransientVideoNetworkFailure(error)) throw error
            throw TrustedVideoTargetException("可信地址均不可达", error)
        }
    }

    private companion object {
        const val SYSTEM_PREFLIGHT_CONNECT_TIMEOUT_MILLIS = 1_500
        // Catalogue pages are interactive requests, not media streams. Keep
        // a bad route from occupying the search pipeline for minutes while
        // the UI is waiting for a retryable result.
        const val SYSTEM_TARGET_CONNECT_TIMEOUT_MILLIS = 8_000
        const val SYSTEM_TARGET_READ_TIMEOUT_MILLIS = 20_000
        const val TRUSTED_TARGET_CONNECT_TIMEOUT_MILLIS = 8_000
        const val TRUSTED_TARGET_READ_TIMEOUT_MILLIS = 20_000
    }

    private suspend fun resolveWithAliDns(host: String): List<InetAddress> {
        addressCache.get(host)?.let { return it }
        return dnsResolver.resolve(host).also { resolved ->
            addressCache.put(host, resolved)
        }
    }

    /** Bind fallback sockets to the same active Android Network used for DNS preflight. */
    private fun createActiveNetworkSocket(): Socket {
        val manager = appContext.getSystemService(ConnectivityManager::class.java)
        return manager?.activeNetwork?.socketFactory?.createSocket() ?: Socket()
    }
}

/**
 * Connects to a DNS address selected by the trusted resolver while preserving the URL hostname
 * for TLS SNI and the platform's default certificate/hostname verification.
 */
private class TrustedAddressHttpsConnectionFactory(
    private val hostname: String,
    private val addresses: List<InetAddress>,
    private val connectTimeoutMillis: Int,
    private val plainSocketFactory: () -> Socket = { Socket() }
) : (URL) -> java.net.HttpURLConnection {
    override fun invoke(url: URL): java.net.HttpURLConnection {
        requireTrustedVideoHttpsUrl(url.toString())
        val transportUrl = addresses.firstOrNull()?.let { trustedAddressUrl(url, it) } ?: url
        val connection = transportUrl.openConnection() as java.net.HttpURLConnection
        connection.setRequestProperty("Host", hostname)
        if (connection is HttpsURLConnection) {
            connection.sslSocketFactory = AddressBoundSniSocketFactory(
                delegate = HttpsURLConnection.getDefaultSSLSocketFactory(),
                hostname = hostname,
                addresses = addresses,
                connectTimeoutMillis = connectTimeoutMillis,
                plainSocketFactory = plainSocketFactory
            )
            val platformVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
            connection.hostnameVerifier = HostnameVerifier { _, session ->
                platformVerifier.verify(hostname, session)
            }
        }
        return connection
    }
}

private class AddressBoundSniSocketFactory(
    private val delegate: SSLSocketFactory,
    private val hostname: String,
    private val addresses: List<InetAddress>,
    private val connectTimeoutMillis: Int,
    private val plainSocketFactory: () -> Socket
) : SSLSocketFactory() {
    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites
    /**
     * HttpsURLConnection implementations are allowed to use the no-arg factory method and
     * connect the returned socket later. Returning an unbound delegate socket here would make
     * that later connect resolve the poisoned transport URL again (and lose the trusted SNI
     * route). HTTPS video URLs are restricted to the default port, so bind this path eagerly.
     */
    override fun createSocket(): Socket = connectTrusted(443)
    override fun createSocket(host: String, port: Int): Socket = connectTrusted(port)
    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket = connectTrusted(port)
    override fun createSocket(host: InetAddress, port: Int): Socket = connectTrusted(port)
    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket = connectTrusted(port)
    override fun createSocket(socket: Socket, host: String, port: Int, autoClose: Boolean): Socket {
        runCatching { socket.close() }
        return connectTrusted(port)
    }

    private fun connectTrusted(port: Int): Socket {
        var lastError: Throwable? = null
        addresses.take(MAX_ADDRESS_ATTEMPTS).forEach { address ->
            val plain = plainSocketFactory()
            var handedOff = false
            try {
                plain.connect(InetSocketAddress(address, port), connectTimeoutMillis)
                val secure = configureTrustedVideoTlsSocket(
                    delegate.createSocket(plain, hostname, port, true)
                )
                handedOff = true
                return secure
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (error is IOException) {
                    lastError = error
                } else {
                    throw error
                }
            } finally {
                if (!handedOff) runCatching { plain.close() }
            }
        }
        throw lastError ?: UnknownHostException(hostname)
    }
}
