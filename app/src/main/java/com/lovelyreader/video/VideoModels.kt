package com.lovelyreader.video

data class VideoSiteRoot(
    val url: String,
    val validatedAtMillis: Long
)

enum class VideoRootResolutionStatus {
    RESOLVED,
    USING_CACHED_ROOT,
    USING_BOOTSTRAP_ROOT,
    UNAVAILABLE
}

data class VideoRootResolution(
    val root: VideoSiteRoot?,
    val status: VideoRootResolutionStatus,
    val detail: String
)

data class VideoTitle(
    val id: String,
    val name: String,
    val detailUrl: String,
    val posterUrl: String? = null,
    val summary: String? = null,
    /** Source-provided release year/date; absent when the search card does not expose it. */
    val releaseInfo: String? = null,
    /** Source-provided lead cast; absent when the search card does not expose it. */
    val castInfo: String? = null,
    /** Source-provided category, region, or genre; absent when unavailable. */
    val categoryInfo: String? = null,
    /** Source-provided update or completion state; absent when unavailable. */
    val updateInfo: String? = null
)

data class VideoTitleDetail(
    val title: VideoTitle,
    val sources: List<VideoSource>
)

data class VideoSource(
    val id: String,
    val titleId: String,
    val label: String,
    val url: String? = null
)

data class VideoEpisode(
    val id: String,
    val sourceId: String,
    val label: String,
    val url: String,
    val position: Int,
    val titleId: String = ""
)

enum class VideoPlaybackMode {
    /** A public HTTPS media resource the app can hand to Media3, Cast, and eligible downloads. */
    DIRECT_MEDIA,

    /** The catalogue's own page player, displayed without the surrounding site page. */
    SITE_PLAYER
}

data class VideoMediaLink(
    val playbackUrl: String,
    val directMp4Url: String? = null,
    val isEncrypted: Boolean = false,
    val playbackMode: VideoPlaybackMode = VideoPlaybackMode.DIRECT_MEDIA
)

enum class VideoDownloadStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

data class VideoDownloadTask(
    val id: String,
    val titleId: String,
    val sourceId: String,
    val episodeId: String,
    val directUrlHash: String,
    val systemDownloadId: Long? = null,
    val status: VideoDownloadStatus = VideoDownloadStatus.QUEUED,
    val localUri: String? = null
)
