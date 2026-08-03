package com.localmusic.app.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localmusic.app.LocalMusicApp
import com.localmusic.app.data.model.Song
import com.localmusic.app.player.PlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LocalMusicApp
    val playerController: PlayerController = app.playerController

    val uiState = playerController.uiState

    val positionMs = playerController.positionMs
    val volume = playerController.volume
    val sleepTimerRemainingMs = playerController.sleepTimerRemainingMs

    private var tickerJob: Job? = null
    private var watcherJob: Job? = null

    fun connect() {
        viewModelScope.launch {
            playerController.connect()
            // 监听播放状态：播放时启动进度更新，暂停/停止时取消，避免无意义后台唤醒。
            watchPlayingState()
        }
    }

    fun disconnect() {
        tickerJob?.cancel()
        watcherJob?.cancel()
        playerController.disconnect()
    }

    /** 监听 isPlaying 变化：播放中按 1Hz 刷新进度；暂停/停止时停掉 ticker，不再周期唤醒。 */
    private fun watchPlayingState() {
        watcherJob?.cancel()
        watcherJob = viewModelScope.launch {
            var wasPlaying = false
            uiState.collect { state ->
                if (state.isPlaying && !wasPlaying) {
                    startTicker()
                } else if (!state.isPlaying && wasPlaying) {
                    tickerJob?.cancel()
                    tickerJob = null
                }
                wasPlaying = state.isPlaying
            }
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                playerController.updatePosition()
                delay(1000)
            }
        }
    }

    /**
     * 播放歌曲列表——播放前实时查询数据库最新歌曲数据，防止改名后 URI 失效。
     * 点击仅传递 songId / index，由 ViewModel 负责拉取最新数据。
     */
    fun playSongs(songs: List<Song>, startIndex: Int = 0) {
        viewModelScope.launch {
            val freshSongs = refreshSongsFromDb(songs)
            if (freshSongs.isEmpty()) return@launch
            // URI 可读预检：只记录警告，不阻断播放（ExoPlayer 会自行处理失败，onPlayerError 会捕获）
            // 阻断会导致 currentSong 不更新，选中 UI 状态丢失
            val targetSong = freshSongs.getOrNull(startIndex)
            if (targetSong != null && !isUriReadable(targetSong.uri)) {
                android.util.Log.w("PlayerViewModel", "playSongs: URI 预检不可读，仍尝试播放 songId=${targetSong.id} uri=${targetSong.uri}")
            }
            playerController.playSongs(freshSongs, startIndex)
        }
    }

    fun playShuffled(songs: List<Song>) {
        viewModelScope.launch {
            val freshSongs = refreshSongsFromDb(songs)
            if (freshSongs.isEmpty()) return@launch
            playerController.playShuffled(freshSongs)
        }
    }

    /**
     * 从数据库实时查询最新歌曲数据，保持原列表顺序。
     * 改名后 URI 已通过原子事务写入数据库，此处确保播放器拿到的是最新 URI。
     */
    private suspend fun refreshSongsFromDb(songs: List<Song>): List<Song> {
        if (songs.isEmpty()) return songs
        return withContext(Dispatchers.IO) {
            val freshList = app.repository.getSongsByIds(songs.map { it.id })
            val freshMap = freshList.associateBy { it.id }
            // 保持原顺序，过滤掉已从数据库删除的歌曲
            songs.mapNotNull { s -> freshMap[s.id] }
        }
    }

    /** SAF URI 可读预检：openInputStream 成功即视为有效（轻量检查，不读取文件内容）。 */
    private suspend fun isUriReadable(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            app.contentResolver.openInputStream(uri)?.use { } != null
        }.getOrDefault(false)
    }

    fun togglePlayPause() = playerController.togglePlayPause()
    fun seekTo(positionMs: Long) = playerController.seekTo(positionMs)
    fun skipToNext() = playerController.skipToNext()
    fun skipToPrevious() = playerController.skipToPrevious()
    fun toggleShuffle() = playerController.toggleShuffle()
    fun cycleRepeatMode() = playerController.cycleRepeatMode()
    fun restartCurrentSong() = playerController.restartCurrentSong()
    fun previousOrRestart() = playerController.previousOrRestart()
    fun setVolume(volume: Float) = playerController.setVolume(volume)
    fun getVolume() = playerController.getVolume()
    fun startSleepTimer(minutes: Long) {
        playerController.startSleepTimer(minutes * 60_000L, viewModelScope)
    }
    fun cancelSleepTimer() = playerController.cancelSleepTimer()

    /** 设置歌曲播放完成回调——由 MainActivity 注入，用于递增播放次数等。 */
    fun setOnSongCompletedListener(listener: ((Song) -> Unit)?) {
        playerController.onSongCompleted = listener
    }

    /** 数据库侧 playCount/favorite 更新后，同步到播放器内存，保证 NowPlaying/队列页立即可见。 */
    fun updateSongInQueue(song: Song) {
        playerController.updateSongInQueue(song)
    }
}
