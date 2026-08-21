package com.lovelyreader.source

import com.lovelyreader.domain.SearchResult
import com.lovelyreader.domain.SourceCapability

object SearchResultMerger {
    fun merge(results: List<SearchResult>): List<SearchResult> {
        return results
            .groupBy { normalizedTitleKey(it.title) }
            .values
            .flatMap(::authorCompatibleGroups)
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

    private fun authorCompatibleGroups(sameTitle: List<SearchResult>): List<List<SearchResult>> {
        val known = sameTitle
            .filter { normalizedAuthorKey(it.author).isNotBlank() }
            .groupBy { normalizedAuthorKey(it.author) }
            .values
            .map { it.toMutableList() }
            .toMutableList()
        val unknown = sameTitle.filter { normalizedAuthorKey(it.author).isBlank() }
        return when {
            known.size == 1 -> listOf(known.single() + unknown)
            known.isEmpty() -> listOf(unknown)
            unknown.isEmpty() -> known
            else -> known + listOf(unknown)
        }
    }
}
