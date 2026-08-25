package com.lovelyreader.source

import com.lovelyreader.domain.SearchResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/** User-facing discovery taxonomy backed only by verified source category pages. */
object DiscoveryCatalog {
    val primaryCategories = listOf(
        "言情",
        "现代都市",
        "玄幻奇幻",
        "武侠仙侠",
        "历史军事",
        "游戏竞技",
        "科幻世界",
        "灵异神怪",
        "美文同人"
    )

    val romanceCategories = listOf(
        "现代言情",
        "古代言情",
        "穿越架空",
        "宫闱情仇",
        "浪漫言情",
        "菁菁校园",
        "爱在职场",
        "耽美纯爱"
    )
}

/**
 * Keeps browsing page cursors and displayed-book history independent per category.
 * An empty selection deliberately does not clear history; callers reset only after
 * the source pages report that the category pool has been exhausted.
 */
class DiscoveryRotation(private val batchSize: Int = 12) {
    private val pages = mutableMapOf<Pair<String, String>, Int>()
    private val displayed = mutableMapOf<String, MutableList<NormalizedBookIdentity>>()
    private val pending = mutableMapOf<String, MutableList<SearchResult>>()
    private val exhaustedSources = mutableSetOf<Pair<String, String>>()
    private val unsupportedSources = mutableSetOf<Pair<String, String>>()
    private val supportedCategories = mutableSetOf<String>()

    fun pageFor(category: String, sourceId: String): Int = pages[category to sourceId] ?: 1

    fun advancePage(category: String, sourceId: String) {
        val key = category to sourceId
        pages[key] = pageFor(category, sourceId) + 1
        supportedCategories += category
    }

    fun markExhausted(category: String, sourceId: String) {
        supportedCategories += category
        exhaustedSources += category to sourceId
    }

    fun markUnsupported(category: String, sourceId: String) {
        unsupportedSources += category to sourceId
    }

    fun canRequest(category: String, sourceId: String): Boolean {
        val key = category to sourceId
        return key !in exhaustedSources && key !in unsupportedSources
    }

    fun hasSupportedSource(category: String): Boolean = category in supportedCategories

    fun select(
        category: String,
        candidates: List<SearchResult>,
        seed: Int,
        seenBooks: Collection<NormalizedBookIdentity> = emptyList()
    ): List<SearchResult> {
        val history = displayed.getOrPut(category) { mutableListOf() }
        val buffer = pending.getOrPut(category) { mutableListOf() }
        val currentSeen = seenBooks.toList()
        buffer.removeAll { candidate ->
            compatibleWithAny(normalizedBookIdentity(candidate), currentSeen)
        }
        SearchResultMerger.merge(candidates).forEach { candidate ->
            val identity = normalizedBookIdentity(candidate)
            if (!compatibleWithAny(identity, currentSeen) &&
                !compatibleWithAny(identity, history) &&
                !compatibleWithAny(identity, buffer.map(::normalizedBookIdentity))) {
                buffer += candidate
            }
        }
        val selected = buffer.shuffled(Random(seed)).take(batchSize)
        buffer.removeAll(selected.toSet())
        selected.forEach { history += normalizedBookIdentity(it) }
        return selected
    }

    fun reset(category: String) {
        pages.keys.removeAll { it.first == category }
        displayed.remove(category)
        pending.remove(category)
        exhaustedSources.removeAll { it.first == category }
        unsupportedSources.removeAll { it.first == category }
        supportedCategories.remove(category)
    }
}

fun normalizedTitleKey(title: String): String = title
    .trim()
    .trim('《', '》', '〈', '〉', '「', '」')
    .replace(Regex("[\\s　]+"), "")
    .lowercase()

fun normalizedAuthorKey(author: String): String = author
    .trim()
    .replace(Regex("[\\s　]+"), "")
    .lowercase()

data class NormalizedBookIdentity(val title: String, val author: String)

fun normalizedBookIdentity(result: SearchResult): NormalizedBookIdentity = NormalizedBookIdentity(
    title = normalizedTitleKey(result.title),
    author = normalizedAuthorKey(result.author)
)

