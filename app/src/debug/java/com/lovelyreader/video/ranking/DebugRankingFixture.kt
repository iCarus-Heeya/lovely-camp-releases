package com.lovelyreader.video.ranking

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Debug-only access to the deterministic ranking catalog.
 *
 * This type lives in the debug source set on purpose. It is not part of the
 * release classpath and its asset is not a release resource. The catalog is
 * test data only: it contains no live source URL, token, cookie, playback
 * address, score, or claim about real-world ranking truth.
 */
object DebugRankingFixture {
    // Deliberately fixed: Debug evidence must not change with wall-clock time.
    const val ASSET_PATH = "ranking/catalog-v1.json"
    const val SCHEMA_VERSION = 1
    const val DEFAULT_SCENARIO = "complete"
    const val DEFAULT_NOW_MILLIS = 1788825600000L // 2026-09-08T00:00:00Z

    /** Reads the immutable debug catalog without touching network or cache. */
    fun readCatalog(context: Context): String =
        context.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }

    /** Returns stable scenario IDs for parameterized debug/state tests. */
    fun scenarioIds(context: Context): List<String> =
        catalog(context).getJSONArray("scenarios").let { scenarios ->
            (0 until scenarios.length()).map { scenarios.getJSONObject(it).getString("id") }
        }

    /** Returns the raw snapshot JSON for a named scenario. */
    fun readScenario(context: Context, scenarioId: String = DEFAULT_SCENARIO): String =
        scenario(context, scenarioId).getJSONObject("snapshot").toString()

    /**
     * Parses one scenario with the fixture clock by default. Tests may pass a
     * different clock to exercise expiration boundaries deterministically.
     */
    fun parseScenario(
        context: Context,
        scenarioId: String = DEFAULT_SCENARIO,
        nowMillis: Long = DEFAULT_NOW_MILLIS
    ): Result<RankingSnapshot> = parseRankingSnapshot(readScenario(context, scenarioId), nowMillis)

    /**
     * Provides a repository-shaped adapter for future Debug UI tests. It only
     * serves the selected scenario and rejects requests whose filters do not
     * match the fixture snapshot; it never falls through to live network code.
     */
    fun repository(
        context: Context,
        scenarioId: String = DEFAULT_SCENARIO,
        nowMillis: Long = DEFAULT_NOW_MILLIS
    ): RankingRepository = DebugFixtureRepository(parseScenario(context, scenarioId, nowMillis))

    private fun catalog(context: Context): JSONObject {
        val root = JSONObject(readCatalog(context))
        check(root.optBoolean("debugFixture")) { "ranking fixture must be marked debugFixture" }
        check(root.optBoolean("testDataOnly")) { "ranking fixture must be marked testDataOnly" }
        check(root.optInt("schemaVersion") == SCHEMA_VERSION) { "unsupported ranking fixture schema" }
        return root
    }

    private fun scenario(context: Context, scenarioId: String): JSONObject {
        val scenarios = catalog(context).getJSONArray("scenarios")
        return (0 until scenarios.length())
            .map { scenarios.getJSONObject(it) }
            .firstOrNull { it.getString("id") == scenarioId }
            ?: error("unknown ranking fixture scenario: $scenarioId")
    }
}

private class DebugFixtureRepository(
    private val parsed: Result<RankingSnapshot>
) : RankingRepository {
    override suspend fun load(request: RankingRequest, forceRefresh: Boolean): RankingLoadResult {
        val snapshot = parsed.getOrNull()
        if (snapshot == null) {
            return RankingLoadResult(
                snapshot = null,
                source = RankingDataSource.EMPTY,
                error = RankingError(
                    kind = RankingFailureKind.MALFORMED_RESPONSE,
                    message = "Debug ranking fixture is intentionally invalid"
                )
            )
        }
        if (!snapshot.matches(request)) {
            return RankingLoadResult(
                snapshot = null,
                source = RankingDataSource.EMPTY,
                error = RankingError(
                    kind = RankingFailureKind.BAD_REQUEST,
                    message = "Debug fixture does not cover this request"
                )
            )
        }
        return RankingLoadResult(snapshot, RankingDataSource.NETWORK)
    }
}

private fun RankingSnapshot.matches(request: RankingRequest): Boolean =
    boardId == request.boardId && filters.period == request.period &&
        filters.mediaType == request.mediaType && filters.region == request.region &&
        filters.year == request.year && filters.genre == request.genre &&
        filters.language == request.language && filters.limit == request.limit &&
        filters.cursor == request.cursor
