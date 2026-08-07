package com.localmusic.app.player

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.util.Log
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
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    /** 播放倍速：1.0f = 正常，0.5f = 半速，2.0f = 二倍速。 */
    val playbackSpeed: Float = 1.0f
)

class PlayerController(private val context: Context) {

    companion object {
        private const val TAG = "PlayerController"
    }

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

    /** 每次开始播放一首新歌时触发（切歌/自动下一首都算），用于记录"最近播放"。 */
    var onSongStarted: ((Song) -> Unit)? = null

    private var wasPlaying = false
    private var lastSongCompletedId: Long = -1L
    private var lastSongStartedId: Long = -1L

    /**
     * 更新播放队列中某首歌的数据（改名后同步刷新）。
     *
     * 兜底机制：
     *  1. 更新 songsByMediaId 缓存 + UI 状态中的 queue / currentSong
     *  2. 同步 ExoPlayer 队列中的 MediaItem URI——如果 URI 变化（改名），
     *     用 replaceMediaItem 替换，正在播放的歌曲自动用新路径续播
     */
    fun updateSongInQueue(updated: Song) {
        val mediaId = updated.id.toString()
        // 先取旧歌曲数据，用于判断 URI 是否变化
        val oldSong = songsByMediaId[mediaId]
        val uriChanged = oldSong == null || oldSong.uri != updated.uri

        songsByMediaId = songsByMediaId.toMutableMap().apply {
            put(mediaId, updated)
        }

        // 同步 ExoPlayer 队列中的 MediaItem URI（改名后关键）
        val controller = mediaController
        if (controller != null && uriChanged) {
            for (index in 0 until controller.mediaItemCount) {
                val item = controller.getMediaItemAt(index)
                if (item.mediaId == mediaId) {
                    Log.i(TAG, "updateSongInQueue: URI 变化 index=$index old=${oldSong?.uri} new=${updated.uri}")
                    val isCurrent = index == controller.currentMediaItemIndex
                    val wasPlaying = controller.isPlaying
                    val position = if (isCurrent) controller.currentPosition else 0L

                    val newItem = MediaItem.Builder()
                        .setMediaId(mediaId)
                        .setUri(updated.uri)
                        .setMediaMetadata(
                            androidx.media3.common.MediaMetadata.Builder()
                                .setTitle(updated.title)
                                .setArtist(updated.artist)
                                .setAlbumTitle(updated.album)
                                .build()
                        )
                        .build()
                    controller.replaceMediaItem(index, newItem)
                    if (isCurrent && wasPlaying) {
                        // 正在播放的歌曲改名后用新路径续播（保持播放位置）
                        controller.prepare()
                        controller.seekTo(position)
                        controller.play()
                        Log.i(TAG, "updateSongInQueue: 已用新 URI 续播 position=$position")
                    }
                    break
                }
            }
        }

        // 更新 UI 状态
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
                    Player.EVENT_REPEAT_MODE_CHANGED,
                    Player.EVENT_PLAYBACK_PARAMETERS_CHANGED
                )
            ) {
                syncFromPlayer()
                // 记录最近播放：当前歌曲 id 变化且正在播放（开始播放/切歌/自动下一首）
                if (nowPlaying) {
                    _uiState.value.currentSong?.let { song ->
                        if (song.id != lastSongStartedId) {
                            lastSongStartedId = song.id
                            onSongStarted?.invoke(song)
                        }
                    }
                }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            val current = _uiState.value.currentSong
            Log.e(TAG, "播放错误: ${error.message}, song=${current?.title}, uri=${current?.uri}")
            Log.e(TAG, "错误码: ${error.errorCode}, 原因: ${error.cause?.javaClass?.simpleName}")

            // 提供单曲异常容错：跳过出错歌曲，跳到下一首继续播放。
            // 只有当队列里确实还有下一首时才跳过；否则停止并清空队列。
            val controller = mediaController
            val queue = _uiState.value.queue
            if (controller != null) {
                val hasNext = queue.any { it.id != current?.id } ||
                    controller.mediaItemCount > 1
                if (hasNext) {
                    Log.i(TAG, "播放错误，自动跳过该曲目，继续播放下一首")
                    _positionMs.value = 0L
                    // 若错误来源于当前条目本身，ExoPlayer 可能已处于 error 状态，
                    // 通过 seekToNextMediaItem 让播放器自己处理转移
                    try {
                        controller.seekToNextMediaItem()
                    } catch (e: Exception) {
                        Log.e(TAG, "seekToNextMediaItem 失败", e)
                        controller.stop()
                        controller.clearMediaItems()
                    }
                    _uiState.value = _uiState.value.copy(isPlaying = false)
                    return
                }
                Log.i(TAG, "队列无可切换歌曲，停止播放")
                controller.stop()
                controller.clearMediaItems()
            }
            wasPlaying = false
            _uiState.value = _uiState.value.copy(
                isPlaying = false,
                currentSong = _uiState.value.currentSong
            )
            _positionMs.value = 0L
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
        val controller = mediaController ?: run {
            Log.e(TAG, "playSongs: mediaController 未连接")
            return
        }
        val song = songs.getOrNull(startIndex)
        Log.i(TAG, "playSongs: startIndex=$startIndex, totalSongs=${songs.size}, uri=${song?.uri}")

        songsByMediaId = songs.associateBy { it.id.toString() }

        // 关键：完全重置播放器，清除任何残留状态或错误
        // 即使 ExoPlayer 处于 STATE_ERROR / STATE_IDLE，stop + clearMediaItems 也能将其重置
        // 后续 setMediaItems + prepare 会重新创建播放管线，等效于"异常状态重建"
        val hadError = controller.playerError != null
        if (hadError) {
            Log.w(TAG, "playSongs: 检测到播放器处于错误状态，执行重建")
        }
        controller.stop()
        controller.clearMediaItems()

        val mediaItems = songs.map { buildMediaItem(it) }
        lastSongCompletedId = -1L
        lastSongStartedId = -1L
        controller.setMediaItems(mediaItems, startIndex.coerceIn(0, songs.lastIndex), 0L)
        controller.prepare()
        controller.play()
        Log.i(TAG, "playSongs: 已调用 setMediaItems + prepare + play, state=${controller.playbackState}, hadError=$hadError")
        syncFromPlayer()
    }

    /** 由 [Song] 构造 ExoPlayer 的 [MediaItem]，URI 与元数据一并写入。 */
    private fun buildMediaItem(song: Song): MediaItem =
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

    fun playShuffled(songs: List<Song>) {
        if (songs.isEmpty()) return
        mediaController?.shuffleModeEnabled = true
        playSongs(songs, 0)
    }

    // ---------- 播放队列管理 ----------

    /**
     * 从播放队列中移除一首歌（不删除曲库数据）。
     * 移除当前播放歌曲时由 ExoPlayer 自动切到下一首。
     */
    fun removeFromQueue(songId: Long) {
        val controller = mediaController ?: return
        val mediaId = songId.toString()
        for (index in 0 until controller.mediaItemCount) {
            if (controller.getMediaItemAt(index).mediaId == mediaId) {
                controller.removeMediaItem(index)
                songsByMediaId = songsByMediaId - mediaId
                syncFromPlayer()
                // 若队列已空则停止播放
                if (controller.mediaItemCount == 0) {
                    controller.stop()
                    _uiState.value = PlayerUiState()
                    _positionMs.value = 0L
                }
                return
            }
        }
    }

    /** 清空整个播放队列并停止播放。 */
    fun clearQueue() {
        val controller = mediaController ?: return
        songsByMediaId = emptyMap()
        controller.stop()
        controller.clearMediaItems()
        _uiState.value = PlayerUiState()
        _positionMs.value = 0L
    }

    /** 将 [song] 插入到当前播放曲目之后（"下一首播放"），不打断当前播放。 */
    fun playNextInQueue(song: Song) {
        val controller = mediaController ?: return
        if (controller.mediaItemCount == 0) {
            playSongs(listOf(song), 0)
            return
        }
        val insertIndex = (controller.currentMediaItemIndex + 1).coerceAtMost(controller.mediaItemCount)
        songsByMediaId = songsByMediaId + (song.id.toString() to song)
        controller.addMediaItem(insertIndex, buildMediaItem(song))
        syncFromPlayer()
    }

    // ---------- 倍速播放 ----------

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.25f, 3.0f)
        mediaController?.setPlaybackSpeed(clamped)
        _uiState.value = _uiState.value.copy(playbackSpeed = clamped)
    }

    fun getPlaybackSpeed(): Float {
        return mediaController?.playbackParameters?.speed ?: _uiState.value.playbackSpeed
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
        Log.d(TAG, "syncFromPlayer: state=${controller.playbackState}, isPlaying=${controller.isPlaying}, index=$currentIndex, duration=${controller.duration}, song=${currentSong?.title}, error=${controller.playerError?.message}")
        _uiState.value = PlayerUiState(
            currentSong = currentSong,
            isPlaying = controller.isPlaying,
            durationMs = controller.duration.coerceAtLeast(0L),
            queue = queue,
            currentIndex = currentIndex,
            shuffleEnabled = controller.shuffleModeEnabled,
            repeatMode = controller.repeatMode,
            playbackSpeed = controller.playbackParameters.speed
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