/**
 * A discovery response must not be interpreted as an empty source page when a
 * provider has returned a browser-verification/interstitial document instead.
 * Keep this shared so homepage and category parsers expose the same state
 * semantics.
 */
fun looksLikeDiscoveryVerificationPage(html: String): Boolean {
    val lower = html.lowercase()
    return "challenge-form" in lower ||
        "cf-chl-" in lower ||
        "正在验证浏览器" in html ||
        "安全验证" in html ||
        "安全驗證" in html ||
        "captcha" in lower
}

/**
 * A list with an explicit empty-state message is distinguishable from a
 * missing or changed list container. This is intentionally narrow: ordinary
 * pages without a known container remain failures.
 */
fun isExplicitlyEmptyDiscoveryPage(html: String): Boolean {
    val hasRecognizedBookLink = Regex(
        "href=[\"'][^\"']*(?:/book/\\d+\\.html|txt\\d+\\.html)",
        RegexOption.IGNORE_CASE
    ).containsMatchIn(html)
    if (hasRecognizedBookLink) return false
    val text = HtmlTools.stripTags(html).replace(Regex("\\s+"), "")
    return Regex("暂无(?:小说|书籍|内容|数据)|没有找到(?:小说|书籍|内容|数据)|无相关(?:小说|书籍|内容|数据)")
        .containsMatchIn(text)
}

fun compatibleWithAny(
    candidate: NormalizedBookIdentity,
    existing: List<NormalizedBookIdentity>
): Boolean {
    val sameTitle = existing.filter { it.title == candidate.title }
    if (sameTitle.isEmpty()) return false
    if (sameTitle.any { it.author == candidate.author }) return true
    val knownAuthors = sameTitle.map { it.author }.filter { it.isNotBlank() }.distinct()
    return when {
        candidate.author.isBlank() -> knownAuthors.size <= 1
        sameTitle.any { it.author.isBlank() } -> knownAuthors.isEmpty() || knownAuthors == listOf(candidate.author)
        else -> false
    }
}

suspend fun safeCategoryBrowse(
    timeoutMillis: Long,
    block: suspend () -> CategoryBrowseResult
): CategoryBrowseResult = try {
    withTimeoutOrNull(timeoutMillis) { block() } ?: CategoryBrowseResult.Failure("请求超时")
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    CategoryBrowseResult.Failure(error::class.java.simpleName)
}

data class DiscoveryEndpoint(
    val sourceId: String,
    val load: suspend (category: String, page: Int) -> CategoryBrowseResult
)

enum class DiscoveryLoadStatus {
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILURE,
    UNSUPPORTED,
    EXHAUSTED,
    NO_NEW_ITEMS,
    SOURCE_UNAVAILABLE,
    STALE
}

data class DiscoveryLoadOutcome(
    val items: List<SearchResult>,
    val status: DiscoveryLoadStatus
)

