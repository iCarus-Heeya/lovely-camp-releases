package com.lovelyreader.video

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * A conservative adapter for site pages exposed by the currently validated root.
 * It only follows same-origin HTML pages and returns empty/null when the page does
 * not expose the ordinary links and data attributes needed for a result.
 */
class DefaultVideoSiteAdapter(
    private val pageFetcher: VideoPageFetcher
) : VideoSiteAdapter {
    override suspend fun search(root: VideoSiteRoot, query: String): List<VideoTitle> {
        val rootUrl = validRoot(root) ?: return emptyList()
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return emptyList()

        val rootPage = fetch(rootUrl) ?: return emptyList()
        val search = searchRequest(rootUrl, parse(rootPage)) ?: return emptyList()
        val resultPage = when (search.method) {
            SearchMethod.POST -> runCatching {
                pageFetcher.postForm(search.action.toString(), mapOf(search.parameter to trimmedQuery))
            }.getOrNull()
            SearchMethod.GET -> {
                val searchUrl = appendQuery(search.action, search.parameter, trimmedQuery)
                    ?.takeIf { isSameOrigin(rootUrl, it) }
                    ?: return emptyList()
                fetch(searchUrl)
            }
        } ?: return emptyList()

        val resultDocument = parse(resultPage)
        val titles = resultDocument.anchors.mapNotNull { anchor ->
            if (!isTitleMarker(anchor.attributes) && !is88ysTitleCard(anchor)) return@mapNotNull null
            val detailUrl = rootBoundUrl(rootUrl, anchor.href) ?: return@mapNotNull null
            val name = firstNonBlank(
                anchor.attributes["data-video-title"],
                anchor.attributes["data-title"],
                anchor.attributes["title"],
                anchor.text
            ) ?: return@mapNotNull null
            val card = searchCardMetadata(resultPage, anchor.href)
            VideoTitle(
                id = detailUrl.toString(),
                name = name,
                detailUrl = detailUrl.toString(),
                posterUrl = publicPosterUrl(
                    rootUrl,
                    firstNonBlank(anchor.attributes["data-poster"], anchor.attributes["data-image"], card.poster)
                )?.toString(),
                summary = firstNonBlank(anchor.attributes["data-summary"], anchor.attributes["data-description"], card.summary),
                releaseInfo = card.releaseInfo,
                castInfo = card.castInfo,
                categoryInfo = card.categoryInfo,
                updateInfo = card.updateInfo
            )
        }.distinctBy(VideoTitle::detailUrl)
        return titles
    }

    override suspend fun loadDetail(root: VideoSiteRoot, titleUrl: String): VideoTitleDetail? {
        val rootUrl = validRoot(root) ?: return null
        val detailUrl = rootBoundUrl(rootUrl, titleUrl) ?: return null
        val page = fetch(detailUrl) ?: return null
        val document = parse(page)
        val titleTag = document.tags.firstOrNull { tag ->
            tag.visible && (tag.attributes.containsKey("data-video-title") ||
                hasClassToken(tag.attributes["class"], "video-title") ||
                hasClassToken(tag.attributes["class"], "drama-title") || tag.name == "h1")
        }
        val titleName = titleTag?.let { tag ->
            firstNonBlank(tag.attributes["data-video-title"], tag.attributes["data-title"], tag.attributes["title"], tag.text)
        } ?: headingText(page) ?: return null

        val title = VideoTitle(
            id = detailUrl.toString(),
            name = titleName,
            detailUrl = detailUrl.toString(),
            posterUrl = rootBoundUrl(rootUrl, titleTag?.attributes?.get("data-poster") ?: titleTag?.attributes?.get("data-image"))?.toString(),
            summary = firstNonBlank(titleTag?.attributes?.get("data-summary"), titleTag?.attributes?.get("data-description"))
        )
        val genericSources = document.anchors.mapIndexedNotNull { index, anchor ->
            if (!isSourceMarker(anchor.attributes)) return@mapIndexedNotNull null
            val sourceUrl = rootBoundUrl(
                rootUrl,
                anchor.attributes["data-source-url"] ?: anchor.href
            ) ?: return@mapIndexedNotNull null
            val label = firstNonBlank(
                anchor.attributes["data-source"],
                anchor.attributes["data-source-label"],
                anchor.attributes["title"],
                anchor.text
            ) ?: return@mapIndexedNotNull null
            val sourceKey = firstNonBlank(anchor.attributes["data-source-id"], anchor.attributes["data-id"])
                ?: index.toString()
            VideoSource(
                id = "${title.id}#source-$sourceKey",
                titleId = title.id,
                label = label,
                url = sourceUrl.toString()
            )
        }.distinctBy { source -> source.url }
        val sources = genericSources.ifEmpty {
            extract88ysSources(page, detailUrl, title.id)
        }

        return if (sources.isEmpty()) null else VideoTitleDetail(title, sources)
    }

    override suspend fun loadEpisodes(root: VideoSiteRoot, source: VideoSource): List<VideoEpisode> {
        val rootUrl = validRoot(root) ?: return emptyList()
        val sourceUrl = rootBoundUrl(rootUrl, source.url) ?: return emptyList()
        val page = fetch(withoutFragment(sourceUrl)) ?: return emptyList()
        val playlistId = sourceUrl.fragment?.takeIf(String::isNotBlank)
        if (playlistId != null) {
            return extract88ysEpisodes(page, playlistId, source)
        }

        return parse(page).anchors.mapNotNull { anchor ->
            if (!isEpisodeMarker(anchor.attributes)) return@mapNotNull null
            val episodeUrl = rootBoundUrl(rootUrl, anchor.attributes["data-episode-url"] ?: anchor.href)
                ?: return@mapNotNull null
            val label = firstNonBlank(
                anchor.attributes["data-episode"],
                anchor.attributes["data-episode-label"],
                anchor.attributes["title"],
                anchor.text
            ) ?: return@mapNotNull null
            VideoEpisode(
                id = "${source.id}#episode-${firstNonBlank(anchor.attributes["data-episode-id"], anchor.attributes["data-id"]) ?: episodeUrl}",
                sourceId = source.id,
                label = label,
                url = episodeUrl.toString(),
                position = 0,
                titleId = source.titleId
            )
        }.distinctBy(VideoEpisode::url).mapIndexed { index, episode -> episode.copy(position = index) }.toList()
    }

    override suspend fun loadMedia(root: VideoSiteRoot, episode: VideoEpisode): VideoMediaLink? {
        val rootUrl = validRoot(root) ?: return null
        val episodeUrl = rootBoundUrl(rootUrl, episode.url) ?: return null
        val page = fetch(episodeUrl) ?: return null
        val document = parse(page)
        val media = document.tags.firstNotNullOfOrNull(::mediaUrl) ?: publicConfiguredMediaUrl(page)
        if (media != null) {
            return VideoMediaLink(
                playbackUrl = media.toString(),
                directMp4Url = media.toString().takeIf(::isDirectMp4)
            )
        }
        // The ordinary episode page is the provider's public player entry point.
        // Keep the opaque player value untouched; the app delegates playback to
        // that player rather than decoding or extracting it.
        if (containsSitePlayerDeclaration(page) || document.tags.any(::isPlayerContainer)) {
            return VideoMediaLink(
                playbackUrl = episodeUrl.toString(),
                playbackMode = VideoPlaybackMode.SITE_PLAYER
            )
        }
        return null
    }

    override fun isDirectMp4(url: String): Boolean {
        val parsed = parseHttps(url) ?: return false
        return parsed.path?.endsWith(".mp4", ignoreCase = true) == true
    }

    private suspend fun fetch(url: URI): String? = runCatching { pageFetcher.get(url.toString()) }.getOrNull()

    private fun validRoot(root: VideoSiteRoot): URI? = parseHttps(root.url)

    private fun rootBoundUrl(root: URI, rawUrl: String?): URI? {
        val raw = rawUrl?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val resolved = runCatching { root.resolve(decodeEntities(raw)) }.getOrNull() ?: return null
        return resolved.takeIf { isSameOrigin(root, it) }
    }

    /** Search cards use a public CDN for lazy poster images, unlike HTML detail links. */
    private fun publicPosterUrl(root: URI, rawUrl: String?): URI? {
        val raw = rawUrl?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val resolved = runCatching { root.resolve(decodeEntities(raw)) }.getOrNull() ?: return null
        return parseHttps(resolved.toString())?.takeIf { uri -> isPublicHost(uri.host) }
    }

    private fun parseHttps(value: String): URI? = runCatching { URI(value.trim()) }.getOrNull()
        ?.takeIf { uri ->
            uri.isAbsolute && uri.host != null && uri.userInfo == null && uri.scheme.equals("https", ignoreCase = true)
        }

    private fun isSameOrigin(root: URI, candidate: URI): Boolean {
        val safeCandidate = parseHttps(candidate.toString()) ?: return false
        return root.scheme.equals(safeCandidate.scheme, ignoreCase = true) &&
            root.host.equals(safeCandidate.host, ignoreCase = true) &&
            effectivePort(root) == effectivePort(safeCandidate)
    }

    private fun effectivePort(uri: URI): Int = if (uri.port == -1) 443 else uri.port

    private fun isPublicHost(host: String?): Boolean {
        val normalized = host?.lowercase()?.trimEnd('.') ?: return false
        return normalized !in setOf("localhost", "::1") && !normalized.endsWith(".local") &&
            !normalized.startsWith("127.") && !normalized.startsWith("10.") &&
            !normalized.startsWith("192.168.") &&
            !normalized.matches(Regex("""172\.(1[6-9]|2[0-9]|3[0-1])\..*"""))
    }

    private fun searchRequest(root: URI, document: ParsedDocument): SearchRequest? {
        val form = document.tags.firstOrNull { tag ->
            tag.name == "form" && tag.visible && tag.attributes["method"].orEmpty().let { method ->
                method.isBlank() || method.equals("get", ignoreCase = true) || method.equals("post", ignoreCase = true)
            }
        } ?: return null
        val action = rootBoundUrl(root, form.attributes["action"] ?: root.toString()) ?: return null
        val input = document.tags.firstOrNull { tag ->
            tag.visible && tag.name == "input" &&
                tag.attributes["name"].orEmpty().isNotBlank() &&
                tag.attributes["type"].orEmpty().let { type -> type.isBlank() || type.equals("search", true) || type.equals("text", true) }
        } ?: return null
        return SearchRequest(
            action,
            input.attributes.getValue("name"),
            if (form.attributes["method"].equals("post", ignoreCase = true)) SearchMethod.POST else SearchMethod.GET
        )
    }

    private fun appendQuery(action: URI, parameter: String, query: String): URI? = runCatching {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val separator = if (action.rawQuery.isNullOrEmpty()) "?" else "&"
        URI(action.toString() + separator + URLEncoder.encode(parameter, StandardCharsets.UTF_8.toString()) + "=" + encoded)
    }.getOrNull()

    private fun isTitleMarker(attributes: Map<String, String>): Boolean =
        attributes.containsKey("data-video-title") || attributes.containsKey("data-title") ||
            classContainsAny(attributes["class"], titleClassTokens)

    private fun is88ysTitleCard(anchor: HtmlAnchor): Boolean =
        firstNonBlank(anchor.attributes["title"], anchor.text) != null &&
            (
                anchor.href.startsWith("/guochanju/", ignoreCase = true) ||
                    hasClassToken(anchor.attributes["class"], "link-hover")
                )

    /**
     * Reads only the visible result-card fragment already returned by the search
     * request. This deliberately does not open each title's detail page.
     */
    private fun searchCardMetadata(page: String, href: String): SearchCardMetadata {
        val card = enclosingSearchCard(page, href) ?: return SearchCardMetadata()
        val actorValues = classParagraphTexts(card, "actor")
        return SearchCardMetadata(
            poster = imageAddress(card),
            summary = labelledCardValue(card, setOf("简介", "剧情", "描述")),
            releaseInfo = firstNonBlank(
                labelledCardValue(card, setOf("上映", "首播", "年份", "年代")),
                actorValues.firstOrNull(::looksLikeReleaseInfo)
            ),
            castInfo = firstNonBlank(
                labelledCardValue(card, setOf("主演", "演员")),
                actorValues.firstOrNull { value -> value != "未知" && !looksLikeReleaseInfo(value) }
            ),
            categoryInfo = firstNonBlank(
                labelledCardValue(card, setOf("类型", "分类", "地区")),
                actorValues.drop(1).firstOrNull { value -> value != "未知" && !looksLikeReleaseInfo(value) }
            ),
            updateInfo = firstNonBlank(
                labelledCardValue(card, setOf("更新", "状态")),
                classParagraphTexts(card, "other").firstOrNull()
            )
        )
    }

    private fun imageAddress(card: String): String? = Regex("""(?is)<img\b([^>]*)>""")
        .find(card)?.groupValues?.getOrNull(1)?.let(::parseAttributes)?.let { attributes ->
            firstNonBlank(attributes["data-original"], attributes["data-src"], attributes["src"])
        }

    private fun classParagraphTexts(card: String, classToken: String): List<String> =
        Regex("""(?is)<p\b([^>]*)>(.*?)</p>""").findAll(card)
            .filter { match -> hasClassToken(parseAttributes(match.groupValues[1])["class"], classToken) }
            .mapNotNull { match -> stripTags(match.groupValues[2]) }
            .toList()

    private fun looksLikeReleaseInfo(value: String): Boolean =
        Regex("""(?:19|20)\d{2}(?:[./-]|年)""").containsMatchIn(value)

    private fun enclosingSearchCard(page: String, href: String): String? {
        val escapedHref = Regex.escape(href)
        val anchor = Regex(
            """(?is)<a\b(?=[^>]*\bhref\s*=\s*(?:"$escapedHref"|'$escapedHref'|$escapedHref(?=\s|>)))[^>]*>"""
        ).find(page) ?: return null
        val opening = Regex("""(?is)<(li|article)\b[^>]*>""").findAll(page, 0)
            .lastOrNull { it.range.first < anchor.range.first } ?: return null
        val tagName = opening.groupValues[1]
        val end = matchingElementEnd(page, opening, tagName) ?: return null
        return page.substring(opening.range.first, end)
    }

    private fun matchingElementEnd(page: String, opening: MatchResult, tagName: String): Int? {
        val tagPattern = Regex("""(?is)</?$tagName\b[^>]*>""")
        var depth = 0
        for (tag in tagPattern.findAll(page, opening.range.first)) {
            if (tag.value.startsWith("</")) depth-- else depth++
            if (depth == 0) return tag.range.last + 1
        }
        return null
    }

    private fun labelledCardValue(card: String, labels: Set<String>): String? {
        val labelExpression = labels.joinToString("|") { Regex.escape(it) }
        val match = Regex(
            """(?is)(?:$labelExpression)\s*[:：]\s*(.*?)(?=</(?:p|li|span|div|dd|dt)>|<br\s*/?>|$)"""
        ).find(card) ?: return null
        return stripTags(match.groupValues[1])
    }

    private fun isSourceMarker(attributes: Map<String, String>): Boolean =
        attributes.containsKey("data-source") || attributes.containsKey("data-source-url") ||
            classContainsAny(attributes["class"], sourceClassTokens)

    private fun isEpisodeMarker(attributes: Map<String, String>): Boolean =
        attributes.containsKey("data-episode") || attributes.containsKey("data-episode-url") ||
            classContainsAny(attributes["class"], episodeClassTokens)

    private fun mediaUrl(tag: HtmlTag): URI? {
        if (!tag.visible || tag.attributes["data-encrypted"].equals("true", ignoreCase = true) ||
            tag.attributes.containsKey("data-drm")
        ) return null
        val raw = when (tag.name) {
            "video", "audio", "source" -> tag.attributes["data-media-url"] ?: tag.attributes["data-playback-url"] ?: tag.attributes["src"]
            else -> tag.attributes["data-media-url"] ?: tag.attributes["data-playback-url"]
        } ?: return null
        return parseHttps(decodeEntities(raw))
    }

    /**
     * Accept only a page's already exposed public media URL. This deliberately
     * does not inspect or transform `mac_url` or other protected player values.
     */
    private fun publicConfiguredMediaUrl(page: String): URI? {
        val mediaUrl = Regex(
            """(?i)["']?(?:url|src|file)["']?\s*:\s*["'](https://[^"'\s]+?\.(?:m3u8|mpd|mp4|m4v|webm)(?:\?[^"'\s]*)?)["']"""
        ).find(page)?.groupValues?.getOrNull(1) ?: return null
        return parseHttps(decodeEntities(mediaUrl))
    }

    private fun containsSitePlayerDeclaration(page: String): Boolean =
        Regex("""(?i)\bmac_url\b""").containsMatchIn(page)

    private fun isPlayerContainer(tag: HtmlTag): Boolean {
        if (!tag.visible) return false
        val id = tag.attributes["id"].orEmpty().lowercase()
        val classes = tag.attributes["class"].orEmpty().lowercase()
        return id == "player" || id.contains("player_") ||
            classes.split(Regex("\\s+")).any { token ->
                token == "player" || token == "player-container" || token == "video-player"
            }
    }

    private fun firstNonBlank(vararg values: String?): String? = values
        .asSequence()
        .mapNotNull { value -> value?.trim()?.takeIf(String::isNotEmpty) }
        .firstOrNull()

    private fun hasClassToken(value: String?, token: String): Boolean =
        value?.split(Regex("\\s+"))?.any { it.equals(token, ignoreCase = true) } == true

    private fun classContainsAny(value: String?, tokens: Set<String>): Boolean =
        value?.split(Regex("\\s+"))?.any { it.lowercase() in tokens } == true

    private fun decodeEntities(value: String): String = value
        .replace("&amp;", "&", ignoreCase = true)
        .replace("&quot;", "\"", ignoreCase = true)
        .replace("&#39;", "'", ignoreCase = true)

    private fun extract88ysSources(page: String, detailUrl: URI, titleId: String): List<VideoSource> {
        val playFrom = Regex("""(?is)<(?:div|ul)\b[^>]*\bclass\s*=\s*(?:"[^"]*\bplayfrom\b[^"]*"|'[^']*\bplayfrom\b[^']*'|[^\s>]*\bplayfrom\b[^\s>]*)[^>]*>(.*?)</(?:div|ul)>""")
            .find(page)?.groupValues?.getOrNull(1) ?: return emptyList()
        return Regex("""(?is)<li\b([^>]*)>(.*?)</li>""").findAll(playFrom).mapIndexedNotNull { index, match ->
            val attributes = parseAttributes(match.groupValues[1])
            val targetRaw = firstNonBlank(attributes["data-tab"], attributes["id"], Regex("""(?is)href\s*=\s*["']#([^"']+)["']""").find(match.groupValues[2])?.groupValues?.getOrNull(1))
                ?: return@mapIndexedNotNull null
            val target = when {
                targetRaw.startsWith("stab", ignoreCase = true) -> targetRaw
                targetRaw.startsWith("tab", ignoreCase = true) -> "s$targetRaw"
                else -> "stab$targetRaw"
            }
            val label = stripTags(match.groupValues[2]) ?: return@mapIndexedNotNull null
            VideoSource("$titleId#source-$target-${index}", titleId, label, "${withoutFragment(detailUrl)}#$target")
        }.toList().distinctBy(VideoSource::url)
    }

    private fun extract88ysEpisodes(page: String, playlistId: String, source: VideoSource): List<VideoEpisode> {
        val playlist = elementInnerHtml(page, playlistId) ?: return emptyList()
        return Regex("""(?is)<a\b([^>]*)>(.*?)</a>""").findAll(playlist).mapNotNull { match ->
            val attributes = parseAttributes(match.groupValues[1])
            val rawUrl = attributes["href"] ?: return@mapNotNull null
            val episodeUrl = rootBoundUrl(URI(source.titleId), rawUrl) ?: return@mapNotNull null
            val label = firstNonBlank(attributes["title"], stripTags(match.groupValues[2])) ?: return@mapNotNull null
            VideoEpisode("${source.id}#episode-$episodeUrl", source.id, label, episodeUrl.toString(), 0, source.titleId)
        }.distinctBy(VideoEpisode::url).mapIndexed { index, episode -> episode.copy(position = index) }.toList()
    }

    private fun stripTags(value: String): String? = decodeEntities(value.replace(Regex("""(?is)<[^>]+>"""), " "))
        .replace(Regex("""\s+"""), " ").trim().takeIf(String::isNotBlank)

    private fun headingText(page: String): String? = Regex("""(?is)<h1\b[^>]*>(.*?)</h1>""")
        .find(page)?.groupValues?.getOrNull(1)?.let(::stripTags)

    /** Returns the contents of a div identified by id, preserving nested divs. */
    private fun elementInnerHtml(page: String, elementId: String): String? {
        val escapedId = Regex.escape(elementId)
        val opening = Regex("""(?is)<div\b(?=[^>]*\bid\s*=\s*(?:"$escapedId"|'$escapedId'|$escapedId(?=\s|>)))[^>]*>""").find(page)
            ?: return null
        var depth = 1
        val contentStart = opening.range.last + 1
        val divTag = Regex("""(?is)</?div\b[^>]*>""")
        for (match in divTag.findAll(page, contentStart)) {
            if (match.value.startsWith("</", ignoreCase = true)) depth-- else depth++
            if (depth == 0) return page.substring(contentStart, match.range.first)
        }
        return null
    }

    private fun withoutFragment(uri: URI): URI = URI(uri.scheme, uri.authority, uri.path, uri.query, null)

    private fun parse(html: String): ParsedDocument {
        val tags = mutableListOf<HtmlTag>()
        val anchors = mutableListOf<HtmlAnchor>()
        val elements = mutableListOf<Element>()
        var index = 0

        while (index < html.length) {
            if (html.regionMatches(index, "<!--", 0, 4, ignoreCase = false)) {
                val commentEnd = html.indexOf("-->", index + 4)
                index = if (commentEnd == -1) html.length else commentEnd + 3
                continue
            }
            if (html[index] != '<') {
                val textEnd = html.indexOf('<', index).let { if (it == -1) html.length else it }
                if (elements.lastOrNull()?.visible != false) {
                    elements.asReversed().firstNotNullOfOrNull(Element::anchor)?.text?.append(decodeEntities(html.substring(index, textEnd)))
                }
                index = textEnd
                continue
            }
            val tagEnd = findTagEnd(html, index + 1)
            if (tagEnd == -1) break
            consumeTag(html.substring(index + 1, tagEnd).trim(), elements, tags, anchors)
            index = tagEnd + 1
        }
        return ParsedDocument(tags, anchors)
    }

    private fun consumeTag(
        rawTag: String,
        elements: MutableList<Element>,
        tags: MutableList<HtmlTag>,
        anchors: MutableList<HtmlAnchor>
    ) {
        if (rawTag.isBlank() || rawTag.startsWith("!") || rawTag.startsWith("?")) return
        if (rawTag.startsWith('/')) {
            closeElement(rawTag.drop(1).trim().lowercase(), elements, anchors)
            return
        }
        val selfClosing = rawTag.endsWith('/')
        val normalized = if (selfClosing) rawTag.dropLast(1).trimEnd() else rawTag
        val nameMatch = tagNamePattern.find(normalized) ?: return
        val name = nameMatch.value.lowercase()
        val attributes = parseAttributes(normalized.drop(nameMatch.range.last + 1))
        val visible = (elements.lastOrNull()?.visible ?: true) &&
            !isHidden(attributes) && name !in hiddenContentTags
        if (visible) tags += HtmlTag(name, attributes, true)
        val anchor = if (name == "a" && visible) {
            attributes["href"]?.let(::decodeEntities)?.let { href -> HtmlAnchorBuilder(href, attributes) }
        } else {
            null
        }
        elements += Element(name, visible, anchor)
        if (selfClosing || name in voidElements) closeElement(name, elements, anchors)
    }

    private fun closeElement(name: String, elements: MutableList<Element>, anchors: MutableList<HtmlAnchor>) {
        val matchingIndex = elements.indexOfLast { it.name == name }
        if (matchingIndex == -1) return
        while (elements.size > matchingIndex) {
            val element = elements.removeAt(elements.lastIndex)
            element.anchor?.let { anchor ->
                anchors += HtmlAnchor(anchor.href, anchor.attributes, anchor.text.toString().trim())
            }
        }
    }

    private fun parseAttributes(raw: String): Map<String, String> = attributePattern.findAll(raw)
        .associate { match ->
            match.groupValues[1].lowercase() to match.groupValues.drop(2).firstOrNull(String::isNotEmpty).orEmpty()
        }

    private fun isHidden(attributes: Map<String, String>): Boolean {
        val style = attributes["style"].orEmpty().lowercase().replace(Regex("\\s+"), "")
        return attributes.containsKey("hidden") || attributes["aria-hidden"].equals("true", ignoreCase = true) ||
            "display:none" in style || "visibility:hidden" in style
    }

    private fun findTagEnd(html: String, start: Int): Int {
        var quote: Char? = null
        for (index in start until html.length) {
            val character = html[index]
            if (quote == null) {
                when (character) {
                    '\'', '\"' -> quote = character
                    '>' -> return index
                }
            } else if (character == quote) {
                quote = null
            }
        }
        return -1
    }

    private data class SearchRequest(val action: URI, val parameter: String, val method: SearchMethod)
    private enum class SearchMethod { GET, POST }
    private data class SearchCardMetadata(
        val poster: String? = null,
        val summary: String? = null,
        val releaseInfo: String? = null,
        val castInfo: String? = null,
        val categoryInfo: String? = null,
        val updateInfo: String? = null
    )
    private data class ParsedDocument(val tags: List<HtmlTag>, val anchors: List<HtmlAnchor>)
    private data class HtmlTag(val name: String, val attributes: Map<String, String>, val visible: Boolean, val text: String = "")
    private data class HtmlAnchor(val href: String, val attributes: Map<String, String>, val text: String)
    private data class HtmlAnchorBuilder(val href: String, val attributes: Map<String, String>, val text: StringBuilder = StringBuilder())
    private data class Element(val name: String, val visible: Boolean, val anchor: HtmlAnchorBuilder?)

    private companion object {
        val tagNamePattern = Regex("""[A-Za-z][A-Za-z0-9:-]*""")
        val attributePattern = Regex("""([^\s=/>]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+)))?""")
        val titleClassTokens = setOf("title", "video-title", "movie-title", "drama-title")
        val sourceClassTokens = setOf("source", "video-source", "play-source")
        val episodeClassTokens = setOf("episode", "video-episode", "play-episode")
        val hiddenContentTags = setOf("script", "style", "template")
        val voidElements = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr")
    }
}
