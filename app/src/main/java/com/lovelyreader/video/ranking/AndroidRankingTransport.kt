package com.lovelyreader.video.ranking

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

enum class RankingTransportFailure { REDIRECT, CONTENT_TYPE, BODY_TOO_LARGE, NETWORK }

class RankingTransportException(
    val failure: RankingTransportFailure,
    cause: Throwable? = null
) : IOException("Ranking transport failed", cause)

class AndroidRankingTransport(
    endpoint: String,
    approvedHosts: Set<String>,
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val connectTimeoutMillis: Int = DEFAULT_CONNECT_TIMEOUT_MILLIS,
    private val readTimeoutMillis: Int = DEFAULT_READ_TIMEOUT_MILLIS,
    private val maxResponseBytes: Int = DEFAULT_MAX_RESPONSE_BYTES
) : RankingTransport {
    private val endpoint = approvedEndpoint(endpoint, approvedHosts)

    init {
        require(connectTimeoutMillis in 1..60_000) { "invalid connect timeout" }
        require(readTimeoutMillis in 1..120_000) { "invalid read timeout" }
        require(maxResponseBytes in 1..MAX_ALLOWED_RESPONSE_BYTES) { "invalid response limit" }
    }

    override suspend fun fetch(request: RankingRequest, etag: String?): RankingHttpResponse = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()
        validateRankingRequest(request).getOrThrow()
        val url = buildRankingRequestUrl(endpoint, request).toURL()
        val connection = try {
            connectionFactory(url)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            throw RankingTransportException(RankingTransportFailure.NETWORK, failure)
        }
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            safeEtag(etag)?.let { connection.setRequestProperty("If-None-Match", it) }

            val status = connection.responseCode
            coroutineContext.ensureActive()
            if (status in 300..399 && status != HttpURLConnection.HTTP_NOT_MODIFIED) {
                throw RankingTransportException(RankingTransportFailure.REDIRECT)
            }
            val contentType = connection.contentType
            if (status == HttpURLConnection.HTTP_OK && !isJson(contentType)) {
                throw RankingTransportException(RankingTransportFailure.CONTENT_TYPE)
            }
            val body = if (status == HttpURLConnection.HTTP_NOT_MODIFIED) null else readBounded(connection, status)
            RankingHttpResponse(
                statusCode = status,
                body = body,
                etag = safeEtag(connection.getHeaderField("ETag")),
                retryAfterSeconds = parseRetryAfter(connection.getHeaderField("Retry-After")),
                contentType = contentType
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: RankingTransportException) {
            throw failure
        } catch (failure: Throwable) {
            throw RankingTransportException(RankingTransportFailure.NETWORK, failure)
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(connection: HttpURLConnection, status: Int): String? {
        val declared = connection.contentLengthLong
        if (declared > maxResponseBytes) throw RankingTransportException(RankingTransportFailure.BODY_TOO_LARGE)
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        stream ?: return null
        return stream.use { input ->
            val output = ByteArrayOutputStream(minOf(maxResponseBytes, 8 * 1024))
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (output.size() + read > maxResponseBytes) throw RankingTransportException(RankingTransportFailure.BODY_TOO_LARGE)
                output.write(buffer, 0, read)
            }
            output.toString(StandardCharsets.UTF_8.name())
        }
    }
}

internal fun approvedEndpoint(raw: String, approvedHosts: Set<String>): URI {
    val uri = runCatching { URI(raw) }.getOrElse { throw IllegalArgumentException("invalid ranking endpoint") }
    val normalizedHosts = approvedHosts.map { it.lowercase() }.toSet()
    require(uri.scheme.equals("https", ignoreCase = true)) { "ranking endpoint must use HTTPS" }
    require(uri.userInfo == null && uri.query == null && uri.fragment == null) { "ranking endpoint contains forbidden components" }
    require(!uri.host.isNullOrBlank() && uri.host.lowercase() in normalizedHosts) { "ranking endpoint host is not approved" }
    require(isPublicEndpointHost(uri.host)) { "ranking endpoint target is not public" }
    require(uri.port == -1 || uri.port == 443) { "ranking endpoint port is not approved" }
    require(uri.normalize().rawPath == uri.rawPath && !uri.rawPath.contains("\\")) { "ranking endpoint path is unsafe" }
    require(uri.rawPath == "/api/v1/ranking") { "ranking endpoint path is not approved" }
    return URI("https", null, uri.host.lowercase(), uri.port, uri.path, null, null)
}

private fun isPublicEndpointHost(host: String): Boolean {
    val value = host.lowercase().removePrefix("[").removeSuffix("]")
    if (value == "localhost" || value.endsWith(".localhost") || value.endsWith(".local")) return false
    val literal = when {
        ':' in value -> runCatching { InetAddress.getByName(value) }.getOrNull()
        value.all { it.isDigit() || it == '.' } -> runCatching { InetAddress.getByName(value) }.getOrNull()
        else -> null
    } ?: return ':' !in value && value.any { it.isLetter() }
    val bytes = literal.address
    val uniqueLocalV6 = bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc
    val carrierGradeNat = bytes.size == 4 && (bytes[0].toInt() and 0xff) == 100 && (bytes[1].toInt() and 0xc0) == 64
    return !literal.isAnyLocalAddress && !literal.isLoopbackAddress && !literal.isLinkLocalAddress &&
        !literal.isSiteLocalAddress && !literal.isMulticastAddress && !uniqueLocalV6 && !carrierGradeNat
}

internal fun buildRankingRequestUrl(endpoint: URI, request: RankingRequest): URI {
    val path = "${endpoint.rawPath}/boards/${percentEncode(request.boardId)}/entries"
    val values = listOf(
        "period" to request.period,
        "mediaType" to request.mediaType.name,
        "region" to request.region,
        "year" to request.year?.toString(),
        "genre" to request.genre,
        "language" to request.language,
        "limit" to request.limit.toString(),
        "cursor" to request.cursor
    )
    val query = values.filter { it.second != null }.joinToString("&") { (name, value) ->
        "${percentEncode(name)}=${percentEncode(value!!)}"
    }
    return URI("${endpoint.scheme}://${endpoint.rawAuthority}$path?$query")
}

private fun percentEncode(value: String): String = buildString {
    value.toByteArray(StandardCharsets.UTF_8).forEach { byte ->
        val unsigned = byte.toInt() and 0xff
        if (unsigned in 'a'.code..'z'.code || unsigned in 'A'.code..'Z'.code ||
            unsigned in '0'.code..'9'.code || unsigned == '-'.code || unsigned == '.'.code ||
            unsigned == '_'.code || unsigned == '~'.code
        ) {
            append(unsigned.toChar())
        } else {
            append('%')
            append(HEX[unsigned ushr 4])
            append(HEX[unsigned and 0x0f])
        }
    }
}

private fun parseRetryAfter(value: String?): Long? = value?.trim()?.toLongOrNull()?.takeIf { it in 0..86_400 }

private const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 8_000
private const val DEFAULT_READ_TIMEOUT_MILLIS = 12_000
private const val DEFAULT_MAX_RESPONSE_BYTES = 512 * 1024
private const val MAX_ALLOWED_RESPONSE_BYTES = 2 * 1024 * 1024
private const val HEX = "0123456789ABCDEF"
