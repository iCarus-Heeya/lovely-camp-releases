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
private fun optionalText(o: JSONObject, n: String): String? {
    if (!o.has(n) || o.isNull(n)) return null
    return text(o.get(n) as? String ?: error("$n must be text"), n, false)
}
private fun requiredText(o: JSONObject, n: String): String = text(o.get(n) as? String ?: error("missing or invalid $n"), n)!!
private fun nullable(o: JSONObject, n: String): String? = optionalText(o, n)
private fun media(s: String) = runCatching { RankingMediaType.valueOf(s) }.getOrElse { error("invalid mediaType") }
private fun instant(s: String, name: String): InstantLike = runCatching { InstantLike(Instant.parse(s).toEpochMilli()) }.getOrElse { error("invalid $name") }
private fun number(o: JSONObject, n: String, integer: Boolean = false): Double {
    val v = o.get(n); require(v is Number) { "$n must be a JSON number" }; val d = v.toDouble(); require(d.isFinite()) { "invalid $n" }
    if (integer) require(d == d.toLong().toDouble()) { "$n must be integer" }; return d
}
private fun intNumber(o: JSONObject, n: String, min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE): Int {
    val d = number(o, n, true); require(d >= min && d <= max) { "$n out of range" }; return d.toInt()
}
private fun rank(v: Int?, name: String): Int? { if (v != null) require(v >= 1) { "invalid $name" }; return v }
private fun array(o: JSONObject, n: String): JSONArray? { if (!o.has(n) || o.isNull(n)) return null; return o.get(n) as? JSONArray ?: error("$n must be array") }
private fun strings(a: JSONArray?, name: String): List<String> = (0 until (a?.length() ?: 0)).map { text(a!!.get(it) as? String ?: error("$name must be text"), name)!! }
private fun coverage(o: JSONObject): RankingCoverage {
    val count = intNumber(o, "sourceCount", 0)
    val ids = strings(o.optJSONArray("coveredSourceIds"), "coveredSourceId")
    require(count == ids.size || count > ids.size) { "invalid coverage" }
    val complete = if (!o.has("complete")) false else o.get("complete") as? Boolean ?: error("complete must be boolean")
    return RankingCoverage(count, ids, complete)
}
private fun ratings(a: JSONArray?): List<SourceRating> = (0 until (a?.length() ?: 0)).map {
    val o = a!!.getJSONObject(it); SourceRating(requiredText(o, "sourceId"), if (o.isNull("value")) null else number(o, "value"), if (o.isNull("scaleMax")) null else number(o, "scaleMax"), if (o.isNull("voteCount")) null else intNumber(o, "voteCount", 0))
}
private fun ranks(a: JSONArray?): List<SourceRank> = (0 until (a?.length() ?: 0)).map {
    val o = a!!.getJSONObject(it); SourceRank(requiredText(o, "sourceId"), if (o.isNull("rank")) null else rank(intNumber(o, "rank", 1), "rank"), if (o.isNull("previousRank")) null else rank(intNumber(o, "previousRank", 1), "previousRank"), if (o.isNull("rankDelta")) null else intNumber(o, "rankDelta"), if (!o.has("isNew")) false else o.get("isNew") as? Boolean ?: error("isNew must be boolean"))
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
    require(number(root, "schemaVersion", true).toInt() == expectedSchemaVersion) { "unsupported schema" }
    val requestId = requiredText(root, "requestId")
    val boardId = requiredText(root, "boardId")
    val f = obj(root, "filters")
    val filters = RankingFilters(nullable(f, "period"), media(requiredText(f, "mediaType")), nullable(f, "region"), if (f.isNull("year")) null else intNumber(f, "year", 1800, 2200), nullable(f, "genre"), nullable(f, "language"), intNumber(f, "limit", 1, 50), nullable(f, "cursor"))
    require(filters.limit in 1..50)
    val snapshotId = requiredText(root, "snapshotId")
    val generated = instant(requiredText(root, "generatedAt"), "generatedAt")
    val data = instant(requiredText(root, "dataAsOf"), "dataAsOf")
    val expires = instant(requiredText(root, "expiresAt"), "expiresAt")
    val stale = instant(requiredText(root, "staleUntil"), "staleUntil")
    listOf(generated, data).forEach { require(it.epochMillis <= nowMillis) { "future timestamp" } }
    require(data.epochMillis <= generated.epochMillis && generated.epochMillis <= expires.epochMillis && expires.epochMillis <= stale.epochMillis)
    val freshness = runCatching { RankingFreshness.valueOf(root.optString("freshness")) }.getOrElse { error("invalid freshness") }
    val availability = runCatching { RankingAvailability.valueOf(root.optString("availability")) }.getOrElse { error("invalid availability") }
    val cov = coverage(obj(root, "coverage"))
    val statusesJson = root.get("sourceStatuses") as? JSONArray ?: error("missing sourceStatuses")
    val statuses = (0 until statusesJson.length()).map { val s = statusesJson.getJSONObject(it); val errorCode = optionalText(s, "errorCode")?.let { code -> runCatching { RankingErrorCode.valueOf(code) }.getOrNull() }; RankingSourceStatus(requiredText(s, "sourceId"), runCatching { RankingSourceHealth.valueOf(requiredText(s, "status")) }.getOrElse { error("invalid source status") }, errorCode) }
    val itemsJson = root.optJSONArray("items") ?: error("missing items")
    val seenIds = mutableSetOf<String>(); val seenRanks = mutableSetOf<Int>(); val items = (0 until itemsJson.length()).map { i ->
        val e = itemsJson.getJSONObject(i); val id = requiredText(e, "canonicalId"); require(seenIds.add(id)) { "duplicate canonicalId" }
        val r = if (e.isNull("rank")) null else rank(intNumber(e, "rank", 1), "rank"); if (r != null) require(seenRanks.add(r)) { "duplicate rank" }
        val poster = nullable(e, "posterUrl"); poster?.let { val u = URI(it); require(u.scheme.equals("https", true) && !u.host.isNullOrBlank() && u.userInfo == null && isPublicHost(u.host)) { "invalid posterUrl" } }
        RankingEntry(id, r, requiredText(e, "displayTitle"), nullable(e, "originalTitle"), strings(array(e, "aliases"), "alias"), media(requiredText(e, "mediaType")), if (e.isNull("releaseYear")) null else intNumber(e, "releaseYear", 1800, 2200), strings(array(e, "countries"), "country"), strings(array(e, "genres"), "genre"), poster, nullable(e, "summary"), if (e.isNull("aggregateScore")) null else number(e, "aggregateScore"), ratings(array(e, "sourceRatings")), ranks(array(e, "sourceRanks")), coverage(obj(e, "coverage")), requiredText(e, "searchTitle"))
    }
    RankingSnapshot(root.getInt("schemaVersion"), requestId, boardId, filters, snapshotId, generated, data, expires, stale, freshness, availability, cov, statuses, items, nullable(root, "nextCursor"))
}

