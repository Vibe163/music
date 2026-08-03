package com.localmusic.app.player

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.localmusic.app.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerUiState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF
)

class PlayerController(private val context: Context) {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var songsByMediaId: Map<String, Song> = emptyMap()

    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _volume = MutableStateFlow(1.0f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _sleepTimerRemainingMs = MutableStateFlow(0L)
    val sleepTimerRemainingMs: StateFlow<Long> = _sleepTimerRemainingMs.asStateFlow()
    private var sleepTimerJob: Job? = null

    var onSongCompleted: ((Song) -> Unit)? = null

    private var wasPlaying = false
    private var lastSongCompletedId: Long = -1L

    fun updateSongInQueue(updated: Song) {
        songsByMediaId = songsByMediaId.toMutableMap().apply {
            put(updated.id.toString(), updated)
        }
        val current = _uiState.value.currentSong
        if (current?.id == updated.id) {
            _uiState.value = _uiState.value.copy(currentSong = updated)
        } else {
            val queue = _uiState.value.queue.map { s ->
                if (s.id == updated.id) updated else s
            }
            _uiState.value = _uiState.value.copy(queue = queue)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val nowPlaying = player.isPlaying
            val justFinished = wasPlaying && !nowPlaying
            wasPlaying = nowPlaying

            val duration = player.duration
            val position = player.currentPosition
            val nearEnd = duration > 0 && position >= duration - 500

            if (justFinished && nearEnd) {
                _uiState.value.currentSong?.let { song ->
                    if (song.id != lastSongCompletedId) {
                        lastSongCompletedId = song.id
                        onSongCompleted?.invoke(song)
                    }
                }
            }

            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED
                )
            ) {
                syncFromPlayer()
            }
        }
    }

    suspend fun connect() {
        if (mediaController != null) return
        val token = SessionToken(context, ComponentName(context, MusicPlayerService::class.java))
        controllerFuture = MediaController.Builder(context, token).buildAsync()
        mediaController = controllerFuture?.await()
        mediaController?.addListener(playerListener)
        _volume.value = getVolume()
        syncFromPlayer()
    }

    fun disconnect() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        mediaController?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        val controller = mediaController ?: return
        songsByMediaId = songs.associateBy { it.id.toString() }
        val mediaItems = songs.map { song ->
            MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(song.uri)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .build()
                )
                .build()
        }
        lastSongCompletedId = -1L
        controller.setMediaItems(mediaItems, startIndex.coerceIn(0, songs.lastIndex), 0L)
        controller.prepare()
        controller.play()
        syncFromPlayer()
    }

    fun playShuffled(songs: List<Song>) {
        if (songs.isEmpty()) return
        mediaController?.shuffleModeEnabled = true
        playSongs(songs, 0)
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs.coerceAtLeast(0L))
        _positionMs.value = positionMs.coerceAtLeast(0L)
    }

    fun skipToNext() {
        mediaController?.seekToNextMediaItem()
    }

    fun skipToPrevious() {
        mediaController?.seekToPreviousMediaItem()
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
    }

    fun cycleRepeatMode() {
        val controller = mediaController ?: return
        controller.repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun restartCurrentSong() {
        val controller = mediaController ?: return
        controller.seekTo(0L)
        controller.play()
        _positionMs.value = 0L
    }

    fun previousOrRestart() {
        val controller = mediaController ?: return
        if (controller.currentPosition > 3000L) {
            restartCurrentSong()
        } else {
            skipToPrevious()
        }
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _volume.value = clamped
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val scaledVolume = (clamped * maxVolume).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, scaledVolume, 0)
    }

    fun getVolume(): Float {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return if (maxVolume > 0) currentVolume.toFloat() / maxVolume.toFloat() else 1.0f
    }

    fun startSleepTimer(durationMs: Long, scope: CoroutineScope = CoroutineScope(Dispatchers.Default)) {
        cancelSleepTimer()
        _sleepTimerRemainingMs.value = durationMs
        sleepTimerJob = scope.launch {
            var remaining = durationMs
            while (remaining > 0 && isActive) {
                delay(1000)
                remaining -= 1000
                _sleepTimerRemainingMs.value = remaining.coerceAtLeast(0L)
            }
            if (_sleepTimerRemainingMs.value <= 0) {
                mediaController?.pause()
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemainingMs.value = 0L
    }

    fun updatePosition() {
        syncPosition()
    }

    private fun syncFromPlayer() {
        val controller = mediaController ?: return
        val queue = buildQueue(controller)
        val currentIndex = controller.currentMediaItemIndex.coerceAtLeast(0)
        val currentSong = queue.getOrNull(currentIndex)
        _uiState.value = PlayerUiState(
            currentSong = currentSong,
            isPlaying = controller.isPlaying,
            durationMs = controller.duration.coerceAtLeast(0L),
            queue = queue,
            currentIndex = currentIndex,
            shuffleEnabled = controller.shuffleModeEnabled,
            repeatMode = controller.repeatMode
        )
        _positionMs.value = controller.currentPosition.coerceAtLeast(0L)
    }

    private fun syncPosition() {
        val controller = mediaController ?: return
        _positionMs.value = controller.currentPosition.coerceAtLeast(0L)
    }

    private fun buildQueue(controller: MediaController): List<Song> {
        val items = mutableListOf<Song>()
        for (index in 0 until controller.mediaItemCount) {
            val mediaId = controller.getMediaItemAt(index).mediaId
            songsByMediaId[mediaId]?.let { items += it }
        }
        if (items.isNotEmpty()) return items
        return _uiState.value.queue
    }
}
