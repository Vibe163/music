package com.localmusic.app.creator.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.localmusic.app.creator.data.model.CreatorWork
import com.localmusic.app.creator.data.model.MediaType
import com.localmusic.app.creator.viewmodel.CreatorViewModel

/**
 * 页面2：作者主页
 *
 * 模拟创作者主页：头像、昵称、粉丝数、获赞数、简介、作品网格
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

    var showOptionsFor by remember { mutableStateOf<CreatorWork?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .statusBarsPadding()
    ) {
        // 顶部导航栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
            }
            Text(
                text = author?.authorName ?: "用户",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { /* 更多 */ }) {
                Icon(Icons.Filled.MoreHoriz, contentDescription = null, tint = Color.White)
            }
        }

        if (author == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("用户不存在", color = Color.White.copy(alpha = 0.6f))
            }
            return
        }

        // 作者资料区
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 头像
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2A2A2A)),
                    contentAlignment = Alignment.Center
                ) {
                    if (author.authorAvatarUri != null) {
                        AsyncImage(
                            model = Uri.parse(author.authorAvatarUri),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            author.authorName.firstOrNull()?.toString() ?: "?",
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 粉丝数 / 获赞数
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatColumn("粉丝", author.fanCount)
                    StatColumn("获赞", author.totalLikeCount)
                    StatColumn("作品", authorWorks.size)
                }
            }

            Spacer(Modifier.height(12.dp))

            // 昵称
            Text(
                author.authorName,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            // 简介
            if (author.authorBio.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    author.authorBio,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // 作品网格
        if (authorWorks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无作品", color = Color.White.copy(alpha = 0.6f))
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(authorWorks) { work ->
                    WorkGridItem(
                        work = work,
                        onClick = {
                            val globalIndex = allWorks.indexOfFirst { it.id == work.id }
                            if (globalIndex >= 0) onWorkClick(globalIndex)
                        },
                        onLongClick = { showOptionsFor = work }
                    )
                }
            }
        }
    }

    // 长按作品选项
    showOptionsFor?.let { work ->
        AlertDialog(
            onDismissRequest = { showOptionsFor = null },
            title = { Text(work.caption.take(20)) },
            text = { Text("选择操作") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.duplicateWork(work.id)
                    showOptionsFor = null
                }) {
                    Text("复制为新作品")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        viewModel.deleteWork(work.id)
                        showOptionsFor = null
                    }) {
                        Text("删除", color = Color(0xFFFE2C55))
                    }
                    TextButton(onClick = { showOptionsFor = null }) {
                        Text("取消")
                    }
                }
            }
        )
    }
}

@Composable
private fun StatColumn(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            formatCount(count),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            label,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun WorkGridItem(
    work: CreatorWork,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(Color(0xFF1A1A1A))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        if (work.mediaList.isNotEmpty()) {
            AsyncImage(
                model = Uri.parse(work.mediaList.first()),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // 无媒体作品的占位
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    work.caption.take(10),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
        }

        // 视频标识
        if (work.mediaType == MediaType.VIDEO) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text("视频", color = Color.White, fontSize = 10.sp)
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
