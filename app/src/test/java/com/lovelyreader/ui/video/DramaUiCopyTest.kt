package com.lovelyreader.ui.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DramaUiCopyTest {
    @Test
    fun downloadStatusChipsUseVisibleSemanticIcons() {
        assertEquals("schedule", downloadStatusIconName(com.lovelyreader.video.VideoDownloadStatus.QUEUED))
        assertEquals("cloud_download", downloadStatusIconName(com.lovelyreader.video.VideoDownloadStatus.DOWNLOADING))
        assertEquals("check_circle", downloadStatusIconName(com.lovelyreader.video.VideoDownloadStatus.COMPLETED))
        assertEquals("error_outline", downloadStatusIconName(com.lovelyreader.video.VideoDownloadStatus.FAILED))
    }

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
    fun emptyDownloadMessageDoesNotExposeTransportFormat() {
        val message = noPublicVideoDownloadMessage()

        assertEquals("所选剧集没有可下载的公开视频", message)
        assertFalse(message.contains("MP4", ignoreCase = true))
    }

    @Test
    fun unknownDownloadPathIsNotExposedAsTechnicalIdentifier() {
        assertEquals("正在准备下载", userFacingDownloadLocation(null))
    }

    @Test
    fun completedDownloadLocationUsesFriendlyCopyInsteadOfProviderUri() {
        val label = userFacingDownloadLocation("content://downloads/fixture-episode-02.mp4")

        assertEquals("已保存到本地", label)
        assertFalse(label.contains("content://", ignoreCase = true))
        assertFalse(label.contains(".mp4", ignoreCase = true))
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
