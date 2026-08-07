package com.localmusic.app.data.importer

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.localmusic.app.data.local.SongDao
import com.localmusic.app.data.model.SongEntity
import com.localmusic.app.util.FileHashUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** 导入结果统计，duplicateNames/failedNames 记录具体歌曲名（供导入日志展示）。 */
data class ImportResult(
    val added: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val duplicateNames: List<String> = emptyList(),
    val failedNames: List<String> = emptyList()
)

/**
 * 通过 SAF 选择的内容导入到曲库（songs 表）。
 * - 单个/多个文件：[importFromUris]
 * - 整个文件夹（树）：[importFromTree]
 *
 * 导入后会持久化 URI 读取权限，重启后仍可播放。
 * 封面在导入时立即降采样为 300×300 缩略图，避免 UI 列表解码全尺寸大图掉帧。
 */
class MusicImporter(
    private val context: Context,
    private val songDao: SongDao
) {

    private val audioExtensions = setOf(
        "mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "wma",
        "mid", "midi", "amr", "mka", "aiff", "aif"
    )

    /** 缩略图目标尺寸（正方形，覆盖 3x 高密度屏 48dp ≈ 144px 仍有冗余）。 */
    private val thumbSize = 300

    /** 导入一组文件 Uri。 */
    suspend fun importFromUris(
        uris: List<Uri>,
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> }
    ): ImportResult = withContext(Dispatchers.IO) {
        val total = uris.size
        var added = 0; var skipped = 0; var failed = 0
        val duplicateNames = mutableListOf<String>()
        val failedNames = mutableListOf<String>()
        uris.forEachIndexed { index, uri ->
            when (importSingle(uri)) {
                ImportStatus.Added -> added++
                ImportStatus.Skipped -> duplicateNames += displayName(uri)
                ImportStatus.Failed -> failedNames += displayName(uri)
            }
            onProgress(index + 1, total)
        }
        ImportResult(added, skipped, failed, duplicateNames, failedNames)
    }

    /** 导入一个文件夹树 Uri（OpenDocumentTree 返回的 treeUri）。 */
    suspend fun importFromTree(
        treeUri: Uri,
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> }
    ): ImportResult = withContext(Dispatchers.IO) {
        // 同时取 READ + WRITE：编辑元数据时需要写回文件
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return@withContext ImportResult(failed = 1)

        val audioFiles = mutableListOf<Uri>()
        collectAudioFiles(root, audioFiles)

        val total = audioFiles.size
        if (total == 0) return@withContext ImportResult()

        var added = 0; var skipped = 0; var failed = 0
        val duplicateNames = mutableListOf<String>()
        val failedNames = mutableListOf<String>()
        audioFiles.forEachIndexed { index, uri ->
            when (importSingle(uri, persistPermission = false)) {
                ImportStatus.Added -> added++
                ImportStatus.Skipped -> duplicateNames += displayName(uri)
                ImportStatus.Failed -> failedNames += displayName(uri)
            }
            onProgress(index + 1, total)
        }
        ImportResult(added, skipped, failed, duplicateNames, failedNames)
    }

    private fun collectAudioFiles(dir: DocumentFile, out: MutableList<Uri>) {
        dir.listFiles().forEach { child ->
            when {
                child.isDirectory -> collectAudioFiles(child, out)
                child.isFile -> {
                    val name = child.name ?: return@forEach
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext in audioExtensions) out += child.uri
                }
            }
        }
    }

    private enum class ImportStatus { Added, Skipped, Failed }

    /**
     * 导入单个外部音频文件（如从 QQ/微信/文件管理器通过"打开方式"传入），返回歌曲 id。
     *  - 已存在（URI 或 MD5 内容相同）→ 返回已有歌曲 id（可直接播放）
     *  - 新增成功 → 返回新 id
     *  - 读取失败 → null
     */
    suspend fun importOne(uri: Uri): Long? = withContext(Dispatchers.IO) {
        val uriString = uri.toString()
        songDao.getIdByUri(uriString)?.let { return@withContext it }

        // 外部 intent 传入的 uri 通常只有临时读权限，持久化以便重启后仍可播放
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        readMetadataAndInsert(uri, uriString)?.id
    }

    private suspend fun importSingle(uri: Uri, persistPermission: Boolean = true): ImportStatus {
        val uriString = uri.toString()
        if (songDao.getIdByUri(uriString) != null) return ImportStatus.Skipped

        if (persistPermission) {
            // 同时取 READ + WRITE：编辑元数据时需要写回文件
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            // 关键：同时获取父目录 Tree URI 的权限。
            // 这样即使文件被重命名，也能通过父目录查找新文件进行修复。
            runCatching {
                val parentTreeUri = extractParentTreeUri(uri)
                if (parentTreeUri != null) {
                    context.contentResolver.takePersistableUriPermission(
                        parentTreeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
            }
        }

        val result = readMetadataAndInsert(uri, uriString) ?: return ImportStatus.Failed
        return if (result.existed) ImportStatus.Skipped else ImportStatus.Added
    }

    /**
     * 读取元数据 → 计算 MD5 内容指纹 → 去重 → 插入，返回歌曲 id。
     *  - URI 已存在 / MD5 内容相同 → 返回已有 id（existed = true）
     *  - 新增成功 → 返回新 id（existed = false）
     *  - 读取失败 → null
     */
    private suspend fun readMetadataAndInsert(uri: Uri, uriString: String): InsertResult? {
        val retriever = MediaMetadataRetriever()
        val metadata: Metadata? = try {
            retriever.setDataSource(context, uri)
            Metadata(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?.takeIf { it.isNotBlank() } ?: fileNameWithoutExt(uri),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?.takeIf { it.isNotBlank() } ?: "未知艺术家",
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    ?.takeIf { it.isNotBlank() } ?: "未知专辑",
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L,
                artBytes = retriever.embeddedPicture
            )
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }

        val meta = metadata ?: return null

        // 内容级去重：不同批次重新下载的同一首歌（URI 不同但内容相同）→ 返回已有记录
        // 例如：抖音一批批下载到 Download 文件夹，文件名带序号变化，URI 每次不同，
        // 仅按 URI 去重识别不了；MD5 相同即判定为同一首歌，避免重复入库
        val md5 = FileHashUtils.computeMd5(context, uri)
        if (md5 != null) {
            songDao.findByMd5(md5)?.let { return InsertResult(it.id, existed = true) }
        }

        // 回退去重：早期版本导入的记录 md5 为 NULL，MD5 查不到；
        // 用「标题 + 时长」近似匹配（同一首歌 tag 标题相同、时长几乎一致）
        findDuplicateByTitleAndDuration(meta.title, meta.durationMs, md5)?.let { existing ->
            // 回填 MD5，后续导入直接命中 MD5 快速去重
            if (md5 != null) {
                songDao.updateMd5(existing.id, md5)
            }
            return InsertResult(existing.id, existed = true)
        }

        val newId = songDao.insert(
            SongEntity(
                title = meta.title,
                artist = meta.artist,
                album = meta.album,
                duration = meta.durationMs,
                uri = uriString,
                albumArtPath = null,
                md5 = md5
            )
        )
        if (newId <= 0) return null

        // 提取封面并立即降采样为 300×300 缩略图
        meta.artBytes?.let { bytes ->
            val path = saveAlbumArtThumbnail(newId, bytes)
            if (path != null) songDao.updateAlbumArtPath(newId, path)
        }
        return InsertResult(newId, existed = false)
    }

    private data class InsertResult(val id: Long, val existed: Boolean)

    /**
     * 回退去重：按「标题 + 时长」近似匹配库中已有记录。
     * 早期版本（md5 字段加入前）导入的歌曲 md5 为 NULL，MD5 去重查不到它们；
     * 同一首歌即使文件名/URI 不同，tag 标题一致、时长几乎相同，借此识别重复。
     */
    private suspend fun findDuplicateByTitleAndDuration(
        title: String,
        durationMs: Long,
        currentMd5: String?
    ): SongEntity? {
        if (durationMs <= 0 || title.isBlank()) return null
        val candidates = songDao.findByDurationRange(
            (durationMs - 1500).coerceAtLeast(0L),
            durationMs + 1500
        )
        val titleNorm = title.lowercase()
        return candidates.firstOrNull {
            it.md5 != currentMd5 && it.title.lowercase() == titleNorm
        }
    }

    /**
     * 将原始内嵌封面字节降采样为 thumbSize×thumbSize JPEG 缩略图。
     *  - 先通过 BitmapFactory.Options.inJustDecodeBounds 读尺寸，计算 inSampleSize 取最近 2 的幂
     *  - 再按 inSampleSize 解码 + 二次缩放，保证输出精确 300×300
     *  - 质量压缩到 85，典型大小 30-80KB（相比原图 5MB+，解码速度提升 10-50 倍）
     */
    private fun saveAlbumArtThumbnail(songId: Long, bytes: ByteArray): String? = runCatching {
        val dir = File(context.filesDir, "albumart").apply { mkdirs() }
        val outFile = File(dir, "$songId.jpg")

        // Step 1: 读原始尺寸
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
        val origW = boundsOpts.outWidth
        val origH = boundsOpts.outHeight

        if (origW <= 0 || origH <= 0) {
            // 无效图片，直接回退保存原图（极少数情况）
            FileOutputStream(outFile).use { it.write(bytes) }
            return@runCatching outFile.absolutePath
        }

        // Step 2: 计算 inSampleSize（取最近 2 的幂，避免缩放伪影）
        val targetSide = thumbSize * 2 // 解码为 2 倍，再二次缩放更平滑
        var sampleSize = 1
        while (origW / sampleSize >= targetSide && origH / sampleSize >= targetSide) {
            sampleSize *= 2
        }

        // Step 3: 按 sampleSize 解码（大幅降低内存）
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
            ?: return@runCatching null

        // Step 4: 二次缩放到精确 thumbSize（保持比例居中裁剪）
        val cropSize = minOf(decoded.width, decoded.height)
        val cropped = android.graphics.Bitmap.createBitmap(
            decoded,
            (decoded.width - cropSize) / 2,
            (decoded.height - cropSize) / 2,
            cropSize,
            cropSize
        )
        val scaled = android.graphics.Bitmap.createScaledBitmap(cropped, thumbSize, thumbSize, true)

        // Step 5: 压缩为 JPEG
        FileOutputStream(outFile).use { fos ->
            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, fos)
        }

        // 回收中间 bitmap
        if (cropped !== decoded) decoded.recycle()
        cropped.recycle()
        scaled.recycle()

        outFile.absolutePath
    }.getOrNull()

    private fun fileNameWithoutExt(uri: Uri): String {
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "未知标题"
        return name.substringBeforeLast('.').ifBlank { "未知标题" }
    }

    private fun displayName(uri: Uri): String =
        DocumentFile.fromSingleUri(context, uri)?.name?.takeIf { it.isNotBlank() }
            ?: fileNameWithoutExt(uri)

    /**
     * 从文件 URI 提取父目录的 Tree URI。
     * 例如：content://com.android.externalstorage.documents/tree/primary%3ADownload/document/primary%3ADownload%2Ftest.mp3
     * 返回：content://com.android.externalstorage.documents/tree/primary%3ADownload
     */
    private fun extractParentTreeUri(fileUri: Uri): Uri? {
        val path = fileUri.path ?: return null
        val treeIdx = path.indexOf("/tree/")
        val docIdx = path.indexOf("/document/")
        if (treeIdx < 0 || docIdx < 0 || docIdx <= treeIdx) return null
        val treePart = path.substring(treeIdx + 6, docIdx)
        return Uri.parse("content://com.android.externalstorage.documents/tree/$treePart")
    }

    private data class Metadata(
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val artBytes: ByteArray?
    )
}
