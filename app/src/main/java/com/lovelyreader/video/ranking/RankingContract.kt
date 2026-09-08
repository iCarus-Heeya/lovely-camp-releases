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
private const val MAX_CAPABILITY_OPTIONS = 64
private fun capabilityOptions(root: JSONObject, name: String): List<RankingFilterOption> {
    val values = array(root, name) ?: return emptyList()
    require(values.length() <= MAX_CAPABILITY_OPTIONS) { "too many $name" }
    val ids = mutableSetOf<String>()
    return (0 until values.length()).map { index ->
        val option = values.getJSONObject(index)
        val id = requiredText(option, "id")
        val label = requiredText(option, "label")
        require(ids.add(id)) { "duplicate $name id" }
        RankingFilterOption(id, label)
    }
}
private fun capabilityYears(root: JSONObject): List<Int> {
    val values = array(root, "years") ?: return emptyList()
    require(values.length() <= MAX_CAPABILITY_OPTIONS) { "too many years" }
    val years = (0 until values.length()).map { index ->
        val value = values.get(index)
        require(value is Number) { "years must be numbers" }
        val number = value.toDouble()
        require(number.isFinite() && number == number.toInt().toDouble()) { "years must be integers" }
        number.toInt().also { require(it in 1800..2200) { "year out of range" } }
    }
    require(years.distinct().size == years.size) { "duplicate year" }
    return years
}
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

/** Parse the versioned capability handshake before any filter is rendered. */
fun parseRankingCapabilities(raw: String, expectedSchemaVersion: Int = 1): Result<RankingCapabilities> = runCatching {
    val root = JSONObject(raw)
    require(root.optInt("schemaVersion", -1) == expectedSchemaVersion) { "unsupported capabilities schema" }
    val boardsJson = root.optJSONArray("boards") ?: JSONArray()
    require(boardsJson.length() <= 64) { "too many boards" }
    val boardIds = mutableSetOf<String>()
    val boards = (0 until boardsJson.length()).map { index ->
        val board = boardsJson.getJSONObject(index)
        val id = requiredText(board, "id")
        val label = requiredText(board, "label")
        require(boardIds.add(id)) { "duplicate board id" }
        RankingBoardOption(id, label)
    }
    val mediaJson = root.optJSONArray("mediaTypes") ?: JSONArray()
    require(mediaJson.length() <= RankingMediaType.entries.size) { "too many media types" }
    val mediaTypes = (0 until mediaJson.length()).map { index ->
        media(mediaJson.getString(index))
    }.also { require(it.distinct().size == it.size) { "duplicate media type" } }
    RankingCapabilities(
        schemaVersion = expectedSchemaVersion,
        supportedBoards = boards,
        supportedMediaTypes = mediaTypes,
        supportedPeriods = capabilityOptions(root, "periods"),
        supportedRegions = capabilityOptions(root, "regions"),
        supportedYears = capabilityYears(root),
        supportedGenres = capabilityOptions(root, "genres"),
        supportedLanguages = capabilityOptions(root, "languages")
    )
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
        // The shared source-site poster loader is not a ranking-approved DNS-bound transport.
        // Keep valid metadata but emit placeholders until that independent boundary exists.
        // Disk cache restores use this parser too, so older named URLs cannot bypass the gate.
        RankingEntry(id, r, requiredText(e, "displayTitle"), nullable(e, "originalTitle"), strings(array(e, "aliases"), "alias"), media(requiredText(e, "mediaType")), if (e.isNull("releaseYear")) null else intNumber(e, "releaseYear", 1800, 2200), strings(array(e, "countries"), "country"), strings(array(e, "genres"), "genre"), null, nullable(e, "summary"), if (e.isNull("aggregateScore")) null else number(e, "aggregateScore"), ratings(array(e, "sourceRatings")), ranks(array(e, "sourceRanks")), coverage(obj(e, "coverage")), requiredText(e, "searchTitle"))
    }
    RankingSnapshot(root.getInt("schemaVersion"), requestId, boardId, filters, snapshotId, generated, data, expires, stale, freshness, availability, cov, statuses, items, nullable(root, "nextCursor"))
}

private fun isPublicHost(host: String): Boolean {
    val lower = host.lowercase().removePrefix("[").removeSuffix("]")
    if (lower == "localhost" || lower.endsWith(".localhost") || lower.endsWith(".local")) return false
    fun publicV4(v: Long): Boolean { val a = (v ushr 24).toInt(); val b = (v ushr 16 and 255).toInt(); return a != 0 && a != 10 && a != 127 && !(a == 169 && b == 254) && !(a == 172 && b in 16..31) && !(a == 192 && b == 168) && !(a == 100 && b in 64..127) && a !in 224..255 }
    fun v4(s: String): Long? { val p=s.split('.'); if(p.any{it.isEmpty()}) return null; val n=p.map{it.toLongOrNull()?:return null}; if(n.size==1)return n[0].takeIf{it in 0..0xffffffffL}; if(n.size>4||n.any{it !in 0..255})return null; return when(n.size){2->(n[0] shl 24) or n[1];3->(n[0] shl 24) or (n[1] shl 16) or n[2];else->(n[0] shl 24) or (n[1] shl 16) or (n[2] shl 8) or n[3]} }
    if (lower.contains(':')) {
        val halves = lower.split("::")
        if (halves.size > 2) return false
        fun expand(groups: List<String>): List<Long>? = groups.flatMap { g ->
            if (g.contains('.')) { val v = v4(g) ?: return null; listOf(v ushr 16, v and 65535) }
            else listOf((g.toLongOrNull(16)?.takeIf { it in 0..0xffffL }) ?: return null)
        }
        val left = expand(if (halves[0].isEmpty()) emptyList() else halves[0].split(':')) ?: return false
        val right = expand(if (halves.size == 2 && halves[1].isNotEmpty()) halves[1].split(':') else emptyList()) ?: return false
        if (halves.size == 2 && halves[0].contains('.')) return false
        val rawGroups = (if (halves[0].isEmpty()) emptyList() else halves[0].split(':')) + (if (halves.size == 2 && halves[1].isNotEmpty()) halves[1].split(':') else emptyList())
        if (rawGroups.withIndex().any { it.value.contains('.') && it.index != rawGroups.lastIndex }) return false
        val missing = 8 - left.size - right.size
        if ((halves.size == 1 && missing != 0) || (halves.size == 2 && missing < 1)) return false
        val h = left + List(missing) { 0L } + right
        if (h.take(5).all { it == 0L } && h[5] == 65535L) return publicV4((h[6] shl 16) or h[7])
        if (h.take(6).all { it == 0L }) return publicV4((h[6] shl 16) or h[7])
        return !(h.all { it == 0L } || (h.take(7).all { it == 0L } && h[7] == 1L) || h[0] in 0xfc00..0xfdff || h[0] in 0xfe80..0xfebf || h[0] in 0xff00..0xffff || (h[0] == 0x2001L && h[1] == 0xdb8L))
    }
    if (lower.all{it.isDigit()||it=='.'}) return v4(lower)?.let(::publicV4) ?: false
    return true
}
