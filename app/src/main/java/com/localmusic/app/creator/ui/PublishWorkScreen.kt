package com.localmusic.app.creator.ui

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.localmusic.app.creator.ui.components.BgmPickerSheet
import com.localmusic.app.creator.ui.components.FeedVideoPlayer
import com.localmusic.app.creator.viewmodel.AlbumFilter
import com.localmusic.app.creator.viewmodel.AlbumMediaItem
import com.localmusic.app.creator.viewmodel.CreatorViewModel
import androidx.compose.runtime.DisposableEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.launch

/**
 * 抖音式发布流程容器：4步1:1复刻
 *
 *  Step1_ALBUM -> Step3_EDIT -> Step4_SUBMIT -> 回调 onPublished()
 *
 *  注：Step2(已选预览条)与Step1合并实现，不是独立页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublishWorkScreen(
    viewModel: CreatorViewModel,
    onBack: () -> Unit,
    onPublished: () -> Unit
) {
    var step by remember { mutableStateOf(PublishStep.STEP1_ALBUM) }
    val userProfile by viewModel.userProfile.collectAsState()
    val profileIncomplete = userProfile.nickname.isBlank() || userProfile.avatarUri == null
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 每次进入发布流程先清空草稿
    LaunchedEffect(Unit) {
        viewModel.resetDraft()
    }

    when (step) {
        PublishStep.STEP1_ALBUM -> {
            Step1AlbumScreen(
                viewModel = viewModel,
                onClose = onBack,
                onNext = { step = PublishStep.STEP3_EDIT }
            )
        }
        PublishStep.STEP3_EDIT -> {
            Step3EditScreen(
                viewModel = viewModel,
                onBack = { step = PublishStep.STEP1_ALBUM },
                onNext = { step = PublishStep.STEP4_SUBMIT }
            )
        }
        PublishStep.STEP4_SUBMIT -> {
            Step4SubmitScreen(
                viewModel = viewModel,
                profileIncomplete = profileIncomplete,
                onBack = { step = PublishStep.STEP3_EDIT },
                onPublished = onPublished
            )
        }
    }
}

private enum class PublishStep { STEP1_ALBUM, STEP3_EDIT, STEP4_SUBMIT }

// ============================================================================
// Step 1：相册选择（对应截图1/2）
//  - 顶栏：☰ 关闭 X  / 「所有照片 ▾」/ 搜索
//  - Tab：全部 视频 图片 动图
//  - 网格：3列，右上角圆形选择，选中后显示数字
//  - 底部选中预览 + 一键成片 / 下一步
// ============================================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Step1AlbumScreen(
    viewModel: CreatorViewModel,
    onClose: () -> Unit,
    onNext: () -> Unit
) {
    val ctx = LocalContext.current
    // Android 13+ READ_MEDIA_IMAGES + READ_MEDIA_VIDEO；旧版 READ_EXTERNAL_STORAGE
    val perms = remember {
        if (android.os.Build.VERSION.SDK_INT >= 33) arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        ) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    var permGranted by remember {
        mutableStateOf(perms.all {
            ContextCompat.checkSelfPermission(ctx, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        })
    }
    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permGranted = result.values.all { it }
    }
    var filter by remember { mutableStateOf(AlbumFilter.ALL) }
    val albumItems = remember { mutableStateListOf<AlbumMediaItem>() }
    val selection = remember { mutableStateListOf<AlbumMediaItem>() }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(permGranted, filter) {
        if (!permGranted) return@LaunchedEffect
        loading = true
        albumItems.clear()
        albumItems += viewModel.queryAlbum(filter)
        loading = false
    }

    LaunchedEffect(Unit) {
        if (!permGranted) permLauncher.launch(perms)
    }

    Scaffold(
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(padding)
                .statusBarsPadding()
        ) {
            // ========= 顶栏 =========
            Step1TopBar(
                onClose = onClose,
                albumName = "所有照片"
            )
            // ========= Tab：全部 / 视频 / 图片 / 动图 =========
            Step1TabBar(selected = filter) { filter = it }
            HorizontalDivider(color = Color(0xFFF0F0F0), thickness = 1.dp)
            // ========= 3列网格 =========
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.White)
            ) {
                when {
                    !permGranted -> PermissionDeniedTip(onRequestPerm = { permLauncher.launch(perms) })
                    loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = Color(0xFFFE2C55)) }
                    albumItems.isEmpty() -> EmptyAlbumTip()
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(albumItems, key = { (if (it.isVideo) "v_" else "i_") + it.id }) { item ->
                            val selIndex = selection.indexOfFirst { it.id == item.id }
                            val isSelected = selIndex >= 0
                            AlbumGridCell(
                                item = item,
                                isSelected = isSelected,
                                selectIndex = if (isSelected) selIndex + 1 else 0,
                                onToggle = {
                                    if (isSelected) {
                                        selection.removeAll { s -> s.id == item.id }
                                    } else {
                                        selection.add(item)
                                    }
                                }
                            )
                        }
                    }
                }
            }
            // ========= 底部：选中预览 + 一键成片 / 下一步 =========
            Step1BottomBar(
                selection = selection,
                onQuick = { /* 一键成片占位，与下一步等价 */ scope.launch {
                    viewModel.setDraftSelection(selection.toList())
                    onNext()
                } },
                onNext = {
                    scope.launch {
                        viewModel.setDraftSelection(selection.toList())
                        onNext()
                    }
                }
            )
        }
    }
}

