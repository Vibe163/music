package com.localmusic.app.creator.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.localmusic.app.creator.viewmodel.CreatorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * 抖音风格头像裁剪页
 *
 * 从相册选图后进入：图片可单指拖动、双指缩放，中间正方形裁剪框框选区域，
 * 确认后按裁剪框区域裁出 300×300 头像并保存到 App 私有目录。
 */
@Composable
fun AvatarCropDialog(
    viewModel: CreatorViewModel,
    imageUri: Uri,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(imageUri) {
        bitmap = withContext(Dispatchers.IO) { decodeSampledBitmap(context, imageUri, 2048) }
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val bmp = bitmap
            if (bmp == null) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            } else {
                CropArea(
                    bitmap = bmp,
                    viewModel = viewModel,
                    onCancel = onCancel,
                    onConfirm = onConfirm
                )
            }
        }
    }
}

@Composable
private fun CropArea(
    bitmap: Bitmap,
    viewModel: CreatorViewModel,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 裁剪框：屏宽 85% 的正方形（抖音风格居中框选）
        val boxSize = maxWidth * 0.85f
        val boxPx = with(density) { boxSize.toPx() }

        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val fitScale = min(boxPx / w, boxPx / h)

        // 图片变换状态：scale（相对原图，px 单位尺寸 = w*scale × h*scale），offset 为居中位置的偏移
        var scale by remember(bitmap) { mutableStateOf(fitScale) }
        var offsetX by remember(bitmap) { mutableStateOf(0f) }
        var offsetY by remember(bitmap) { mutableStateOf(0f) }

        // 图片层（可拖动/缩放）
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(boxSize)
                .clipToBounds()
                .pointerInput(bitmap) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val oldScale = scale
                        val newScale = (oldScale * zoom).coerceIn(fitScale, 4f)
                        scale = newScale
                        // 围绕中心缩放（保持图片中心稳定）+ 平移
                        offsetX = offsetX * (newScale / oldScale) + pan.x
                        offsetY = offsetY * (newScale / oldScale) + pan.y
                        // 限制偏移：图片必须始终覆盖整个裁剪框
                        val maxOx = max(0f, (w * newScale - boxPx) / 2f)
                        val maxOy = max(0f, (h * newScale - boxPx) / 2f)
                        offsetX = offsetX.coerceIn(-maxOx, maxOx)
                        offsetY = offsetY.coerceIn(-maxOy, maxOy)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawImage(
                    image = bitmap.asImageBitmap(),
                    dstOffset = IntOffset(
                        x = ((boxPx - w * scale) / 2f + offsetX).toInt(),
                        y = ((boxPx - h * scale) / 2f + offsetY).toInt()
                    ),
                    dstSize = IntSize(
                        width = (w * scale).toInt(),
                        height = (h * scale).toInt()
                    )
                )
            }
        }

        // 遮罩层：裁剪框四周变暗 + 白色边框 + 九宫格线
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cw = size.width
            val ch = size.height
            val left = (cw - boxPx) / 2f
            val top = (ch - boxPx) / 2f
            val right = left + boxPx
            val bottom = top + boxPx
            val maskColor = Color(0x99000000)

            // 上 / 下 / 左 / 右 四个遮罩矩形
            drawRect(maskColor, topLeft = androidx.compose.ui.geometry.Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(cw, top))
            drawRect(maskColor, topLeft = androidx.compose.ui.geometry.Offset(0f, bottom), size = androidx.compose.ui.geometry.Size(cw, ch - bottom))
            drawRect(maskColor, topLeft = androidx.compose.ui.geometry.Offset(0f, top), size = androidx.compose.ui.geometry.Size(left, boxPx))
            drawRect(maskColor, topLeft = androidx.compose.ui.geometry.Offset(right, top), size = androidx.compose.ui.geometry.Size(cw - right, boxPx))

            // 白色边框
            drawRect(
                color = Color.White,
                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                size = androidx.compose.ui.geometry.Size(boxPx, boxPx),
                style = Stroke(width = 2.dp.toPx())
            )
            // 九宫格线
            val lineColor = Color.White.copy(alpha = 0.5f)
            for (i in 1..2) {
                val x = left + boxPx * i / 3f
                val y = top + boxPx * i / 3f
                drawLine(lineColor, androidx.compose.ui.geometry.Offset(x, top), androidx.compose.ui.geometry.Offset(x, bottom), strokeWidth = 1.dp.toPx())
                drawLine(lineColor, androidx.compose.ui.geometry.Offset(left, y), androidx.compose.ui.geometry.Offset(right, y), strokeWidth = 1.dp.toPx())
            }
        }

        // 顶部取消
        Text(
            text = "取消",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 12.dp, top = 8.dp)
                .clickable { onCancel() }
                .padding(12.dp)
        )

        // 底部确认按钮
        Button(
            onClick = {
                if (saving) return@Button
                saving = true
                scope.launch {
                    val uri = saveCrop(bitmap, w, h, boxPx, scale, offsetX, offsetY, viewModel)
                    saving = false
                    if (uri != null) onConfirm(uri)
                }
            },
            enabled = !saving,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFE2C55),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text(if (saving) "保存中…" else "确认", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** 按裁剪框区域从原图裁出 300×300 头像并保存 */
private suspend fun saveCrop(
    bitmap: Bitmap,
    w: Float,
    h: Float,
    boxPx: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    viewModel: CreatorViewModel
): String? = withContext(Dispatchers.IO) {
    // 裁剪框中心相对原图左上角的偏移（原图像素单位）
    val cropCenterX = (w / 2f) - (offsetX / scale)
    val cropCenterY = (h / 2f) - (offsetY / scale)
    val cropSizePx = boxPx / scale
    val half = cropSizePx / 2f

    val sx = (cropCenterX - half).coerceIn(0f, w - cropSizePx)
    val sy = (cropCenterY - half).coerceIn(0f, h - cropSizePx)

    val cropped = Bitmap.createBitmap(
        bitmap,
        sx.toInt().coerceAtLeast(0),
        sy.toInt().coerceAtLeast(0),
        cropSizePx.toInt().coerceIn(1, bitmap.width),
        cropSizePx.toInt().coerceIn(1, bitmap.height)
    )
    val avatar = Bitmap.createScaledBitmap(cropped, 300, 300, true)
    if (cropped !== avatar) cropped.recycle()
    viewModel.saveCroppedAvatar(avatar)
}

/** 采样解码，限制最大边 ≤ maxSize，避免大图 OOM */
private fun decodeSampledBitmap(context: Context, uri: Uri, maxSize: Int): Bitmap? = runCatching {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

    var sample = 1
    while (bounds.outWidth / sample > maxSize || bounds.outHeight / sample > maxSize) {
        sample *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
}.getOrNull()
