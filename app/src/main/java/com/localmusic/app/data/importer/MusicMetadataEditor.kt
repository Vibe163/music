package com.localmusic.app.data.importer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

/**
 * 编辑音乐文件的元数据（ID3/Vorbis/MP4 tag），直接写入实际文件，不只是前端显示。
 *
 * 流程：
 *  1. 把 SAF URI 内容 copy 到 cacheDir 临时文件
 *  2. 用 JAudioTagger 读取 + 修改 tag + 读回验证
 *  3. 把临时文件内容写回 SAF URI（openOutputStream "wt" 覆盖写）
 *  4. 从原 URI 读取验证 tag 落盘
 *  5. 重命名文件（同步文件名）
 *  6. 迁移 URI 权限，更新数据库
 *
 * 前置条件：导入时必须已 takePersistableUriPermission(READ | WRITE)。
 */
class MusicMetadataEditor(private val context: Context) {

    /** 编辑结果。 */
    sealed class Result {
        /** 成功。newUri 是重命名后的新 URI（如果重命名发生了），否则等于原 URI。 */
        data class Success(val newUri: Uri) : Result()
        /** 无写入权限（旧版本导入的歌曲，只有 READ 权限） */
        data object NoWritePermission : Result()
        /** 其他错误 */
        data class Failed(val message: String) : Result()
    }

