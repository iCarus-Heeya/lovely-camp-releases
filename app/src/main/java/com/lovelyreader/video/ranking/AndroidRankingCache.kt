package com.lovelyreader.video.ranking

import android.content.SharedPreferences
import kotlinx.coroutines.CancellationException
import java.nio.charset.StandardCharsets
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidRankingCache(
    private val preferences: SharedPreferences,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val maxEntryBytes: Int = DEFAULT_MAX_CACHE_ENTRY_BYTES
) : RankingEtagCache {
    init {
        require(maxEntryBytes in 1..MAX_ALLOWED_CACHE_ENTRY_BYTES) { "invalid cache entry limit" }
    }

    override suspend fun read(key: String): CachedRanking? = withContext(Dispatchers.IO) {
        if (!key.startsWith(CACHE_NAMESPACE)) return@withContext null
        val raw = preferences.getString(key, null) ?: return@withContext null
        if (raw.toByteArray(StandardCharsets.UTF_8).size > maxEntryBytes) {
            remove(key)
            return@withContext null
        }
        try {
            val envelope = JSONObject(raw)
            require(envelope.getInt("version") == CACHE_VERSION)
            val now = clockMillis()
            val cachedAt = envelope.getLong("cachedAtMillis")
            require(cachedAt in 0..now)
            val etag = if (envelope.isNull("etag")) null else safeEtag(envelope.getString("etag"))
            val snapshotJson = envelope.getJSONObject("snapshot")
            require(snapshotJson.has("hasMorePages"))
            val hasMorePages = snapshotJson.getBoolean("hasMorePages")
            // Freshness is a time-relative presentation property. Recompute it
            // on every read so a valid snapshot can transition to STALE after
            // its original write instead of being discarded as inconsistent.
            val snapshot = parseRankingSnapshot(snapshotJson.withFreshnessAt(now).toString(), now).getOrThrow()
            CachedRanking(snapshot, cachedAt, etag, hasMorePages)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            remove(key)
            null
        }
    }

    override suspend fun write(key: String, snapshot: RankingSnapshot) {
        write(key, snapshot, null, clockMillis())
    }

    override suspend fun write(key: String, snapshot: RankingSnapshot, etag: String?, cachedAtMillis: Long): Unit = withContext(Dispatchers.IO) {
        require(key.startsWith(CACHE_NAMESPACE)) { "ranking cache key is not namespaced" }
        val envelope = JSONObject()
            .put("version", CACHE_VERSION)
            .put("cachedAtMillis", cachedAtMillis)
            .put("etag", safeEtag(etag) ?: JSONObject.NULL)
            .put("snapshot", snapshot.toCacheJson())
            .toString()
        require(envelope.toByteArray(StandardCharsets.UTF_8).size <= maxEntryBytes) { "ranking cache entry is oversized" }
        check(preferences.edit().putString(key, envelope).commit()) { "ranking cache write failed" }
    }

    override suspend fun remove(key: String): Unit = withContext(Dispatchers.IO) {
        if (key.startsWith(CACHE_NAMESPACE)) preferences.edit().remove(key).commit()
        Unit
    }
}

/**
 * A cursor can be an opaque, signed pagination token.  The cache key already
 * scopes an entry to its request cursor, so retaining either input or output
 * cursors in SharedPreferences provides no lookup value and risks persisting
 * a credential-like value on disk.
 */
private fun RankingSnapshot.toCacheJson(): JSONObject = copy(
    filters = filters.copy(cursor = null),
    nextCursor = null
).toJson().put("hasMorePages", nextCursor != null)

private fun JSONObject.withFreshnessAt(nowMillis: Long): JSONObject {
    val copy = JSONObject(toString())
    val expiresAt = Instant.parse(copy.getString("expiresAt")).toEpochMilli()
    val staleUntil = Instant.parse(copy.getString("staleUntil")).toEpochMilli()
    val freshness = when {
        nowMillis < expiresAt -> RankingFreshness.FRESH
        nowMillis <= staleUntil -> RankingFreshness.STALE
        else -> RankingFreshness.EXPIRED
    }
    return copy.put("freshness", freshness.name)
}

