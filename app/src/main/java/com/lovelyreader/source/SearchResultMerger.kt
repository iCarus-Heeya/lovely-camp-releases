package com.lovelyreader.source

import com.lovelyreader.domain.SearchResult
import com.lovelyreader.domain.SourceCapability

object SearchResultMerger {
    fun merge(results: List<SearchResult>): List<SearchResult> {
        return results
            .groupBy { "${it.title.trim()}::${it.author.trim()}" }
            .values
            .map { group ->
                val canonical = group.maxWithOrNull(
                    compareBy<SearchResult> { SourceCapability.TXT_IMPORT in it.capabilities }
                        .thenBy { SourceCapability.READ_CHAPTER in it.capabilities }
                        .thenBy { SourceCapability.OPEN_ORIGINAL in it.capabilities }
                ) ?: group.first()
                canonical.copy(
                    summary = canonical.summary.ifBlank {
                        group.firstOrNull { it.summary.isNotBlank() }?.summary.orEmpty()
                    },
                    latestChapter = canonical.latestChapter
                        ?: group.firstOrNull { !it.latestChapter.isNullOrBlank() }?.latestChapter
                )
            }
    }
}