class DiscoveryCoordinator(
    private val rotation: DiscoveryRotation,
    private val sourceHealth: SourceHealthLedger = SourceHealthLedger()
) {
    suspend fun load(
        category: String,
        sources: List<DiscoveryEndpoint>,
        seenTitles: Set<String>,
        seed: Int,
        seenBooks: Collection<NormalizedBookIdentity> = emptyList(),
        isCurrent: () -> Boolean = { true }
    ): DiscoveryLoadOutcome = coroutineScope {
        if (!isCurrent()) {
            return@coroutineScope DiscoveryLoadOutcome(emptyList(), DiscoveryLoadStatus.STALE)
        }
        val legacySeen = seenTitles.map { NormalizedBookIdentity(normalizedTitleKey(it), "") }
        val seenIdentities = legacySeen + seenBooks.map {
            NormalizedBookIdentity(normalizedTitleKey(it.title), normalizedAuthorKey(it.author))
        }
        // The pending buffer may have been filled before the user read or shelved
        // a book. Recheck the repository gate before consuming that fast path.
        if (!isCurrent()) {
            return@coroutineScope DiscoveryLoadOutcome(emptyList(), DiscoveryLoadStatus.STALE)
        }
        val buffered = rotation.select(category, emptyList(), seed, seenIdentities)
        if (buffered.isNotEmpty()) {
            return@coroutineScope DiscoveryLoadOutcome(buffered, DiscoveryLoadStatus.SUCCESS)
        }
        val requestableSources = sources.filter {
            rotation.canRequest(category, it.sourceId) && sourceHealth.canRequest(it.sourceId)
        }
        if (requestableSources.isEmpty()) {
            return@coroutineScope DiscoveryLoadOutcome(
                emptyList(),
                if (sources.any { !sourceHealth.canRequest(it.sourceId) }) {
                    DiscoveryLoadStatus.SOURCE_UNAVAILABLE
                } else if (sources.isNotEmpty() && rotation.hasSupportedSource(category)) {
                    DiscoveryLoadStatus.EXHAUSTED
                } else {
                    DiscoveryLoadStatus.UNSUPPORTED
                }
            )
        }
        val requested = requestableSources.map { endpoint ->
            async {
                val page = rotation.pageFor(category, endpoint.sourceId)
                val result = try {
                    endpoint.load(category, page)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    CategoryBrowseResult.Failure(error::class.java.simpleName)
                }
                endpoint to result
            }
        }.awaitAll()

        val successes = requested.filter { it.second is CategoryBrowseResult.Success }
        val failures = requested.filter { it.second is CategoryBrowseResult.Failure }
        requested.forEach { (endpoint, result) ->
            when (result) {
                is CategoryBrowseResult.Failure -> sourceHealth.recordFailure(endpoint.sourceId, result.reason)
                is CategoryBrowseResult.Success -> sourceHealth.recordSuccess(endpoint.sourceId)
                CategoryBrowseResult.Unsupported -> Unit
            }
        }
        if (!isCurrent()) {
            return@coroutineScope DiscoveryLoadOutcome(emptyList(), DiscoveryLoadStatus.STALE)
        }
        if (successes.isEmpty()) {
            requested.forEach { (endpoint, result) ->
                if (result is CategoryBrowseResult.Unsupported) {
                    rotation.markUnsupported(category, endpoint.sourceId)
                }
            }
            return@coroutineScope DiscoveryLoadOutcome(
                items = emptyList(),
                status = if (failures.isNotEmpty()) DiscoveryLoadStatus.FAILURE else DiscoveryLoadStatus.UNSUPPORTED
            )
        }

        val merged = SearchResultMerger.merge(
            successes.flatMap { (_, result) -> (result as CategoryBrowseResult.Success).items }
        ).filterNot { compatibleWithAny(normalizedBookIdentity(it), seenIdentities) }
        if (!isCurrent()) {
            return@coroutineScope DiscoveryLoadOutcome(emptyList(), DiscoveryLoadStatus.STALE)
        }
        requested.forEach { (endpoint, result) ->
            when (result) {
                CategoryBrowseResult.Unsupported -> rotation.markUnsupported(category, endpoint.sourceId)
                is CategoryBrowseResult.Success -> {
                    if (result.hasMore) rotation.advancePage(category, endpoint.sourceId)
                    else rotation.markExhausted(category, endpoint.sourceId)
                }
                is CategoryBrowseResult.Failure -> Unit
            }
        }
        val selected = rotation.select(category, merged, seed, seenIdentities)
        val hasMore = successes.any { (_, result) -> (result as CategoryBrowseResult.Success).hasMore }
        val partial = failures.isNotEmpty() || successes.any { (_, result) ->
            (result as CategoryBrowseResult.Success).partialFailure
        }
        DiscoveryLoadOutcome(
            items = selected,
            status = when {
                partial -> DiscoveryLoadStatus.PARTIAL_SUCCESS
                selected.isNotEmpty() -> DiscoveryLoadStatus.SUCCESS
                !hasMore -> DiscoveryLoadStatus.EXHAUSTED
                else -> DiscoveryLoadStatus.NO_NEW_ITEMS
            }
        )
    }
}

class DiscoveryRequestGate {
    private var current = 0

    fun begin(): Int = ++current

    fun isCurrent(id: Int): Boolean = id == current
}
