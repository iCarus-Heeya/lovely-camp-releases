package com.lovelyreader.video

import android.content.Context
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Owns the Cast session listener and transfers only media accepted by
 * [castMediaTarget]. A failed transfer never stops local playback.
 */
class VideoCastController(
    context: Context,
    private val mediaPreflight: CastMediaPreflight = CastMediaPreflight()
) {
    private val castContext = sharedCastContext(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var registered = false
    private var request: CastRequest? = null
    private var loadedKey: String? = null
    private var pendingKey: String? = null
    private var requestGeneration = 0
    private var preflightJob: Job? = null

    val isAvailable: Boolean
        get() = castContext != null

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            request?.onMessage?.invoke("已连接电视，正在检查媒体地址…")
            loadCurrentMedia(session)
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            loadCurrentMedia(session)
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            val hadRemotePlayback = loadedKey?.startsWith("${session.sessionId}|") == true
            loadedKey = null
            pendingKey = null
            if (hadRemotePlayback) {
                request?.onLocalFallback?.invoke()
                request?.onMessage?.invoke("投屏已结束，正在继续使用手机播放。")
            }
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            if (loadedKey?.startsWith("${session.sessionId}|") == true) {
                loadedKey = null
                pendingKey = null
                request?.onLocalFallback?.invoke()
                request?.onMessage?.invoke("投屏连接已中断，可继续使用手机播放。")
            }
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            request?.onMessage?.invoke("未能连接电视，手机播放不受影响。")
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            request?.onLocalFallback?.invoke()
            request?.onMessage?.invoke("未能恢复投屏，正在继续使用手机播放。")
        }

        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionEnding(session: CastSession) = Unit
    }

    fun register() {
        val manager = castContext?.sessionManager ?: return
        if (!registered) {
            manager.addSessionManagerListener(sessionListener, CastSession::class.java)
            registered = true
        }
    }

    fun unregister() {
        val manager = castContext?.sessionManager ?: return
        if (registered) {
            manager.removeSessionManagerListener(sessionListener, CastSession::class.java)
            registered = false
        }
    }

    fun close() {
        unregister()
        scope.cancel()
    }

    fun setMedia(
        media: VideoMediaLink?,
        title: String,
        currentPositionMs: () -> Long,
        onRemotePlaybackStarted: () -> Unit,
        onLocalFallback: () -> Unit,
        onMessage: (String) -> Unit
    ) {
        requestGeneration++
        val target = castMediaTarget(media)
        preflightJob?.cancel()
        request = target?.let { CastRequest(it, title, currentPositionMs, onRemotePlaybackStarted, onLocalFallback, onMessage) }
        loadedKey = null
        pendingKey = null
        if (target == null) {
            onMessage("当前片源暂不支持投屏，仍可在手机上观看。")
            return
        }
        val generation = requestGeneration
        preflightJob = scope.launch {
            onMessage(CastReadinessCopy.CheckingMedia)
            val current = request ?: return@launch
            if (mediaPreflight.check(target) != CastPreflightResult.Ready || generation != requestGeneration || request !== current) {
                if (generation == requestGeneration) onMessage(CastReadinessCopy.MediaUnavailable)
                return@launch
            }
            request = current.copy(preflightReady = true)
            castContext?.sessionManager?.currentCastSession
                ?.takeIf(CastSession::isConnected)
                ?.let(::loadCurrentMedia)
        }
    }

    fun clearMedia() {
        requestGeneration++
        preflightJob?.cancel()
        request = null
        loadedKey = null
        pendingKey = null
    }

    private fun loadCurrentMedia(session: CastSession) {
        val current = request ?: return
        if (!current.preflightReady) return
        val remoteClient = session.remoteMediaClient ?: run {
            current.onMessage("电视端暂未准备好播放，手机播放不受影响。")
            return
        }
        val key = "${session.sessionId}|${current.target.url}"
        if (key == loadedKey || key == pendingKey) return
        pendingKey = key
        val generation = requestGeneration
        current.onMessage(CastReadinessCopy.ReceiverPreparing)

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_TV_SHOW).apply {
            putString(MediaMetadata.KEY_TITLE, current.title)
        }
        val mediaInfo = MediaInfo.Builder(current.target.url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(current.target.contentType)
            .setMetadata(metadata)
            .build()
        val loadRequest = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .setCurrentTime(current.currentPositionMs().coerceAtLeast(0L))
            .build()

        remoteClient.load(loadRequest).setResultCallback { result ->
            if (generation != requestGeneration || request !== current) return@setResultCallback
            pendingKey = null
            if (result.status.isSuccess) {
                loadedKey = key
                current.onRemotePlaybackStarted()
                current.onMessage("电视已开始播放。")
            } else {
                current.onMessage("电视暂时无法播放此媒体地址，手机播放不受影响。")
            }
        }
    }

    private data class CastRequest(
        val target: CastMediaTarget,
        val title: String,
        val currentPositionMs: () -> Long,
        val onRemotePlaybackStarted: () -> Unit,
        val onLocalFallback: () -> Unit,
        val onMessage: (String) -> Unit,
        val preflightReady: Boolean = false
    )

    companion object {
        fun initialize(context: Context): Boolean = sharedCastContext(context.applicationContext) != null

        private fun sharedCastContext(context: Context): CastContext? =
            runCatching { CastContext.getSharedInstance(context) }.getOrNull()
    }
}
