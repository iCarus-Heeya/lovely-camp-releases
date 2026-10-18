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
// The bundled public snapshot exposes every year in its 1950-current scope.
// 77 years is still a bounded handshake, but the former 64-item ceiling made
// capabilities fail closed as soon as the catalogue became genuinely broad.
private const val MAX_CAPABILITY_OPTIONS = 128
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
    val count = intNumber(o, "sourceCount", 1)
    require(o.has("coveredSourceIds") && !o.isNull("coveredSourceIds")) { "missing coveredSourceIds" }
    val covered = o.get("coveredSourceIds")
    require(covered is JSONArray) { "coveredSourceIds must be array" }
    val ids = strings(covered, "coveredSourceId")
    require(ids.distinct().size == ids.size) { "duplicate covered source id" }
    require(count >= ids.size) { "invalid coverage" }
    val complete = if (!o.has("complete")) false else o.get("complete") as? Boolean ?: error("complete must be boolean")
    if (complete) require(count == ids.size) { "complete coverage must list every source" }
    return RankingCoverage(count, ids, complete)
}

private fun metadataCoverage(o: JSONObject): RankingMetadataCoverage {
    val total = intNumber(o, "total", 0)
    fun count(name: String): Int = intNumber(o, name, 0, total)
    return RankingMetadataCoverage(
        total = total,
        chineseTitle = count("chineseTitle"),
        summary = count("summary"),
        poster = count("poster"),
        region = count("region"),
        rating = count("rating")
    )
}
private fun aggregateScore(o: JSONObject, name: String): Double? {
    if (!o.has(name) || o.isNull(name)) return null
    return number(o, name).also { value ->
        require(value in RANKING_AGGREGATE_SCORE_MIN..RANKING_AGGREGATE_SCORE_MAX) { "$name out of range" }
    }
}

private fun optionalFiniteNumber(o: JSONObject, name: String): Double? {
    if (!o.has(name) || o.isNull(name)) return null
    return number(o, name)
}

private fun ratings(a: JSONArray?): List<SourceRating> {
    val ids = mutableSetOf<String>()
    return (0 until (a?.length() ?: 0)).map {
        val o = a!!.getJSONObject(it)
        val sourceId = requiredText(o, "sourceId")
        require(ids.add(sourceId)) { "duplicate source rating" }
        val value = if (o.isNull("value")) null else number(o, "value")
        val scaleMax = if (o.isNull("scaleMax")) null else number(o, "scaleMax").also { require(it > 0.0) { "scaleMax must be positive" } }
        if (value != null) {
            val max = scaleMax ?: RANKING_AGGREGATE_SCORE_MAX
            require(value in RANKING_AGGREGATE_SCORE_MIN..max) { "rating exceeds scale" }
        }
        SourceRating(sourceId, value, scaleMax, if (o.isNull("voteCount")) null else intNumber(o, "voteCount", 0))
    }
}

