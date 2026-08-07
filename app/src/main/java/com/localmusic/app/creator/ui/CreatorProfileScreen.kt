package com.localmusic.app.creator.ui

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.localmusic.app.creator.data.model.CreatorWork
import com.localmusic.app.creator.data.model.MediaType
import com.localmusic.app.creator.ui.components.CreatorProfileEditorDialog
import com.localmusic.app.creator.ui.components.ProfileMoreSheet
import com.localmusic.app.creator.viewmodel.CreatorProfileViewModel
import com.localmusic.app.creator.viewmodel.CreatorViewModel
import kotlinx.coroutines.launch

/** 背景区高度（约屏幕 40%，沉浸式延伸穿透状态栏） */
private val HeaderHeight = 350.dp
/** 头像尺寸 */
private val AvatarSize = 90.dp
/** 头像向下压出背景区的距离（一半悬浮在资料卡上） */
private val AvatarOverlap = 45.dp
/** 下滑关闭手势：触发退场的位移阈值 */
private val DismissThreshold = 120.dp
/** 下滑关闭手势：Alpha 渐变到最淡所需的参考位移 */
private val DismissAlphaDistance = 480.dp

/**
 * 创作者个人主页 —— 1:1 复刻抖音用户主页（独立全屏层级）
 *
 * 层级与手势（解决与视频流重叠/混合）：
 *  - 本页是独立的全屏不透明层（路由级滑入/滑出转场，不透明背景）
 *  - 顶部 Header 区支持「下滑关闭」：整页跟随手指平移（TranslationY）
 *    并同步渐变（Alpha），超过阈值自动滑出销毁并返回，未超过则回弹
 *  - 生命周期同步：页面打开时下层视频流/BGM 自动暂停，销毁后恢复播放
 *
 * 结构（1:1 抖音）：
 *  - 沉浸式背景区：大图背景（高斯模糊 + 暗色遮罩 + 底部白色渐变）穿透状态栏
 *  - 头像（白边大圆）悬浮于背景与资料卡交界，右侧：昵称 + 抖音号
 *  - 资料卡区：获赞/关注/粉丝 → 简介 → IP属地 → 编辑资料/关注按钮 + 分享
 *  - 「作品 / 喜欢」双 Tab + 三列无间距网格（封面底部黑色渐变 + 白色播放量）
 *
 * 功能（与抖音一致）：
 *  - Tab 切换：作品(全部) / 喜欢(userLiked)
 *  - 关注/取关：按 authorId 持久化，按钮状态实时变化
 *  - 自己主页：显示「编辑资料」，点击弹出资料编辑器（改名/头像后实时同步）
 *  - 本地搜索：搜索按钮展开搜索栏，按标题/文案/标签过滤作品
 *  - 更多：底部弹窗（举报/拉黑/分享主页/复制抖音号）
 */