@Composable
private fun Step1TopBar(
    onClose: () -> Unit,
    albumName: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(Color.White),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Close,
            contentDescription = "关闭",
            tint = Color.Black,
            modifier = Modifier
                .padding(horizontal = 14.dp)
                .size(24.dp)
                .clickable { onClose() }
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = albumName,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black
        )
        Icon(
            Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Filled.Search,
            contentDescription = "搜索",
            tint = Color.Black,
            modifier = Modifier
                .padding(horizontal = 14.dp)
                .size(23.dp)
        )
    }
}

@Composable
private fun Step1TabBar(selected: AlbumFilter, onSelect: (AlbumFilter) -> Unit) {
    val tabs = remember {
        listOf(
            AlbumFilter.ALL to "全部",
            AlbumFilter.VIDEO to "视频",
            AlbumFilter.IMAGE to "图片",
            AlbumFilter.GIF to "动图"
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(Color.White),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)
    ) {
        tabs.forEach { (f, label) ->
            val sel = f == selected
            Column(
                modifier = Modifier
                    .height(44.dp)
                    .clickable { onSelect(f) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(Modifier.height(14.dp))
                Text(
                    label,
                    fontSize = 15.sp,
                    color = if (sel) Color.Black else Color(0xFF888888),
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                )
                Box(
                    modifier = Modifier
                        .width(26.dp)
                        .height(3.dp)
                        .background(if (sel) Color.Black else Color.Transparent, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun AlbumGridCell(
    item: AlbumMediaItem,
    isSelected: Boolean,
    selectIndex: Int,
    onToggle: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(Color(0xFFF0F0F0))
            .clickable { onToggle() }
    ) {
        AsyncImage(
            model = item.contentUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // 视频/动图标识
        if (item.isVideo) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(5.dp)
                    .alpha(0.9f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    Icons.Filled.Movie,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(11.dp)
                )
                Text("视频", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
        // 右上角选择圆圈
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(22.dp)
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color(0xFFFE2C55)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$selectIndex",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                        .background(Color.Black.copy(alpha = 0.15f))
                )
            }
        }
    }
}

@Composable
private fun Step1BottomBar(
    selection: List<AlbumMediaItem>,
    onQuick: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .navigationBarsPadding()
    ) {
        // 选中预览条
        if (selection.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    selection.forEachIndexed { idx, it ->
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(5.dp))
                        ) {
                            AsyncImage(
                                model = it.contentUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(1.dp)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.65f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    "已选${selection.size}",
                    fontSize = 12.sp,
                    color = Color(0xFF888888)
                )
            }
        }
        // 按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onQuick,
                enabled = selection.isNotEmpty(),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF3F3F5),
                    disabledContainerColor = Color(0xFFF3F3F5)
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(
                    "一键成片",
                    color = if (selection.isNotEmpty()) Color.Black else Color(0xFFBBBBBB),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Button(
                onClick = onNext,
                enabled = selection.isNotEmpty(),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFE2C55),
                    disabledContainerColor = Color(0x44FE2C55)
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(
                    "下一步",
                    color = if (selection.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.7f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable private fun PermissionDeniedTip(onRequestPerm: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.Image, null, tint = Color(0xFFCCCCCC), modifier = Modifier.size(70.dp))
        Spacer(Modifier.height(16.dp))
        Text("需要相册权限才能选择素材", fontSize = 15.sp, color = Color(0xFF666666))
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onRequestPerm,
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55)),
            modifier = Modifier.height(44.dp)
        ) {
            Text("授权相册访问", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable private fun EmptyAlbumTip() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Outlined.Layers, null, tint = Color(0xFFCCCCCC), modifier = Modifier.size(70.dp))
        Spacer(Modifier.height(16.dp))
        Text("相册暂无素材", fontSize = 15.sp, color = Color(0xFF666666))
    }
}

// ============================================================================
// Step 3：编辑页（对应截图3）
//  - 黑色背景
//  - 顶栏：<返回 / BGM选择条 🎵AI「@xxx创作的原声 - xxx」 X / ⚙设置
//  - 右侧垂直工具栏：文字 话题 贴纸 特效 标记 滤镜 更多
//  - 中间：大图预览（第一张图）
//  - 底部：「AI ▶ 故事感照，情绪文案一键配」/ 特效 / 「限时日常」开关 / 下一步
// ============================================================================
@Composable
private fun Step3EditScreen(
    viewModel: CreatorViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val selection by viewModel.draftSelection.collectAsState()
    val draftBgm by viewModel.draftBgm.collectAsState()
    var showBgmPicker by remember { mutableStateOf(false) }
    var limitedTime by remember { mutableStateOf(true) }
    val firstMedia = selection.firstOrNull()
    val coverUri = firstMedia?.contentUri
    val firstIsVideo = firstMedia?.isVideo == true
    val context = LocalContext.current

    // ===== 编辑页独立 BGM 播放器（常驻播放：打开面板/关面板都尽量不中断，避免停了就不响）=====
    //  - 进入页面：有 draftBgm 就循环播放
    //  - draftBgm 变化（用户在面板里切歌）→ 播放器同步切歌并立刻接着播
    //  - 是否打开 showBgmPicker 不再控制播放/暂停，避免状态竞态
    //  - 离开 Step3：释放播放器
    var editBgmPlayer: ExoPlayer? by remember { mutableStateOf(null) }

    // 初始化 & 释放播放器
    DisposableEffect(Unit) {
        val player = ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
        }
        editBgmPlayer = player
        onDispose {
            player.release()
        }
    }

    // 当 draftBgm 变化时，更新编辑页播放器曲目，只要有 BGM 就立刻播
    LaunchedEffect(draftBgm?.id, editBgmPlayer) {
        val player = editBgmPlayer ?: return@LaunchedEffect
        val bgm = draftBgm
        if (bgm != null) {
            val currentTrackId = (player.currentMediaItem?.mediaId)?.toLongOrNull() ?: -1L
            if (currentTrackId != bgm.id) {
                player.stop()
                player.clearMediaItems()
                player.setMediaItem(
                    MediaItem.Builder()
                        .setUri(Uri.parse(bgm.uri.toString()))
                        .setMediaId(bgm.id.toString())
                        .build()
                )
                player.prepare()
            }
            player.playWhenReady = true
        } else {
            player.stop()
            player.clearMediaItems()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
    ) {
        // 中间：媒体预览
        //  - 视频：FeedVideoPlayer（循环播放 + 视频原声静音，BGM 从 editBgmPlayer 叠加）
        //  - 图片：AsyncImage 裁剪居中
        if (coverUri != null) {
            if (firstIsVideo) {
                FeedVideoPlayer(
                    videoUri = coverUri.toString(),
                    isCurrentPage = true,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxSize()
                )
            } else {
                AsyncImage(
                    model = coverUri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxSize()
                )
            }
        }

        // ========== 顶栏 ==========
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                // 返回时停掉编辑页 BGM，避免退到相册仍响
                editBgmPlayer?.pause()
                onBack()
            }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            // BGM 选择条
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .padding(end = 10.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable { showBgmPicker = true },
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.MusicNote, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("AI", color = Color(0xFFD0FD55), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (draftBgm != null) "本地音乐库·${draftBgm!!.title}" else "默认BGM·纯音乐片段",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.Close, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                }
            }
            Icon(Icons.Filled.Settings, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }

        // ========== 右侧垂直工具栏 ==========
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp, bottom = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            EditSideTool(Icons.Filled.TextFields, "文字")
            EditSideTool(Icons.Filled.Tag, "话题")
            EditSideTool(Icons.Outlined.EmojiEmotions, "贴纸")
            EditSideTool(Icons.Filled.Star, "特效")
            EditSideTool(Icons.Filled.Sell, "标记")
            EditSideTool(Icons.Filled.WbSunny, "滤镜")
            EditSideTool(Icons.Filled.ExpandMore, "更多", rotate180 = true)
        }

        // ========== 底部 ==========
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 8.dp)
        ) {
            // AI 胶囊 + 特效
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.10f))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Star, null, tint = Color(0xFFC4A160), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("AI", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(4.dp))
                    Text("故事感照，情绪文案一键配", color = Color.White, fontSize = 13.sp)
                }
                Spacer(Modifier.width(8.dp))
                Row(
                    modifier = Modifier
                        .height(36.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(Icons.Filled.Star, null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Text("特效", color = Color.White, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            // 限时日常 + 下一步
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 「限时日常」胶囊按钮
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable { limitedTime = !limitedTime }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LimitedDailyDot(active = limitedTime)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "限时日常",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Button(
                    onClick = {
                        // 下一步前先停掉编辑页 BGM，避免进入第四步/继续播放占用音频焦点
                        editBgmPlayer?.pause()
                        onNext()
                    },
                    enabled = selection.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55), disabledContainerColor = Color(0x44FE2C55)),
                    shape = RoundedCornerShape(23.dp)
                ) {
                    Text("下一步", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // BGM 选择弹窗（复用 BgmPickerSheet）
        if (showBgmPicker) {
            BgmPickerSheet(
                viewModel = viewModel,
                onDismiss = { showBgmPicker = false },
                onSongSelect = { song ->
                    // 选中即生效：写入 draftBgm → editBgmPlayer 自动切歌并播放
                    viewModel.setDraftBgm(song)
                },
                onTogglePlayback = {
                    // 同首歌再次点击：切换编辑页 BGM 播放/暂停
                    val player = editBgmPlayer
                    if (player != null) {
                        player.playWhenReady = !player.playWhenReady
                    }
                }
            )
        }
    }
}

@Composable
private fun LimitedDailyDot(active: Boolean) {
    // 彩虹色外圈 + 内部蓝绿色小圈（模拟截图中的效果）
    val brush = Brush.sweepGradient(
        colors = listOf(
            Color(0xFFFF4D67), Color(0xFFFFBA5C), Color(0xFFFFDB6A),
            Color(0xFF22C55E), Color(0xFF22D3EE), Color(0xFF3B82F6),
            Color(0xFF8B5CF6), Color(0xFFFF4D67)
        )
    )
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(brush),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(17.dp)
                .clip(CircleShape)
                .background(Color(0xFF1F1F1F)),
            contentAlignment = Alignment.Center
        ) {
            if (active) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.horizontalGradient(
                                listOf(Color(0xFF22D3EE), Color(0xFF3B82F6))
                            )
                        )
                )
            }
        }
    }
}

