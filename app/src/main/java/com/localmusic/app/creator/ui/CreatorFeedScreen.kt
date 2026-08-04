package com.localmusic.app.creator.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.DensityMedium
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.foundation.basicMarquee
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.localmusic.app.creator.data.model.CreatorWork
import com.localmusic.app.creator.data.model.MediaType
import com.localmusic.app.creator.ui.components.BgmPickerSheet
import com.localmusic.app.creator.ui.components.CommentBottomSheet
import com.localmusic.app.creator.ui.components.ConfirmDeleteWorkDialog
import com.localmusic.app.creator.ui.components.EditCaptionDialog
import com.localmusic.app.creator.ui.components.FeedBgmPlayer
import com.localmusic.app.creator.ui.components.FeedInteractionBar
import com.localmusic.app.creator.ui.components.FeedVideoPlayer
import com.localmusic.app.creator.ui.components.ShareWorkBottomSheet
import com.localmusic.app.creator.ui.components.ShareSheetAction
import com.localmusic.app.creator.viewmodel.CreatorViewModel

// 抖音红
private val DouyinRed = Color(0xFFFE2C55)

/**
 * 创作者训练空间 - 作品浏览页（观众视角）
 *
 * 1:1复刻抖音截图：
 * - 顶部：☰菜单 + [热点 精选 团购 同城 商城 直播 关注• 推荐] + 🔍搜索
 * - 中间：全屏垂直翻页视频/图片
 * - 右侧：头像(彩虹圈+红+) → 红心 → 评论 → 黄星 → 分享 → 拍同款头像
 * - 左下：推荐条 → @用户名+图文 → 文案+展开 → 相关搜索条
 * - 底部：首页(⋎) 朋友  [+大按钮]  消息  我
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CreatorFeedScreen(
    viewModel: CreatorViewModel,
    onPublishClick: () -> Unit,
    onAvatarClick: (String) -> Unit,
    onEditWork: (CreatorWork) -> Unit,
    onExitCreatorSpace: () -> Unit = {}
) {
    val works by viewModel.works.collectAsState()
    val bgmLibrary by viewModel.bgmLibrary.collectAsState()

    // 空状态（用户截图要求：只留全黑背景 + 底部 CreatorBottomNav 5 格导航，上面什么都不显示）
    if (works.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // 贴最底部：CreatorBottomNav（首页⋎ / 朋友 / 方框+号 / 消息 / 我）
            BottomDouyinNavBar(
                selectedIndex = 0,
                onTabSelected = { index ->
                    // 中间+号：发布作品
                    if (index == 2) onPublishClick()
                    // "我" tab：返回 App 主我的页面
                    if (index == 4) onExitCreatorSpace()
                },
                onExitCreatorSpace = onExitCreatorSpace,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black)
                    .navigationBarsPadding()
            )
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { works.size })

    // 当前作品（删除后 currentIndex 可能短暂越界，用 coerceIn 保证永远有效）
    val currentIndex = pagerState.currentPage
    val safeIndex = currentIndex.coerceIn(0, works.lastIndex)
    val currentWork = works[safeIndex]

    // 删除后索引越界时，异步修正 pagerState 到有效页
    LaunchedEffect(works.size, currentIndex) {
        if (currentIndex >= works.size && works.isNotEmpty()) {
            pagerState.scrollToPage(works.lastIndex)
        }
    }

    // 双击点赞红心动画状态
    var showHeart by remember { mutableStateOf(false) }
    var heartX by remember { mutableStateOf(0f) }
    var heartY by remember { mutableStateOf(0f) }

    // 视频暂停状态（单击切换）
    var isVideoPaused by remember(currentIndex) { mutableStateOf(false) }

    // 评论弹窗
    var showComments by remember { mutableStateOf(false) }

    // 分享/管理弹窗
    var showShareWork by remember { mutableStateOf(false) }

    // 编辑文案弹窗
    var showEditCaption by remember { mutableStateOf(false) }

    // 更改配音弹窗
    var showChangeBgm by remember { mutableStateOf(false) }

    // 删除确认弹窗
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // 文案展开状态
    var captionExpanded by remember(currentIndex) { mutableStateOf(false) }

    // 同步当前索引到 ViewModel
    LaunchedEffect(currentIndex) {
        viewModel.setCurrentIndex(currentIndex)
    }

    // 获取当前作品的 BGM URI（直接查询，避免中间 null 状态导致 BGM 短暂中断）
    var currentBgmUri by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentWork.bgmSongId, bgmLibrary) {
        currentBgmUri = viewModel.getBgmSong(currentWork.bgmSongId)?.uri?.toString()
    }

    // 底部导航栏当前选中tab（0=首页，1=朋友，2=发布，3=消息，4=我的）
    var bottomTabIndex by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ============================================================
        // 1. 主内容：垂直翻页视频/图片
        // ============================================================
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val work = works.getOrNull(pageIndex) ?: return@VerticalPager
            val isCurrentPage = pageIndex == currentIndex

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // 媒体内容
                when (work.mediaType) {
                    MediaType.VIDEO -> {
                        if (work.mediaList.isNotEmpty()) {
                            FeedVideoPlayer(
                                videoUri = work.mediaList.first(),
                                isCurrentPage = isCurrentPage && !isVideoPaused
                            )
                        }
                    }
                    MediaType.IMAGE -> {
                        ImageGallery(
                            imageUris = work.mediaList,
                            isCurrentPage = isCurrentPage
                        )
                    }
                }

                // 双击点赞 + 单击暂停手势层
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(pageIndex) {
                            detectTapGestures(
                                onTap = {
                                    if (work.mediaType == MediaType.VIDEO && isCurrentPage) {
                                        isVideoPaused = !isVideoPaused
                                    }
                                },
                                onDoubleTap = { offset ->
                                    if (isCurrentPage && !work.userLiked) {
                                        heartX = offset.x
                                        heartY = offset.y
                                        showHeart = true
                                        viewModel.updateWork(work.copy(userLiked = true))
                                    }
                                }
                            )
                        }
                )

                // 视频暂停图标（中央大Play箭头）
                if (work.mediaType == MediaType.VIDEO && isVideoPaused && isCurrentPage) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "播放",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(72.dp)
                        )
                    }
                }
            }
        }

        // ============================================================
        // 1.5 顶部与底部遮罩层（增强文字可读性）
        // ============================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))
                    )
                )
        )

        // ============================================================
        // 2. 双击点赞红心动画
        // ============================================================
        if (showHeart) {
            LaunchedEffect(showHeart) {
                kotlinx.coroutines.delay(900)
                showHeart = false
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { },
                contentAlignment = Alignment.TopStart
            ) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = DouyinRed,
                    modifier = Modifier
                        .let { m ->
                            val startPad = with(androidx.compose.ui.platform.LocalDensity.current) { heartX.toDp() } - 50.dp
                            val topPad = with(androidx.compose.ui.platform.LocalDensity.current) { heartY.toDp() } - 50.dp
                            m.padding(
                                start = if (startPad.value < 0) 0.dp else startPad,
                                top = if (topPad.value < 0) 0.dp else topPad
                            )
                        }
                        .size(100.dp)
                )
            }
        }

        // ============================================================
        // 3. 顶部 Tab 导航栏（1:1抖音，☰按钮返回App主导航）
        // ============================================================
        TopDouyinTabBar(
            onMenuClick = onExitCreatorSpace,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        )

        // ============================================================
        // 4. 右侧互动栏（1:1抖音）—— 居中偏下定位
        // ============================================================
        FeedInteractionBar(
            avatarUri = currentWork.authorAvatarUri,
            authorName = currentWork.authorName,
            likeCount = currentWork.likeCount + (if (currentWork.userLiked) 1 else 0),
            commentCount = currentWork.commentCount,
            collectCount = currentWork.collectCount + (if (currentWork.userCollected) 1 else 0),
            shareCount = currentWork.shareCount,
            userLiked = currentWork.userLiked,
            userCollected = currentWork.userCollected,
            onAvatarClick = { onAvatarClick(currentWork.authorId) },
            onLikeClick = {
                viewModel.updateWork(currentWork.copy(userLiked = !currentWork.userLiked))
            },
            onCommentClick = { showComments = true },
            onCollectClick = {
                viewModel.updateWork(currentWork.copy(userCollected = !currentWork.userCollected))
            },
            onShareClick = { showShareWork = true },
            onShootSame = { onPublishClick() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 150.dp)
        )

        // ============================================================
        // 5. 左下角信息区（1:1抖音）
        // ============================================================
        BottomLeftInfoPanel(
            work = currentWork,
            captionExpanded = captionExpanded,
            onCaptionToggle = { captionExpanded = !captionExpanded },
            onEditWork = { onEditWork(currentWork) },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 12.dp, bottom = 72.dp, end = 76.dp)
        )

        // ============================================================
        // 6. 底部导航栏（1:1抖音）
        // ============================================================
        BottomDouyinNavBar(
            selectedIndex = bottomTabIndex,
            onTabSelected = { index ->
                bottomTabIndex = index
                // 中间大+号 → 发布
                if (index == 2) onPublishClick()
            },
            onExitCreatorSpace = onExitCreatorSpace,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black)
                .navigationBarsPadding()
        )
    }

    // 播放 BGM
    FeedBgmPlayer(
        bgmUri = currentBgmUri,
        isCurrentPage = true
    )

    // 评论弹窗
    if (showComments) {
        CommentBottomSheet(
            commentCount = currentWork.commentCount,
            onDismiss = { showComments = false }
        )
    }

    // 分享/管理弹窗（只负责抛事件，不嵌套子弹窗）
    if (showShareWork) {
        ShareWorkBottomSheet(
            work = currentWork,
            onDismiss = { showShareWork = false },
            onAction = { action ->
                // 先关闭分享面板，再显示对应子弹窗（避免蒙层叠加）
                showShareWork = false
                when (action) {
                    ShareSheetAction.EditCaption -> showEditCaption = true
                    ShareSheetAction.ChangeBgm -> showChangeBgm = true
                    ShareSheetAction.DeleteConfirm -> showDeleteConfirm = true
                }
            }
        )
    }

    // 编辑文案弹窗（同级渲染，不嵌套）
    if (showEditCaption) {
        EditCaptionDialog(
            initialTitle = currentWork.title,
            initialCaption = currentWork.caption,
            initialTags = currentWork.tags,
            onDismiss = { showEditCaption = false },
            onConfirm = { newTitle, newCaption, newTags ->
                viewModel.updateWork(
                    currentWork.copy(
                        title = newTitle,
                        caption = newCaption,
                        tags = newTags
                    )
                )
                showEditCaption = false
            }
        )
    }

    // 更改配音弹窗（同级渲染，不嵌套）
    if (showChangeBgm) {
        BgmPickerSheet(
            viewModel = viewModel,
            onSongSelect = { song ->
                viewModel.updateWork(currentWork.copy(bgmSongId = song.id))
            },
            onTogglePlayback = { /* 配音选择页复用现有逻辑 */ },
            onDismiss = { showChangeBgm = false }
        )
    }

    // 删除确认弹窗（同级渲染，不嵌套）
    if (showDeleteConfirm) {
        ConfirmDeleteWorkDialog(
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                viewModel.deleteWork(currentWork.id)
                showDeleteConfirm = false
            }
        )
    }
}