    /**
     * 修改指定歌曲文件的元数据并写回。同时重命名文件。
     * @param uri 歌曲文件的 SAF URI
     * @param title 新标题
     * @param artist 新艺术家
     * @param album 新专辑
     */
    suspend fun editMetadata(
        uri: Uri,
        title: String?,
        artist: String?,
        album: String?
    ): Result = withContext(Dispatchers.IO) {
        val ext = guessExtension(uri, title)
        val tempFile = File(context.cacheDir, "edit_meta_${System.currentTimeMillis()}.$ext")
        // currentUri 跟踪实际有效的 URI（重命名后会更新）
        var currentUri = uri
        try {
            // Step 1: SAF URI → 临时文件
            runCatching {
                context.contentResolver.openInputStream(currentUri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext Result.Failed("无法读取文件")
            }.onFailure {
                Log.wtf(TAG, "Step1 读取文件失败", it)
                return@withContext Result.Failed("S1 ${it.javaClass.simpleName}: ${it.message}")
            }

            // Step 2: JAudioTagger 读取 + 修改 tag + 读回验证
            runCatching {
                val audioFile = AudioFileIO.read(tempFile)
                val tag = audioFile.tag ?: audioFile.createDefaultTag().also { audioFile.tag = it }
                if (!title.isNullOrBlank()) tag.setField(FieldKey.TITLE, title)
                if (!artist.isNullOrBlank()) tag.setField(FieldKey.ARTIST, artist)
                if (!album.isNullOrBlank()) tag.setField(FieldKey.ALBUM, album)
                audioFile.commit()

                val reread = AudioFileIO.read(tempFile)
                val rereadTag = reread.tag
                    ?: throw IllegalStateException("commit 后仍无 tag，文件格式可能不支持")
                fun check(field: FieldKey, expect: String?) {
                    if (expect.isNullOrBlank()) return
                    val actual = rereadTag.getFirst(field)
                    if (!actual.equals(expect, ignoreCase = true)) {
                        throw IllegalStateException("字段 ${field.name} 写入失败：期望=$expect 实际=$actual")
                    }
                }
                check(FieldKey.TITLE, title)
                check(FieldKey.ARTIST, artist)
                check(FieldKey.ALBUM, album)
            }.onFailure {
                Log.wtf(TAG, "Step2 修改元数据失败", it)
                return@withContext Result.Failed("S2 ${it.javaClass.simpleName}: ${it.message}")
            }

            // Step 3: 临时文件 → 写回 SAF URI（覆盖写）
            runCatching {
                context.contentResolver.openOutputStream(currentUri, "wt")?.use { output ->
                    tempFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                } ?: return@withContext Result.Failed("无法打开文件写入流")
            }.onFailure { e ->
                Log.e(TAG, "Step3 写回文件失败", e)
                if (e is SecurityException) return@withContext Result.NoWritePermission
                return@withContext Result.Failed("S3 ${e.javaClass.simpleName}: ${e.message}")
            }

            // Step 4: 写回验证
            runCatching {
                val verifyFile = File(context.cacheDir, "verify_${System.currentTimeMillis()}.$ext")
                try {
                    context.contentResolver.openInputStream(currentUri)?.use { input ->
                        verifyFile.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw IllegalStateException("写回后无法打开原文件读取流")

                    val verifyAudio = AudioFileIO.read(verifyFile)
                    val verifyTag = verifyAudio.tag
                        ?: throw IllegalStateException("写回后原文件无 tag")
                    if (!title.isNullOrBlank()) {
                        val actual = verifyTag.getFirst(FieldKey.TITLE)
                        if (!actual.equals(title, ignoreCase = true)) {
                            throw IllegalStateException("写回失败：原文件 title 仍为 '$actual'，期望 '$title'")
                        }
                    }
                    if (!artist.isNullOrBlank()) {
                        val actual = verifyTag.getFirst(FieldKey.ARTIST)
                        if (!actual.equals(artist, ignoreCase = true)) {
                            throw IllegalStateException("写回失败：原文件 artist 仍为 '$actual'，期望 '$artist'")
                        }
                    }
                } finally {
                    verifyFile.delete()
                }
            }.onFailure {
                Log.e(TAG, "Step4 写回验证失败", it)
                return@withContext Result.Failed("S4 ${it.javaClass.simpleName}: ${it.message}")
            }

            // Step 5: 重命名文件（同步文件名）
            runCatching {
                if (!title.isNullOrBlank()) {
                    val safeName = sanitizeFileName(title, ext)
                    val renamed = DocumentsContract.renameDocument(
                        context.contentResolver,
                        currentUri,
                        safeName
                    )
                    if (renamed != null && renamed != currentUri) {
                        Log.i(TAG, "重命名成功：$currentUri → $renamed")
                        // 对新 URI 重新获取持久化权限
                        context.contentResolver.takePersistableUriPermission(
                            renamed,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                        // 释放旧 URI 权限
                        runCatching {
                            context.contentResolver.releasePersistableUriPermission(
                                currentUri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )
                        }
                        // 关键：同时确保拥有父目录 Tree URI 的权限
                        // 这样即使文件再次被重命名，也能通过父目录找回
                        runCatching {
                            val parentTreeUri = extractParentTreeUri(renamed)
                            if (parentTreeUri != null) {
                                context.contentResolver.takePersistableUriPermission(
                                    parentTreeUri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                )
                            }
                        }
                        currentUri = renamed
                    }
                }
            }.onFailure {
                Log.e(TAG, "Step5 重命名文件失败（Tag 已写入成功，可跳过）", it)
                // 重命名失败不阻塞 Tag 保存，但文件未改名
            }

            Result.Success(currentUri)
        } finally {
            tempFile.delete()
        }
    }

    /** 清理非法文件名字符。 */
    private fun sanitizeFileName(name: String, ext: String): String {
        val cleaned = name.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim()
        val trimmed = cleaned.trimEnd('.', ' ')
        return "$trimmed.$ext"
    }

    /**
     * 从文件 URI 提取父目录的 Tree URI。
     */
    private fun extractParentTreeUri(fileUri: Uri): Uri? {
        val path = fileUri.path ?: return null
        val treeIdx = path.indexOf("/tree/")
        val docIdx = path.indexOf("/document/")
        if (treeIdx < 0 || docIdx < 0 || docIdx <= treeIdx) return null
        val treePart = path.substring(treeIdx + 6, docIdx)
        return Uri.parse("content://com.android.externalstorage.documents/tree/$treePart")
    }

    /**
     * 推断文件扩展名。JAudioTagger 靠扩展名选择 Reader，扩展名错误会抛
     * CannotReadException: No Reader（例如用 mp3 Reader 读 m4a）。
     *
     * 优先级：
     *  1. 读取文件头 magic bytes（最可靠，抖音下载的 m4a URI 路径常无扩展名）
     *  2. URI 路径里的扩展名
     *  3. 标题里的扩展名
     *  4. ContentResolver mimeType
     *  5. 兜底 mp3
     */
    private fun guessExtension(uri: Uri, title: String?): String {
        // 1. magic bytes 最可靠，先读文件头判断真实格式
        detectFormatByMagicBytes(uri)?.let { return it }

        // 2. URI 路径里的扩展名
        val path = uri.path ?: ""
        val dotIdx = path.lastIndexOf('.')
        if (dotIdx > 0 && dotIdx < path.length - 1) {
            val ext = path.substring(dotIdx + 1).lowercase()
            if (ext in SUPPORTED_EXTS) return ext
        }
        // 3. 从标题推断（部分场景 title 带 .mp3 后缀）
        if (!title.isNullOrBlank()) {
            val tDot = title.lastIndexOf('.')
            if (tDot > 0 && tDot < title.length - 1) {
                val ext = title.substring(tDot + 1).lowercase()
                if (ext in SUPPORTED_EXTS) return ext
            }
        }
        // 4. 查 mimeType
        runCatching {
            val mime = context.contentResolver.getType(uri)
            when (mime) {
                "audio/mpeg", "audio/mp3" -> return "mp3"
                "audio/mp4", "audio/m4a", "audio/x-m4a" -> return "m4a"
                "audio/flac" -> return "flac"
                "audio/ogg" -> return "ogg"
                "audio/wav", "audio/x-wav" -> return "wav"
            }
        }
        // 5. 兜底
        return "mp3"
    }

    /**
     * 通过读取文件头 magic bytes 判断真实音频格式。
     * 抖音下载的音乐 SAF URI 路径经常不含扩展名，仅靠扩展名无法让 JAudioTagger 识别。
     */
    private fun detectFormatByMagicBytes(uri: Uri): String? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val header = ByteArray(12)
                val read = input.read(header)
                if (read < 4) return@use null

                // ID3 tag header: MP3
                if (header[0] == 0x49.toByte() && header[1] == 0x44.toByte() && header[2] == 0x33.toByte()) {
                    return@use "mp3"
                }
                // MP3 帧同步 0xFF Ex/Fx
                if ((header[0].toInt() and 0xFF) == 0xFF && (header[1].toInt() and 0xE0) == 0xE0) {
                    return@use "mp3"
                }
                // fLaC
                if (header[0] == 0x66.toByte() && header[1] == 0x4C.toByte() &&
                    header[2] == 0x61.toByte() && header[3] == 0x43.toByte()
                ) {
                    return@use "flac"
                }
                // OggS
                if (header[0] == 0x4F.toByte() && header[1] == 0x67.toByte() &&
                    header[2] == 0x67.toByte() && header[3] == 0x53.toByte()
                ) {
                    return@use "ogg"
                }
                // RIFF....WAVE
                if (read >= 12 && header[0] == 0x52.toByte() && header[1] == 0x49.toByte() &&
                    header[2] == 0x46.toByte() && header[3] == 0x46.toByte() &&
                    header[8] == 0x57.toByte() && header[9] == 0x41.toByte() &&
                    header[10] == 0x56.toByte() && header[11] == 0x45.toByte()
                ) {
                    return@use "wav"
                }
                // MP4 系列：偏移 4-7 为 "ftyp"（涵盖 m4a/m4p/m4b/mp4，对 JAudioTagger 统一用 m4a）
                if (read >= 8 && header[4] == 0x66.toByte() && header[5] == 0x74.toByte() &&
                    header[6] == 0x79.toByte() && header[7] == 0x70.toByte()
                ) {
                    return@use "m4a"
                }
                null
            }
        }.getOrNull()
    }

    private companion object {
        const val TAG = "MusicMetadataEditor"
        val SUPPORTED_EXTS = setOf("mp3", "m4a", "flac", "ogg", "wav", "mp4", "m4p", "m4b")
    }
}
