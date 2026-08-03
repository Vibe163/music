package com.localmusic.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.localmusic.app.player.PlayerUiState
import com.localmusic.app.ui.components.SongListItem
import com.localmusic.app.ui.viewmodel.PlayerViewModel
import com.localmusic.app.util.formatDuration
import androidx.palette.graphics.Palette
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onToggleFavorite: (() -> Unit)? = null,
    onShareSong: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val playerState by viewModel.uiState.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val volume by viewModel.volume.collectAsState()
    val sleepRemaining by viewModel.sleepTimerRemainingMs.collectAsState()
    val song = playerState.currentSong

    var showQueue by remember { mutableStateOf(false) }
    var showVolume by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var sleepSelectedOption by remember { mutableStateOf(-1) }

    var dominantColor by remember { mutableStateOf(Color(0xFF1E1E1E)) }
    var vibrantColor by remember { mutableStateOf(Color(0xFF4A90E2)) }

    val context = LocalContext.current
    val imageLoader = remember { coil.ImageLoader.Builder(context).build() }

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val coverSizeDp = (screenWidthDp * 0.65f).coerceAtMost(340f).coerceAtLeast(220f)

    LaunchedEffect(song?.albumArtPath) {
        val artPath = song?.albumArtPath
        if (!artPath.isNullOrBlank()) {
            val request = ImageRequest.Builder(context).data(artPath).build()
            val result = imageLoader.execute(request)
            val drawable = result.drawable
            if (drawable != null) {
                val bitmap = drawable.toBitmap()
                val palette = Palette.from(bitmap).generate()
                val dom = palette.getDominantColor(android.graphics.Color.parseColor("#1E1E1E"))
                val vib = palette.getVibrantColor(android.graphics.Color.parseColor("#4A90E2"))
                dominantColor = Color(dom)
                vibrantColor = Color(vib)
            }
        }
    }

    val gradientBrush = remember(dominantColor, vibrantColor) {
        Brush.verticalGradient(
            colors = listOf(
                dominantColor.copy(alpha = 0.95f),
                dominantColor.copy(alpha = 0.88f),
                Color.Black.copy(alpha = 0.95f)
            )
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        AlbumBackground(
            artPath = song?.albumArtPath,
            dominantColor = dominantColor,
            vibrantColor = vibrantColor
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (song?.album.isNullOrBlank() || song?.album == "未知专辑") "正在播放"
                                else song?.album ?: "正在播放",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White)
                        }
                    },
                    actions = {
                        if (onShareSong != null) {
                            IconButton(onClick = onShareSong) {
                                Icon(Icons.Default.Share, "分享", tint = Color.White)
                            }
                        }
                        IconButton(onClick = { showQueue = true }) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, "播放队列", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            if (song == null) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无播放歌曲", color = Color.White.copy(alpha = 0.7f))
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Cover with swipe gestures
                    CoverWithSwipe(
                        artPath = song.albumArtPath,
                        vibrantColor = vibrantColor,
                        isPlaying = playerState.isPlaying,
                        coverSizeDp = coverSizeDp,
                        onSwipeLeft = { viewModel.skipToNext() },
                        onSwipeRight = { viewModel.previousOrRestart() }
                    )

                    Spacer(Modifier.height(28.dp))

                    // Song info
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            lineHeight = 28.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = buildString {
                                append(song.artist.ifBlank { "未知艺术家" })
                                if (song.album.isNotBlank() && song.album != "未知专辑") {
                                    append(" · ")
                                    append(song.album)
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            modifier = Modifier.padding(top = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (onToggleFavorite != null) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.12f),
                                    modifier = Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = onToggleFavorite
                                    )
                                ) {
                                    Icon(
                                        imageVector = if (song.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "喜欢",
                                        tint = if (song.favorite) Color(0xFFFF6B6B) else Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.padding(10.dp).size(22.dp)
                                    )
                                }
                            }
                            if (song.playCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White.copy(alpha = 0.1f)
                                ) {
                                    Text(
                                        text = "▶ ${song.playCount}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White.copy(alpha = 0.75f),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // Progress bar
                    ProgressBar(
                        positionMs = positionMs,
                        durationMs = playerState.durationMs,
                        onSeek = viewModel::seekTo,
                        vibrantColor = vibrantColor
                    )

                    Spacer(Modifier.height(20.dp))

                    // Controls
                    ControlsRow(
                        playerState = playerState,
                        viewModel = viewModel,
                        vibrantColor = vibrantColor,
                        onSleepTimerClick = { showSleepTimer = true },
                        onVolumeClick = { showVolume = true },
                        sleepRemaining = sleepRemaining
                    )

                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    // Queue bottom sheet
    if (showQueue && playerState.queue.isNotEmpty()) {
        QueueSheet(
            queue = playerState.queue,
            currentIndex = playerState.currentIndex,
            onDismiss = { showQueue = false },
            onSongClick = { index ->
                viewModel.playSongs(playerState.queue, index)
                showQueue = false
            }
        )
    }

    // Volume sheet
    if (showVolume) {
        VolumeSheet(
            currentVolume = volume,
            onVolumeChange = { viewModel.setVolume(it) },
            onDismiss = { showVolume = false }
        )
    }

    // Sleep timer sheet
    if (showSleepTimer) {
        SleepTimerSheet(
            selectedOption = sleepSelectedOption,
            onSelect = { minutes ->
                sleepSelectedOption = minutes
                if (minutes > 0) {
                    viewModel.startSleepTimer(minutes.toLong())
                } else {
                    viewModel.cancelSleepTimer()
                }
                showSleepTimer = false
            },
            currentRemainingMs = sleepRemaining,
            onCancel = {
                viewModel.cancelSleepTimer()
                showSleepTimer = false
            },
            onDismiss = { showSleepTimer = false }
        )
    }
}

@Composable
private fun CoverWithSwipe(
    artPath: String?,
    vibrantColor: Color,
    isPlaying: Boolean,
    coverSizeDp: Float,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
) {
    var offsetX by remember { mutableStateOf(0f) }
    var isUserInteracting by remember { mutableStateOf(false) }

    val rotation by animateFloatAsState(
        targetValue = if (isPlaying && !isUserInteracting) 360f else 0f,
        animationSpec = if (isPlaying) {
            infiniteRepeatable(
                animation = tween(durationMillis = 20000, easing = androidx.compose.animation.core.LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        } else {
            tween(300)
        },
        label = "cover_rotation"
    )

    Box(
        modifier = Modifier
            .size(coverSizeDp.dp)
            .shadow(24.dp, RoundedCornerShape(28.dp), clip = false)
            .graphicsLayer {
                rotationZ = rotation
                translationX = offsetX
                alpha = 1f - (abs(offsetX) / 600f)
            }
            .clip(RoundedCornerShape(28.dp))
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { isUserInteracting = true },
                    onDragEnd = {
                        isUserInteracting = false
                        offsetX = 0f
                    },
                    onDragCancel = {
                        isUserInteracting = false
                        offsetX = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val newOffset = (offsetX + dragAmount).coerceIn(-400f, 400f)
                        offsetX = newOffset
                        if (offsetX < -120f) {
                            onSwipeLeft()
                            offsetX = 0f
                        } else if (offsetX > 120f) {
                            onSwipeRight()
                            offsetX = 0f
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            vibrantColor.copy(alpha = 0.75f),
                            vibrantColor.copy(alpha = 0.35f),
                            vibrantColor.copy(alpha = 0.15f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!artPath.isNullOrBlank()) {
                AsyncImage(
                    model = artPath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                        .clip(RoundedCornerShape(24.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    vibrantColor.copy(alpha = 0.6f),
                                    vibrantColor.copy(alpha = 0.2f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size((coverSizeDp * 0.45f).dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumBackground(
    artPath: String?,
    dominantColor: Color,
    vibrantColor: Color
) {
    Box(Modifier.fillMaxSize()) {
        if (!artPath.isNullOrBlank()) {
            AsyncImage(
                model = artPath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(80.dp)
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(
                        dominantColor.copy(alpha = 0.85f),
                        Color.Black.copy(alpha = 0.92f)
                    )
                )
            )
        )
        Box(
            Modifier.fillMaxWidth().height(260.dp).background(
                Brush.verticalGradient(
                    colors = listOf(vibrantColor.copy(alpha = 0.25f), Color.Transparent)
                )
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgressBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    vibrantColor: Color
) {
    val duration = durationMs.coerceAtLeast(1L)
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }
    val current = if (dragging) dragValue else positionMs.toFloat().coerceIn(0f, duration.toFloat())

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = current,
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                onSeek(dragValue.toLong().coerceAtLeast(0L))
                dragging = false
            },
            valueRange = 0f..duration.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = vibrantColor,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth(),
            thumb = {
                Box(
                    modifier = Modifier
                        .shadow(4.dp, CircleShape, clip = false)
                        .clip(CircleShape)
                        .background(Color.White)
                        .size(16.dp)
                )
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(if (dragging) dragValue.toLong() else positionMs),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp
            )
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ControlsRow(
    playerState: PlayerUiState,
    viewModel: PlayerViewModel,
    vibrantColor: Color,
    onSleepTimerClick: () -> Unit,
    onVolumeClick: () -> Unit,
    sleepRemaining: Long
) {
    val activeColor = vibrantColor
    val inactiveColor = Color.White.copy(alpha = 0.65f)

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Shuffle
        ControlButton(
            onClick = viewModel::toggleShuffle,
            icon = Icons.Default.Shuffle,
            contentDescription = "随机播放",
            isActive = playerState.shuffleEnabled,
            activeColor = activeColor,
            inactiveColor = inactiveColor,
            size = 40.dp,
            iconSize = 22.dp
        )

        // Previous (smart: restart or previous)
        ControlButton(
            onClick = viewModel::previousOrRestart,
            icon = Icons.Default.SkipPrevious,
            contentDescription = "上一首/重放",
            isActive = true,
            activeColor = activeColor,
            inactiveColor = inactiveColor,
            size = 48.dp,
            iconSize = 32.dp
        )

        // Play/Pause
        PlayPauseButton(
            isPlaying = playerState.isPlaying,
            onClick = viewModel::togglePlayPause,
            activeColor = activeColor
        )

        // Next
        ControlButton(
            onClick = viewModel::skipToNext,
            icon = Icons.Default.SkipNext,
            contentDescription = "下一首",
            isActive = true,
            activeColor = activeColor,
            inactiveColor = inactiveColor,
            size = 48.dp,
            iconSize = 32.dp
        )

        // Repeat
        ControlButton(
            onClick = viewModel::cycleRepeatMode,
            icon = if (playerState.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
            contentDescription = "循环模式",
            isActive = playerState.repeatMode != Player.REPEAT_MODE_OFF,
            activeColor = activeColor,
            inactiveColor = inactiveColor,
            size = 40.dp,
            iconSize = 22.dp
        )
    }

    // Bottom row: volume + sleep timer
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        BottomActionButton(
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            label = "音量",
            onClick = onVolumeClick,
            activeColor = activeColor
        )
        BottomActionButton(
            icon = Icons.Default.Schedule,
            label = if (sleepRemaining > 0) formatSleepRemaining(sleepRemaining) else "定时",
            onClick = onSleepTimerClick,
            activeColor = if (sleepRemaining > 0) activeColor else inactiveColor,
            isActive = sleepRemaining > 0
        )
    }
}

private fun formatSleepRemaining(ms: Long): String {
    val minutes = ms / 60000
    return "${minutes}分钟后"
}

@Composable
private fun ControlButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isActive: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.82f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = 400f
        ),
        label = "scale"
    )
    val color by animateColorAsState(
        targetValue = if (isActive) activeColor else inactiveColor,
        animationSpec = androidx.compose.animation.core.tween(150),
        label = "color"
    )

    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = color, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    activeColor: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
            stiffness = 400f
        ),
        label = "play_scale"
    )

    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(16.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(activeColor, activeColor.copy(alpha = 0.85f))
                )
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .size(72.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "暂停" else "播放",
            tint = Color.White,
            modifier = Modifier.size(38.dp)
        )
    }
}

@Composable
private fun BottomActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    activeColor: Color,
    isActive: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = 400f
        ),
        label = "bottom_btn_scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) activeColor else Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) activeColor else Color.White.copy(alpha = 0.65f),
            fontSize = 11.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(
    queue: List<com.localmusic.app.data.model.Song>,
    currentIndex: Int,
    onDismiss: () -> Unit,
    onSongClick: (Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "播放队列 (${queue.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            LazyColumn(modifier = Modifier.fillMaxHeight(0.6f)) {
                itemsIndexed(queue, key = { _, s -> s.id }, contentType = { _, _ -> "song" }) { index, queueSong ->
                    val onClick = remember(index) { { onSongClick(index) } }
                    SongListItem(
                        song = queueSong,
                        isActive = index == currentIndex,
                        onClick = onClick
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeSheet(
    currentVolume: Float,
    onVolumeChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = "音量控制",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = currentVolume,
                    onValueChange = onVolumeChange,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerSheet(
    selectedOption: Int,
    onSelect: (Int) -> Unit,
    currentRemainingMs: Long,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val options = listOf(5, 10, 15, 30, 60)
    val labels = listOf("5 分钟", "10 分钟", "15 分钟", "30 分钟", "60 分钟")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = "定时关闭",
                style = MaterialTheme.typography.titleMedium
            )
            if (currentRemainingMs > 0) {
                Text(
                    text = "已设置：${currentRemainingMs / 60000} 分钟后自动停止",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            options.forEachIndexed { index, minutes ->
                val isSelected = selectedOption == minutes
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        )
                        .clickable { onSelect(minutes) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = labels[index],
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                    if (isSelected) {
                        Text(
                            text = "✓",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            if (currentRemainingMs > 0) {
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("取消定时", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
