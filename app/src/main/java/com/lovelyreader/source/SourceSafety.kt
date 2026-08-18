package com.lovelyreader.source

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

class SourceSafety(
    private val baseUrl: String,
    private val disallowedPrefixes: List<String> = emptyList()
) {
    private val baseUri = URI(baseUrl)
    private val normalizedDisallowedPrefixes = disallowedPrefixes.map { normalizePath(it).lowercase(Locale.ROOT) }

    fun requireAllowed(url: String): String {
        val uri = URI(url).normalize()
        require(uri.scheme == "https") { "Only HTTPS source URLs are allowed." }
        require(uri.host.equals(baseUri.host, ignoreCase = true)) { "Cross-site source URLs are not allowed." }

        val path = normalizePath(uri.rawPath.orEmpty()).lowercase(Locale.ROOT)
        val blocked = normalizedDisallowedPrefixes.any { prefix -> path.startsWith(prefix) }
        require(!blocked) { "This source path is disallowed by source policy." }

        return uri.toString()
    }

    fun isAllowed(url: String): Boolean {
        return runCatching { requireAllowed(url) }.isSuccess
    }

    private fun normalizePath(path: String): String {
        val decodedPath = URLDecoder.decode(path.ifBlank { "/" }, "UTF-8")
        val withLeadingSlash = if (decodedPath.startsWith("/")) decodedPath else "/$decodedPath"
        return URI(null, null, withLeadingSlash, null).normalize().path
    }
}