@Composable
fun CreatorProfileScreen(
    profileViewModel: CreatorProfileViewModel,
    creatorViewModel: CreatorViewModel,
    onBack: () -> Unit,
    onWorkClick: (Int) -> Unit
) {
    val authorWorks by profileViewModel.authorWorks.collectAsState()
    val visibleWorks by profileViewModel.visibleWorks.collectAsState()
    val searchedWorks by profileViewModel.searchedWorks.collectAsState()
    val isSearching by profileViewModel.isSearching.collectAsState()
    val searchQuery by profileViewModel.searchQuery.collectAsState()
    val selectedTab by profileViewModel.selectedTab.collectAsState()
    val isFollowing by profileViewModel.isFollowing.collectAsState()
    val isSelf by profileViewModel.isSelf.collectAsState()
    val totalLikeCount by profileViewModel.totalLikeCount.collectAsState()
    val fanCount by profileViewModel.fanCount.collectAsState()
    val workCount by profileViewModel.workCount.collectAsState()
    val likedCount by profileViewModel.likedCount.collectAsState()
    val headerMedia by profileViewModel.headerMedia.collectAsState()

    val author = authorWorks.firstOrNull()

    // 顶部搜索栏展开状态
    var showSearchBar by remember { mutableStateOf(false) }
    // 更多弹窗
    var showMoreSheet by remember { mutableStateOf(false) }
    // 编辑资料弹窗（自己主页）
    var showEditProfile by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // ====================================================================
    // 下滑关闭手势：整页平移（TranslationY）+ 渐变（Alpha）退场
    // ====================================================================
    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    val pageAlpha = remember { Animatable(1f) }
    val density = LocalDensity.current
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val dismissThresholdPx = with(density) { DismissThreshold.toPx() }
    val alphaDistancePx = with(density) { DismissAlphaDistance.toPx() }

    /** 滑出退场：平移到底部 + 淡出，完成后彻底销毁本页 */
    fun dismissPage() {
        scope.launch {
            launch { pageAlpha.animateTo(0f, tween(220, easing = FastOutSlowInEasing)) }
            dragOffset.animateTo(screenHeightPx, tween(220, easing = FastOutSlowInEasing))
            onBack()
        }
    }

    /** 未超过阈值：弹性回弹恢复原位 */
    fun settleBack() {
        scope.launch {
            launch { dragOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
            pageAlpha.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow))
        }
    }

    // 生命周期同步：无论以何种方式退出（下滑/返回键/系统手势），都恢复下层视频流播放
    DisposableEffect(Unit) {
        onDispose { creatorViewModel.setProfileOpen(false) }
    }

    if (author == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("该用户暂无作品", color = Color.Gray, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                Text("返回看看其他内容吧", color = Color.Gray.copy(alpha = 0.6f), fontSize = 13.sp)
            }
        }
        return
    }

    // 搜索激活时展示搜索结果，否则展示当前 Tab 作品
    val displayWorks = if (isSearching) searchedWorks else visibleWorks

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        // ===== 可拖拽的整页内容（独立不透明层级）=====
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = dragOffset.value
                    alpha = pageAlpha.value
                }
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // ===== Header（背景区 + 资料卡；Header 区域支持下滑关闭）=====
                item(span = { GridItemSpan(3) }) {
                    ProfileHeader(
                        author = author,
                        headerImage = headerMedia,
                        totalLikeCount = totalLikeCount,
                        followingCount = profileViewModel.followingCount,
                        fanCount = fanCount,
                        workCount = workCount,
                        likedCount = likedCount,
                        isSelf = isSelf,
                        isFollowing = isFollowing,
                        selectedTab = selectedTab,
                        onTabSelected = profileViewModel::selectTab,
                        onFollowClick = profileViewModel::toggleFollow,
                        onEditProfileClick = { showEditProfile = true },
                        onShareClick = {
                            Toast.makeText(context, "已复制主页链接（模拟）", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { _, dragAmount ->
                                    // 只响应下滑（页面位移向下）；上滑仅回弹
                                    scope.launch {
                                        val next = (dragOffset.value + dragAmount).coerceAtLeast(0f)
                                        dragOffset.snapTo(next)
                                        pageAlpha.snapTo(
                                            (1f - next / alphaDistancePx).coerceIn(0.15f, 1f)
                                        )
                                    }
                                },
                                onDragEnd = {
                                    if (dragOffset.value > dismissThresholdPx) dismissPage()
                                    else settleBack()
                                },
                                onDragCancel = { settleBack() }
                            )
                        }
                    )
                }

                // ===== 作品网格 =====
                if (displayWorks.isEmpty()) {
                    item(span = { GridItemSpan(3) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isSearching) "没有找到相关作品" else "暂无内容",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        }
                    }
                } else {
                    items(displayWorks) { work ->
                        val onWorkItemClick = remember(work.id, isSearching) {
                            {
                                val globalIndex = profileViewModel.getGlobalIndex(work.id)
                                if (globalIndex >= 0) onWorkClick(globalIndex)
                            }
                        }
                        // 置顶：仅非搜索状态下，作品 Tab 的第一个作品
                        val isFirst = !isSearching && selectedTab == 0 && displayWorks.first().id == work.id
                        ProfileWorkItem(
                            work = work,
                            isFirst = isFirst,
                            onClick = onWorkItemClick
                        )
                    }
                }

                // ===== Footer =====
                item(span = { GridItemSpan(3) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂时没有更多了", fontSize = 13.sp, color = Color.Gray)
                    }
                }
            }

            // ===== 顶部悬浮导航栏（返回 / 搜索 / 更多，覆盖在背景图上，随页面一起平移）=====
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        CircleIconButton(Icons.Filled.ChevronLeft, "返回")
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { showSearchBar = !showSearchBar }) {
                        CircleIconButton(if (showSearchBar) Icons.Filled.Close else Icons.Filled.Search, "搜索")
                    }
                    IconButton(onClick = { showMoreSheet = true }) {
                        CircleIconButton(Icons.Filled.MoreHoriz, "更多")
                    }
                }

                // 搜索栏（展开时显示，覆盖在背景图下半部）
                if (showSearchBar) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = profileViewModel::setSearchQuery,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("搜索作者作品", fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f)) },
                            singleLine = true,
                            shape = RoundedCornerShape(20.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Black.copy(alpha = 0.35f),
                                unfocusedContainerColor = Color.Black.copy(alpha = 0.3f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = Color.White
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            leadingIcon = {
                                Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "清除",
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable { profileViewModel.clearSearch() }
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // 更多弹窗
    if (showMoreSheet) {
        ProfileMoreSheet(
            isSelf = isSelf,
            isFollowing = isFollowing,
            onDismiss = { showMoreSheet = false },
            onEditProfile = { showEditProfile = true },
            onShareProfile = {
                Toast.makeText(context, "已复制主页链接（模拟）", Toast.LENGTH_SHORT).show()
            },
            onCopyId = {
                val text = "抖音号: ${author.authorName}"
                Toast.makeText(context, "已复制：$text", Toast.LENGTH_SHORT).show()
            },
            onReport = {
                Toast.makeText(context, "感谢反馈，我们将进行评估（模拟）", Toast.LENGTH_SHORT).show()
            },
            onBlock = {
                Toast.makeText(context, "已加入黑名单（模拟）", Toast.LENGTH_SHORT).show()
            },
            onToggleFollow = profileViewModel::toggleFollow
        )
    }

    // 编辑资料弹窗（自己主页）
    if (showEditProfile) {
        CreatorProfileEditorDialog(
            viewModel = creatorViewModel,
            onDismiss = { showEditProfile = false }
        )
    }
}

// =====================================================================
// 头部：沉浸式背景区 + 资料卡（1:1 抖音）
// =====================================================================
@Composable
private fun ProfileHeader(
    author: CreatorWork,
    headerImage: String?,
    totalLikeCount: Int,
    followingCount: Int,
    fanCount: Int,
    workCount: Int,
    likedCount: Int,
    isSelf: Boolean,
    isFollowing: Boolean,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onFollowClick: () -> Unit,
    onEditProfileClick: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // ===== 1. 沉浸式背景区（穿透状态栏，底部白色渐变过渡）=====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HeaderHeight)
        ) {
            // 背景图（作者作品封面，高斯模糊）
            if (headerImage != null) {
                AsyncImage(
                    model = headerImage,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(radius = 24.dp)
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(colors = listOf(Color(0xFF2B2B2B), Color(0xFF111111)))
                    )
                )
            }
            // 暗色遮罩（保证状态栏/导航图标与文字可见）
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
            )
            // 底部渐变过渡到白色资料卡
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.6f), Color.White)
                    )
                )
            )

            // 头像 + 昵称 + 抖音号（底部左侧，头像向下压出一半到资料卡）
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 24.dp, end = 16.dp)
                    .offset(y = AvatarOverlap),
                verticalAlignment = Alignment.Bottom
            ) {
                // 圆形头像（白色细边框，抖音样式）
                Box(
                    modifier = Modifier
                        .size(AvatarSize)
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    ) {
                        if (author.authorAvatarUri != null) {
                            AsyncImage(
                                model = author.authorAvatarUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        } else {
                            Box(
                                Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFF2A2A2A)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.width(14.dp))

                // 昵称 + 抖音号（白色带阴影，浮在背景图上）
                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                    Text(
                        text = author.authorName,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.5f),
                                offset = androidx.compose.ui.geometry.Offset(0f, 1f),
                                blurRadius = 4f
                            )
                        )
                    )
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "抖音号: ${author.authorName}",
                            fontSize = 13.sp,
                            color = Color.White,
                            style = androidx.compose.ui.text.TextStyle(
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.5f),
                                    offset = androidx.compose.ui.geometry.Offset(0f, 1f),
                                    blurRadius = 4f
                                )
                            )
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }

        // ===== 2. 资料卡区（获赞/关注/粉丝 + 简介 + 属地 + 按钮 + Tab）=====
        // 统一边距：左右 16dp，元素间垂直 8~12dp（1:1 抖音）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp)
        ) {
            // 头像压出区域留白（头像底部悬浮在白色资料卡上）
            Spacer(Modifier.height(AvatarOverlap + 14.dp))

            // 获赞 / 关注 / 粉丝（紧凑排列，紧跟头像下方）
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                ProfileStat(formatCount(totalLikeCount), "获赞")
                ProfileStat(followingCount.toString(), "关注")
                ProfileStat(formatCount(fanCount), "粉丝")
            }

            Spacer(Modifier.height(10.dp))

            // 个人简介
            if (author.authorBio.isNotBlank()) {
                Text(
                    text = author.authorBio,
                    fontSize = 14.sp,
                    color = Color.Black,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
            }

            // IP 属地
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFF3F3F5))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("IP：北京", fontSize = 11.sp, color = Color.Gray)
            }

            Spacer(Modifier.height(12.dp))

            // 主操作按钮：自己→[编辑资料]  他人→[+关注/已关注]；次操作：[分享]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isSelf) {
                    // 自己主页：编辑资料（描边按钮，抖音样式）
                    OutlinedButton(
                        onClick = onEditProfileClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.Black
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Black)
                        Spacer(Modifier.width(4.dp))
                        Text("编辑资料", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    // 他人主页：关注 / 已关注
                    Button(
                        onClick = onFollowClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFollowing) Color(0xFFF3F3F5) else Color(0xFFFE2C55)
                        ),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        if (isFollowing) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("已关注", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text("＋关注", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                // 分享按钮（独立方钮）
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFF3F3F5))
                        .clickable { onShareClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // 分类 Tab（1:1 抖音：作品 / 喜欢）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                ProfileTab("作品 $workCount", selectedTab == 0) { onTabSelected(0) }
                ProfileTab("喜欢 $likedCount", selectedTab == 1) { onTabSelected(1) }
            }
        }
    }
}

