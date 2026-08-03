package com.localmusic.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ShareUtils {

    private const val TAG = "ShareUtils"
    private const val FILE_PROVIDER_AUTHORITY = "com.localmusic.app.fileprovider"

    suspend fun shareAudio(
        context: Context,
        songUri: Uri,
        songTitle: String,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((String) -> Unit)? = null
    ) {
        withContext(Dispatchers.IO) {
            runCatching {
                // Step 1: Parse original filename from content URI
                val originalFileName = resolveOriginalFileName(context, songUri)
                val fileExt = originalFileName.substringAfterLast('.', "").lowercase()
                val fileName = if (fileExt.isNotBlank()) originalFileName else "$songTitle.mp3"
                val mimeType = getMimeTypeFromExt(fileExt)

                // Step 2: Pre-validate - check if file exists and is readable
                val canRead = runCatching {
                    context.contentResolver.openInputStream(songUri)?.use { true } ?: false
                }.getOrDefault(false)

                if (!canRead) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "文件不存在或无法读取，请重新导入后再试", Toast.LENGTH_LONG).show()
                    }
                    onFailure?.invoke("文件无法读取")
                    return@runCatching
                }

                // Step 3: Copy to shared dir with original filename
                val sharedFile = copyToSharedDir(context, songUri, fileName)
                if (!sharedFile.exists() || sharedFile.length() <= 0) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "分享文件为空，复制失败", Toast.LENGTH_LONG).show()
                    }
                    onFailure?.invoke("文件复制失败")
                    return@runCatching
                }

                // Step 4: Generate share URI via FileProvider
                val sharedUri = FileProvider.getUriForFile(
                    context,
                    FILE_PROVIDER_AUTHORITY,
                    sharedFile
                )

                // Step 5: Build share intent with proper MIME type
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, sharedUri)
                    putExtra(Intent.EXTRA_SUBJECT, songTitle)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = android.content.ClipData.newUri(
                        context.contentResolver,
                        fileName,
                        sharedUri
                    )
                }

                val chooser = Intent.createChooser(intent, "分享音频")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)

                Log.d(TAG, "Share success: $fileName, mimeType=$mimeType, size=${sharedFile.length()}")
                onSuccess?.invoke()
            }.onFailure { e ->
                Log.e(TAG, "Share failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "分享失败：${e.message}", Toast.LENGTH_LONG).show()
                }
                onFailure?.invoke(e.message ?: "未知错误")
            }
        }
    }

    /**
     * Resolve the original filename from a SAF content URI.
     * Handles various URI formats:
     * - content://com.android.externalstorage.documents/document/.../file.mp3
     * - content://media/external/audio/media/...
     */
    private fun resolveOriginalFileName(context: Context, uri: Uri): String {
        val displayNameColumn = android.provider.OpenableColumns.DISPLAY_NAME

        // Method 1: Query display name from the content resolver
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(displayNameColumn),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(displayNameColumn)
                    if (columnIndex >= 0) {
                        val name = cursor.getString(columnIndex)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        }

        // Method 2: Extract from URI path
        runCatching {
            val lastSegment = uri.lastPathSegment ?: ""
            if (lastSegment.isNotBlank()) {
                val decoded = java.net.URLDecoder.decode(lastSegment, "UTF-8")
                val fileName = decoded.substringAfterLast('/', decoded)
                if (fileName.isNotBlank() && '.' in fileName) {
                    return fileName
                }
                val parts = decoded.split('/')
                val lastPart = parts.lastOrNull { it.contains('.') }
                if (lastPart != null) return lastPart
                return fileName.ifBlank { lastSegment }
            }
        }

        // Fallback: extract from full path
        runCatching {
            val fullPath = uri.path ?: ""
            val fileName = fullPath.substringAfterLast('/')
            if (fileName.isNotBlank()) return fileName
        }

        return "unknown_audio.mp3"
    }

    /**
     * Copy file to shared directory preserving original filename and extension.
     */
    private fun copyToSharedDir(
        context: Context,
        sourceUri: Uri,
        fileName: String
    ): File {
        val sharedDir = File(context.cacheDir, "shared_audio").apply { mkdirs() }

        // Clean old files (older than 30 min)
        cleanOldFiles(sharedDir, 1800_000L)

        val safeName = sanitizeFileName(fileName)
        // Use a unique prefix to avoid conflicts but keep original name
        val uniquePrefix = System.currentTimeMillis().toString()
        val targetFile = File(sharedDir, "${uniquePrefix}_$safeName")

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output, bufferSize = 65536) // 64KB buffer for faster copy
            }
        }

        return targetFile
    }

    private fun cleanOldFiles(dir: File, maxAgeMs: Long) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        dir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifBlank { "unknown_audio.mp3" }
    }

    /**
     * Get MIME type from file extension.
     * Defaults to audio/mpeg for reliability with chat apps.
     */
    private fun getMimeTypeFromExt(ext: String): String {
        return when (ext.lowercase()) {
            "mp3" -> "audio/mpeg"
            "m4a", "mp4" -> "audio/mp4"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            "opus" -> "audio/opus"
            "wma" -> "audio/x-ms-wma"
            "ape" -> "audio/ape"
            "wv" -> "audio/x-wavpack"
            "mid", "midi" -> "audio/midi"
            "amr" -> "audio/amr"
            else -> "audio/mpeg" // Default to mp3 - most compatible
        }
    }
}