private fun isPublicHost(host: String): Boolean {
    val lower = host.lowercase().removePrefix("[").removeSuffix("]")
    if (lower == "localhost" || lower.endsWith(".localhost") || lower.endsWith(".local")) return false
    if (lower.contains(":")) {
        return !(lower == "::" || lower == "::1" || lower.startsWith("fe8") || lower.startsWith("fe9") || lower.startsWith("fea") || lower.startsWith("feb") || lower.startsWith("fc") || lower.startsWith("fd") || lower.startsWith("ff") || lower.startsWith("2001:db8:"))
    }
    if (lower.contains('.')) {
        val parts = lower.split('.').map { it.toLongOrNull() ?: return true }
        if (parts.isEmpty() || parts.size > 4 || parts.any { it !in 0..255 }) return true
        val value = when (parts.size) { 1 -> parts[0]; 2 -> (parts[0] shl 24) or parts[1]; 3 -> (parts[0] shl 24) or (parts[1] shl 16) or parts[2]; else -> (parts[0] shl 24) or (parts[1] shl 16) or (parts[2] shl 8) or parts[3] }
        val a = ((value ushr 24) and 255).toInt(); val b = ((value ushr 16) and 255).toInt()
        return a != 0 && a != 10 && a != 127 && !(a == 169 && b == 254) && !(a == 172 && b in 16..31) && !(a == 192 && b == 168) && !(a == 100 && b in 64..127) && a !in 224..255
    }
    if (lower.any { it in "[]" }) return false
    val octets = lower.split('.').mapNotNull { it.toIntOrNull() }
    if (octets.size != 4 || octets.any { it !in 0..255 }) return true
    val a = octets[0]; val b = octets[1]
    return a != 0 && a != 10 && a != 127 && !(a == 169 && b == 254) && !(a == 172 && b in 16..31) && !(a == 192 && b == 168) && !(a == 100 && b in 64..127) && a !in 224..255
}
