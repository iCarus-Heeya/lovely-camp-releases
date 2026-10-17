package com.lovelyreader.video.ranking

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException

interface RankingTransport {
    suspend fun fetch(request: RankingRequest, etag: String?): RankingHttpResponse
}

data class RankingHttpResponse(
    val statusCode: Int,
    val body: String?,
    val etag: String?,
    val retryAfterSeconds: Long?,
    val contentType: String?
)

interface RankingCache {
    suspend fun read(key: String): CachedRanking?
    suspend fun write(key: String, snapshot: RankingSnapshot)
    suspend fun remove(key: String)
}

/** Optional cache capability used to persist the validator together with the snapshot. */
interface RankingEtagCache : RankingCache {
    suspend fun write(key: String, snapshot: RankingSnapshot, etag: String?, cachedAtMillis: Long)
}

data class CachedRanking(
    val snapshot: RankingSnapshot,
    val cachedAtMillis: Long,
    val etag: String? = null,
    /** True when the original network response had another opaque page. */
    val hasMorePages: Boolean = snapshot.nextCursor != null
)

interface RankingRepository {
    suspend fun load(request: RankingRequest, forceRefresh: Boolean = false): RankingLoadResult

    /** Versioned capability handshake; empty means no verified filters are available. */
    suspend fun loadCapabilities(): Result<RankingCapabilities> = Result.success(RankingCapabilities())
}

enum class RankingDataSource { NETWORK, BUNDLED, CACHE_FRESH, CACHE_STALE, EMPTY }

enum class RankingFailureKind {
    INVALID_REQUEST,
    BAD_REQUEST,
    NOT_FOUND,
    SNAPSHOT_EXPIRED,
    RATE_LIMITED,
    SERVICE_UNAVAILABLE,
    HTTP_ERROR,
    CONTENT_TYPE,
    MALFORMED_RESPONSE,
    NETWORK,
    NOT_MODIFIED_WITHOUT_CACHE
}

data class RankingError(
    val kind: RankingFailureKind,
    val message: String,
    val statusCode: Int? = null,
    val retryable: Boolean = false,
    val retryAfterSeconds: Long? = null
)

data class RankingLoadResult(
    val snapshot: RankingSnapshot?,
    val source: RankingDataSource,
    val error: RankingError? = null,
    /** Bounded diagnostic only; never contains storage errors, paths, payloads or credentials. */
    val cacheWriteFailed: Boolean = false
)

class DefaultRankingRepository(
    private val transport: RankingTransport,
    private val cache: RankingCache,
    private val clockMillis: () -> Long = System::currentTimeMillis
) : RankingRepository {
    override suspend fun load(request: RankingRequest, forceRefresh: Boolean): RankingLoadResult {
        val validation = validateRankingRequest(request)
        if (validation.isFailure) {
            return emptyError(RankingFailureKind.INVALID_REQUEST, "Invalid ranking request")
        }

        val key = rankingCacheKey(request)
        val now = clockMillis()
        val cached = try {
            cache.read(key)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            runCatching { cache.remove(key) }
            null
        }
        if (!forceRefresh && cached != null && !cached.hasMorePages && now < cached.snapshot.expiresAt.epochMillis) {
            return RankingLoadResult(cached.snapshot, RankingDataSource.CACHE_FRESH)
        }

        val response = try {
            transport.fetch(request, cached?.takeUnless { it.hasMorePages }?.etag)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: RankingTransportException) {
            return fallback(cached, now, failure.toRankingError())
        } catch (_: Throwable) {
            return fallback(cached, now, RankingError(RankingFailureKind.NETWORK, SAFE_NETWORK_MESSAGE, retryable = true))
        }

        return when (response.statusCode) {
            200 -> handleSuccess(request, key, cached, now, response)
            304 -> {
                if (cached?.hasMorePages == true) {
                    emptyError(
                        RankingFailureKind.MALFORMED_RESPONSE,
                        "Ranking cache pagination cannot be revalidated without a cursor",
                        304
                    )
                } else if (cached != null && now <= cached.snapshot.staleUntil.epochMillis) {
                    val source = if (!cached.hasMorePages && now < cached.snapshot.expiresAt.epochMillis) {
                        RankingDataSource.CACHE_FRESH
                    } else {
                        RankingDataSource.CACHE_STALE
                    }
                    RankingLoadResult(cached.snapshot, source)
                } else {
                    emptyError(RankingFailureKind.NOT_MODIFIED_WITHOUT_CACHE, "Ranking cache is unavailable", 304)
                }
            }
            400 -> fallback(cached, now, httpError(RankingFailureKind.BAD_REQUEST, response, retryable = false))
            404 -> fallback(cached, now, httpError(RankingFailureKind.NOT_FOUND, response, retryable = false))
            409 -> fallback(cached, now, httpError(RankingFailureKind.SNAPSHOT_EXPIRED, response, retryable = true))
            429 -> fallback(cached, now, httpError(RankingFailureKind.RATE_LIMITED, response, retryable = true))
            in 500..599 -> fallback(cached, now, httpError(RankingFailureKind.SERVICE_UNAVAILABLE, response, retryable = true))
            else -> fallback(cached, now, httpError(RankingFailureKind.HTTP_ERROR, response, retryable = false))
        }
    }

    private suspend fun handleSuccess(
        request: RankingRequest,
        key: String,
        cached: CachedRanking?,
        now: Long,
        response: RankingHttpResponse
    ): RankingLoadResult {
        if (!isJson(response.contentType)) {
            return fallback(cached, now, RankingError(RankingFailureKind.CONTENT_TYPE, "Ranking response is not JSON"))
        }
        val raw = response.body ?: return fallback(
            cached,
            now,
            RankingError(RankingFailureKind.MALFORMED_RESPONSE, "Ranking response is empty")
        )
        val snapshot = parseRankingSnapshot(raw, now).getOrElse {
            return fallback(cached, now, RankingError(RankingFailureKind.MALFORMED_RESPONSE, "Ranking response is invalid"))
        }
        if (!snapshot.matches(request)) {
            return fallback(cached, now, RankingError(RankingFailureKind.MALFORMED_RESPONSE, "Ranking response does not match request"))
        }
        // Cursor pages contain opaque continuation tokens. Keep them in memory
        // only; the first page is cached with a hasMorePages marker so a fresh
        // cache hit never masquerades as a paginatable snapshot.
        val cacheWriteFailed = if (request.cursor != null) {
            false
        } else {
            try {
                if (cache is RankingEtagCache) {
                    cache.write(key, snapshot, safeEtag(response.etag), now)
                } else {
                    cache.write(key, snapshot)
                }
                false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                true
            }
        }
        return RankingLoadResult(snapshot, RankingDataSource.NETWORK, cacheWriteFailed = cacheWriteFailed)
    }

    private fun fallback(cached: CachedRanking?, now: Long, error: RankingError): RankingLoadResult {
        if (cached != null && now <= cached.snapshot.staleUntil.epochMillis) {
            val source = if (!cached.hasMorePages && now < cached.snapshot.expiresAt.epochMillis) {
                RankingDataSource.CACHE_FRESH
            } else {
                RankingDataSource.CACHE_STALE
            }
            return RankingLoadResult(cached.snapshot, source, error)
        }
        return RankingLoadResult(null, RankingDataSource.EMPTY, error)
    }

    private fun httpError(kind: RankingFailureKind, response: RankingHttpResponse, retryable: Boolean) = RankingError(
        kind = kind,
        message = "Ranking service returned HTTP ${response.statusCode}",
        statusCode = response.statusCode,
        retryable = retryable,
        retryAfterSeconds = response.retryAfterSeconds
    )
}

