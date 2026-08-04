package com.localmusic.app.creator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlin.random.Random

/** 模拟评论数据模型 */
private data class MockComment(
    val userName: String,
    val avatarSeed: Int,
    val content: String,
    val likeCount: Int
)

/** 预置模拟评论模板（训练空间用，非真实社交） */
private val commentTemplates = listOf(
    "这个BGM配得太绝了" to "音乐搭配达人",
    "文案好戳" to "情感观察员",
    "节奏感拉满" to "节奏大师",
    "第一眼就抓住了" to "视觉训练师",
    "画面和BGM完美契合" to "审美专家",
    "这个转场太丝滑了" to "技术流",
    "色调很高级" to "调色爱好者",
    "看完想反复刷" to "路人甲",
    "学习了" to "训练新人",
    "这搭配绝了" to "搭配研究员"
)

private val avatarColors = listOf(
    Color(0xFFFE2C55), Color(0xFF25F4EE), Color(0xFFFEB927),
    Color(0xFF8B5CF6), Color(0xFF10B981), Color(0xFFF59E0B)
)

/**
 * 抖音风格评论弹窗
 * 显示本地生成的模拟评论，第一阶段不支持发布真实评论
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentBottomSheet(
    commentCount: Int,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 根据评论数生成模拟评论
    val comments = remember(commentCount) {
        val size = minOf(commentCount.coerceAtLeast(3), 30)
        (1..size).map { i ->
            val (content, user) = commentTemplates[i % commentTemplates.size]
            MockComment(
                userName = user,
                avatarSeed = i,
                content = content,
                likeCount = Random.nextInt(0, 9999)
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1A1A1A)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "$commentCount 条评论",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(comments) { comment ->
                    CommentItem(comment)
                }
            }
        }
    }
}

@Composable
private fun CommentItem(comment: MockComment) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        // 头像（用色块代替）
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(avatarColors[comment.avatarSeed % avatarColors.size]),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = comment.userName.first().toString(),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.size(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = comment.userName,
                color = Color(0xFFAAAAAA),
                fontSize = 13.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = comment.content,
                color = Color.White,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = formatTimeAgo(comment.avatarSeed),
                color = Color(0xFF888888),
                fontSize = 12.sp
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "❤",
                color = Color(0xFFFE2C55),
                fontSize = 16.sp
            )
            Text(
                text = formatCount(comment.likeCount),
                color = Color(0xFF888888),
                fontSize = 12.sp
            )
        }
    }
}

private fun formatTimeAgo(seed: Int): String {
    val options = listOf("1小时前", "2小时前", "1天前", "3天前", "1周前", "刚刚")
    return options[seed % options.size]
}

private fun formatCount(count: Int): String {
    return when {
        count >= 10000 -> "${count / 10000}.${(count % 10000) / 1000}万"
        else -> count.toString()
    }
}
