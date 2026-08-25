package com.lovelyreader.performance

enum class PerformanceBand { SMALL, MEDIUM, LARGE }

object PerformancePolicy {
    const val readerPageCacheLimit: Int = 3

    fun bandFor(bookCount: Int, chapterCount: Int): PerformanceBand = when {
        bookCount >= 500 || chapterCount >= 1_000 -> PerformanceBand.LARGE
        bookCount >= 100 || chapterCount >= 300 -> PerformanceBand.MEDIUM
        else -> PerformanceBand.SMALL
    }
}