private fun RankingSnapshot.toJson() = JSONObject()
    .put("schemaVersion", schemaVersion)
    .put("requestId", requestId)
    .put("boardId", boardId)
    .put("filters", filters.toJson())
    .put("snapshotId", snapshotId)
    .put("generatedAt", generatedAt.iso())
    .put("dataAsOf", dataAsOf.iso())
    .put("expiresAt", expiresAt.iso())
    .put("staleUntil", staleUntil.iso())
    .put("freshness", freshness.name)
    .put("availability", availability.name)
    .put("coverage", coverage.toJson())
    .put("sourceStatuses", JSONArray(sourceStatuses.map { it.toJson() }))
    .put("items", JSONArray(items.map { it.toJson() }))
    .put("nextCursor", nextCursor ?: JSONObject.NULL)
    .put("methodologyVersion", methodologyVersion ?: JSONObject.NULL)
    .put("identityMapVersion", identityMapVersion ?: JSONObject.NULL)

private fun RankingFilters.toJson() = JSONObject()
    .put("period", period ?: JSONObject.NULL)
    .put("mediaType", mediaType.name)
    .put("region", region ?: JSONObject.NULL)
    .put("year", year ?: JSONObject.NULL)
    .put("genre", genre ?: JSONObject.NULL)
    .put("language", language ?: JSONObject.NULL)
    .put("limit", limit)
    .put("cursor", cursor ?: JSONObject.NULL)

private fun RankingCoverage.toJson() = JSONObject()
    .put("sourceCount", sourceCount)
    .put("coveredSourceIds", JSONArray(coveredSourceIds))
    .put("complete", complete)

private fun RankingSourceStatus.toJson() = JSONObject()
    .put("sourceId", sourceId)
    .put("status", status.name)
    .put("errorCode", errorCode?.name ?: JSONObject.NULL)

private fun RankingEntry.toJson() = JSONObject()
    .put("canonicalId", canonicalId)
    .put("rank", rank ?: JSONObject.NULL)
    .put("displayTitle", displayTitle)
    .put("originalTitle", originalTitle ?: JSONObject.NULL)
    .put("aliases", JSONArray(aliases))
    .put("mediaType", mediaType.name)
    .put("releaseYear", releaseYear ?: JSONObject.NULL)
    .put("countries", JSONArray(countries))
    .put("genres", JSONArray(genres))
    .put("posterUrl", posterUrl ?: JSONObject.NULL)
    .put("summary", summary ?: JSONObject.NULL)
    .put("aggregateScore", aggregateScore ?: JSONObject.NULL)
    .put("sourceRatings", JSONArray(sourceRatings.map { it.toJson() }))
    .put("sourceRanks", JSONArray(sourceRanks.map { it.toJson() }))
    .put("coverage", coverage.toJson())
    .put("searchTitle", searchTitle)

private fun SourceRating.toJson() = JSONObject()
    .put("sourceId", sourceId)
    .put("value", value ?: JSONObject.NULL)
    .put("scaleMax", scaleMax ?: JSONObject.NULL)
    .put("voteCount", voteCount ?: JSONObject.NULL)

private fun SourceRank.toJson() = JSONObject()
    .put("sourceId", sourceId)
    .put("rank", rank ?: JSONObject.NULL)
    .put("previousRank", previousRank ?: JSONObject.NULL)
    .put("rankDelta", rankDelta ?: JSONObject.NULL)
    .put("isNew", isNew)

private fun InstantLike.iso(): String = Instant.ofEpochMilli(epochMillis).toString()

private const val CACHE_VERSION = 3
private const val DEFAULT_MAX_CACHE_ENTRY_BYTES = 512 * 1024
private const val MAX_ALLOWED_CACHE_ENTRY_BYTES = 2 * 1024 * 1024
