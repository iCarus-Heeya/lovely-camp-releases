package com.lovelyreader.video

/**
 * Short-lived in-memory reuse for the provider homepage validated immediately
 * before a search. It avoids issuing the same fragile homepage request twice.
 */
class VideoSearchFormPageCache(private val ttlMillis: Long = 90_000) {
    private var cachedUrl: String? = null
    private var cachedPage: String? = null
    private var storedAtMillis: Long = 0L

    @Synchronized
    fun store(url: String, page: String, nowMillis: Long = System.currentTimeMillis()) {
        if (!hasSearchForm(page)) return
        cachedUrl = url
        cachedPage = page
        storedAtMillis = nowMillis
    }

    @Synchronized
    fun takeFresh(url: String, nowMillis: Long = System.currentTimeMillis()): String? {
        val page = cachedPage ?: return null
        if (url != cachedUrl || nowMillis - storedAtMillis !in 0..ttlMillis) return null
        return page
    }

    private fun hasSearchForm(page: String): Boolean =
        Regex("(?is)<form\\b[^>]*>").containsMatchIn(page) &&
            Regex("(?is)<input\\b(?=[^>]*\\bname\\s*=\\s*(?:[\"']?(?:wd|q|query|keyword)[\"']?))[^>]*>")
                .containsMatchIn(page)
}
