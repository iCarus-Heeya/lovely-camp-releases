package com.lovelyreader.ui.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DramaUiCopyTest {
    @Test
    fun dramaStatusMessagesAreChineseForUnavailableAndCastFallbackStates() {
        listOf(
            dramaStatusCopy(DramaStatus.RootUnavailable),
            dramaStatusCopy(DramaStatus.Searching),
            dramaStatusCopy(DramaStatus.NoSearchResults),
            dramaStatusCopy(DramaStatus.CastTargetUnavailable)
        ).forEach { message ->
            assertFalse(
                "App-owned drama status must not expose English: $message",
                message.any { it in 'A'..'Z' || it in 'a'..'z' }
            )
        }
    }

    @Test
    fun selectedEpisodeActionUsesNaturalChineseAndAccurateCount() {
        assertEquals("下载已选 3 集", selectedEpisodeDownloadLabel(3))
    }

    @Test
    fun unknownDownloadPathIsNotExposedAsTechnicalIdentifier() {
        assertEquals("正在准备下载", userFacingDownloadLocation(null))
    }

    @Test
    fun recentViewingNeverShowsTheInternalEpisodeUrl() {
        val label = recentEpisodeDisplayLabel(
            "https://old-provider.example/title#source-stab81-0#episode-https://old-provider.example/vod-play-id-1-src-1-num-1.html"
        )

        assertEquals("第1集", label)
        assertFalse(label.contains("http", ignoreCase = true))
    }

    @Test
    fun downloadSourceLabelNeverShowsTheInternalProviderUrl() {
        val label = downloadSourceDisplayLabel(
            "https://old-provider.example/title#source-stab81-0"
        )

        assertEquals("片源 1", label)
        assertFalse(label.contains("http", ignoreCase = true))
    }
}
