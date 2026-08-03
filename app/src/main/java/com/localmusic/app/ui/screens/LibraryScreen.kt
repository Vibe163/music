package com.localmusic.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localmusic.app.data.model.FAVORITES_PLAYLIST_ID
import com.localmusic.app.data.model.PlaylistWithCount
import com.localmusic.app.data.model.Song
import com.localmusic.app.ui.components.SongListItem
import com.localmusic.app.ui.components.SongMenuItem
import com.localmusic.app.ui.viewmodel.LibraryViewModel
import com.localmusic.app.ui.viewmodel.SortMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onSongClick: (songId: Long) -> Unit,
    onSongAddToPlaylist: (Song) -> Unit,
    onEditSong: (Song) -> Unit,
    onRemoveSong: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onShareSong: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onAddSongsToPlaylist: () -> Unit,
    onPickFiles: () -> Unit,
    onPickFolder: () -> Unit,
    currentPlayingSongId: Long,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val displaySongs by viewModel.displaySongs.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val progress by viewModel.importProgress.collectAsState()
    var showAddMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val isFavorites = uiState.currentPlaylistId == FAVORITES_PLAYLIST_ID

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(uiState.currentPlaylistName) },
                actions = {
                    if (isFavorites) {
                        IconButton(onClick = onCreatePlaylist) {
                            Icon(Icons.Default.Create, contentDescription = "新建歌单")
                        }
                    }
                    Box {
                        IconButton(onClick = { showAddMenu = true }) {
                            Icon(Icons.Default.Add, contentDescription = "添加音乐")
                        }
                        if (showAddMenu) {
                            DropdownMenu(
                                expanded = showAddMenu,
                                onDismissRequest = { showAddMenu = false }
                            ) {
                                if (isFavorites) {
                                    DropdownMenuItem(
                                        text = { Text("选择文件") },
                                        leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null) },
                                        onClick = {
                                            showAddMenu = false
                                            onPickFiles()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("选择文件夹") },
                                        leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                                        onClick = {
                                            showAddMenu = false
                                            onPickFolder()
                                        }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("添加歌曲") },
                                        leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null) },
                                        onClick = {
                                            showAddMenu = false
                                            onAddSongsToPlaylist()
                                        }
                                    )
                                }
                            }
                        }
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
            PlaylistChipsRow(
                playlists = playlists,
                currentId = uiState.currentPlaylistId,
                onSelect = { viewModel.setCurrentPlaylist(it) },
                onCreateNew = onCreatePlaylist
            )

            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("搜索歌曲、艺术家、专辑") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            if (progress.running) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    val ratio = if (progress.total > 0)
                        progress.processed.toFloat() / progress.total
                    else 0f
                    LinearProgressIndicator(
                        progress = { ratio.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "正在导入 ${progress.processed}/${progress.total} …",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            } else {
                progress.lastResult?.let { result ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "已添加 ${result.added} 首，跳过 ${result.skipped} 首" +
                                if (result.failed > 0) "，失败 ${result.failed} 首" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = viewModel::clearImportResult) {
                            Text("知道了")
                        }
                    }
                }
            }

            when {
                displaySongs.isEmpty() && uiState.searchQuery.isBlank() -> {
                    if (isFavorites) {
                        EmptyLibraryState(onPickFiles = onPickFiles, onPickFolder = onPickFolder)
                    } else {
                        EmptyPlaylistState(
                            playlistName = uiState.currentPlaylistName,
                            onAddSongs = onAddSongsToPlaylist
                        )
                    }
                }
                displaySongs.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "没有匹配的歌曲",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    QuickActionsRow(
                        songCount = displaySongs.size,
                        onPlayAll = onPlayAll,
                        onShuffleAll = onShuffleAll,
                        onSortClick = { showSortMenu = true }
                    )

                    if (showSortMenu) {
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("按添加时间") },
                                onClick = { viewModel.setSortMode(SortMode.DATE_ADDED); showSortMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("按标题") },
                                onClick = { viewModel.setSortMode(SortMode.TITLE); showSortMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("按艺术家") },
                                onClick = { viewModel.setSortMode(SortMode.ARTIST); showSortMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("按专辑") },
                                onClick = { viewModel.setSortMode(SortMode.ALBUM); showSortMenu = false }
                            )
                        }
                    }

                    SongList(
                        songs = displaySongs,
                        currentPlayingSongId = currentPlayingSongId,
                        onSongClick = onSongClick,
                        onEditSong = onEditSong,
                        onSongAddToPlaylist = onSongAddToPlaylist,
                        onRemoveSong = onRemoveSong,
                        onToggleFavorite = onToggleFavorite,
                        onShareSong = onShareSong
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistChipsRow(
    playlists: List<PlaylistWithCount>,
    currentId: Long,
    onSelect: (Long) -> Unit,
    onCreateNew: () -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(playlists, key = { it.id }) { playlist ->
            FilterChip(
                selected = currentId == playlist.id,
                onClick = { onSelect(playlist.id) },
                label = {
                    Text(
                        "${playlist.name}${if (playlist.songCount > 0) " ${playlist.songCount}" else ""}",
                        maxLines = 1
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
        item {
            FilterChip(
                selected = false,
                onClick = onCreateNew,
                label = { Text("+ 新建歌单") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
    }
}

@Composable
private fun QuickActionsRow(
    songCount: Int,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onSortClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "共 $songCount 首",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        AssistChip(
            onClick = onPlayAll,
            label = { Text("播放全部") },
            leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
        Spacer(Modifier.width(8.dp))
        AssistChip(
            onClick = onShuffleAll,
            label = { Text("随机播放") },
            leadingIcon = { Icon(Icons.Default.Shuffle, contentDescription = null) }
        )
        Spacer(Modifier.width(8.dp))
        AssistChip(
            onClick = onSortClick,
            label = { Text("排序") },
            leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null) }
        )
    }
}

@Composable
private fun SongList(
    songs: List<Song>,
    currentPlayingSongId: Long,
    onSongClick: (Long) -> Unit,
    onEditSong: (Song) -> Unit,
    onSongAddToPlaylist: (Song) -> Unit,
    onRemoveSong: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onShareSong: (Song) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(
            songs,
            key = { _, s -> s.id },
            contentType = { _, _ -> "song" }
        ) { _, song ->
            val isActive = song.id == currentPlayingSongId
            val onClick = { onSongClick(song.id) }
            val onFavClick = { onToggleFavorite(song) }
            val menuItems = remember(song) {
                listOf(
                    SongMenuItem("分享") { onShareSong(song) },
                    SongMenuItem("编辑信息") { onEditSong(song) },
                    SongMenuItem("添加到歌单") { onSongAddToPlaylist(song) },
                    SongMenuItem("移除") { onRemoveSong(song) }
                )
            }
            SongListItem(
                song = song,
                isActive = isActive,
                onClick = onClick,
                onToggleFavorite = onFavClick,
                menuItems = menuItems
            )
        }
    }
}

@Composable
private fun EmptyLibraryState(
    onPickFiles: () -> Unit,
    onPickFolder: () -> Unit
) {
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
                text = "曲库还是空的",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "添加手机本地的音乐文件或整个文件夹",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onPickFiles) { Text("选择文件") }
                TextButton(onClick = onPickFolder) { Text("选择文件夹") }
            }
        }
    }
}

@Composable
private fun EmptyPlaylistState(
    playlistName: String,
    onAddSongs: () -> Unit
) {
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
                text = "$playlistName 还是空的",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "从曲库挑选歌曲添加进来",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            androidx.compose.material3.Button(onClick = onAddSongs) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("  添加歌曲")
            }
        }
    }
}
