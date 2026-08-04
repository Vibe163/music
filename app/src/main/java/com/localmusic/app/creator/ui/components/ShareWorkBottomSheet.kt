package com.localmusic.app.creator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localmusic.app.creator.data.model.CreatorWork
import kotlinx.coroutines.launch

/**
 * 分享弹窗操作类型（抛给父页面处理，避免窗口嵌套卡死）
 */
sealed interface ShareSheetAction {
    data object EditCaption : ShareSheetAction
    data object ChangeBgm : ShareSheetAction
    data object DeleteConfirm : ShareSheetAction
}

/**
 * 作品分享/管理底栏（参考抖音"分享给"弹窗）
 * 只保留3项：编辑文案 / 更改配音 / 删除作品
 *
 *  注意：所有子操作（编辑、删除、改配音）都通过 onAction 回调抛给父页面，
 *       不再在 Sheet 内部嵌套另一个 AlertDialog / ModalBottomSheet，
 *       避免 2 个 scrim 蒙层叠加导致窗口卡死、点击全部失效。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareWorkBottomSheet(
    work: CreatorWork,
    onDismiss: () -> Unit,
    onAction: (ShareSheetAction) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        scrimColor = Color(0x33000000),
        dragHandle = { /* 去掉默认拖柄，用自定义头部 */ }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(Color.White)
                .navigationBarsPadding()
        ) {
            // ===== 顶部标题 + [×]关闭（1:1截图布局）=====
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 16.dp, top = 19.dp)
                    .height(30.dp)
            ) {
                Text(
                    text = "分享给",
                    fontSize = 17.sp,
                    color = Color(0xFF111111),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF2F2F2))
                        .clickable {
                            scope.launch { sheetState.hide(); onDismiss() }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.Black, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(22.dp))

            // ===== 操作按钮行：横向滚动 + 均匀大间距（1:1截图第二行结构）=====
            run {
                val scrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .horizontalScroll(scrollState)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        ShareActionItem(
                            icon = {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = null,
                                    tint = Color(0xFF111111),
                                    modifier = Modifier.size(26.dp)
                                )
                            },
                            label = "编辑文案",
                            onClick = {
                                scope.launch {
                                    sheetState.hide()
                                    onAction(ShareSheetAction.EditCaption)
                                }
                            }
                        )
                        ShareActionItem(
                            icon = {
                                Icon(
                                    Icons.Filled.LibraryMusic,
                                    contentDescription = null,
                                    tint = Color(0xFF111111),
                                    modifier = Modifier.size(26.dp)
                                )
                            },
                            label = "更改配音",
                            onClick = {
                                scope.launch {
                                    sheetState.hide()
                                    onAction(ShareSheetAction.ChangeBgm)
                                }
                            }
                        )
                        ShareActionItem(
                            icon = {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = null,
                                    tint = Color(0xFFFE2C55),
                                    modifier = Modifier.size(26.dp)
                                )
                            },
                            label = "删除作品",
                            labelColor = Color(0xFFFE2C55),
                            onClick = {
                                scope.launch {
                                    sheetState.hide()
                                    onAction(ShareSheetAction.DeleteConfirm)
                                }
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// =====================================================================
// 以下 Dialog 均声明为 public，由父页面 CreatorFeedScreen 同级渲染
// =====================================================================

/**
 * 编辑文案 Dialog：标题 / 作品描述 / 话题标签（以空格或#分隔）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCaptionDialog(
    initialTitle: String,
    initialCaption: String,
    initialTags: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (title: String, caption: String, tags: List<String>) -> Unit
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var caption by remember(initialCaption) { mutableStateOf(initialCaption) }
    var tagsText by remember(initialTags) {
        mutableStateOf(initialTags.joinToString(" ") { if (it.startsWith("#")) it else "#$it" })
    }

    androidx.compose.material3.AlertDialog(
        containerColor = Color.White,
        titleContentColor = Color.Black,
        textContentColor = Color(0xFF222222),
        onDismissRequest = onDismiss,
        title = {
            Text("编辑文案", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("标题", style = MaterialTheme.typography.labelMedium, color = Color(0xFF666666))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(60) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    placeholder = { Text("添加标题", color = Color(0xFF999999)) }
                )
                Text("作品描述", style = MaterialTheme.typography.labelMedium, color = Color(0xFF666666))
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it.take(300) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    shape = RoundedCornerShape(8.dp),
                    placeholder = { Text("添加作品描述...", color = Color(0xFF999999)) }
                )
                Text("话题标签（多个之间用空格或#分隔）", style = MaterialTheme.typography.labelMedium, color = Color(0xFF666666))
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it.take(120) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    placeholder = { Text("#话题1 #话题2", color = Color(0xFF999999)) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val tags = tagsText
                    .split("#", " ", "\n", "\t")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                onConfirm(title, caption, tags)
            }) {
                Text("保存", color = Color(0xFFFE2C55), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Color(0xFF555555))
            }
        }
    )
}

/**
 * 删除作品二次确认 Dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmDeleteWorkDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        containerColor = Color.White,
        titleContentColor = Color.Black,
        textContentColor = Color(0xFF333333),
        onDismissRequest = onDismiss,
        title = { Text("删除作品", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = { Text("确认删除此作品？") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = Color(0xFFFE2C55), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Color(0xFF555555))
            }
        }
    )
}

// =====================================================================
// 私有组件
// =====================================================================

@Composable
private fun ShareActionItem(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    labelColor: Color = Color(0xFF111111),
) {
    Column(
        modifier = Modifier
            .width(62.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(0.8.dp, Color(0xFFD6D6D6), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(Modifier.height(9.dp))
        Text(
            text = label,
            fontSize = 12.5.sp,
            color = labelColor,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
