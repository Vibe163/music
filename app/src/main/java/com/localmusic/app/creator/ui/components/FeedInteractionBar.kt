package com.localmusic.app.creator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * 抖音风格右侧互动栏 —— 1:1复刻截图顺序
 *
 * 垂直排列顺序（从上到下）：
 *  1. 作者头像（彩色光圈 + 右下红色+号）
 *  2. 点赞（红心/白心 + 数字）
 *  3. 评论（对话框 + 数字）
 *  4. 收藏（黄星/白星 + 数字）
 *  5. 分享（箭头 + 数字）
 *  6. 拍同款（作者小头像 + "拍同款"文字）
 */
@Composable
fun FeedInteractionBar(
    avatarUri: String?,
    authorName: String,
    likeCount: Int,
    commentCount: Int,
    collectCount: Int,
    shareCount: Int,
    userLiked: Boolean,
    userCollected: Boolean,
    onAvatarClick: () -> Unit,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onCollectClick: () -> Unit,
    onShareClick: () -> Unit,
    onShootSame: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = modifier
    ) {
        // 1. 作者头像（彩色渐变光圈 + 右下红色+号）
        AvatarWithRainbowRing(
            avatarUri = avatarUri,
            authorName = authorName,
            onClick = onAvatarClick
        )

        Spacer(Modifier.height(4.dp))

        // 2. 点赞
        InteractionButton(
            icon = if (userLiked) Icons.Filled.Favorite else Icons.Outlined.Favorite,
            count = likeCount,
            tint = if (userLiked) Color(0xFFFE2C55) else Color.White,
            onClick = onLikeClick
        )

        // 3. 评论
        InteractionButton(
            icon = Icons.Filled.ChatBubble,
            count = commentCount,
            tint = Color.White,
            onClick = onCommentClick
        )

        // 4. 收藏
        InteractionButton(
            icon = if (userCollected) Icons.Filled.Star else Icons.Outlined.Star,
            count = collectCount,
            tint = if (userCollected) Color(0xFFFFC107) else Color.White,
            onClick = onCollectClick
        )

        // 5. 分享
        InteractionButton(
            icon = Icons.Filled.Share,
            count = shareCount,
            tint = Color.White,
            onClick = onShareClick
        )

        // 6. 拍同款（作者小头像 + 拍同款文字）
        ShootSameButton(
            avatarUri = avatarUri,
            onClick = onShootSame
        )
    }
}

/**
 * 作者头像：圆形 + 彩虹色渐变光圈（抖音风格）
 * 右下角一个红底白+号的圆形按钮（稍微突出）
 */
@Composable
private fun AvatarWithRainbowRing(
    avatarUri: String?,
    authorName: String,
    onClick: () -> Unit
) {
    // 彩虹渐变光圈（抖音标准配色：粉→紫→蓝→绿→黄→红）
    val rainbowBrush = Brush.sweepGradient(
        colors = listOf(
            Color(0xFFFF6B6B),   // 红
            Color(0xFFFE2C55),   // 抖音红
            Color(0xFFFFA500),   // 橙
            Color(0xFFFFEB3B),   // 黄
            Color(0xFF4CAF50),   // 绿
            Color(0xFF2196F3),   // 蓝
            Color(0xFF9C27B0),   // 紫
            Color(0xFFFE2C55),   // 抖音红
            Color(0xFFFF6B6B),   // 红
        )
    )

    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(rainbowBrush)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // 内层黑色边框 + 头像
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp)
                .clip(CircleShape)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = avatarUri,
                contentDescription = authorName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2A2A2A))
            )
        }

        // 右下角红色+号（稍微超出一点圈外）—— 用 absoluteOffset 代替负 padding
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .absoluteOffset(y = 6.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(Color(0xFFFE2C55))
                .border(1.5.dp, Color.Black, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "关注",
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

/**
 * 互动按钮：图标 + 数字（垂直排列）
 */
@Composable
private fun InteractionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = formatCount(count),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 拍同款按钮：作者小头像（圆形）+ "拍同款" 文字
 * 竖排布局
 */
@Composable
private fun ShootSameButton(
    avatarUri: String?,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF1A1A1A))
                .border(1.5.dp, Color.Black.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = avatarUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = "拍同款",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** 数字格式化：1万以下显示原数，1万以上显示 x.x万 */
private fun formatCount(count: Int): String {
    return when {
        count >= 10000 -> "${count / 10000}.${(count % 10000) / 1000}万"
        else -> count.toString()
    }
}
