package com.localmusic.app.creator.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 主页「更多」按钮的底部弹窗（参考抖音个人页右上角更多）
 *
 * - 他人主页：分享主页 / 复制抖音号 / 举报 / 拉黑（或取消关注）
 * - 自己主页：编辑资料 / 分享主页 / 复制抖音号 / 资料设置
 *
 * 与 ShareWorkBottomSheet 一致：操作通过回调抛给父页面，不在 Sheet 内嵌套弹窗。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileMoreSheet(
    isSelf: Boolean,
    isFollowing: Boolean,
    onDismiss: () -> Unit,
    onEditProfile: () -> Unit = {},
    onShareProfile: () -> Unit = {},
    onCopyId: () -> Unit = {},
    onReport: () -> Unit = {},
    onBlock: () -> Unit = {},
    onToggleFollow: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            if (isSelf) {
                MoreItem(Icons.Filled.Edit, "编辑资料") { onEditProfile(); onDismiss() }
                MoreItem(Icons.Filled.Share, "分享主页") { onShareProfile(); onDismiss() }
                MoreItem(Icons.Filled.ContentCopy, "复制抖音号") { onCopyId(); onDismiss() }
                MoreItem(Icons.Filled.Settings, "资料设置") { onDismiss() }
            } else {
                MoreItem(Icons.Filled.Share, "分享主页") { onShareProfile(); onDismiss() }
                MoreItem(Icons.Filled.ContentCopy, "复制抖音号") { onCopyId(); onDismiss() }
                MoreItem(Icons.Filled.Flag, "举报") { onReport(); onDismiss() }
                MoreItem(
                    imageVector = if (isFollowing) Icons.Filled.PersonRemove else Icons.Filled.Block,
                    label = if (isFollowing) "取消关注" else "拉黑"
                ) {
                    if (isFollowing) onToggleFollow() else onBlock()
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun MoreItem(
    imageVector: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            color = Color.Black
        )
    }
}
