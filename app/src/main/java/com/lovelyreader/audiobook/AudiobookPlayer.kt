package com.lovelyreader.audiobook

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class AudiobookPlaybackState(
    val connected: Boolean = false,
    val isPlaying: Boolean = false,
    val bookId: String? = null,
    val chapterId: String? = null,
    val title: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null
)

/** Thin UI bridge to the Media3 service; it never owns a second player. */
class AudiobookPlayer(context: Context) : Player.Listener {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(AudiobookPlaybackState())
    val state: StateFlow<AudiobookPlaybackState> = _state.asStateFlow()
    private var controller: MediaController? = null
    private var currentBookId: String? = null
    private var currentChapterId: String? = null
    private var currentTitle: String = ""
    private var lastErrorMessage: String? = null

    init {
        val token = SessionToken(appContext, ComponentName(appContext, AudiobookPlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener({
            runCatching {
                controller = future.get().also { mediaController ->
                    mediaController.addListener(this)
                    publish(mediaController)
                }
            }.onFailure { error ->
                lastErrorMessage = error.message ?: "后台播放器连接失败"
                _state.value = _state.value.copy(errorMessage = lastErrorMessage)
            }
        }, Runnable::run)
    }

    fun play(
        file: File,
        bookId: String,
        chapterId: String,
        title: String,
        startPositionMs: Long = 0L
    ): Boolean {
        val mediaController = controller ?: return false
        currentBookId = bookId
        currentChapterId = chapterId
        currentTitle = title
        lastErrorMessage = null
        val item = MediaItem.Builder()
            .setMediaId("$bookId/$chapterId")
            .setUri(file.toURI().toString())
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .build()
        mediaController.setMediaItem(item, startPositionMs.coerceAtLeast(0L))
        mediaController.prepare()
        mediaController.play()
        publish(mediaController)
        return true
    }

    fun pause() {
        controller?.pause()
        controller?.let(::publish)
    }

    fun resume() {
        controller?.play()
        controller?.let(::publish)
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
        controller?.let(::publish)
    }

    fun snapshot(): AudiobookPlaybackState {
        controller?.let(::publish)
        return state.value
    }

    private fun publish(player: Player) {
        _state.value = AudiobookPlaybackState(
            connected = true,
            isPlaying = player.isPlaying,
            bookId = currentBookId,
            chapterId = currentChapterId,
            title = currentTitle,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it > 0L } ?: 0L,
            errorMessage = lastErrorMessage
        )
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        controller?.let(::publish)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        controller?.let(::publish)
    }

    override fun onPlayerError(error: PlaybackException) {
        lastErrorMessage = error.message ?: "本章音频无法播放"
        controller?.let(::publish)
    }

    fun release() {
        controller?.removeListener(this)
        controller?.release()
        controller = null
    }
}
