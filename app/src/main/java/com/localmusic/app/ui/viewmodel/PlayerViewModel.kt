package com.localmusic.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localmusic.app.LocalMusicApp
import com.localmusic.app.data.model.Song
import com.localmusic.app.player.PlayerController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

    fun playSongs(songs: List<Song>, startIndex: Int = 0) {
        playerController.playSongs(songs, startIndex)
    }

    fun playShuffled(songs: List<Song>) {
        playerController.playShuffled(songs)
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
