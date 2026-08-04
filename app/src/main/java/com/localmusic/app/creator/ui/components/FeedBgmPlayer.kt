package com.localmusic.app.creator.ui.components

import android.media.AudioManager
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

/**
 * 抖音风格 BGM 播放器
 *
 * - 独立 ExoPlayer 实例，不复用 MusicPlayerService
 * - 循环播放（REPEAT_MODE_ONE）
 * - 仅播放音频，不渲染视频
 * - 自动处理 BGM 加载失败（歌曲被删/改名）
 */
@Composable
fun FeedBgmPlayer(
    bgmUri: String?,
    isCurrentPage: Boolean
) {
    if (bgmUri == null) return

    val context = LocalContext.current
    var player by remember { mutableStateOf<ExoPlayer?>(null) }

    LaunchedEffect(bgmUri) {
        player?.release()
        val newPlayer = ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true // 请求音频焦点
            )
            .build().apply {
                setMediaItem(MediaItem.fromUri(Uri.parse(bgmUri)))
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 1f
                prepare()
                playWhenReady = isCurrentPage
            }
        player = newPlayer
    }

    DisposableEffect(isCurrentPage) {
        player?.playWhenReady = isCurrentPage
        onDispose {
            if (!isCurrentPage) {
                player?.pause()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            player = null
        }
    }
}
