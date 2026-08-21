package com.lovelyreader.video

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoMetadataMergeTest {
    @Test
    fun `keeps detail values and uses search values only when detail omits them`() {
        val search = title(
            releaseInfo = "2024",
            castInfo = "甲、乙",
            categoryInfo = "悬疑",
            updateInfo = "更新至 12 集"
        )
        val detail = title(
            summary = "详情简介",
            releaseInfo = null,
            castInfo = "丙",
            updateInfo = null
        )

        val merged = mergeVideoTitleMetadata(search, detail)

        assertEquals("详情简介", merged.summary)
        assertEquals("2024", merged.releaseInfo)
        assertEquals("丙", merged.castInfo)
        assertEquals("悬疑", merged.categoryInfo)
        assertEquals("更新至 12 集", merged.updateInfo)
    }

    private fun title(
        summary: String? = null,
        releaseInfo: String? = null,
        castInfo: String? = null,
        categoryInfo: String? = null,
        updateInfo: String? = null
    ) = VideoTitle(
        id = "title",
        name = "剧集",
        detailUrl = "https://video.example/title",
        posterUrl = "https://cdn.example/title.jpg",
        summary = summary,
        releaseInfo = releaseInfo,
        castInfo = castInfo,
        categoryInfo = categoryInfo,
        updateInfo = updateInfo
    )
}
