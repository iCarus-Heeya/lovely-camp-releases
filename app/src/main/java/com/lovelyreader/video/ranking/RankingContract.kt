package com.lovelyreader.video.ranking

import java.net.URI
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

private const val MAX_TEXT = 5000
private fun text(value: String?, name: String, required: Boolean = true): String? {
    if (value == null || value.isBlank()) { if (required) error("missing $name"); return null }
    require(value.length <= MAX_TEXT) { "$name is oversized" }; return value
}
private fun obj(o: JSONObject, name: String) = o.optJSONObject(name) ?: error("missing $name")
private fun nullable(o: JSONObject, n: String): String? = if (o.isNull(n)) null else o.optString(n, null)
private fun media(s: String) = runCatching { RankingMediaType.valueOf(s) }.getOrElse { error("invalid mediaType") }
private fun instant(s: String, name: String): InstantLike = runCatching { InstantLike(Instant.parse(s).toEpochMilli()) }.getOrElse { error("invalid $name") }
private fun rank(v: Int?, name: String): Int? { if (v != null) require(v >= 1) { "invalid $name" }; return v }
private fun strings(a: JSONArray?, name: String): List<String> = (0 until (a?.length() ?: 0)).map { text(a!!.getString(it), name)!! }
private fun coverage(o: JSONObject): RankingCoverage {
    val count = o.optInt("sourceCount", -1); require(count >= 0)
    val ids = strings(o.optJSONArray("coveredSourceIds"), "coveredSourceId")
    require(count == ids.size || count > ids.size) { "invalid coverage" }
    return RankingCoverage(count, ids, o.optBoolean("complete", false))
}
private fun ratings(a: JSONArray?): List<SourceRating> = (0 until (a?.length() ?: 0)).map {
    val o = a!!.getJSONObject(it); SourceRating(text(o.optString("sourceId", null), "sourceId")!!,
        if (o.isNull("value")) null else o.optDouble("value"), if (o.isNull("scaleMax")) null else o.optDouble("scaleMax"), if (o.isNull("voteCount")) null else o.optInt("voteCount"))
}
private fun ranks(a: JSONArray?): List<SourceRank> = (0 until (a?.length() ?: 0)).map {
    val o = a!!.getJSONObject(it); SourceRank(text(o.optString("sourceId", null), "sourceId")!!,
        if (o.isNull("rank")) null else rank(o.optInt("rank"), "rank"), if (o.isNull("previousRank")) null else rank(o.optInt("previousRank"), "previousRank"), if (o.isNull("rankDelta")) null else o.optInt("rankDelta"), o.optBoolean("isNew", false))
}

fun validateRankingRequest(request: RankingRequest): Result<RankingRequest> = runCatching {
    text(request.boardId, "boardId"); require(request.limit in 1..50) { "limit must be 1..50" }
    request.period?.let { text(it, "period") }; request.region?.let { text(it, "region") }
    request.genre?.let { text(it, "genre") }; request.language?.let { text(it, "language") }
    request.year?.let { require(it in 1800..2200) { "invalid year" } }
    request.cursor?.let { text(it, "cursor") }; request
}

fun parseRankingSnapshot(raw: String, nowMillis: Long = System.currentTimeMillis(), expectedSchemaVersion: Int = 1): Result<RankingSnapshot> = runCatching {
    val root = JSONObject(raw)
    require(root.optInt("schemaVersion", -1) == expectedSchemaVersion) { "unsupported schema" }
    val requestId = text(root.optString("requestId", null), "requestId")!!
    val boardId = text(root.optString("boardId", null), "boardId")!!
    val f = obj(root, "filters")
    val filters = RankingFilters(nullable(f, "period"), media(f.optString("mediaType")), nullable(f, "region"), if (f.isNull("year")) null else f.optInt("year"), nullable(f, "genre"), nullable(f, "language"), f.optInt("limit", -1), nullable(f, "cursor"))
    require(filters.limit in 1..50)
    val snapshotId = text(root.optString("snapshotId", null), "snapshotId")!!
    val generated = instant(root.optString("generatedAt", ""), "generatedAt")
    val data = instant(root.optString("dataAsOf", ""), "dataAsOf")
    val expires = instant(root.optString("expiresAt", ""), "expiresAt")
    val stale = instant(root.optString("staleUntil", ""), "staleUntil")
    listOf(generated, data).forEach { require(it.epochMillis <= nowMillis) { "future timestamp" } }
    require(data.epochMillis <= generated.epochMillis && generated.epochMillis <= expires.epochMillis && expires.epochMillis <= stale.epochMillis)
    val freshness = runCatching { RankingFreshness.valueOf(root.optString("freshness")) }.getOrElse { error("invalid freshness") }
    val availability = runCatching { RankingAvailability.valueOf(root.optString("availability")) }.getOrElse { error("invalid availability") }
    val cov = coverage(obj(root, "coverage"))
    val statuses = (0 until root.optJSONArray("sourceStatuses").length()).map { val s = root.getJSONArray("sourceStatuses").getJSONObject(it); RankingSourceStatus(text(s.optString("sourceId", null), "sourceId")!!, runCatching { RankingSourceHealth.valueOf(s.optString("status")) }.getOrElse { error("invalid source status") }) }
    val itemsJson = root.optJSONArray("items") ?: error("missing items")
    val seenIds = mutableSetOf<String>(); val seenRanks = mutableSetOf<Int>(); val items = (0 until itemsJson.length()).map { i ->
        val e = itemsJson.getJSONObject(i); val id = text(e.optString("canonicalId", null), "canonicalId")!!; require(seenIds.add(id)) { "duplicate canonicalId" }
        val r = if (e.isNull("rank")) null else rank(e.optInt("rank"), "rank"); if (r != null) require(seenRanks.add(r)) { "duplicate rank" }
        val poster = nullable(e, "posterUrl"); poster?.let { val u = URI(it); require(u.scheme == "https" && !u.host.isNullOrBlank() && u.userInfo == null) { "invalid posterUrl" } }
        RankingEntry(id, r, text(e.optString("displayTitle", null), "displayTitle")!!, nullable(e, "originalTitle"), strings(e.optJSONArray("aliases"), "alias"), media(e.optString("mediaType")), if (e.isNull("releaseYear")) null else e.optInt("releaseYear"), strings(e.optJSONArray("countries"), "country"), strings(e.optJSONArray("genres"), "genre"), poster, nullable(e, "summary"), if (e.isNull("aggregateScore")) null else e.optDouble("aggregateScore"), ratings(e.optJSONArray("sourceRatings")), ranks(e.optJSONArray("sourceRanks")), coverage(obj(e, "coverage")), text(e.optString("searchTitle", null), "searchTitle")!!)
    }
    RankingSnapshot(root.getInt("schemaVersion"), requestId, boardId, filters, snapshotId, generated, data, expires, stale, freshness, availability, cov, statuses, items, nullable(root, "nextCursor"))
}
