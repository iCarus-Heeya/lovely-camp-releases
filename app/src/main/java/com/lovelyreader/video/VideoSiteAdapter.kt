package com.lovelyreader.video

interface VideoSiteAdapter {
    suspend fun search(root: VideoSiteRoot, query: String): List<VideoTitle>

    suspend fun loadDetail(root: VideoSiteRoot, titleUrl: String): VideoTitleDetail?

    suspend fun loadEpisodes(root: VideoSiteRoot, source: VideoSource): List<VideoEpisode>

    suspend fun loadMedia(root: VideoSiteRoot, episode: VideoEpisode): VideoMediaLink?

    fun isDirectMp4(url: String): Boolean
}