@Composable
private fun EditSideTool(icon: ImageVector, label: String, rotate180: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            null,
            tint = Color.White,
            modifier = Modifier
                .size(26.dp)
                .then(if (rotate180) Modifier else Modifier)
                .let {
                    if (rotate180) it else it
                }
        )
        Spacer(Modifier.height(3.dp))
        Text(label, color = Color.White, fontSize = 11.sp)
    }
}

// ============================================================================
// Step 4：作品设置（对应截图4）
//  白色背景、封面预览、标题/描述、话题推荐、地点、标签、声明、公开、高级
//  底部：分享 + 限时日常 + 发作品
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Step4SubmitScreen(
    viewModel: CreatorViewModel,
    profileIncomplete: Boolean,
    onBack: () -> Unit,
    onPublished: () -> Unit
) {
    val selection by viewModel.draftSelection.collectAsState()
    val draftBgm by viewModel.draftBgm.collectAsState()
    val coverUri = selection.firstOrNull()?.contentUri
    // title/desc/publishing 下沉到 VM，避免 Composable 重组丢失
    val title by viewModel.draftTitle.collectAsState()
    val desc by viewModel.draftCaption.collectAsState()
    val tags by viewModel.draftTags.collectAsState()
    val location by viewModel.draftLocation.collectAsState()
    val publishing by viewModel.isPublishing.collectAsState()
    var limitedTime by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val suggestedTags = remember {
        listOf(
            "#帅气侧影", "#风格化摄影", "#暗黑系撸铁",
            "#氛围感写真创作", "#光影人像", "#高级感黑白"
        )
    }
    val suggestedLocations = remember {
        listOf("北湖景区", "睢县恒山湖湿地公园", "睢县城湖", "北湖公园", "龙美术馆")
    }

    Scaffold(
        containerColor = Color.White,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.Black)
                    }
                },
                actions = {
                    Text(
                        text = "预览",
                        modifier = Modifier.padding(end = 16.dp),
                        color = Color.Black,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    navigationIconContentColor = Color.Black
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // ===== 封面预览大卡片 + 小缩略图选择 =====
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(180.dp)
                            .height(210.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFEEEEEE)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        if (coverUri != null) {
                            AsyncImage(
                                model = coverUri,
                                null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        // AI 编辑封面
                        Box(
                            modifier = Modifier
                                .padding(bottom = 10.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(Color.Black.copy(alpha = 0.65f))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Star, null, tint = Color(0xFFC4A160), modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("AI 编辑封面", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    // 小缩略图行（当前封面 + 添加）
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 当前选择的封面（红框）
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .border(2.5.dp, Color(0xFFFE2C55), RoundedCornerShape(7.dp))
                        ) {
                            if (coverUri != null) {
                                AsyncImage(
                                    model = coverUri, null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        // 添加更多
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(Color(0xFFF3F3F5)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Add, null, tint = Color(0xFFAAAAAA), modifier = Modifier.size(24.dp))
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                // ===== 标题 / 描述输入 =====
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = title,
                        onValueChange = { v -> viewModel.setDraftTitle(v) },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 15.sp,
                            color = Color.Black,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (title.isEmpty()) {
                                    Text(
                                        text = "添加标题",
                                        fontSize = 15.sp,
                                        color = Color(0xFFBBBBBB),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = desc,
                        onValueChange = { v -> viewModel.setDraftCaption(v) },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 15.sp,
                            color = Color.Black,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.TopStart) {
                                if (desc.isEmpty()) {
                                    Text(
                                        text = "添加作品描述...",
                                        fontSize = 15.sp,
                                        color = Color(0xFFBBBBBB),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    Spacer(Modifier.height(18.dp))
                    // #话题 @朋友 按钮
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFF3F3F5))
                                .clickable { }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("# 话题", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFF3F3F5))
                                .clickable { }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("@ 朋友", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFF3F3F5))
                                .clickable { }
                                .padding(8.dp)
                        ) {
                            Icon(Icons.Filled.Fullscreen, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    // 推荐话题
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        suggestedTags.forEach { tag ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFFF3F3F5))
                                    .clickable {
                                        val new = tags.toMutableList()
                                        if (!new.contains(tag)) new.add(tag) else new.remove(tag)
                                        viewModel.setDraftTags(new)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    tag,
                                    color = if (tags.contains(tag)) Color(0xFFFE2C55) else Color.Black,
                                    fontSize = 13.sp,
                                    fontWeight = if (tags.contains(tag)) FontWeight.SemiBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = Color(0xFFF0F0F0), thickness = 1.dp)
                // ===== 地点、标签、声明、公开、高级 入口行 =====
                SubmitRow(
                    icon = Icons.Filled.LocationOn,
                    title = "选择地点",
                    trailing = null,
                    onClick = {}
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    suggestedLocations.forEach { loc ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFFF3F3F5))
                                .clickable { viewModel.setDraftLocation(loc) }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                loc,
                                color = if (location == loc) Color(0xFFFE2C55) else Color.Black,
                                fontSize = 13.sp,
                                fontWeight = if (location == loc) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFFF0F0F0), thickness = 1.dp)
                SubmitRow(
                    icon = Icons.Filled.FilterNone,
                    title = "添加标签",
                    trailing = "可颂机位 | 龙美术馆（西岸馆）-毛主席雕塑",
                    onClick = {}
                )
                SubmitRow(icon = Icons.Filled.Spa, title = "添加自主声明", trailing = null, onClick = {})
                SubmitRow(icon = Icons.Filled.Public, title = "公开 · 所有人可见", trailing = null, onClick = {})
                SubmitRow(icon = Icons.Filled.Settings, title = "高级设置", trailing = null, onClick = {})

                Spacer(Modifier.height(12.dp))
                Text(
                    "发布成功后将保存内容至本地",
                    fontSize = 12.sp,
                    color = Color(0xFFBBBBBB),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
            }
            // ===== 底部栏：分享 / 限时日常 / 发作品 =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF3F3F5)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Share, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                }
                // 限时日常
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(Color(0xFFF3F3F5))
                        .clickable { limitedTime = !limitedTime }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LimitedDailyDot(active = limitedTime)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "限时日常",
                        color = Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Button(
                    onClick = {
                        if (publishing || selection.isEmpty() || profileIncomplete) return@Button
                        scope.launch {
                            val ok = viewModel.publishFromDraft {
                                // 进度回调（暂不UI展示）
                            }
                            if (ok) onPublished() else {
                                android.widget.Toast.makeText(
                                    context,
                                    "保存失败，请重试",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    enabled = !publishing && selection.isNotEmpty() && !profileIncomplete,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFE2C55),
                        disabledContainerColor = Color(0x44FE2C55)
                    ),
                    shape = RoundedCornerShape(23.dp)
                ) {
                    if (publishing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        if (publishing) "发布中..." else "发作品",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun SubmitRow(
    icon: ImageVector,
    title: String,
    trailing: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color(0xFF444444), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            color = Color.Black,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    trailing,
                    color = Color(0xFF999999),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = Color(0xFFBBBBBB), modifier = Modifier.size(18.dp))
        }
    }
}
