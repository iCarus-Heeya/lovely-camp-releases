package com.lovelyreader.source

object HtmlTools {
    fun stripTags(value: String): String {
        return value
            .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<[^>]+>"), "")
            .decodeEntities()
            .replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    fun String.decodeEntities(): String {
        return replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
    }

    fun absoluteUrl(baseUrl: String, href: String): String {
        return when {
            href.startsWith("http://") || href.startsWith("https://") -> href
            href.startsWith("//") -> "https:$href"
            href.startsWith("/") -> baseUrl.trimEnd('/') + href
            else -> baseUrl.trimEnd('/') + "/" + href
        }
    }

    fun firstMatch(input: String, pattern: String): String? {
        return Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(input)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
    }

    fun allMatches(input: String, pattern: String): List<MatchResult> {
        return Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(input)
            .toList()
    }
}
