package com.lovelyreader.source

import com.lovelyreader.domain.SearchResult
import com.lovelyreader.domain.SourceCapability

object SourceReadEligibility {
    fun canReadInApp(sources: List<NovelSource>, result: SearchResult): Boolean {
        val source = sources.firstOrNull { it.sourceId == result.sourceId } ?: return false
        return SourceCapability.READ_CHAPTER in result.capabilities &&
            SourceCapability.READ_CHAPTER in source.capabilities &&
            source.isSafeReadUrl(result.bookUrl)
    }
}
