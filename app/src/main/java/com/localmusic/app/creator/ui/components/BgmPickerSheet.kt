package com.localmusic.app.creator.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localmusic.app.creator.viewmodel.CreatorViewModel
import com.localmusic.app.data.model.FAVORITES_PLAYLIST_ID
import com.localmusic.app.data.model.PlaylistWithCount
import com.localmusic.app.data.model.Song
import kotlin.math.roundToInt

/**
 * BGM 选择弹窗（1:1 对齐抖音「添加配音」面板截图）
 *
 * 核心设计：不使用独立试听播放器，直接复用编辑页的 editBgmPlayer。
 * 点击歌曲 = 选中 + 切换 editBgmPlayer 播放（唯一音频源，无混音）。
 *
 * 交互：
 *   - 整条横条点击 → onSongSelect(song) → 编辑页切歌并播放
 *   - 同一首再次点击 → onTogglePlayback() → 编辑页播放/暂停
 *   - 选中即刻生效，不关闭 BottomSheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BgmPickerSheet(
    viewModel: CreatorViewModel,
    onSongSelect: (Song) -> Unit,
    onTogglePlayback: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val playlists by viewModel.playlists.collectAsState()
    val draftBgm by viewModel.draftBgm.collectAsState()

    // 当前选中歌单（默认第一个=主收藏）
    var selectedPlaylistId by remember { mutableStateOf(playlists.firstOrNull()?.id ?: FAVORITES_PLAYLIST_ID) }
    var currentPlaylistSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loadingSongs by remember { mutableStateOf(false) }

    // 当前选中的歌曲ID = draftBgm 的 ID（单一数据源）
    val selectedSongId = draftBgm?.id ?: -1L
    // 是否正在播放（draftBgm 非空即认为编辑页在播，暂停状态由 isPlayingBgm 外部传入）
    // 简化：只要 draftBgm 非空就显示波形动画
    val isPlaying = draftBgm != null

    // 每次选中歌单 → 加载对应歌曲
    LaunchedEffect(selectedPlaylistId) {
        val pid = selectedPlaylistId ?: return@LaunchedEffect
        loadingSongs = true
        currentPlaylistSongs = viewModel.getPlaylistSongs(pid)
        loadingSongs = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        scrimColor = Color(0x33000000),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(Color.White)
        ) {
            // ===== ① 顶部小灰拖柄条 =====
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFD9D9D9))
                )
            }

            // ===== ② 歌单Tab =====
            val tabRowScroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp)
                    .horizontalScroll(tabRowScroll),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                playlists.forEach { pl ->
                    PlaylistTab(
                        playlist = pl,
                        isSelected = selectedPlaylistId == pl.id,
                        onClick = { selectedPlaylistId = pl.id }
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            // ===== ③ 歌曲列表 =====
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
            ) {
                if (currentPlaylistSongs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (loadingSongs) "加载中…" else "该歌单暂无歌曲",
                            color = Color(0xFF999999),
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(currentPlaylistSongs, key = { it.id }) { song ->
                            val isSelected = song.id == selectedSongId
                            SongRow(
                                song = song,
                                isSelected = isSelected,
                                isPlaying = isSelected && isPlaying,
                                onClick = {
                                    if (song.id == selectedSongId) {
                                        // 同首歌：切换播放/暂停
                                        onTogglePlayback()
                                    } else {
                                        // 新歌：选中并播放
                                        onSongSelect(song)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // ===== ④ 底部工具栏 =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomTool(icon = {
                    Icon(Icons.Filled.Close, null, tint = Color(0xFF222222), modifier = Modifier.size(24.dp))
                }, label = "无原声", labelColor = Color(0xFF222222), labelWeight = FontWeight.SemiBold)
                BottomTool(icon = {
                    Icon(Icons.Filled.MusicNote, null, tint = Color(0xFF222222), modifier = Modifier.size(22.dp))
                }, label = "音乐开", labelColor = Color(0xFF222222), labelWeight = FontWeight.SemiBold)
                BottomTool(icon = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Box(Modifier.width(20.dp).height(2.dp).background(Color(0xFF666666), RoundedCornerShape(1.dp)))
                        Box(Modifier.width(16.dp).height(2.dp).background(Color(0xFF666666), RoundedCornerShape(1.dp)))
                        Box(Modifier.width(10.dp).height(2.dp).background(Color(0xFF666666), RoundedCornerShape(1.dp)))
                    }
                }, label = "音量")
            }
        }
    }
}

// =====================================================================
// 子组件
// =====================================================================

@Composable
private fun PlaylistTab(
    playlist: PlaylistWithCount,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = playlist.name,
            fontSize = if (isSelected) 17.sp else 16.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) Color(0xFF111111) else Color(0xFF5A5A5A)
        )
        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(22.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF111111))
            )
        } else {
            Spacer(Modifier.height(3.dp))
        }
    }
}

/**
 * 歌曲行（1:1截图）
 *  - 整条横条一个 clickable
 *  - 第一行：歌名（选中红色加粗 / 未选中黑色）+ 选中波形
 *  - 第二行：@艺术家 · 时长
 */
@Composable
private fun SongRow(
    song: Song,
    isSelected: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFFF375F))
            )
            Spacer(Modifier.width(12.dp))
        } else {
            Spacer(Modifier.width(15.dp))
        }

        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFEFEFEF))
                .border(
                    width = if (isSelected) 2.dp else 0.6.dp,
                    color = if (isSelected) Color(0xFFFF375F) else Color(0xFFE3E3E3),
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = Color(0xFFB0B0B0),
                modifier = Modifier.size(22.dp)
            )
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x55000000), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Pause,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = song.title.takeIf { it.isNotBlank() } ?: "未知歌曲",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) Color(0xFFFF375F) else Color(0xFF1A1A1A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isSelected) {
                    Spacer(Modifier.width(8.dp))
                    PlayingWaves(playing = isPlaying, red = true)
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("@", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF7A7A7A))
                Text(
                    text = song.artist.takeIf { it.isNotBlank() } ?: "未知艺术家",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF7A7A7A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text("·", fontSize = 13.sp, color = Color(0xFF999999))
                Text(formatDuration(song.duration), fontSize = 13.sp, color = Color(0xFF999999))
            }
        }
    }
}

@Composable
private fun PlayingWaves(playing: Boolean, red: Boolean) {
    val color = if (red) Color(0xFFFF375F) else Color(0xFF111111)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val trans = rememberInfiniteTransition(label = "wave")
        val heights = listOf(10.dp, 14.dp, 10.dp)
        val anim = if (playing) listOf(
            trans.animateFloat(0.35f, 1f, infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse), label = "w1"),
            trans.animateFloat(0.5f, 1f, infiniteRepeatable(tween(360, easing = LinearEasing), RepeatMode.Reverse), label = "w2"),
            trans.animateFloat(0.4f, 1f, infiniteRepeatable(tween(480, easing = LinearEasing), RepeatMode.Reverse), label = "w3")
        ) else null
        heights.forEachIndexed { i, h ->
            val f = anim?.get(i)?.value ?: 0.55f
            Box(
                modifier = Modifier
                    .width(2.2.dp)
                    .height(h * f)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun BottomTool(
    icon: @Composable () -> Unit,
    label: String,
    labelColor: Color = Color(0xFF777777),
    labelWeight: FontWeight = FontWeight.Normal
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Box(modifier = Modifier.size(26.dp), contentAlignment = Alignment.Center) { icon() }
        Text(label, fontSize = 12.sp, color = labelColor, fontWeight = labelWeight)
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000f).roundToInt().coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