// =====================================================================
// 辅助组件
// =====================================================================

/** 顶部图标按钮（纯白色图标，无背景，浮在背景图上，抖音样式） */
@Composable
private fun CircleIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String) {
    Icon(icon, contentDescription = desc, tint = Color.White, modifier = Modifier.size(24.dp))
}

/** 获赞/关注/粉丝 数字项（抖音样式：数字粗体 + 灰色标签，紧凑） */
@Composable
private fun ProfileStat(count: String, label: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(text = count, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        Spacer(Modifier.width(4.dp))
        Text(text = label, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 2.dp))
    }
}

/** 分类 Tab（选中时黑色粗体 + 底部短下划线） */
@Composable
private fun ProfileTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 2.dp)
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.Black else Color.Gray
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(26.dp)
                .height(3.dp)
                .background(if (selected) Color(0xFF222222) else Color.Transparent)
        )
    }
}

/** 作品网格项（正方形、Crop、无间距）：置顶标签 / 视频标识 / 底部渐变 + 白色播放量 */
@Composable
private fun ProfileWorkItem(
    work: CreatorWork,
    isFirst: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(Color(0xFFF3F3F5))
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = work.mediaList.firstOrNull(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // 底部黑色渐变阴影（保证浅色封面下播放量可读，1:1 抖音）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .align(Alignment.BottomStart)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.85f)
                    )
                )
        )

        // 置顶标签（第一个作品，抖音黄色"置顶"）
        if (isFirst) {
            Box(
                modifier = Modifier
                    .padding(5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFFEBC0B))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text("置顶", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 视频作品右上角视频标识
        if (work.mediaType == MediaType.VIDEO) {
            Icon(
                imageVector = Icons.Filled.SmartDisplay,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(18.dp)
            )
        }

        // 左下角播放量（白色小字 + 点赞图标，位于渐变阴影之上）
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 6.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Favorite,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = formatCount(work.likeCount),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/** 数字格式化：1.2万 / 3.4亿（抖音风格） */
private fun formatCount(count: Int): String = when {
    count >= 100_000_000 -> "${(count / 100_000_000.0).let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it) }}亿"
    count >= 10_000 -> "${(count / 10_000.0).let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it) }}万"
    else -> count.toString()
}
