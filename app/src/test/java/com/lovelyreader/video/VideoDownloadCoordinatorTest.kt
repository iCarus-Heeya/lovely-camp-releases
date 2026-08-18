package com.lovelyreader.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoDownloadCoordinatorTest {
    @Test
    fun downloadsAnHttpsMp4Once() {
        val gateway = RecordingGateway()
        val result = VideoDownloadCoordinator(gateway).download("https://cdn.example.com/videos/clip.MP4")

        assertTrue(result is DownloadResult.Accepted)
        assertEquals(listOf("https://cdn.example.com/videos/clip.MP4"), gateway.urls)
    }

    @Test
    fun rejectsHttpUrlWithoutCallingGateway() {
        assertRejectedWithoutGatewayCall("http://cdn.example.com/video.mp4")
    }

    @Test
    fun rejectsM3u8UrlWithoutCallingGateway() {
        assertRejectedWithoutGatewayCall("https://cdn.example.com/playlist.m3u8")
    }

    @Test
    fun rejectsBlobUrlWithoutCallingGateway() {
        assertRejectedWithoutGatewayCall("blob:https://example.com/video.mp4")
    }

    private fun assertRejectedWithoutGatewayCall(url: String) {
        val gateway = RecordingGateway()
        val result = VideoDownloadCoordinator(gateway).download(url)

        assertTrue(result is DownloadResult.Rejected)
        assertTrue(gateway.urls.isEmpty())
    }

    private class RecordingGateway : DownloadGateway {
        val urls = mutableListOf<String>()

        override fun download(url: String): DownloadResult {
            urls += url
            return DownloadResult.Accepted
        }
    }
}
