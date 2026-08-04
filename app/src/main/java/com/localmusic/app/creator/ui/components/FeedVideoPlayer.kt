package com.localmusic.app.creator.ui.components

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

/**
 * 抖音风格视频播放组件
 *
 * - 保持原始比例（RESIZE_MODE_FIT，不裁剪不拉伸）
 * - 视频原声正常播放
 * - 自动循环播放
 * - 离开页面自动释放
 *
 * 隔离：独立 ExoPlayer 实例，不复用 MusicPlayerService
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun FeedVideoPlayer(
    videoUri: String,
    isCurrentPage: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }

    LaunchedEffect(videoUri) {
        player?.release()
        hasError = false
        isLoading = true
        val newPlayer = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUri)))
            volume = 1f // 视频原声正常播放
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
            playWhenReady = isCurrentPage
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) isLoading = false
                    if (state == Player.STATE_IDLE) hasError = true
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    hasError = true
                    isLoading = false
                }
            })
        }
        player = newPlayer
        // 3秒超时兜底：还没ready就当失败，避免一直loading全黑
        delay(3000)
        if (isLoading) {
            isLoading = false
            hasError = true
        }
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A)),
        contentAlignment = Alignment.Center
    ) {
        player?.let { p ->
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = p
                        useController = false // 隐藏播放控制器
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT // 保持原始比例，不裁剪不拉伸
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                },
                update = { view ->
                    view.player = p
                    view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Loading 中显示转圈
        if (isLoading && !hasError) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(36.dp),
                strokeWidth = 3.dp
            )
        }
        // 加载失败/无媒体文件 → 显示灰色占位 + 提示
        if (hasError) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Filled.Movie,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(60.dp)
                )
                Text(
                    text = "视频",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
