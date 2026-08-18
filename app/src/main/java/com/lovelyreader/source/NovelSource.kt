package com.lovelyreader.source

import com.lovelyreader.domain.BookDetail
import com.lovelyreader.domain.Chapter
import com.lovelyreader.domain.ChapterContent
import com.lovelyreader.domain.DownloadOption
import com.lovelyreader.domain.RankingPeriod
import com.lovelyreader.domain.SearchResult
import com.lovelyreader.domain.SizeBand
import com.lovelyreader.domain.SourceCapability
import com.lovelyreader.domain.SourceHealth
import java.net.URI

interface NovelSource {
    val sourceId: String
    val displayName: String
    val baseUrl: String
    val capabilities: Set<SourceCapability>

    suspend fun search(query: String): List<SearchResult>
    suspend fun getBookDetail(bookUrl: String): BookDetail?
    suspend fun getChapterList(bookUrl: String): List<Chapter>
    suspend fun getChapterContent(chapterUrl: String): ChapterContent?
    suspend fun getDownloadOptions(bookUrl: String): List<DownloadOption>
    suspend fun healthCheck(): SourceHealth

    fun isSafeReadUrl(bookUrl: String): Boolean {
        val sourceUri = runCatching { URI(baseUrl) }.getOrNull() ?: return false
        val bookUri = runCatching { URI(bookUrl) }.getOrNull() ?: return false
        return bookUri.scheme == "https" && bookUri.host.equals(sourceUri.host, ignoreCase = true)
    }
}

interface BrowsableNovelSource {
    suspend fun ranking(period: RankingPeriod): List<SearchResult>
    suspend fun randomBrowse(category: String, finishedOnly: Boolean, sizeBand: SizeBand): List<SearchResult>
}
