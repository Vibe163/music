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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localmusic.app.data.importer.ImportResult
import com.localmusic.app.data.model.ALL_SONGS_PLAYLIST_ID
import com.localmusic.app.data.model.FAVORITES_PLAYLIST_ID
import com.localmusic.app.data.model.RECENTLY_PLAYED_PLAYLIST_ID
import com.localmusic.app.data.model.PlaylistWithCount
import com.localmusic.app.data.model.Song
import com.localmusic.app.ui.components.SongListItem
import com.localmusic.app.ui.components.SongMenuItem
import com.localmusic.app.ui.viewmodel.LibraryViewModel
import com.localmusic.app.ui.viewmodel.SortMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onSongClick: (songId: Long) -> Unit,
    onSongAddToPlaylist: (Song) -> Unit,
    onPlayNextSong: (Song) -> Unit,
    onEditSong: (Song) -> Unit,
    onRemoveSong: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onShareSong: (Song) -> Unit,
    onPlayAll: () -> Unit,
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
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // 切换歌单时回到列表顶部（否则会停留在上一个歌单的滚动位置）
    LaunchedEffect(uiState.currentPlaylistId) {
        listState.scrollToItem(0)
    }

    // 定位到正在播放的歌曲：仅当当前列表包含该歌曲时显示按钮
    val playingIndex = if (currentPlayingSongId > 0)
        displaySongs.indexOfFirst { it.id == currentPlayingSongId }
    else -1
    val onLocatePlaying: (() -> Unit)? = if (playingIndex >= 0) {
        {
            coroutineScope.launch { listState.animateScrollToItem(playingIndex) }
        }
    } else null

    val isLibrary = uiState.currentPlaylistId == ALL_SONGS_PLAYLIST_ID
    val isFavorites = uiState.currentPlaylistId == FAVORITES_PLAYLIST_ID
    val isRecentlyPlayed = uiState.currentPlaylistId == RECENTLY_PLAYED_PLAYLIST_ID

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(uiState.currentPlaylistName) },
                actions = {
                    if (isLibrary) {
                        Box {
                            IconButton(onClick = { showAddMenu = true }) {
                                Icon(Icons.Default.Add, contentDescription = "添加音乐")
                            }
                            if (showAddMenu) {
                                DropdownMenu(
                                    expanded = showAddMenu,
                                    onDismissRequest = { showAddMenu = false }
                                ) {
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
                                }
                            }
                        }
                    } else if (!isFavorites && !isRecentlyPlayed) {
                        IconButton(onClick = { showAddMenu = true }) {
                            Icon(Icons.Default.Add, contentDescription = "添加歌曲")
                        }
                        if (showAddMenu) {
                            DropdownMenu(
                                expanded = showAddMenu,
                                onDismissRequest = { showAddMenu = false }
                            ) {
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
                // 导入完成：不再显示行内提示，改为弹窗（见 Composable 末尾 ImportCompletedDialog）
            }

            when {
                displaySongs.isEmpty() && uiState.searchQuery.isBlank() -> {
                    when {
                        isLibrary -> EmptyLibraryState(onPickFiles = onPickFiles, onPickFolder = onPickFolder)
                        isFavorites -> EmptyFavoritesState()
                        isRecentlyPlayed -> EmptyRecentlyPlayedState()
                        else -> EmptyPlaylistState(
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
                        onSortClick = { showSortMenu = true },
                        onLocatePlaying = onLocatePlaying,
                        sortMenuExpanded = showSortMenu,
                        onDismissSortMenu = { showSortMenu = false },
                        sortMenuContent = {
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
                    )

                    SongList(
                        songs = displaySongs,
                        currentPlayingSongId = currentPlayingSongId,
                        listState = listState,
                        onSongClick = onSongClick,
                        onEditSong = onEditSong,
                        onSongAddToPlaylist = onSongAddToPlaylist,
                        onPlayNextSong = onPlayNextSong,
                        onRemoveSong = onRemoveSong,
                        removeLabel = if (isFavorites) "取消收藏" else "移除",
                        onToggleFavorite = onToggleFavorite,
                        onShareSong = onShareSong
                    )
                }
            }
        }
    }

    // 导入完成弹窗：提示新增 / 重复 / 失败数量
    progress.lastResult?.let { result ->
        ImportCompletedDialog(
            result = result,
            onDismiss = viewModel::clearImportResult
        )
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
    onSortClick: () -> Unit,
    onLocatePlaying: (() -> Unit)?,
    sortMenuExpanded: Boolean,
    onDismissSortMenu: () -> Unit,
    sortMenuContent: @Composable () -> Unit
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
        Box {
            AssistChip(
                onClick = onSortClick,
                label = { Text("排序") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null) }
            )
            DropdownMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = onDismissSortMenu
            ) {
                sortMenuContent()
            }
        }
        if (onLocatePlaying != null) {
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onLocatePlaying) {
                Icon(
                    Icons.Default.MyLocation,
                    contentDescription = "定位到正在播放的歌曲",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SongList(
    songs: List<Song>,
    currentPlayingSongId: Long,
    listState: LazyListState,
    onSongClick: (Long) -> Unit,
    onEditSong: (Song) -> Unit,
    onSongAddToPlaylist: (Song) -> Unit,
    onPlayNextSong: (Song) -> Unit,
    onRemoveSong: (Song) -> Unit,
    removeLabel: String,
    onToggleFavorite: (Song) -> Unit,
    onShareSong: (Song) -> Unit
) {
    LazyColumn(
        state = listState,
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
                    SongMenuItem("播放") { onClick() },
                    SongMenuItem("下一首播放") { onPlayNextSong(song) },
                    SongMenuItem("分享") { onShareSong(song) },
                    SongMenuItem("编辑信息") { onEditSong(song) },
                    SongMenuItem("添加到歌单") { onSongAddToPlaylist(song) },
                    SongMenuItem(removeLabel) { onRemoveSong(song) }
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

@Composable
private fun EmptyRecentlyPlayedState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "还没有播放记录",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "播放过的歌曲会自动出现在这里",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyFavoritesState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "还没有收藏的歌曲",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "去曲库点亮歌曲右侧的红心即可收藏",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 导入完成弹窗：提示新增 / 重复 / 失败数量
 *
 *  - 新增：成功导入的新音乐数
 *  - 重复：因 URI 或 MD5 内容相同被跳过的数量（用户视角统一显示为"重复"）
 *  - 失败：读取失败的极少数文件
 */
@Composable
private fun ImportCompletedDialog(
    result: ImportResult,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入完成") },
        text = {
            Column {
                Text("成功添加 ${result.added} 首新音乐")
                if (result.skipped > 0) {
                    Text(
                        "跳过 ${result.skipped} 首重复音乐",
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                if (result.failed > 0) {
                    Text(
                        "失败 ${result.failed} 首",
                        modifier = Modifier.padding(top = 6.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了")
            }
        }
    )
}
