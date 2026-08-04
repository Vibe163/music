package com.localmusic.app

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.localmusic.app.data.model.Song
import com.localmusic.app.data.importer.MusicMetadataEditor
import com.localmusic.app.creator.ui.CreatorFeedScreen
import com.localmusic.app.creator.ui.CreatorProfileScreen
import com.localmusic.app.creator.ui.PublishWorkScreen
import com.localmusic.app.creator.viewmodel.CreatorViewModel
import com.localmusic.app.ui.components.AddSongsToPlaylistDialog
import com.localmusic.app.ui.components.AddToPlaylistDialog
import com.localmusic.app.ui.components.BottomNavBar
import com.localmusic.app.ui.components.CreatePlaylistDialog
import com.localmusic.app.ui.components.EditSongInfoDialog
import com.localmusic.app.ui.components.ImportResultDialog
import com.localmusic.app.ui.components.MiniPlayerBar
import com.localmusic.app.ui.navigation.Screen
import com.localmusic.app.ui.screens.LibraryScreen
import com.localmusic.app.ui.screens.NowPlayingScreen
import com.localmusic.app.ui.screens.PlaylistDetailScreen
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

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AppContent()
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
                // 注入播放完成回调：自动累计播放次数
                playerViewModel.setOnSongCompletedListener { song ->
                    libraryViewModel.incrementPlayCount(song.id) { refreshed ->
                        playerViewModel.updateSongInQueue(refreshed)
                    }
                }
                onDispose {
                    playerViewModel.setOnSongCompletedListener(null)
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

            // 当前歌单（非主收藏）添加歌曲对话框
            val allLibrarySongs by libraryViewModel.currentPlaylistSongs.collectAsState()
            if (showAddSongsDialog) {
                AddSongsToPlaylistDialog(
                    allSongs = allLibrarySongs,
                    existingIds = currentPlaylistSongs.map { it.id }.toSet(),
                    onConfirm = { ids ->
                        val currentId = libraryViewModel.uiState.value.currentPlaylistId
                        if (currentId != com.localmusic.app.data.model.FAVORITES_PLAYLIST_ID) {
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

            // 创作者资料编辑弹窗（在「我的」页面触发）
            if (showCreatorProfileEditor) {
                com.localmusic.app.creator.ui.components.CreatorProfileEditorDialog(
                    viewModel = creatorViewModel,
                    onDismiss = { showCreatorProfileEditor = false }
                )
            }

            // 创作者空间沉浸式全屏：进入 CreatorFeed / CreatorPublish / CreatorProfile 均隐藏 App 底部导航 & MiniPlayer
            val creatorRoutes = setOf(
                Screen.CreatorFeed.route,
                Screen.CreatorPublish.route
            )
            val inCreatorSpace = creatorRoutes.any { currentRoute == it || currentRoute?.startsWith(it) == true }
                || currentRoute?.startsWith(Screen.CreatorProfile.route.takeWhile { it != '{' }) == true
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
                                onClick = onMiniPlayerClick
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
                            onEditSong = onEditSong,
                            onRemoveSong = onRemoveSong,
                            onToggleFavorite = onToggleFavorite,
                            onShareSong = onShareSong,
                            onPlayAll = onPlayAll,
                            onShuffleAll = onShuffleAll,
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
                            onCreatorProfileClick = { showCreatorProfileEditor = true },
                            creatorNickname = creatorProfile.nickname,
                            creatorAvatarUri = creatorProfile.avatarUri
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
                            onPlayAll = {
                                playerViewModel.playSongs(detailState.songs, 0)
                            },
                            onShuffle = {
                                playerViewModel.playShuffled(detailState.songs)
                            },
                            onAddSongs = { showAddSongs = true },
                            onPickFiles = { pickFilesLauncher.launch(arrayOf("audio/*")) },
                            onPickFolder = { pickFolderLauncher.launch(null) }
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
                            onAvatarClick = { authorId ->
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

                    // 创作者训练空间：作者主页
                    composable(
                        route = Screen.CreatorProfile.route,
                        arguments = listOf(navArgument("authorId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val authorId = backStackEntry.arguments?.getString("authorId") ?: return@composable
                        CreatorProfileScreen(
                            viewModel = creatorViewModel,
                            authorId = authorId,
                            onBack = { navController.popBackStack() },
                            onWorkClick = { index ->
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