fun rankingCacheKey(request: RankingRequest): String {
    val canonical = listOf(
        request.boardId,
        request.period.orEmpty(),
        request.mediaType.name,
        request.region.orEmpty(),
        request.year?.toString().orEmpty(),
        request.genre.orEmpty(),
        request.language.orEmpty(),
        request.limit.toString(),
        request.cursor.orEmpty()
    ).joinToString(separator = "|") { value -> "${value.toByteArray(StandardCharsets.UTF_8).size}:$value" }
    val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(StandardCharsets.UTF_8))
    return CACHE_NAMESPACE + digest.joinToString("") { "%02x".format(it) }
}

private fun RankingSnapshot.matches(request: RankingRequest): Boolean =
    boardId == request.boardId && filters.period == request.period && filters.mediaType == request.mediaType &&
        filters.region == request.region && filters.year == request.year && filters.genre == request.genre &&
        filters.language == request.language && filters.limit == request.limit && filters.cursor == request.cursor

private fun RankingTransportException.toRankingError(): RankingError = when (failure) {
    RankingTransportFailure.CONTENT_TYPE -> RankingError(RankingFailureKind.CONTENT_TYPE, "Ranking response is not JSON")
    RankingTransportFailure.BODY_TOO_LARGE -> RankingError(RankingFailureKind.MALFORMED_RESPONSE, "Ranking response exceeds size limit")
    RankingTransportFailure.REDIRECT -> RankingError(RankingFailureKind.NETWORK, SAFE_NETWORK_MESSAGE, retryable = false)
    RankingTransportFailure.NETWORK -> RankingError(RankingFailureKind.NETWORK, SAFE_NETWORK_MESSAGE, retryable = true)
}

private fun httpMessage(kind: RankingFailureKind) = when (kind) {
    RankingFailureKind.BAD_REQUEST -> "Ranking filters are unsupported"
    RankingFailureKind.NOT_FOUND -> "Ranking board was not found"
    RankingFailureKind.SNAPSHOT_EXPIRED -> "Ranking snapshot expired"
    RankingFailureKind.RATE_LIMITED -> "Ranking service is rate limited"
    RankingFailureKind.SERVICE_UNAVAILABLE -> "Ranking service is unavailable"
    else -> "Ranking request failed"
}

private fun emptyError(kind: RankingFailureKind, message: String = httpMessage(kind), status: Int? = null) =
    RankingLoadResult(null, RankingDataSource.EMPTY, RankingError(kind, message, status))

internal fun isJson(contentType: String?): Boolean =
    contentType?.substringBefore(';')?.trim()?.equals("application/json", ignoreCase = true) == true

internal fun safeEtag(value: String?): String? = value?.takeIf {
    it.length in 1..512 && '\r' !in it && '\n' !in it
}

internal const val CACHE_NAMESPACE = "videoRanking.v1."
private const val SAFE_NETWORK_MESSAGE = "Ranking request failed"