// =====================================================================
// 顶部导航Tab栏（1:1抖音截图）
@Composable
private fun TopDouyinTabBar(
    onMenuClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        "热点", "精选", "团购", "同城", "商城", "直播", "关注", "推荐"
    )
    var selectedIndex by remember { mutableStateOf(7) } // 默认选中"推荐"
    val followIndex = 6

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧 ☰ 菜单按钮
        Icon(
            Icons.Filled.Menu,
            contentDescription = "菜单",
            tint = Color.White,
            modifier = Modifier
                .padding(start = 12.dp, end = 6.dp)
                .size(24.dp)
                .clickable(onClick = onMenuClick)
        )

        // 中间可横向滚动的Tab列表
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = index == selectedIndex
                val isFollow = index == followIndex
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { selectedIndex = index }
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Text(
                            text = tab,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
                            fontSize = 16.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                        if (isFollow) {
                            Box(
                                modifier = Modifier
                                    .absoluteOffset(x = 5.dp, y = (-2).dp)
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(DouyinRed)
                            )
                        }
                    }
                    if (isSelected) {
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height(2.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(Color.White)
                        )
                    } else {
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }

        // 右侧 🔍 搜索按钮
        Icon(
            Icons.Filled.Search,
            contentDescription = "搜索",
            tint = Color.White,
            modifier = Modifier
                .padding(start = 6.dp, end = 12.dp)
                .size(24.dp)
        )
    }
}

