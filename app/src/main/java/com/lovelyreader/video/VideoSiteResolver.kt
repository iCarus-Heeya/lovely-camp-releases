package com.lovelyreader.video

import java.net.URI

interface VideoPageFetcher {
    suspend fun get(url: String): String

    /**
     * Submits an ordinary HTML form. Existing fetchers which only support GET
     * deliberately fail rather than silently changing a POST search into a GET.
     */
    suspend fun postForm(url: String, fields: Map<String, String>): String {
        throw UnsupportedOperationException("POST form fetching is not available")
    }
}

interface VideoRootStore {
    fun load(): VideoSiteRoot?
    fun save(root: VideoSiteRoot)
}

class VideoSiteResolver(
    private val pageFetcher: VideoPageFetcher,
    private val rootStore: VideoRootStore,
    private val clock: () -> Long = System::currentTimeMillis
) {
    suspend fun resolve(entryUrl: String = DEFAULT_ENTRY_URL): VideoRootResolution {
        val cachedRoot = rootStore.load()?.takeIf(::isAllowedCachedRoot)
        val entry = parseHttpsUrl(entryUrl)
            ?: return cachedOrUnavailable(cachedRoot, "片源地址格式无效。")
        val entryHost = entry.host
            ?: return cachedOrUnavailable(cachedRoot, "片源地址格式无效。")

        val entryPage = runCatching { pageFetcher.get(entry.toString()) }
            .getOrElse { return cachedOrBootstrap(cachedRoot, "暂时无法连接片源地址。") }
        val candidateUrl = extractAnchors(entryPage)
            .filter { href -> isAllowedCandidate(href, entryHost) }
            .firstOrNull()
            ?.let { candidate ->
                val url = candidate.toString()
                runCatching { pageFetcher.get(url) }
                    .getOrNull()
                    ?.takeIf(::isCataloguePage)
                    ?.let { url }
            }
            ?: return cachedOrUnavailable(cachedRoot, "片源网站暂时不可用。")

        val validatedRoot = VideoSiteRoot(
            url = candidateUrl,
            validatedAtMillis = clock()
        )
        rootStore.save(validatedRoot)
        return VideoRootResolution(
            root = validatedRoot,
            status = VideoRootResolutionStatus.RESOLVED,
            detail = "片源地址已更新。"
        )
    }

    private fun cachedOrUnavailable(
        cachedRoot: VideoSiteRoot?,
        unavailableDetail: String
    ): VideoRootResolution {
        return if (cachedRoot != null) {
            VideoRootResolution(
                root = cachedRoot,
                status = VideoRootResolutionStatus.USING_CACHED_ROOT,
                detail = "正在使用上次可用的片源地址。$unavailableDetail"
            )
        } else {
            VideoRootResolution(
                root = null,
                status = VideoRootResolutionStatus.UNAVAILABLE,
                detail = unavailableDetail
            )
        }
    }

    /** A non-persisted bootstrap keeps first installs usable when the mutable entry is blocked. */
    private fun cachedOrBootstrap(cachedRoot: VideoSiteRoot?, unavailableDetail: String): VideoRootResolution {
        if (cachedRoot != null) return cachedOrUnavailable(cachedRoot, unavailableDetail)
        return VideoRootResolution(
            root = VideoSiteRoot(BOOTSTRAP_ROOT_URL, validatedAtMillis = 0L),
            status = VideoRootResolutionStatus.USING_BOOTSTRAP_ROOT,
            detail = "正在使用备用片源地址。$unavailableDetail"
        )
    }

    private fun isAllowedCandidate(candidate: URI, entryHost: String): Boolean {
        val host = candidate.host?.lowercase() ?: return false
        return candidate.scheme.equals("https", ignoreCase = true) &&
            host != entryHost.lowercase() &&
            !isRentryHost(host) &&
            !isKnownAppDownloadHost(host)
    }

    private fun isAllowedCachedRoot(root: VideoSiteRoot): Boolean {
        val rootUrl = parseHttpsUrl(root.url) ?: return false
        val host = rootUrl.host?.lowercase() ?: return false
        return !isRentryHost(host) && !isKnownAppDownloadHost(host)
    }

    /** A reachable download announcement is not a usable catalogue root. */
    private fun isCataloguePage(page: String): Boolean {
        if (page.isBlank()) return false
        val hasForm = Regex("""(?is)<form\b[^>]*>""").containsMatchIn(page)
        val hasSearchInput = Regex(
            """(?is)<input\b(?=[^>]*\bname\s*=\s*(?:[\"']?(?:wd|q|query|keyword)[\"']?))[^>]*>"""
        ).containsMatchIn(page)
        return hasForm && hasSearchInput
    }

    private fun extractAnchors(html: String): Sequence<URI> {
        val candidates = mutableListOf<URI>()
        val elements = mutableListOf<HtmlElement>()
        var index = 0

        while (index < html.length) {
            if (html.regionMatches(index, "<!--", 0, 4, ignoreCase = false)) {
                val commentEnd = html.indexOf("-->", index + 4)
                index = if (commentEnd == -1) html.length else commentEnd + 3
                continue
            }

            if (html[index] != '<') {
                val textEnd = html.indexOf('<', index).let { found ->
                    if (found == -1) html.length else found
                }
                if (elements.lastOrNull()?.visible != false && hasVisibleText(html.substring(index, textEnd))) {
                    activeAnchor(elements)?.hasVisibleContent = true
                }
                index = textEnd
                continue
            }

            val tagEnd = findTagEnd(html, index + 1)
            if (tagEnd == -1) break
            val tag = html.substring(index + 1, tagEnd).trim()
            consumeTag(tag, elements, candidates)
            index = tagEnd + 1
        }

        return candidates.asSequence()
    }

    private fun consumeTag(
        tag: String,
        elements: MutableList<HtmlElement>,
        candidates: MutableList<URI>
    ) {
        if (tag.isBlank() || tag.startsWith("!") || tag.startsWith("?")) return
        if (tag.startsWith("/")) {
            closeElement(tag.drop(1).trim().lowercase(), elements, candidates)
            return
        }

        val selfClosing = tag.endsWith("/")
        val normalizedTag = if (selfClosing) tag.dropLast(1).trimEnd() else tag
        val nameMatch = tagNamePattern.find(normalizedTag) ?: return
        val name = nameMatch.value.lowercase()
        val attributes = normalizedTag.drop(nameMatch.range.last + 1)
        val visible = (elements.lastOrNull()?.visible ?: true) &&
            !isHidden(attributes) &&
            name !in nonVisibleContentElements
        val pendingAnchor = if (name == "a" && visible) {
            attributeValue(attributes, "href")
                ?.let(::decodeHtmlEntities)
                ?.let(::parseHttpsUrl)
                ?.let(::PendingAnchor)
        } else {
            null
        }
        elements += HtmlElement(name, visible, pendingAnchor)
        if (visible && name in renderedContentElements) {
            activeAnchor(elements)?.hasVisibleContent = true
        }

        if (selfClosing || name in voidElements) {
            closeElement(name, elements, candidates)
        }
    }

    private fun closeElement(
        name: String,
        elements: MutableList<HtmlElement>,
        candidates: MutableList<URI>
    ) {
        val matchingIndex = elements.indexOfLast { element -> element.name == name }
        if (matchingIndex == -1) return
        while (elements.size > matchingIndex) {
            val element = elements.removeAt(elements.lastIndex)
            val anchor = element.pendingAnchor
            if (anchor != null && anchor.hasVisibleContent) {
                candidates += anchor.url
            }
        }
    }

    private fun activeAnchor(elements: List<HtmlElement>): PendingAnchor? {
        return elements.asReversed().firstNotNullOfOrNull { element -> element.pendingAnchor }
    }

    private fun findTagEnd(html: String, startIndex: Int): Int {
        var quote: Char? = null
        for (index in startIndex until html.length) {
            val character = html[index]
            if (quote != null) {
                if (character == quote) quote = null
            } else {
                when (character) {
                    '\'', '\"' -> quote = character
                    '>' -> return index
                }
            }
        }
        return -1
    }

    private fun isHidden(attributes: String): Boolean {
        val style = attributeValue(attributes, "style")?.lowercase().orEmpty()
        return hasAttribute(attributes, "hidden") ||
            attributeValue(attributes, "aria-hidden")?.equals("true", ignoreCase = true) == true ||
            hiddenStylePattern.containsMatchIn(style)
    }

    private fun hasVisibleText(text: String): Boolean {
        return decodeHtmlEntities(text).replace('\u00A0', ' ').isNotBlank()
    }

    private fun hasAttribute(attributes: String, name: String): Boolean {
        return attributePattern(name).containsMatchIn(attributes)
    }

    private fun attributeValue(attributes: String, name: String): String? {
        val match = attributePattern(name).find(attributes) ?: return null
        return match.groupValues.drop(1).firstOrNull { value -> value.isNotEmpty() } ?: ""
    }

    private fun attributePattern(name: String): Regex {
        return Regex(
            """(?:^|\s)${Regex.escape(name)}(?:\s*=\s*(?:\"([^\"]*)\"|'([^']*)'|([^\s\"'=<>`]+)))?(?=\s|$)""",
            RegexOption.IGNORE_CASE
        )
    }

    private fun parseHttpsUrl(rawUrl: String): URI? {
        return runCatching { URI(rawUrl.trim()) }
            .getOrNull()
            ?.takeIf { it.isAbsolute && it.host != null && it.scheme.equals("https", ignoreCase = true) }
    }

    private fun isRentryHost(host: String): Boolean {
        return host == "rentry.la" || host.endsWith(".rentry.la")
    }

    private fun isKnownAppDownloadHost(host: String): Boolean = host == "www.88ys.app" || host.endsWith(".88ys.app")

    private fun decodeHtmlEntities(value: String): String {
        return value
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
    }

    private companion object {
        const val DEFAULT_ENTRY_URL = "https://rentry.la/88ys"
        const val BOOTSTRAP_ROOT_URL = "https://www.88ystv.com"
        val tagNamePattern = Regex("""[A-Za-z][A-Za-z0-9:-]*""")
        val hiddenStylePattern = Regex("""(?:^|;)\s*(?:display\s*:\s*none|visibility\s*:\s*hidden)\s*(?:;|$)""")
        val nonVisibleContentElements = setOf("script", "template")
        val renderedContentElements = setOf("audio", "canvas", "embed", "iframe", "img", "object", "picture", "svg", "video")
        val voidElements = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr")
    }

    private data class HtmlElement(
        val name: String,
        val visible: Boolean,
        val pendingAnchor: PendingAnchor?
    )

    private data class PendingAnchor(
        val url: URI,
        var hasVisibleContent: Boolean = false
    )
}
