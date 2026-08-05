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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.localmusic.app.creator.data.model.CreatorWork
import com.localmusic.app.creator.data.model.MediaType
import com.localmusic.app.creator.viewmodel.UserHomeViewModel

/**
 * 用户主页 —— 高度仿写抖音移动端个人主页
 *
 * 层级顺序固定（不可修改）：
 *  1. 第一层：顶部全屏背景横幅（本地图片，沉浸式顶部）
 *  2. 第二层：用户信息区（左侧圆形头像 + 右侧昵称/备注/统计/IP）
 *  3. 第三层：操作按钮区（居中红色关注按钮 + 右侧分享按钮）
 *  4. 第四层：Material3 TabRow（作品 / 日常 / 收藏）
 *  5. 第五层：作品列表区（3 列等宽网格，本地媒体卡片）
 *
 * 数据全部本地（UserHomeViewModel 纯本地筛选），无网络请求。
 */
@Composable
fun UserHomeScreen(
    viewModel: UserHomeViewModel,
    onBack: () -> Unit
) {
    val works by viewModel.works.collectAsState()
    val visibleWorks by viewModel.visibleWorks.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val headerMedia by viewModel.headerMedia.collectAsState()
    val likeCount by viewModel.likeCount.collectAsState()

    val author = works.firstOrNull()
    if (author == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Text("用户不存在", color = Color.Gray)
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // 第一~四层：Header（背景横幅 / 用户信息 / 按钮 / Tab）
            item(span = { GridItemSpan(3) }) {
                UserHomeHeader(
                    author = author,
                    headerMedia = headerMedia,
                    likeCount = likeCount,
                    selectedTab = selectedTab,
                    onTabSelected = viewModel::selectTab
                )
            }

            // 第五层：3 列本地媒体网格（Coil 加载本地封面 / VideoFrameDecoder 视频缩略图）
            items(visibleWorks.size) { index ->
                LocalMediaGridItem(work = visibleWorks[index])
            }

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

        // 沉浸式顶部返回按钮（悬浮在背景横幅上）
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
                    Icon(
                        Icons.Filled.ChevronLeft,
                        contentDescription = "返回",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

// =====================================================================
// 第一层 ~ 第四层：Header
// =====================================================================
@Composable
private fun UserHomeHeader(
    author: CreatorWork,
    headerMedia: String?,
    likeCount: Int,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Column {
        // ===== 第一层：顶部全屏背景横幅（本地图片 + 渐变遮罩）=====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
        ) {
            if (headerMedia != null) {
                AsyncImage(
                    model = headerMedia,
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
            // 顶部黑色渐变（沉浸式，保证状态栏/返回图标可见）
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)
                    )
                )
            )
            // 底部渐变过渡到白色内容区
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.6f), Color.White)
                    )
                )
            )
        }

        // ===== 第二层：用户信息区（左头像 + 右信息）=====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧圆形本地头像
            AsyncImage(
                model = author.authorAvatarUri,
                contentDescription = "头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2A2A2A))
            )
            Spacer(Modifier.width(14.dp))

            // 右侧：昵称 / 个人备注 / 获赞关注粉丝 / IP属地
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = author.authorName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (author.authorBio.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = author.authorBio,
                        fontSize = 13.sp,
                        color = Color.Gray,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ProfileStatText("获赞 ${formatCount(likeCount)}")
                    ProfileStatText("关注 421")
                    ProfileStatText("粉丝 78")
                }
                Spacer(Modifier.height(6.dp))
                Text("IP属地: 北京", fontSize = 11.sp, color = Color.Gray)
            }
        }

        // ===== 第三层：操作按钮区（居中红色关注 + 右侧分享）=====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {},
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55)),
                shape = RoundedCornerShape(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(2.dp))
                Text("关注", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            // 右侧独立分享按钮
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFF3F3F5))
                    .clickable { },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = "分享",
                    tint = Color.Black,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ===== 第四层：Material3 TabRow（作品 / 日常 / 收藏）=====
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.White,
            contentColor = Color.Black
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { onTabSelected(0) },
                text = { Text("作品", fontSize = 15.sp, fontWeight = FontWeight.Medium) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { onTabSelected(1) },
                text = { Text("日常", fontSize = 15.sp, fontWeight = FontWeight.Medium) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { onTabSelected(2) },
                text = { Text("收藏", fontSize = 15.sp, fontWeight = FontWeight.Medium) }
            )
        }
    }
}

// =====================================================================
// 第五层：3 列本地媒体网格项
// =====================================================================
/** 本地媒体卡片：Coil 加载本地封面，视频缩略图由 VideoFrameDecoder 生成，正方形等宽 */
@Composable
private fun LocalMediaGridItem(work: CreatorWork) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(Color(0xFFF3F3F5))
    ) {
        AsyncImage(
            model = work.mediaList.firstOrNull(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

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

        // 底部播放量（爱心 + 数字）
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

// =====================================================================
// 辅助
// =====================================================================

/** 统计文本（抖音样式：数字粗体 + 灰色标签，紧凑） */
@Composable
private fun ProfileStatText(text: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        val parts = text.split(" ")
        Text(text = parts.first(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        if (parts.size > 1) {
            Spacer(Modifier.width(3.dp))
            Text(text = parts[1], fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

/** 数字格式化：1.2万 / 3.4亿（抖音风格） */
private fun formatCount(count: Int): String = when {
    count >= 100_000_000 -> "${(count / 100_000_000.0).let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it) }}亿"
    count >= 10_000 -> "${(count / 10_000.0).let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it) }}万"
    else -> count.toString()
}