// 左下角信息区（1:1抖音截图）
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun BottomLeftInfoPanel(
    work: CreatorWork,
    captionExpanded: Boolean,
    onCaptionToggle: () -> Unit,
    onEditWork: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. @用户名 + [图文]标签
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "@${work.authorName.trimStart('@')}",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onEditWork() }
            )
            if (work.mediaType == MediaType.IMAGE) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        androidx.compose.material.icons.Icons.Filled.SmartDisplay,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = "图文",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 2. 文案 + 展开
        if (work.caption.isNotBlank()) {
            val maxChars = 50
            val truncated = work.caption.length > maxChars && !captionExpanded
            val showText = if (truncated) work.caption.take(maxChars) + "..." else work.caption

            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = showText,
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier
                        .clickable { onEditWork() }
                )
                if (truncated) {
                    Text(
                        text = "展开",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClick = onCaptionToggle)
                    )
                }
            }
        }

        // 3. 相关搜索条 (1:1复刻抖音样式)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.25f))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = Color(0xFFFE2C55),
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "及時行樂", // 模拟截图中的搜索词
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }

        // 4. 滚动音乐标题条 (Marquee)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 2.dp)
        ) {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "AI 默认BGM - 纯音乐片段",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .width(180.dp)
                    .basicMarquee(iterations = Int.MAX_VALUE)
            )
        }
    }
}