private fun ranks(a: JSONArray?): List<SourceRank> {
    val ids = mutableSetOf<String>()
    return (0 until (a?.length() ?: 0)).map {
        val o = a!!.getJSONObject(it)
        val sourceId = requiredText(o, "sourceId")
        require(ids.add(sourceId)) { "duplicate source rank" }
        val currentRank = if (o.isNull("rank")) null else rank(intNumber(o, "rank", 1), "rank")
        val previousRank = if (o.isNull("previousRank")) null else rank(intNumber(o, "previousRank", 1), "previousRank")
        val rankDelta = if (o.isNull("rankDelta")) null else intNumber(o, "rankDelta")
        val isNew = if (o.has("isNew")) o.get("isNew") as? Boolean ?: error("isNew must be boolean") else previousRank == null
        if (isNew) {
            require(previousRank == null && rankDelta == null) { "new rank cannot have history" }
        } else {
            require(previousRank != null) { "non-new rank must have previousRank" }
        }
        if (rankDelta != null) {
            require(currentRank != null && previousRank != null) { "rankDelta requires current and previous rank" }
            require(rankDelta == previousRank - currentRank) { "rankDelta is inconsistent" }
        }
        if (previousRank != null) require(currentRank != null) { "previousRank requires current rank" }
        SourceRank(sourceId, currentRank, previousRank, rankDelta, isNew)
    }
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
    val statusIds = mutableSetOf<String>()
    val statuses = (0 until statusesJson.length()).map {
        val s = statusesJson.getJSONObject(it)
        val sourceId = requiredText(s, "sourceId")
        require(statusIds.add(sourceId)) { "duplicate source status" }
        val errorCode = optionalText(s, "errorCode")?.let { code ->
            runCatching { RankingErrorCode.valueOf(code) }.getOrElse { error("invalid errorCode") }
        }
        val health = runCatching { RankingSourceHealth.valueOf(requiredText(s, "status")) }
            .getOrElse { error("invalid source status") }
        if (health == RankingSourceHealth.AVAILABLE) {
            require(errorCode == null) { "available source cannot carry an error code" }
        } else {
            require(errorCode != null) { "unavailable source must carry an error code" }
        }
        RankingSourceStatus(sourceId, health, errorCode)
    }
    require(cov.coveredSourceIds.all(statusIds::contains)) { "coverage source has no status" }
    val statusById = statuses.associateBy { it.sourceId }
    require(cov.coveredSourceIds.all { statusById[it]?.status == RankingSourceHealth.AVAILABLE }) {
        "coverage includes unavailable source"
    }
    require(statusIds.size <= cov.sourceCount) { "too many source statuses" }
    val itemsJson = root.optJSONArray("items") ?: error("missing items")
    val seenIds = mutableSetOf<String>(); val seenRanks = mutableSetOf<Int>(); val items = (0 until itemsJson.length()).map { i ->
        val e = itemsJson.getJSONObject(i); val id = requiredText(e, "canonicalId"); require(seenIds.add(id)) { "duplicate canonicalId" }
        val r = if (e.isNull("rank")) null else rank(intNumber(e, "rank", 1), "rank"); if (r != null) require(seenRanks.add(r)) { "duplicate rank" }
        val poster = nullable(e, "posterUrl")?.let { value ->
            if (value.startsWith("asset://")) {
                require(value.startsWith("asset://ranking/posters/") &&
                    !value.contains("..") && !value.contains('\\') && value.length <= 240) {
                    "invalid ranking poster asset"
                }
                value
            } else {
                val u = URI(value)
                require(u.scheme.equals("https", true) && !u.host.isNullOrBlank() && u.userInfo == null && isPublicHost(u.host)) { "invalid posterUrl" }
                // Release snapshots may reference only the public metadata CDNs
                // used by the catalog builder. Unknown hosts remain placeholders
                // until a dedicated image transport is explicitly approved.
                value.takeIf { isApprovedRankingPosterHost(u.host) }
            }
        }
        val itemCoverage = coverage(obj(e, "coverage"))
        require(itemCoverage.sourceCount <= cov.sourceCount) { "item coverage exceeds snapshot source count" }
        require(itemCoverage.coveredSourceIds.all(cov.coveredSourceIds::contains)) { "item coverage exceeds snapshot coverage" }
        val itemRatings = ratings(array(e, "sourceRatings"))
        val itemRanks = ranks(array(e, "sourceRanks"))
        require(itemRatings.all { it.sourceId in itemCoverage.coveredSourceIds }) { "rating source is not covered" }
        require(itemRanks.all { it.sourceId in itemCoverage.coveredSourceIds }) { "rank source is not covered" }
        RankingEntry(id, r, requiredText(e, "displayTitle"), nullable(e, "originalTitle"), strings(array(e, "aliases"), "alias"), media(requiredText(e, "mediaType")), if (e.isNull("releaseYear")) null else intNumber(e, "releaseYear", 1800, 2200), strings(array(e, "countries"), "country"), strings(array(e, "genres"), "genre"), poster, nullable(e, "summary"), if (e.isNull("aggregateScore")) null else aggregateScore(e, "aggregateScore"), itemRatings, itemRanks, itemCoverage, requiredText(e, "searchTitle"), optionalFiniteNumber(e, "rankingScore"))
    }
    val allSourcesAvailable = cov.complete && cov.sourceCount == statuses.size &&
        statuses.all { status -> status.status == RankingSourceHealth.AVAILABLE }
    val hasUnavailableSource = statuses.any { status -> status.status != RankingSourceHealth.AVAILABLE }
    when (availability) {
        RankingAvailability.AVAILABLE -> require(items.isNotEmpty() && allSourcesAvailable) {
            "available snapshot must contain entries and complete healthy sources"
        }
        RankingAvailability.PARTIAL -> require(items.isNotEmpty() && !cov.complete && hasUnavailableSource) {
            "partial snapshot must contain entries and an incomplete unhealthy source set"
        }
        RankingAvailability.EMPTY -> require(items.isEmpty() && allSourcesAvailable) {
            "empty snapshot must contain no entries and no unavailable sources"
        }
        RankingAvailability.UNAVAILABLE -> require(items.isEmpty() && !cov.complete && hasUnavailableSource) {
            "unavailable snapshot must contain no entries and an unavailable source"
        }
    }
    val methodologyVersion = optionalText(root, "methodologyVersion")
    val identityMapVersion = optionalText(root, "identityMapVersion")
    if (items.any { it.aggregateScore != null }) {
        require(!methodologyVersion.isNullOrBlank()) { "aggregate score requires methodologyVersion" }
        require(!identityMapVersion.isNullOrBlank()) { "aggregate score requires identityMapVersion" }
    }
    when (freshness) {
        RankingFreshness.FRESH -> require(nowMillis < expires.epochMillis) { "freshness is inconsistent" }
        RankingFreshness.STALE -> require(nowMillis >= expires.epochMillis && nowMillis <= stale.epochMillis) { "freshness is inconsistent" }
        RankingFreshness.EXPIRED -> require(nowMillis > stale.epochMillis) { "freshness is inconsistent" }
    }
    val totalCount = if (!root.has("totalCount") || root.isNull("totalCount")) null else intNumber(root, "totalCount", 0)
    val metadata = if (!root.has("metadataCoverage") || root.isNull("metadataCoverage")) null else metadataCoverage(obj(root, "metadataCoverage"))
    RankingSnapshot(root.getInt("schemaVersion"), requestId, boardId, filters, snapshotId, generated, data, expires, stale, freshness, availability, cov, statuses, items, nullable(root, "nextCursor"), methodologyVersion, identityMapVersion, totalCount, metadata)
}

private fun isApprovedRankingPosterHost(host: String): Boolean {
    val normalized = host.lowercase().trimEnd('.')
    return normalized == "static.tvmaze.com" ||
        normalized == "upload.wikimedia.org" ||
        normalized == "thumb.wikimedia.org" ||
        normalized == "commons.wikimedia.org" ||
        normalized == "m.media-amazon.com"
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
