package com.localmusic.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.localmusic.app.data.model.Song
import com.localmusic.app.data.model.SongEntity
import com.localmusic.app.data.model.toSong
import com.localmusic.app.data.importer.MusicMetadataEditor
import com.localmusic.app.creator.ui.CreatorFeedScreen
import com.localmusic.app.creator.ui.CreatorProfileScreen
import com.localmusic.app.creator.ui.PublishWorkScreen
import com.localmusic.app.creator.viewmodel.CreatorProfileViewModel
import com.localmusic.app.creator.viewmodel.CreatorViewModel
import com.localmusic.app.player.PlayerUiState
import com.localmusic.app.ui.components.AddSongsToPlaylistDialog
import com.localmusic.app.ui.components.AddToPlaylistDialog
import com.localmusic.app.ui.components.BottomNavBar
import com.localmusic.app.ui.components.CreatePlaylistDialog
import com.localmusic.app.ui.components.EditSongInfoDialog
import com.localmusic.app.ui.components.ImportResultDialog
import com.localmusic.app.ui.components.MiniPlayerBar
import com.localmusic.app.ui.navigation.Screen
import com.localmusic.app.ui.screens.ImportLogsScreen
import com.localmusic.app.ui.screens.LibraryScreen
import com.localmusic.app.ui.screens.NowPlayingScreen
import com.localmusic.app.ui.screens.PlaylistDetailScreen
import com.localmusic.app.util.formatDuration
import com.localmusic.app.ui.screens.PlaylistsScreen
import com.localmusic.app.ui.screens.SettingsScreen
import com.localmusic.app.ui.theme.LocalMusicTheme
import com.localmusic.app.ui.viewmodel.LibraryViewModel
import com.localmusic.app.ui.viewmodel.PlayerViewModel
import com.localmusic.app.ui.viewmodel.PlaylistDetailViewModel
import com.localmusic.app.util.RequestNotificationPermission
import com.localmusic.app.util.ShareUtils
import com.localmusic.app.util.ThemeMode
import com.localmusic.app.util.rememberThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    // activity 级 VM：处理外部"打开方式"传入的音频文件时使用（与 setContent 内 viewModel() 为同一实例）
    private val playerViewModel: PlayerViewModel by viewModels()
    private val libraryViewModel: LibraryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 处理其他应用"打开方式"传入的音频文件（如从 QQ/微信/文件管理器打开）
        handleOpenAudioIntent(intent)

        setContent {
            AppContent()
        }
    }

    // App 已在运行时从外部再次打开音频文件
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpenAudioIntent(intent)
    }

    /**
     * 处理 ACTION_VIEW 音频 intent：
     *  - 导入到曲库（URI + MD5 内容去重，已存在不重复入库）
     *  - 导入成功后直接播放
     */
    private fun handleOpenAudioIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        val app = applicationContext as LocalMusicApp

        libraryViewModel.importExternalAudio(uri) { songId ->
            if (songId == null) {
                Toast.makeText(this, "无法打开该音频文件", Toast.LENGTH_SHORT).show()
                return@importExternalAudio
            }
            CoroutineScope(Dispatchers.IO).launch {
                val song = app.repository.getSongById(songId)
                withContext(Dispatchers.Main) {
                    if (song != null) {
                        playerViewModel.playSongs(listOf(song), 0)
                        Toast.makeText(this@MainActivity, "已导入并播放", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "导入失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    @Composable
    private fun AppContent() {
        val themeModeState = rememberThemeMode()
        val isDark = when (themeModeState.value) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }

        LocalMusicTheme(darkTheme = isDark) {
            val navController = rememberNavController()
            val libraryViewModel: LibraryViewModel = viewModel()
            val playerViewModel: PlayerViewModel = viewModel()
            val creatorViewModel: CreatorViewModel = viewModel()

            var showCreatePlaylist by remember { mutableStateOf(false) }
            var songToAdd by remember { mutableStateOf<Song?>(null) }
            var songToEdit by remember { mutableStateOf<Song?>(null) }
            var savingMetadata by remember { mutableStateOf(false) }
            var showAddSongsDialog by remember { mutableStateOf(false) }
            var showImportResultDialog by remember { mutableStateOf(false) }
            var showCreatorProfileEditor by remember { mutableStateOf(false) }
            var showDuplicateDialog by remember { mutableStateOf(false) }
            val context = LocalContext.current

            val creatorProfile by creatorViewModel.userProfile.collectAsState()

            val playerState by playerViewModel.uiState.collectAsState()
            // 进度独立订阅：高频 1Hz 只影响 MiniPlayerBar，不触发主 uiState 重组
            val positionMs by playerViewModel.positionMs.collectAsState()
            val currentPlayingSongId = playerState.currentSong?.id ?: -1L
            val playlistsWithFavorites by libraryViewModel.playlists.collectAsState()
            val userPlaylists by libraryViewModel.userPlaylists.collectAsState()
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            // 当前歌单歌曲：订阅后作为首页点歌的播放队列源
            val libraryUiState by libraryViewModel.uiState.collectAsState()
            val currentPlaylistSongs by libraryViewModel.currentPlaylistSongs.collectAsState()
            val migrationResult by libraryViewModel.migrationResult.collectAsState()

            // SAF 启动器：选择文件 / 选择文件夹
            val pickFilesLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenMultipleDocuments()
            ) { uris: List<Uri> ->
                if (uris.isNotEmpty()) libraryViewModel.importFromFiles(uris)
            }
            val pickFolderLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocumentTree()
            ) { uri: Uri? ->
                if (uri != null) libraryViewModel.importFromFolder(uri)
            }

            // 迁移启动器：导出（保存文件）和导入（打开 JSON 文件）
            var pendingExportJson by remember { mutableStateOf<String?>(null) }
            val exportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json")
            ) { uri: Uri? ->
                if (uri != null && pendingExportJson != null) {
                    runCatching {
                        contentResolver.openOutputStream(uri)?.use { os ->
                            os.write(pendingExportJson!!.toByteArray())
                        }
                        Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
                    }.onFailure { e ->
                        Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    pendingExportJson = null
                }
            }
            val importLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                if (uri != null) {
                    val json = runCatching {
                        contentResolver.openInputStream(uri)?.use { input ->
                            String(input.readBytes())
                        }
                    }.getOrNull()
                    if (json != null) {
                        libraryViewModel.importPlaylistsFromJson(json) { summary ->
                            if (summary != null) {
                                showImportResultDialog = true
                            } else {
                                Toast.makeText(context, "导入失败：JSON 格式错误", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }

            // —— 稳定化 lambda：切断 MainActivity 重组向 LibraryScreen 的传导 ——
            // onSongClick 用当前歌单歌曲作为播放队列源，只有歌单变化时才重建
            val onSongClick: (Long) -> Unit = remember(currentPlaylistSongs) {
                { songId: Long ->
                    val index = currentPlaylistSongs.indexOfFirst { it.id == songId }.coerceAtLeast(0)
                    val song = currentPlaylistSongs.getOrNull(index)
                    Log.i("MainActivity", "onSongClick: songId=$songId, index=$index, uri=${song?.uri}, title=${song?.title}, totalSongs=${currentPlaylistSongs.size}")
                    if (currentPlaylistSongs.isNotEmpty()) {
                        playerViewModel.playSongs(currentPlaylistSongs, index)
                    }
                }
            }
            val onSongAddToPlaylist: (Song) -> Unit = remember {
                { song: Song -> songToAdd = song }
            }
            val onPlayNextSong: (Song) -> Unit = remember {
                { song: Song -> playerViewModel.playNextInQueue(song) }
            }
            val onEditSong: (Song) -> Unit = remember {
                { song: Song -> songToEdit = song }
            }
            val onRemoveSong: (Song) -> Unit = remember {
                { song: Song -> libraryViewModel.removeSongFromCurrentPlaylist(song.id) }
            }
            val onToggleFavorite: (Song) -> Unit = remember {
                { song: Song ->
                    libraryViewModel.toggleFavorite(song.id, !song.favorite) { refreshed ->
                        playerViewModel.updateSongInQueue(refreshed)
                    }
                }
            }
            val onShareSong: (Song) -> Unit = remember(context) {
                { song: Song ->
                    CoroutineScope(Dispatchers.IO).launch {
                        ShareUtils.shareAudio(context, song.uri, song.title)
                    }
                }
            }
            val onPlayAll: () -> Unit = remember(currentPlaylistSongs) {
                { if (currentPlaylistSongs.isNotEmpty()) playerViewModel.playSongs(currentPlaylistSongs, 0) }
            }
            val onShuffleAll: () -> Unit = remember(currentPlaylistSongs) {
                { if (currentPlaylistSongs.isNotEmpty()) playerViewModel.playShuffled(currentPlaylistSongs) }
            }
            val onCreatePlaylist: () -> Unit = remember {
                { showCreatePlaylist = true }
            }
            val onAddSongsToPlaylist: () -> Unit = remember {
                { showAddSongsDialog = true }
            }
            val onPickFiles: () -> Unit = remember(pickFilesLauncher) {
                { pickFilesLauncher.launch(arrayOf("audio/*")) }
            }
            val onPickFolder: () -> Unit = remember(pickFolderLauncher) {
                { pickFolderLauncher.launch(null) }
            }

            // 迁移相关回调
            val onExportPlaylists: () -> Unit = remember(exportLauncher, libraryViewModel) {
                {
                    libraryViewModel.exportPlaylistsAsync { json ->
                        pendingExportJson = json
                        exportLauncher.launch("localmusic_playlists_${System.currentTimeMillis()}.json")
                    }
                }
            }
            val onImportPlaylists: () -> Unit = remember(importLauncher) {
                { importLauncher.launch(arrayOf("application/json", "*/*")) }
            }

            DisposableEffect(Unit) {
                playerViewModel.connect()
                // 注入播放完成回调：自动累计播放次数 + 刷新最近播放时间戳
                playerViewModel.setOnSongCompletedListener { song ->
                    libraryViewModel.incrementPlayCount(song.id) { refreshed ->
                        playerViewModel.updateSongInQueue(refreshed)
                    }
                    libraryViewModel.markRecentlyPlayed(song.id)
                }
                // 注入开始播放回调：记录最近播放（开始播放/切歌即记录，无需播完）
                playerViewModel.setOnSongStartedListener { song ->
                    libraryViewModel.markRecentlyPlayed(song.id)
                }
                onDispose {
                    playerViewModel.setOnSongCompletedListener(null)
                    playerViewModel.setOnSongStartedListener(null)
                    playerViewModel.disconnect()
                }
            }

            RequestNotificationPermission()

            // 对话框
            if (showCreatePlaylist) {
                CreatePlaylistDialog(
                    onDismiss = { showCreatePlaylist = false },
                    onConfirm = { name ->
                        libraryViewModel.createPlaylist(name)
                        showCreatePlaylist = false
                    }
                )
            }
            songToAdd?.let { song ->
                AddToPlaylistDialog(
                    playlists = userPlaylists,
                    onDismiss = { songToAdd = null },
                    onSelect = { playlistId ->
                        libraryViewModel.addSongToPlaylist(playlistId, song.id)
                        songToAdd = null
                    }
                )
            }

            // 当前歌单（用户歌单）添加歌曲对话框
            val allLibrarySongs by libraryViewModel.librarySongs.collectAsState()
            if (showAddSongsDialog) {
                AddSongsToPlaylistDialog(
                    allSongs = allLibrarySongs,
                    existingIds = currentPlaylistSongs.map { it.id }.toSet(),
                    onConfirm = { ids ->
                        val currentId = libraryViewModel.uiState.value.currentPlaylistId
                        if (currentId != com.localmusic.app.data.model.FAVORITES_PLAYLIST_ID &&
                            currentId != com.localmusic.app.data.model.ALL_SONGS_PLAYLIST_ID &&
                            currentId != com.localmusic.app.data.model.RECENTLY_PLAYED_PLAYLIST_ID
                        ) {
                            libraryViewModel.addSongsToPlaylist(currentId, ids)
                        }
                        showAddSongsDialog = false
                    },
                    onDismiss = { showAddSongsDialog = false }
                )
            }

            // 编辑歌曲信息对话框：写入实际文件元数据 + 同步数据库
            songToEdit?.let { song ->
                EditSongInfoDialog(
                    song = song,
                    saving = savingMetadata,
                    onDismiss = { if (!savingMetadata) songToEdit = null },
                    onConfirm = { title, artist, album ->
                        savingMetadata = true
                        libraryViewModel.updateSongMetadata(song.id, title, artist, album) { result, updatedSong ->
                            savingMetadata = false
                            when (result) {
                                is MusicMetadataEditor.Result.Success -> {
                                    songToEdit = null
                                    updatedSong?.let { playerViewModel.updateSongInQueue(it) }
                                    Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                                }
                                is MusicMetadataEditor.Result.NoWritePermission -> {
                                    Toast.makeText(
                                        context,
                                        "该文件无写入权限，请删除后重新导入以启用编辑",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                is MusicMetadataEditor.Result.Failed -> {
                                    Toast.makeText(
                                        context,
                                        "保存失败：${result.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                )
            }

            // 导入结果对话框
            if (showImportResultDialog && migrationResult != null) {
                ImportResultDialog(
                    summary = migrationResult!!,
                    onDismiss = {
                        showImportResultDialog = false
                        libraryViewModel.clearMigrationResult()
                    }
                )
            }

            // 重复歌曲确认删除弹窗：列出重复项，用户选择「仅删曲库」或「连文件一起删」
            val duplicateGroups by libraryViewModel.duplicateGroups.collectAsState()
            val duplicateScanning by libraryViewModel.duplicateScanning.collectAsState()
            val scanProgress by libraryViewModel.scanProgress.collectAsState()

            // 重复扫描进行中：进度弹窗
            if (showDuplicateDialog && duplicateScanning) {
                AlertDialog(
                    onDismissRequest = { showDuplicateDialog = false },
                    title = { Text("正在扫描曲库") },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            Text(
                                "正在比对文件内容 ${scanProgress.first}/${scanProgress.second} 首",
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showDuplicateDialog = false }) { Text("取消") }
                    }
                )
            } else if (showDuplicateDialog && duplicateGroups.isNotEmpty()) {
                val totalDuplicates = duplicateGroups.sumOf { it.duplicates.size }
                AlertDialog(
                    onDismissRequest = {
                        showDuplicateDialog = false
                        libraryViewModel.clearDuplicateGroups()
                    },
                    title = { Text("发现 $totalDuplicates 首重复歌曲（${duplicateGroups.size} 组）") },
                    text = {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 400.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                "点击任意一行即可试听，确认后再选择删除方式",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            duplicateGroups.forEachIndexed { index, group ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                                val keepPlaying = isThisSongPlaying(playerState, group.keep.id)
                                // 保留项
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { playDuplicatePreview(playerViewModel, playerState, group.keep) }
                                ) {
                                    Icon(
                                        imageVector = if (keepPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (keepPlaying) "暂停" else "试听",
                                        tint = if (keepPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        " 组${index + 1} 保留",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "  ${shortTitle(group.keep.title)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = true)
                                    )
                                    Text(
                                        formatDuration(group.keep.duration),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // 每条重复项 + 命中理由
                                group.duplicates.forEach { dup ->
                                    val dupPlaying = isThisSongPlaying(playerState, dup.song.id)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { playDuplicatePreview(playerViewModel, playerState, dup.song) }
                                    ) {
                                        Icon(
                                            imageVector = if (dupPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = if (dupPlaying) "暂停" else "试听",
                                            tint = if (dupPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.padding(start = 12.dp).size(16.dp)
                                        )
                                        Text(
                                            "  ${shortTitle(dup.song.title)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (dupPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = true)
                                        )
                                        Text(
                                            formatDuration(dup.song.duration),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        "  ${dup.reason}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 24.dp, bottom = 4.dp)
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                libraryViewModel.removeDuplicateGroups(deleteFiles = true) { removed ->
                                    showDuplicateDialog = false
                                    Toast.makeText(
                                        this@MainActivity,
                                        "已删除 $removed 首重复歌曲（含文件）",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        ) {
                            Text("删除曲库+文件", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        Row {
                            TextButton(
                                onClick = {
                                    libraryViewModel.removeDuplicateGroups(deleteFiles = false) { removed ->
                                        showDuplicateDialog = false
                                        Toast.makeText(
                                            this@MainActivity,
                                            "已删除 $removed 首重复歌曲（仅曲库）",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            ) {
                                Text("仅删除曲库")
                            }
                            TextButton(
                                onClick = {
                                    showDuplicateDialog = false
                                    libraryViewModel.clearDuplicateGroups()
                                }
                            ) {
                                Text("取消")
                            }
                        }
                    }
                )
            }

            // 创作者资料编辑弹窗（在「我的」页面触发）
            if (showCreatorProfileEditor) {
                com.localmusic.app.creator.ui.components.CreatorProfileEditorDialog(
                    viewModel = creatorViewModel,
                    onDismiss = { showCreatorProfileEditor = false }
                )
            }

            // 创作者空间沉浸式全屏：进入 CreatorFeed / CreatorPublish / CreatorProfile 均隐藏 App 底部导航 & MiniPlayer
            // 带参数路由（CreatorProfile）用 {} 前的静态前缀匹配
            val creatorRoutePrefixes = listOf(
                Screen.CreatorFeed.route,
                Screen.CreatorPublish.route,
                Screen.CreatorProfile.route.takeWhile { it != '{' }
            )
            val inCreatorSpace = creatorRoutePrefixes.any { prefix ->
                currentRoute == prefix || currentRoute?.startsWith(prefix) == true
            }
            val showBottomBar = !inCreatorSpace && currentRoute in setOf(
                Screen.Library.route,
                Screen.PlaylistsTab.route,
                Screen.CreatorFeed.route,
                Screen.Settings.route
            )
            val showMiniPlayer = !inCreatorSpace && playerState.currentSong != null &&
                currentRoute != Screen.NowPlaying.route

            // 进入创作者空间时暂停外部音乐播放，避免与创作者 BGM/视频原声叠加；
            // 退出后保持暂停，不自动恢复
            LaunchedEffect(inCreatorSpace) {
                if (inCreatorSpace && playerState.isPlaying) {
                    playerViewModel.togglePlayPause()
                }
            }

            // MiniPlayer 点击跳转——稳定化避免每秒重建
            val onMiniPlayerClick: () -> Unit = remember(navController) {
                { navController.navigate(Screen.NowPlaying.route) }
            }

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    Column {
                        if (showMiniPlayer) {
                            MiniPlayerBar(
                                playerState = playerState,
                                positionMs = positionMs,
                                onPlayPause = playerViewModel::togglePlayPause,
                                onNext = playerViewModel::skipToNext,
                                onPrevious = playerViewModel::previousOrRestart,
                                onClick = onMiniPlayerClick,
                                onSeek = playerViewModel::seekTo
                            )
                        }
                        if (showBottomBar) {
                            BottomNavBar(
                                currentRoute = currentRoute,
                                onNavigate = { route ->
                                    navController.navigate(route) {
                                        popUpTo(Screen.Library.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                }
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = Screen.Library.route,
                    // 统一导航过渡：淡入淡出 + 轻微缩放（无滑动，避免黑底全屏页切换时的闪帧/残影）
                    enterTransition = {
                        fadeIn(tween(250, easing = FastOutSlowInEasing)) +
                            scaleIn(initialScale = 1.03f, animationSpec = tween(250, easing = FastOutSlowInEasing))
                    },
                    exitTransition = {
                        fadeOut(tween(250, easing = FastOutSlowInEasing)) +
                            scaleOut(targetScale = 0.97f, animationSpec = tween(250, easing = FastOutSlowInEasing))
                    },
                    popEnterTransition = {
                        fadeIn(tween(250, easing = FastOutSlowInEasing)) +
                            scaleIn(initialScale = 0.97f, animationSpec = tween(250, easing = FastOutSlowInEasing))
                    },
                    popExitTransition = {
                        fadeOut(tween(250, easing = FastOutSlowInEasing)) +
                            scaleOut(targetScale = 1.03f, animationSpec = tween(250, easing = FastOutSlowInEasing))
                    },
                    // 固定布局：创作者空间时 bottomBar 为空，padding 底部为 0，页面全屏；
                    // 正常页面由 Scaffold 提供底部栏 padding。避免退出瞬间 modifier 突变引发闪帧
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    composable(Screen.Library.route) {
                        LibraryScreen(
                            viewModel = libraryViewModel,
                            onSongClick = onSongClick,
                            onSongAddToPlaylist = onSongAddToPlaylist,
                            onPlayNextSong = onPlayNextSong,
                            onEditSong = onEditSong,
                            onRemoveSong = onRemoveSong,
                            onToggleFavorite = onToggleFavorite,
                            onShareSong = onShareSong,
                            onPlayAll = onPlayAll,
                            onCreatePlaylist = onCreatePlaylist,
                            onAddSongsToPlaylist = onAddSongsToPlaylist,
                            onPickFiles = onPickFiles,
                            onPickFolder = onPickFolder,
                            currentPlayingSongId = currentPlayingSongId
                        )
                    }

                    composable(Screen.PlaylistsTab.route) {
                        PlaylistsScreen(
                            playlists = playlistsWithFavorites,
                            onPlaylistClick = { playlistId ->
                                navController.navigate(Screen.PlaylistDetail.createRoute(playlistId))
                            },
                            onCreatePlaylist = { showCreatePlaylist = true },
                            onDeletePlaylist = { libraryViewModel.deletePlaylist(it) }
                        )
                    }

                    composable(Screen.Settings.route) {
                        SettingsScreen(
                            themeMode = themeModeState.value,
                            onThemeModeChange = { themeModeState.value = it },
                            onExportPlaylists = onExportPlaylists,
                            onImportPlaylists = onImportPlaylists,
                            onOpenImportLogs = {
                                navController.navigate(Screen.ImportLogs.route)
                            },
                            onCheckDuplicates = {
                                showDuplicateDialog = true
                                libraryViewModel.checkDuplicates { count ->
                                    if (count == 0 && showDuplicateDialog) {
                                        showDuplicateDialog = false
                                        val unreadable = libraryViewModel.lastScanUnreadable.value
                                        Toast.makeText(
                                            this@MainActivity,
                                            if (unreadable > 0) {
                                                "没有发现重复歌曲（$unreadable 首文件无法读取，未参与内容比对）"
                                            } else {
                                                "没有发现重复歌曲"
                                            },
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            },
                            onCreatorProfileClick = { showCreatorProfileEditor = true },
                            creatorNickname = creatorProfile.nickname,
                            creatorAvatarUri = creatorProfile.avatarUri
                        )
                    }

                    composable(Screen.ImportLogs.route) {
                        ImportLogsScreen(
                            viewModel = libraryViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = Screen.PlaylistDetail.route,
                        arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: return@composable
                        val detailViewModel: PlaylistDetailViewModel = viewModel(
                            viewModelStoreOwner = backStackEntry
                        )
                        val detailState by detailViewModel.uiState.collectAsState()
                        val allSongs by detailViewModel.allSongs.collectAsState()
                        var showAddSongs by remember { mutableStateOf(false) }

                        PlaylistDetailScreen(
                            viewModel = detailViewModel,
                            onBack = { navController.popBackStack() },
                            onSongClick = { index ->
                                playerViewModel.playSongs(detailState.songs, index)
                            },
                            onEditSong = onEditSong,
                            onToggleFavorite = onToggleFavorite,
                            onShareSong = onShareSong,
                            onPlayNextSong = onPlayNextSong,
                            onPlayAll = {
                                playerViewModel.playSongs(detailState.songs, 0)
                            },
                            onShuffle = {
                                playerViewModel.playShuffled(detailState.songs)
                            },
                            onAddSongs = { if (!detailState.isFavorites) showAddSongs = true },
                            onDeletePlaylist = {
                                libraryViewModel.deletePlaylist(playlistId)
                                navController.popBackStack()
                            }
                        )

                        if (showAddSongs) {
                            AddSongsToPlaylistDialog(
                                allSongs = allSongs,
                                existingIds = detailState.songs.map { it.id }.toSet(),
                                onConfirm = { ids ->
                                    detailViewModel.addSongs(ids)
                                    showAddSongs = false
                                },
                                onDismiss = { showAddSongs = false }
                            )
                        }
                    }

                    composable(Screen.NowPlaying.route) {
                        val currentSongId = playerState.currentSong?.id ?: -1L
                        val currentSongFavorite = playerState.currentSong?.favorite ?: false
                        NowPlayingScreen(
                            viewModel = playerViewModel,
                            onBack = { navController.popBackStack() },
                            onToggleFavorite = {
                                if (currentSongId > 0) {
                                    libraryViewModel.toggleFavorite(currentSongId, !currentSongFavorite)
                                }
                            },
                            onShareSong = {
                                playerState.currentSong?.let { song ->
                                    CoroutineScope(Dispatchers.IO).launch {
                                        ShareUtils.shareAudio(context, song.uri, song.title)
                                    }
                                }
                            }
                        )
                    }

                    // 创作者训练空间：作品浏览页（观众视角）——沉浸式全屏，退出返回本地音乐tab
                    composable(Screen.CreatorFeed.route) {
                        CreatorFeedScreen(
                            viewModel = creatorViewModel,
                            onPublishClick = { navController.navigate(Screen.CreatorPublish.route) },
                            // 点击头像 → 创作者个人主页（独立全屏层，从底部滑入；下层视频流自动暂停）
                            onAvatarClick = { authorId ->
                                creatorViewModel.setProfileOpen(true)
                                navController.navigate(Screen.CreatorProfile.createRoute(authorId))
                            },
                            onEditWork = { /* 第一阶段就地编辑暂未实现，浏览页内已可直接修改状态 */ },
                            onExitCreatorSpace = {
                                // 点击顶部☰按钮，返回App主"本地音乐"tab
                                navController.navigate(Screen.Library.route) {
                                    popUpTo(Screen.Library.route) { inclusive = false }
                                    launchSingleTop = true
                                }
                            }
                        )
                    }

                    // 创作者训练空间：作品发布页
                    composable(Screen.CreatorPublish.route) {
                        PublishWorkScreen(
                            viewModel = creatorViewModel,
                            onBack = { navController.popBackStack() },
                            onPublished = {
                                navController.popBackStack()
                                navController.navigate(Screen.CreatorFeed.route) {
                                    popUpTo(Screen.CreatorFeed.route) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        )
                    }

                    // 创作者训练空间：作者主页（独立全屏层：从底部滑入/向下滑出，不透明背景避免与视频流混合）
                    composable(
                        route = Screen.CreatorProfile.route,
                        arguments = listOf(navArgument("authorId") { type = NavType.StringType }),
                        enterTransition = {
                            slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = tween(320, easing = FastOutSlowInEasing)
                            )
                        },
                        popExitTransition = {
                            slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = tween(320, easing = FastOutSlowInEasing)
                            )
                        }
                    ) { backStackEntry ->
                        val authorId = backStackEntry.arguments?.getString("authorId") ?: return@composable
                        val profileViewModel: CreatorProfileViewModel = viewModel(
                            key = "creator_profile_$authorId",
                            factory = CreatorProfileViewModel.factory(authorId)
                        )
                        CreatorProfileScreen(
                            profileViewModel = profileViewModel,
                            creatorViewModel = creatorViewModel,
                            onBack = { navController.popBackStack() },
                            onWorkClick = { index ->
                                creatorViewModel.setProfileOpen(false)
                                creatorViewModel.setCurrentIndex(index)
                                navController.navigate(Screen.CreatorFeed.route)
                            }
                        )
                    }
                }
            }
        }
    }
}

// 抖音文件名 → 精简标题：@账号_日期_@账号创作的原声 → "@账号 创作的原声"；@账号_日期_歌名 → 歌名；普通文件 → 基名
private val douyinNameRegex = Regex("^(@.*?)_(\\d{8}(?:\\d{6})?)_(.+)$")
private val originalSoundRegex = Regex("(.+?)(创作的原声|用户创作的原声|原创)$")

private fun shortTitle(title: String): String {
    val base = title.substringBeforeLast('.')
    val m = douyinNameRegex.find(base)
    if (m != null) {
        val account = m.groupValues[1]
        val rest = m.groupValues[3]
        if (originalSoundRegex.containsMatchIn(rest)) {
            return "$account 创作的原声"
        }
        return rest.removePrefix("@")
    }
    // 歌名-歌手-歌曲ID-码率
    val m2 = Regex("^(.+)-[^-]+-\\d+-\\d+$").find(base)
    return m2?.groupValues?.get(1) ?: base
}

/** 该歌曲是否正在弹窗试听中。 */
private fun isThisSongPlaying(playerState: PlayerUiState, songId: Long): Boolean =
    playerState.currentSong?.id == songId && playerState.isPlaying

/** 弹窗内点击试听：正在播放则暂停，否则从该曲开始播放。 */
private fun playDuplicatePreview(
    playerViewModel: PlayerViewModel,
    playerState: PlayerUiState,
    song: SongEntity
) {
    if (playerState.currentSong?.id == song.id && playerState.isPlaying) {
        playerViewModel.togglePlayPause()
    } else {
        playerViewModel.playSongs(listOf(song.toSong()), 0)
    }
}
