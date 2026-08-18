package com.lovelyreader.domain

enum class BookStatus {
    UNKNOWN,
    SERIALIZING,
    FINISHED
}

enum class SourceCapability {
    SEARCH,
    READ_CHAPTER,
    CHAPTER_CACHE,
    TXT_IMPORT,
    EPUB_IMPORT,
    OPEN_ORIGINAL
}

data class SearchResult(
    val sourceId: String,
    val title: String,
    val author: String,
    val bookUrl: String,
    val summary: String = "",
    val coverUrl: String? = null,
    val latestChapter: String? = null,
    val capabilities: Set<SourceCapability> = emptySet()
)

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val status: BookStatus = BookStatus.UNKNOWN,
    val summary: String = "",
    val coverUrl: String? = null,
    val sourceIds: List<String> = emptyList()
)

data class BookDetail(
    val book: Book,
    val sourceUrl: String,
    val category: String? = null,
    val wordCountOrSize: String? = null,
    val latestChapter: String? = null,
    val offlineLabel: String = "暂不可离线"
)

data class Chapter(
    val title: String,
    val url: String,
    val order: Int
)

data class ChapterContent(
    val title: String,
    val url: String,
    val content: String
)

data class PartialChapter(
    val bookId: String,
    val title: String,
    val url: String,
    val content: String,
    val sourceId: String
)

data class DownloadOption(
    val label: String,
    val url: String,
    val format: String,
    val allowed: Boolean
)

data class ReadingProgress(
    val bookId: String,
    val chapterUrl: String,
    val percent: Int,
    val lastReadIndex: Int = 0,
    val lastReadOffset: Int = 0
)

data class Bookmark(
    val bookId: String,
    val chapterUrl: String,
    val label: String = "想你的这一页"
)

data class HusbandNote(
    val id: String,
    val message: String
)

data class SourceHealth(
    val sourceId: String,
    val available: Boolean,
    val message: String
)

enum class RankingPeriod(val label: String) {
    MONTH("月榜"),
    YEAR("年榜"),
    TOTAL("总榜")
}

data class SizeBand(
    val label: String,
    val minKb: Int,
    val maxKb: Int
) {
    fun contains(sizeKb: Int): Boolean = sizeKb in minKb..maxKb
}
