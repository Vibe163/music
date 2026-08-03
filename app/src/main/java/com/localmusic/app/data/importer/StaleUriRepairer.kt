package com.localmusic.app.data.importer

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.localmusic.app.data.local.SongDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 修复失效的歌曲 URI。
 *
 * 历史问题：旧版本编辑元数据时会重命名文件（DocumentsContract.renameDocument），
 * 但部分版本未把新 URI 写回数据库，导致数据库里存的 URI 指向已不存在的旧文件名，
 * 播放和再编辑都失败。
 *
 * 修复策略（应用启动时执行一次）：
 *  1. 遍历所有歌曲，尝试 openInputStream 探测 URI 是否可读
 *  2. 失效的 URI：从其路径提取父目录 tree URI，列出同级文件，
 *     按原文件名（从 URI 解析）或当前 title 精确匹配新文件
 *  3. 找到则更新数据库 URI 为新 URI，并 takePersistableUriPermission
 *
 * 注意：新版本已不再重命名文件，不会再产生新的失效 URI，本类仅用于修复历史数据。
 */
class StaleUriRepairer(
    private val context: Context,
    private val songDao: SongDao
) {

    data class RepairStats(val total: Int, val repaired: Int, val failed: Int)

    suspend fun repairAll(): RepairStats = withContext(Dispatchers.IO) {
        val songs = songDao.getAll()
        var repaired = 0
        var failed = 0
        var checked = 0

        Log.i(TAG, "开始 URI 修复检查，共 ${songs.size} 首歌")

        for (song in songs) {
            val uri = runCatching { Uri.parse(song.uri) }.getOrNull() ?: continue
            checked++
            if (isUriReadable(uri)) continue  // URI 有效，无需修复

            Log.w(TAG, "发现失效 URI：songId=${song.id} title=${song.title} uri=${song.uri}")
            val newUri = repairUri(uri, song.title)
            if (newUri != null) {
                runCatching {
                    songDao.updateUri(song.id, newUri.toString())
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            newUri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    }
                    runCatching {
                        val parentTreeUri = extractParentTreeUri(uri)
                        if (parentTreeUri != null) {
                            context.contentResolver.takePersistableUriPermission(
                                parentTreeUri,
                                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )
                        }
                    }
                    repaired++
                    Log.i(TAG, "修复成功：songId=${song.id} → $newUri")
                }.onFailure {
                    failed++
                    Log.e(TAG, "修复写回数据库失败：songId=${song.id}", it)
                }
            } else {
                failed++
                Log.w(TAG, "无法修复：songId=${song.id} title=${song.title}（无父目录权限或未找到匹配文件）")
            }
        }

        Log.i(TAG, "URI 修复完成：检查 $checked 首，修复 $repaired 首，失败 $failed 首")
        RepairStats(total = songs.size, repaired = repaired, failed = failed)
    }

    /** 探测 URI 是否可读（openInputStream 成功即视为有效）。 */
    private fun isUriReadable(uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.read() } != null
    }.getOrDefault(false)

    /**
     * 从失效 URI 提取父目录 tree URI，列出同级文件，按原文件名或 title 匹配新文件。
     */
    private fun repairUri(staleUri: Uri, title: String): Uri? {
        val path = staleUri.path ?: return null
        val treeIdx = path.indexOf("/tree/")
        val docIdx = path.indexOf("/document/")
        if (treeIdx < 0 || docIdx < 0 || docIdx <= treeIdx) return null

        val treePart = path.substring(treeIdx + 6, docIdx)
        val parentTreeUri = Uri.parse("content://com.android.externalstorage.documents/tree/$treePart")

        return runCatching {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    parentTreeUri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }

            val treeDoc = DocumentFile.fromTreeUri(context, parentTreeUri) ?: return null
            if (!treeDoc.canRead()) {
                Log.w(TAG, "无法读取父目录：$parentTreeUri")
                return null
            }

            val docPart = path.substring(docIdx + 10)
            val oldFileName = Uri.decode(docPart.substringAfterLast("%2F", docPart.substringAfterLast("/")))
            Log.i(TAG, "搜索文件：oldFileName=$oldFileName, title=$title")

            val children = treeDoc.listFiles()
            Log.i(TAG, "父目录共有 ${children.size} 个文件")

            // 1. 精确匹配原文件名（重命名失败但文件还在的情况）
            children.firstOrNull { doc ->
                doc.name?.equals(oldFileName, ignoreCase = true) == true
            }?.let {
                Log.i(TAG, "精确匹配原文件名成功：${it.name}")
                return it.uri
            }

            // 2. 按 title 匹配（重命名成功，新文件名 = title + 扩展名）
            if (title.isNotBlank()) {
                children.firstOrNull { doc ->
                    val name = doc.name ?: return@firstOrNull false
                    val nameWithoutExt = name.substringBeforeLast('.', name)
                    nameWithoutExt.equals(title, ignoreCase = true) && isAudioFile(name)
                }?.let {
                    Log.i(TAG, "按 title 匹配成功：${it.name}")
                    return it.uri
                }
            }

            Log.w(TAG, "未找到匹配文件")
            null
        }.onFailure {
            Log.w(TAG, "修复异常：$staleUri", it)
        }.getOrNull()
    }

    private fun isAudioFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in AUDIO_EXTS
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

    private companion object {
        const val TAG = "StaleUriRepairer"
        val AUDIO_EXTS = setOf("mp3", "m4a", "flac", "ogg", "wav", "mp4", "m4p", "m4b", "aac")
    }
}
