package com.localmusic.app.creator.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.localmusic.app.creator.data.storage.CreatorUserProfile
import com.localmusic.app.creator.viewmodel.CreatorViewModel

/**
 * 创作者资料编辑弹窗（在「我的」页面调用）
 *
 * 统一设置头像、昵称、简介，发布作品时自动引用，无需每次发布都填
 */
@Composable
fun CreatorProfileEditorDialog(
    viewModel: CreatorViewModel,
    onDismiss: () -> Unit
) {
    val currentProfile by viewModel.userProfile.collectAsState()

    var avatarUri by remember { mutableStateOf(currentProfile.avatarUri) }
    var nickname by remember { mutableStateOf(currentProfile.nickname) }
    var bio by remember { mutableStateOf(currentProfile.bio) }
    // 从相册选中后先进入裁剪页（抖音风格框选），确认裁剪后才更新头像
    var cropSourceUri by remember { mutableStateOf<Uri?>(null) }

    val pickAvatarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            cropSourceUri = uri
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创作者资料") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 头像选择
                Text("头像", fontSize = 14.sp)
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2A2A2A))
                        .clickable {
                            pickAvatarLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        avatarUri != null -> {
                            AsyncImage(
                                model = Uri.parse(avatarUri),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        else -> {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
                        }
                    }
                }

                // 昵称
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { if (it.length <= 20) nickname = it },
                    label = { Text("昵称（最多20字）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // 简介
                OutlinedTextField(
                    value = bio,
                    onValueChange = { if (it.length <= 100) bio = it },
                    label = { Text("简介（最多100字）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.updateUserProfile(avatarUri, nickname, bio)
                onDismiss()
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )

    // 选中相册图片后进入裁剪页，确认裁剪后才应用头像
    cropSourceUri?.let { uri ->
        AvatarCropDialog(
            viewModel = viewModel,
            imageUri = uri,
            onCancel = { cropSourceUri = null },
            onConfirm = { croppedUri ->
                avatarUri = croppedUri
                cropSourceUri = null
            }
        )
    }
}