// 底部导航栏（1:1抖音截图）
@Composable
private fun BottomDouyinNavBar(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    onExitCreatorSpace: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(Color.Black),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Tab 0: 首页
        BottomNavTab(
            label = "首页",
            isSelected = selectedIndex == 0,
            hasDropdown = true,
            onClick = { onTabSelected(0) }
        )
        // Tab 1: 朋友
        BottomNavTab(
            label = "朋友",
            isSelected = selectedIndex == 1,
            onClick = { onTabSelected(1) }
        )
        // Tab 2: 中间+号 (1:1深度复刻抖音三层叠加样式)
        Box(
            modifier = Modifier
                .width(45.dp)
                .height(28.dp)
                .clickable { onTabSelected(2) },
            contentAlignment = Alignment.Center
        ) {
            // 底层：左青右红的溢出背景
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
            ) {
                Box(modifier = Modifier.weight(1f).fillMaxSize().background(Color(0xFF25F4EE)))
                Box(modifier = Modifier.weight(1f).fillMaxSize().background(Color(0xFFFE2C55)))
            }
            // 顶层：覆盖在中间的白色按钮，露出左右边缘
            Box(
                modifier = Modifier
                    .width(37.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "发布",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        // Tab 3: 消息
        BottomNavTab(
            label = "消息",
            isSelected = selectedIndex == 3,
            onClick = { onTabSelected(3) }
        )
        // Tab 4: 我
        BottomNavTab(
            label = "我",
            isSelected = selectedIndex == 4,
            onClick = {
                onTabSelected(4)
                onExitCreatorSpace()
            }
        )
    }
}

@Composable
private fun BottomNavTab(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    hasDropdown: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = label,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                fontSize = 18.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold
            )
            if (hasDropdown) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}



// =====================================================================
// 图片作品多图画廊（左右滑切换）
// =====================================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageGallery(
    imageUris: List<String>,
    isCurrentPage: Boolean
) {
    if (imageUris.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "点击底部 + 发布作品",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp
                )
            }
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { imageUris.size })

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            AsyncImage(
                model = Uri.parse(imageUris[pageIndex]),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 多图指示器（底部圆点）
        if (imageUris.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                repeat(imageUris.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pagerState.currentPage) Color.White
                                else Color.White.copy(alpha = 0.35f)
                            )
                    )
                }
            }
        }
    }
}

// =====================================================================
// 空状态（没作品时的引导页）
// =====================================================================
@Composable
private fun EmptyFeedState(onPublishClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "还没有作品",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "点击底部中间 + 按钮发布第一个作品",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp
            )
            Spacer(Modifier.height(10.dp))
            // 模拟底部中间的大+号（跟正式导航里的一样）
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFF25F4EE), Color(0xFFFE2C55))
                        )
                    )
                    .clickable(onClick = onPublishClick),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(52.dp)
                        .height(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "发布",
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
