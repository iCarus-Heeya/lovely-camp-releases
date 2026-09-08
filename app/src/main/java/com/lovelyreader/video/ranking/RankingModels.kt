package com.lovelyreader.video.ranking

data class InstantLike(val epochMillis: Long)

enum class RankingMediaType { ALL, MOVIE, SERIES, VARIETY }
enum class RankingBoardKind { SOURCE_RANK, AGGREGATE_RANK, FEATURED }
enum class RankingFreshness { FRESH, STALE, EXPIRED }
enum class RankingAvailability { AVAILABLE, PARTIAL, EMPTY, UNAVAILABLE }
enum class RankingSourceHealth { AVAILABLE, DEGRADED, UNAVAILABLE }
enum class RankingErrorCode { BAD_REQUEST, NOT_FOUND, SNAPSHOT_EXPIRED, RATE_LIMITED, NO_SNAPSHOT, PARSE_ERROR }

/**
 * Server-declared ranking capabilities. The client must not invent boards or
 * media filters when this contract is empty or unavailable.
 */
data class RankingBoardOption(val id: String, val label: String)

data class RankingCapabilities(
    val schemaVersion: Int = 1,
    val supportedBoards: List<RankingBoardOption> = emptyList(),
    val supportedMediaTypes: List<RankingMediaType> = emptyList()
) {
    fun normalizedBoards(): List<RankingBoardOption> = supportedBoards
        .asSequence()
        .filter { it.id.isNotBlank() }
        .distinctBy { it.id }
        .toList()

    fun normalizedMediaTypes(): List<RankingMediaType> = supportedMediaTypes.distinct()

    val hasVerifiedFilters: Boolean
        get() = normalizedBoards().isNotEmpty() || normalizedMediaTypes().isNotEmpty()
}

data class RankingRequest(
    val boardId: String, val period: String?, val mediaType: RankingMediaType,
    val region: String?, val year: Int?, val genre: String?, val language: String?,
    val limit: Int = 20, val cursor: String? = null
)

data class RankingFilters(
    val period: String?, val mediaType: RankingMediaType, val region: String?, val year: Int?,
    val genre: String?, val language: String?, val limit: Int, val cursor: String?
)

data class SourceRating(val sourceId: String, val value: Double?, val scaleMax: Double?, val voteCount: Int?)
data class SourceRank(val sourceId: String, val rank: Int?, val previousRank: Int?, val rankDelta: Int?, val isNew: Boolean)
data class RankingCoverage(val sourceCount: Int, val coveredSourceIds: List<String>, val complete: Boolean)
data class RankingSourceStatus(val sourceId: String, val status: RankingSourceHealth, val errorCode: RankingErrorCode? = null)

data class RankingEntry(
    val canonicalId: String, val rank: Int?, val displayTitle: String, val originalTitle: String?,
    val aliases: List<String>, val mediaType: RankingMediaType, val releaseYear: Int?,
    val countries: List<String>, val genres: List<String>, val posterUrl: String?, val summary: String?,
    val aggregateScore: Double?, val sourceRatings: List<SourceRating>, val sourceRanks: List<SourceRank>,
    val coverage: RankingCoverage, val searchTitle: String
)

data class RankingSnapshot(
    val schemaVersion: Int, val requestId: String, val boardId: String, val filters: RankingFilters,
    val snapshotId: String, val generatedAt: InstantLike, val dataAsOf: InstantLike,
    val expiresAt: InstantLike, val staleUntil: InstantLike, val freshness: RankingFreshness,
    val availability: RankingAvailability, val coverage: RankingCoverage,
    val sourceStatuses: List<RankingSourceStatus>, val items: List<RankingEntry>, val nextCursor: String?
)
