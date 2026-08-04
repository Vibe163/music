package com.localmusic.app.creator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.outlined.Favorite
import com.localmusic.app.creator.data.model.CreatorWork
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.localmusic.app.creator.data.model.MediaType
import com.localmusic.app.creator.viewmodel.CreatorViewModel

/**
 * 创作者个人主页 - 1:1复刻抖音截图
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
    val context = androidx.compose.ui.platform.LocalContext.current

    if (author == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
            Text("用户不存在", color = Color.Gray)
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        // 1. 背景图 (Header Cover)
        AsyncImage(
            model = "https://coresg-normal.trae.ai/api/ide/v1/text_to_image?prompt=high+contrast+black+and+white+dramatic+boxing+photography+punch+impact+smoke+cinematic&image_size=landscape_16_9",
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        )

        // 2. 顶部透明导航栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 4.dp),
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
                    Icon(Icons.Filled.ChevronLeft, null, tint = Color.White, modifier = Modifier.size(24.dp))
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
                    Icon(Icons.Default.Search, null, tint = Color.White, modifier = Modifier.size(20.dp))
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
                    Icon(Icons.Default.MoreHoriz, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }

        // 3. 主体内容滚动区
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            // Header 部分
            item(span = { GridItemSpan(3) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                ) {
                    // 头像与基本信息
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 160.dp) // 露出一部分背景
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                            .background(Color.White)
                            .padding(horizontal = 16.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                // 头像
                                Box(
                                    modifier = Modifier
                                        .offset(y = (-30).dp)
                                        .size(100.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .padding(2.dp)
                                ) {
                                    // 抖音青色外圈
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
                                        AsyncImage(
                                            model = author.authorAvatarUri,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                                .border(2.dp, Color.White, CircleShape)
                                        )
                                    }
                                }

                                Spacer(Modifier.width(16.dp))

                                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                                    Text(
                                        text = author.authorName,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "抖音号: qingyu50251",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            imageVector = androidx.compose.material.icons.Icons.Default.ContentCopy,
                                            contentDescription = null,
                                            tint = Color.Gray,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }

                            // 获赞 关注 粉丝
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                StatItem("5750", "获赞")
                                StatItem("421", "关注")
                                StatItem("78", "粉丝")
                            }

                            Spacer(Modifier.height(16.dp))

                            // 简介
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("家人: ", fontSize = 14.sp, color = Color.Black)
                                    Text("@普通人的人", fontSize = 14.sp, color = Color(0xFF507DAF))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("恋人: ", fontSize = 14.sp, color = Color.Black)
                                    Text("@爱吃西瓜的火龙果(成长版)", fontSize = 14.sp, color = Color(0xFF507DAF))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFF3F3F5))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("IP: 北京", fontSize = 11.sp, color = Color.Gray)
                            }

                            Spacer(Modifier.height(16.dp))

                            // 按钮
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
                                    Icon(androidx.compose.material.icons.Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    Text("关注", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFF3F3F5))
                                        .clickable { },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Filled.ArrowDropDown,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            // Tab 切换
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                ProfileTab("作品 6", true)
                                ProfileTab("日常", false)
                                ProfileTab("收藏", false)
                            }
                        }
                    }
                    HorizontalDivider(color = Color(0xFFF0F0F0), thickness = 0.5.dp)
                }
            }

            // 作品列表
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
                    isSecond = index == 1,
                    onClick = onWorkItemClick
                )
            }

            // Footer
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

        // 底部分享条
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp)
                .padding(horizontal = 16.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFFF8F8F8))
                .clickable { }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Send, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("快捷分享主页链接给朋友", fontSize = 14.sp, color = Color.Black, modifier = Modifier.weight(1f))
                Text("复制链接", fontSize = 14.sp, color = Color(0xFF507DAF), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StatItem(count: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = count, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        Spacer(Modifier.width(4.dp))
        Text(text = label, fontSize = 14.sp, color = Color.Gray)
    }
}

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
                    imageVector = androidx.compose.material.icons.Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = if (selected) Color.Black else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (selected) {
            Spacer(Modifier.height(4.dp))
            Box(modifier = Modifier.width(28.dp).height(2.dp).background(Color(0xFF222222)))
        }
    }
}

@Composable
private fun ProfileWorkItem(
    work: CreatorWork,
    isFirst: Boolean,
    isSecond: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .background(Color(0xFFF3F3F5))
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = work.mediaList.firstOrNull(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // 顶部标识
        if (isFirst) {
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFFEBC0B))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text("置顶", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        } else if (isSecond) {
            Text(
                text = "9小时前",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }

        // 右上角叠加图标 (多图/视频)
        Icon(
            imageVector = androidx.compose.material.icons.Icons.Filled.SmartDisplay,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(16.dp)
        )

        // 底部点赞数 (空心爱心)
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Favorite,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "${work.likeCount}",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // "刚刚看过" 遮罩
        if (isFirst) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        androidx.compose.material.icons.Icons.Filled.PlayArrow,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        "刚刚看过",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun formatCount(count: Int): String {
    return when {
        count >= 10000 -> "${count / 10000}.${(count % 10000) / 1000}万"
        else -> count.toString()
    }
}
