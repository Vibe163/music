package com.localmusic.app.creator.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * 抖音风格右侧互动栏 —— 1:1复刻截图顺序
 *
 * 垂直排列顺序（从上到下）：
 *  1. 作者头像（白圈 + 底部红色+号）
 *  2. 点赞（红心/白心 + 数字）
 *  3. 评论（对话框 + 数字）
 *  4. 收藏（黄星/白星 + 数字）
 *  5. 分享（右弯箭头 + 数字）
 *  6. 拍同款（旋转黑胶唱片）
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
        verticalArrangement = Arrangement.spacedBy(14.dp),
        // 拦截空白区域点击：不穿透触发视频暂停（与抖音一致，右侧栏空隙不暂停）
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { }
    ) {
        // 1. 作者头像
        AvatarWithPlus(
            avatarUri = avatarUri,
            authorName = authorName,
            onClick = onAvatarClick
        )

        Spacer(Modifier.height(2.dp))

        // 2. 点赞
        InteractionButton(
            icon = Icons.Filled.Favorite,
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
            icon = Icons.Filled.Star,
            count = collectCount,
            tint = if (userCollected) Color(0xFFFFD700) else Color.White,
            onClick = onCollectClick
        )

        // 5. 分享
        InteractionButton(
            icon = Icons.AutoMirrored.Filled.Reply,
            count = shareCount,
            tint = Color.White,
            flipIcon = true,
            onClick = onShareClick
        )

        Spacer(Modifier.height(8.dp))

        // 6. 旋转唱片
        RotatingMusicDisc(
            avatarUri = avatarUri,
            onClick = onShootSame
        )
    }
}

/**
 * 作者头像：彩虹圈 + 底部红色+号
 */
@Composable
private fun AvatarWithPlus(
    avatarUri: String?,
    authorName: String,
    onClick: () -> Unit
) {
    // 抖音彩虹外圈
    val rainbowBrush = Brush.sweepGradient(
        colors = listOf(
            Color(0xFFFF4D67), Color(0xFFFFBA5C), Color(0xFFFFDB6A),
            Color(0xFF22C55E), Color(0xFF22D3EE), Color(0xFF3B82F6),
            Color(0xFF8B5CF6), Color(0xFFFF4D67)
        )
    )

    Box(
        modifier = Modifier
            .size(50.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // 彩虹外圈
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(rainbowBrush)
                .padding(1.5.dp)
        ) {
            // 头像主体
            AsyncImage(
                model = avatarUri,
                contentDescription = authorName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(1.dp, Color.Black, CircleShape)
                    .background(Color(0xFF2A2A2A))
            )
        }

        // 底部红色+号
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .absoluteOffset(y = 8.dp)
                .size(19.dp)
                .clip(CircleShape)
                .background(Color(0xFFFE2C55))
                .border(1.2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "关注",
                tint = Color.White,
                modifier = Modifier.size(13.dp)
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
    flipIcon: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(38.dp)
                .graphicsLayer {
                    if (flipIcon) {
                        rotationY = 180f // 翻转 Reply 图标使其向右
                    }
                }
        )
        Text(
            text = formatCount(count),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 1.dp)
        )
    }
}

/**
 * 旋转唱片按钮：黑胶唱片效果 (1:1复刻纹理与音符)
 */
@Composable
private fun RotatingMusicDisc(
    avatarUri: String?,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "disc")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing)
        ),
        label = "rotation"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .graphicsLayer { rotationZ = rotation },
            contentAlignment = Alignment.Center
        ) {
            // 外圈黑胶（带细微纹理）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .drawBehind {
                        // 绘制唱片细纹
                        val radius = size.minDimension / 2
                        val centerX = size.width / 2
                        val centerY = size.height / 2
                        for (i in 1..4) {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.05f),
                                radius = radius * (0.4f + i * 0.12f),
                                center = Offset(centerX, centerY),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.5.dp.toPx())
                            )
                        }
                    }
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF333333),
                                Color(0xFF000000)
                            )
                        )
                    )
                    .border(7.dp, Color(0xFF111111), CircleShape)
            )

            // 内圈头像
            AsyncImage(
                model = avatarUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
            )

            // 中心跳动的音符（模拟抖音细节）
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .size(10.dp)
                    .graphicsLayer {
                        val scale = 0.8f + 0.2f * sin(rotation * PI.toFloat() / 90f)
                        scaleX = scale
                        scaleY = scale
                    }
            )
        }
        Text(
            text = "拍同款",
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp)
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
