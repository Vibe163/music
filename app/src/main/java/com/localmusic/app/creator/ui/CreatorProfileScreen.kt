package com.localmusic.app.creator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.localmusic.app.creator.data.model.MediaType
import com.localmusic.app.creator.viewmodel.CreatorViewModel

/** 背景区高度（抖音约屏幕 1/3） */
private val HeaderHeight = 300.dp
/** 头像尺寸 */
private val AvatarSize = 100.dp
/** 头像向下压出背景区的距离（一半悬浮在资料卡上） */
private val AvatarOverlap = 50.dp

/**
 * 创作者个人主页 - 1:1 复刻抖音用户主页
 *
 * 结构：
 *  - 顶部沉浸式背景区（作者作品封面）+ 渐变遮罩，头像悬浮于背景与资料卡交界
 *  - 背景区左侧：头像 + 昵称 + 抖音号；右上角：搜索 + 更多
 *  - 资料卡区：获赞/关注/粉丝（紧凑）、家人/恋人/IP、[+关注][分享]按钮、分类 Tab
 *  - 作品区：三列无间距正方形网格（Crop），视频标识、播放量、置顶标签
 */
@Composable
fun CreatorProfileScreen(
    viewModel: CreatorViewModel,
    authorId: String,
    onBack: () -> Unit,
    onWorkClick: (Int) -> Unit
) {
    val allWorks by viewModel.works.collectAsState()
    val authorWorks = remember(allWorks, authorId) {
        allWorks.filter { it.authorId == authorId }.sortedByDescending { it.createdAt }
    }
    val author = authorWorks.firstOrNull()

    if (author == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
            Text("用户不存在", color = Color.Gray)
        }
        return
    }

    // 背景图：作者第一个作品的封面（本地 URI，纯离线可用；无作品时回退深色渐变）
    val headerImage = authorWorks.firstNotNullOfOrNull { it.mediaList.firstOrNull() }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ===== Header（背景区 + 资料卡）=====
            item(span = { GridItemSpan(3) }) {
                ProfileHeader(
                    author = author,
                    headerImage = headerImage,
                    workCount = authorWorks.size
                )
            }

            // ===== 作品网格 =====
            items(authorWorks.size) { index ->
                val work = authorWorks[index]
                val onWorkItemClick = remember(work.id, allWorks, onWorkClick) {
                    {
                        val globalIndex = allWorks.indexOfFirst { it.id == work.id }
                        if (globalIndex >= 0) onWorkClick(globalIndex)
                    }
                }
                ProfileWorkItem(
                    work = work,
                    isFirst = index == 0,
                    onClick = onWorkItemClick
                )
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

        // ===== 顶部悬浮导航栏（返回 / 搜索 / 更多，覆盖在背景图上）=====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {}) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            IconButton(onClick = {}) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.MoreHoriz, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// =====================================================================
// 头部：背景区 + 资料卡
// =====================================================================
@Composable
private fun ProfileHeader(
    author: com.localmusic.app.creator.data.model.CreatorWork,
    headerImage: String?,
    workCount: Int
) {
    Column {
        // ===== 1. 沉浸式背景区（头像 / 昵称 / 抖音号）=====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HeaderHeight)
        ) {
            // 背景图（作者作品封面）
            if (headerImage != null) {
                AsyncImage(
                    model = headerImage,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(colors = listOf(Color(0xFF2B2B2B), Color(0xFF111111)))
                    )
                )
            }
            // 顶部黑色渐变（保证状态栏/导航图标可见）
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)
                    )
                )
            )
            // 底部渐变过渡到白色资料卡
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.White)
                    )
                )
            )

            // 头像 + 昵称 + 抖音号（底部左侧，头像向下压出一半到资料卡）
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 16.dp)
                    .offset(y = AvatarOverlap),
                verticalAlignment = Alignment.Bottom
            ) {
                // 圆形头像（抖音青色渐变圈 + 白边）
                Box(
                    modifier = Modifier
                        .size(AvatarSize)
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(2.dp)
                ) {
                    val ringBrush = remember {
                        Brush.sweepGradient(
                            colors = listOf(
                                Color(0xFF00F2FE), Color(0xFF4FACFE), Color(0xFF00F2FE)
                            )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(ringBrush)
                            .padding(2.dp)
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
                Column(modifier = Modifier.padding(bottom = 10.dp)) {
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
                            text = "抖音号: qingyu50251",
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

        // ===== 2. 资料卡区（获赞/关注/粉丝 + 简介 + 按钮 + Tab）=====
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp)
        ) {
            // 头像压出区域留白（头像底部 50dp 悬浮在白色资料卡上）
            Spacer(Modifier.height(AvatarOverlap + 16.dp))

            // 获赞 / 关注 / 粉丝（与头像右侧对齐，紧凑排列）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(AvatarSize))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    ProfileStat("5750", "获赞")
                    ProfileStat("421", "关注")
                    ProfileStat("78", "粉丝")
                }
            }

            Spacer(Modifier.height(14.dp))

            // 简介：家人 / 恋人 / IP
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("家人: ", fontSize = 14.sp, color = Color.Black)
                    Text("@普通人的人", fontSize = 14.sp, color = Color(0xFF507DAF))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("恋人: ", fontSize = 14.sp, color = Color.Black)
                    Text("@爱吃西瓜的火龙果(成长版)", fontSize = 14.sp, color = Color(0xFF507DAF))
                }
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFF3F3F5))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("IP: 北京", fontSize = 11.sp, color = Color.Gray)
                }
            }

            Spacer(Modifier.height(16.dp))

            // [+关注] [分享]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {},
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55)),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("关注", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                // 分享按钮（独立方钮）
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFF3F3F5))
                        .clickable { },
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

            Spacer(Modifier.height(16.dp))

            // 分类 Tab
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ProfileTab("作品 $workCount", true)
                ProfileTab("日常", false)
                ProfileTab("收藏", false)
            }
        }
    }
}

// =====================================================================
// 辅助组件
// =====================================================================

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
private fun ProfileTab(label: String, selected: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) Color.Black else Color.Gray
            )
            if (label.startsWith("作品")) {
                Icon(
                    imageVector = Icons.Filled.MoreHoriz,
                    contentDescription = null,
                    tint = if (selected) Color.Black else Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (selected) {
            Spacer(Modifier.height(5.dp))
            Box(modifier = Modifier.width(28.dp).height(3.dp).background(Color(0xFF222222)))
        }
    }
}

/** 作品网格项（正方形、Crop、无间距）：置顶标签 / 视频标识 / 播放量 */
@Composable
private fun ProfileWorkItem(
    work: com.localmusic.app.creator.data.model.CreatorWork,
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

        // 底部播放量（爱心 + 数字，深色底半透明条保证可读）
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.25f))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Favorite,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(13.dp)
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
