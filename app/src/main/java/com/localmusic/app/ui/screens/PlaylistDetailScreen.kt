package com.localmusic.app.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localmusic.app.ui.components.SongListItem
import com.localmusic.app.ui.components.SongMenuItem
import com.localmusic.app.ui.viewmodel.PlaylistDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    viewModel: PlaylistDetailViewModel,
    onBack: () -> Unit,
    onSongClick: (Int) -> Unit,
    onEditSong: (com.localmusic.app.data.model.Song) -> Unit,
    onToggleFavorite: (com.localmusic.app.data.model.Song) -> Unit,
    onShareSong: (com.localmusic.app.data.model.Song) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onAddSongs: () -> Unit,
    onPickFiles: () -> Unit,
    onPickFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(uiState.playlistName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 头部
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = uiState.playlistName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "共 ${uiState.songs.size} 首歌曲",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (uiState.songs.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(onClick = onPlayAll, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text("  播放全部")
                        }
                        OutlinedButton(onClick = onShuffle, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Shuffle, contentDescription = null)
                            Text("  随机播放")
                        }
                    }
                }
            }

            if (uiState.songs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LibraryMusic,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (uiState.isFavorites) "主收藏还是空的"
                            else "歌单还是空的",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (uiState.isFavorites) "从手机本地导入音乐文件或文件夹"
                            else "从曲库挑选歌曲添加进来",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        if (uiState.isFavorites) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TextButton(onClick = onPickFiles) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null)
                                    Text("  选择文件")
                                }
                                TextButton(onClick = onPickFolder) {
                                    Icon(Icons.Default.Folder, contentDescription = null)
                                    Text("  选择文件夹")
                                }
                            }
                        } else {
                            Button(onClick = onAddSongs) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Text("  添加歌曲")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    itemsIndexed(
                        uiState.songs,
                        key = { _, s -> s.id },
                        contentType = { _, _ -> "song" }
                    ) { index, song ->
                        val onClick = { onSongClick(index) }
                        val onFavClick = { onToggleFavorite(song) }
                        val menuItems = remember(song) {
                            listOf(
                                SongMenuItem("分享") { onShareSong(song) },
                                SongMenuItem("编辑信息") { onEditSong(song) },
                                SongMenuItem("移除") { viewModel.removeSong(song.id) }
                            )
                        }
                        SongListItem(
                            song = song,
                            onClick = onClick,
                            onToggleFavorite = onFavClick,
                            menuItems = menuItems
                        )
                    }
                }
            }
        }
    }
}
