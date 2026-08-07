package com.localmusic.app.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.localmusic.app.data.importer.ImportResult
import com.localmusic.app.data.importer.MusicImporter
import com.localmusic.app.data.importer.MusicMetadataEditor
import com.localmusic.app.data.local.ImportLogDao
import com.localmusic.app.data.local.FingerprintCacheDao
import com.localmusic.app.data.local.PlaylistDao
import com.localmusic.app.data.local.SongDao
import com.localmusic.app.data.migration.ImportProgress
import com.localmusic.app.data.migration.ImportSummary
import com.localmusic.app.data.migration.MigrationManager
import com.localmusic.app.data.migration.MigrationPackage
import com.localmusic.app.data.model.ImportLogEntity
import com.localmusic.app.data.model.Playlist
import com.localmusic.app.data.model.PlaylistSong
import com.localmusic.app.data.model.PlaylistWithCount
import com.localmusic.app.data.model.Song
import com.localmusic.app.data.model.SongEntity
import com.localmusic.app.data.model.toSong
import com.localmusic.app.data.model.FingerprintCacheEntity
import com.localmusic.app.util.AudioFingerprintUtils
import com.localmusic.app.util.Chromaprint
import com.localmusic.app.util.FileHashUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/** 一条重复记录：song 为重复歌曲，reason 为命中通道说明（展示给用户看的归组理由）。 */
data class DuplicateEntry(
    val song: SongEntity,
    val reason: String
)

/** 重复歌曲分组：keep 为保留的一份，duplicates 为可删除的重复项（含命中原因）。 */
data class DuplicateGroup(
    val keep: SongEntity,
    val duplicates: List<DuplicateEntry>
)

/** 精确重复扫描结果：unreadableCount 为文件无法读取（URI 失效）未参与比对的歌曲数。 */
data class DuplicateScanResult(
    val groups: List<DuplicateGroup>,
    val unreadableCount: Int
)

class MusicRepository(
    private val context: Context,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val importLogDao: ImportLogDao? = null,
    private val fingerprintCacheDao: FingerprintCacheDao? = null,
    private val importer: MusicImporter = MusicImporter(context, songDao),
    private val metadataEditor: MusicMetadataEditor = MusicMetadataEditor(context)
) {

    private companion object {
        const val TAG = "MusicRepository"
    }

    /** 观察整个曲库（全部导入音乐），持久化流。distinctUntilChanged 过滤瞬时脏数据。 */
    fun observeSongs(): Flow<List<SongEntity>> = songDao.observeAll().distinctUntilChanged()

    /** 观察主收藏（favorite = true 的红心歌曲），持久化流。 */
    fun observeFavoriteSongs(): Flow<List<SongEntity>> =
        songDao.observeFavorites().distinctUntilChanged()

    /** 观察最近播放（播放完成的歌曲，按时间倒序），持久化流。 */
    fun observeRecentlyPlayedSongs(): Flow<List<SongEntity>> =
        songDao.observeRecentlyPlayed().distinctUntilChanged()

    fun observePlaylists(): Flow<List<PlaylistWithCount>> =
        playlistDao.observePlaylists().distinctUntilChanged()

    fun observePlaylistSongIds(playlistId: Long): Flow<List<Long>> =
        playlistDao.observeSongIdsInPlaylist(playlistId).distinctUntilChanged()

    // ---------- 导入 ----------

    /** 导入选中的文件。 */
    suspend fun importFromUris(
        uris: List<Uri>,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): ImportResult = importer.importFromUris(uris, onProgress)

    /** 导入选中的文件夹（树）。 */
    suspend fun importFromTree(
        treeUri: Uri,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): ImportResult = importer.importFromTree(treeUri, onProgress)

    /**
     * 导入单个外部音频文件（其他应用"打开方式"传入），返回歌曲 id。
     * 已存在（URI/MD5 内容相同）返回已有 id，失败返回 null。
     */
    suspend fun importOneAndGetId(uri: Uri): Long? = importer.importOne(uri)

    // ---------- 歌曲 ----------

    suspend fun getSongById(songId: Long): Song? = songDao.getById(songId)?.toSong()

    suspend fun getSongsByIds(songIds: List<Long>): List<Song> =
        if (songIds.isEmpty()) emptyList() else songDao.getByIds(songIds).map { it.toSong() }

    suspend fun getAllSongs(): List<Song> = songDao.getAll().map { it.toSong() }

    /** 主收藏（红心歌曲）的一次性快照。 */
    suspend fun getFavoriteSongs(): List<Song> = songDao.getFavorites().map { it.toSong() }

    /** 删除曲库中的一首歌（从所有歌单同步移除）。 */
    suspend fun deleteSong(songId: Long) {
        songDao.delete(songId)
    }

    /** 递增播放次数。 */
    suspend fun incrementPlayCount(songId: Long) {
        songDao.incrementPlayCount(songId)
    }

    /** 切换喜欢状态。 */
    suspend fun toggleFavorite(songId: Long, favorite: Boolean) {
        songDao.updateFavorite(songId, favorite)
    }

    /** 标记歌曲为最近播放（播放完成时调用）。 */
    suspend fun markRecentlyPlayed(songId: Long) {
        android.util.Log.i(TAG, "markRecentlyPlayed: songId=$songId time=${System.currentTimeMillis()}")
        songDao.updateLastPlayed(songId, System.currentTimeMillis())
    }

    /** 从最近播放中移除（清空播放时间戳，曲库保留）。 */
    suspend fun clearRecentlyPlayed(songId: Long) {
        songDao.clearRecentlyPlayed(songId)
    }

    /** 导入日志：按时间倒序观察每次导入结果。 */
    fun observeImportLogs(): Flow<List<ImportLogEntity>> {
        val dao = importLogDao ?: return kotlinx.coroutines.flow.flowOf(emptyList())
        return dao.observeAll()
    }

    /** 记录一次导入日志（新增/重复/失败数量 + 重复与失败的歌名）。 */
    suspend fun recordImportLog(result: ImportResult, timestamp: Long = System.currentTimeMillis()) {
        importLogDao?.insert(
            ImportLogEntity(
                timestamp = timestamp,
                addedCount = result.added,
                duplicateNames = result.duplicateNames.joinToString("\n"),
                failedNames = result.failedNames.joinToString("\n")
            )
        )
    }

    /**
     * 扫描曲库重复歌曲（双通道精确检测）：
     *  - 通道 1（MD5 文件指纹）：文件字节完全一致 → 重复
     *  - 通道 2（音频指纹）：chromaprint 指纹相似（相同声音内容，容忍转码、
     *    音量差异、声道/重采样差异、轻微裁剪）→ 重复
     * 匹配时与组内所有成员比对（不止最早导入的 keep），支持传递归组。
     * 指纹缓存在 fingerprint_cache 表（uri + lastModified + size 未变则复用，
     * 避免每次扫描重新解码整曲）。
     * 每组保留最早导入的一份；无法读取（URI 失效）的歌曲单独计数。
     */
    suspend fun findDuplicateGroupsExact(
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> }
    ): DuplicateScanResult = withContext(Dispatchers.IO) {
        val songs = songDao.getAll().sortedBy { it.dateAdded }

        data class GroupAcc(
            val keep: SongEntity,
            val md5s: MutableSet<String>,
            val fingerprints: MutableList<IntArray>,
            val members: MutableList<SongEntity>,
            val dups: MutableList<DuplicateEntry>
        )

        val total = songs.size * 2 // MD5 + 音频指纹两个阶段
        var unreadable = 0

        // 阶段 1：并行计算 MD5（信号量限 4 路 IO），并回填/修正库中的 md5 字段
        val semaphore = Semaphore(4)
        val md5ById = songs.map { song ->
            async {
                semaphore.withPermit {
                    song.id to FileHashUtils.computeMd5(context, Uri.parse(song.uri))
                }
            }
        }.awaitAll().toMap()
        onProgress(songs.size, total)

        // 阶段 2：音频指纹（缓存未失效则复用；否则解码重算，信号量限 2 路解码）
        val fpDao = fingerprintCacheDao
        val cacheRows = runCatching { fpDao?.getAll().orEmpty() }.getOrDefault(emptyList())
            .associateBy { it.songId }
        val fpSemaphore = Semaphore(2)

        data class FpResult(
            val songId: Long,
            val fp: IntArray?,
            val cacheEntity: FingerprintCacheEntity?
        )

        // 并发解码只返回结果，避免多个协程并发改共享集合
        val fpResults = songs.mapIndexed { index, song ->
            async {
                fpSemaphore.withPermit {
                    var fp: IntArray? = null
                    var cacheEntity: FingerprintCacheEntity? = null
                    val cached = cacheRows[song.id]
                    val uri = Uri.parse(song.uri)
                    val lastModified = AudioFingerprintUtils.lastModified(context, uri)
                    val size = AudioFingerprintUtils.fileSize(context, uri)
                    if (cached != null &&
                        cached.uri == song.uri &&
                        (lastModified == 0L || cached.lastModified == lastModified) &&
                        (size == 0L || cached.size == size)
                    ) {
                        fp = runCatching { Chromaprint.decode(cached.fingerprint) }.getOrNull()
                    }
                    if (fp == null) {
                        val fresh = AudioFingerprintUtils.computeFingerprint(context, uri)
                        if (fresh != null) {
                            fp = fresh
                            cacheEntity = FingerprintCacheEntity(
                                songId = song.id,
                                uri = song.uri,
                                lastModified = lastModified,
                                size = size,
                                fingerprint = Chromaprint.encode(fresh)
                            )
                        }
                    }
                    onProgress(songs.size + index + 1, total)
                    FpResult(song.id, fp, cacheEntity)
                }
            }
        }.awaitAll()

        val fpById = fpResults.associate { it.songId to it.fp }
        val cacheToWrite = fpResults.mapNotNull { it.cacheEntity }

        if (cacheToWrite.isNotEmpty()) {
            runCatching { fpDao?.upsertAll(cacheToWrite) }
        }

        val durationById = songs.associate { it.id to it.duration }

        // 阶段 3：归组。先 MD5 精确命中，再音频指纹相似匹配
        //（指纹长度差 ≤ 2×对齐窗、时长差 ≤ 25% 做预过滤，避免全量两两比对）
        val groups = mutableListOf<GroupAcc>()

        songs.forEachIndexed { index, song ->
            val md5 = md5ById[song.id]
            if (md5 == null && fpById[song.id] == null) {
                unreadable++
                if (unreadable <= 10) {
                    Log.w(TAG, "duplicate scan: 无法读取文件 uri=${song.uri}, title=${song.title}")
                }
            } else if (md5 != null && song.md5 != md5) {
                // 回填/修正库中的指纹，下次扫描直接命中
                runCatching { songDao.updateMd5(song.id, md5) }
            }

            val fp = fpById[song.id]
            val songDur = durationById[song.id] ?: 0L

            // 找已存在的组：与组内所有成员比对（含 keep），命中即入组
            var matched: GroupAcc? = null
            var reason = ""
            outer@ for (group in groups) {
                if (md5 != null && group.md5s.contains(md5)) {
                    matched = group
                    reason = "内容完全一致（MD5）"
                    break
                }
                if (fp != null && group.fingerprints.isNotEmpty()) {
                    // 时长预过滤：同一音频不同编码/裁剪时长基本一致，放宽到 ±25%
                    val keepDur = group.keep.duration
                    val durOk = songDur <= 0 || keepDur <= 0 ||
                        maxOf(songDur, keepDur) * 4 <= minOf(songDur, keepDur) * 5
                    if (durOk) {
                        for (memberFp in group.fingerprints) {
                            if (kotlin.math.abs(memberFp.size - fp.size) <= 2 * Chromaprint.MAX_ALIGN_OFFSET &&
                                Chromaprint.isSimilar(memberFp, fp)
                            ) {
                                matched = group
                                reason = "音频指纹一致（相同声音内容）"
                                break@outer
                            }
                        }
                    }
                }
            }

            if (matched != null) {
                if (md5 != null) matched.md5s.add(md5)
                if (fp != null && matched.fingerprints.none { Chromaprint.isSimilar(it, fp) }) {
                    matched.fingerprints.add(fp)
                }
                matched.members.add(song)
                matched.dups.add(DuplicateEntry(song, reason))
                Log.i(TAG, "duplicate scan: 发现重复「${song.title}」(${song.artist}) id=${song.id} == 保留「${matched.keep.title}」(${matched.keep.artist}) id=${matched.keep.id}")
            } else {
                groups.add(
                    GroupAcc(
                        keep = song,
                        md5s = if (md5 != null) mutableSetOf(md5) else mutableSetOf(),
                        fingerprints = if (fp != null) mutableListOf(fp) else mutableListOf(),
                        members = mutableListOf(song),
                        dups = mutableListOf()
                    )
                )
            }
            onProgress(songs.size + index + 1, total)
        }

        Log.i(
            TAG,
            "duplicate scan 完成: total=${songs.size}, 重复组=${groups.count { it.dups.isNotEmpty() }}, 重复歌曲数=${groups.sumOf { it.dups.size }}, 无法读取=$unreadable"
        )

        DuplicateScanResult(
            groups = groups.filter { it.dups.isNotEmpty() }.map { group ->
                DuplicateGroup(
                    keep = group.keep,
                    duplicates = group.dups
                )
            },
            unreadableCount = unreadable
        )
    }

    /**
     * 删除指定重复歌曲：
     *  - [deleteFiles] = false：仅从曲库删除（保留文件）
     *  - [deleteFiles] = true：同时删除磁盘文件（SAF 权限内的文件）
     * @return 删除的歌曲数量
     */
    suspend fun removeDuplicateSongs(ids: List<Long>, deleteFiles: Boolean): Int =
        withContext(Dispatchers.IO) {
            var removed = 0
            for (id in ids) {
                val entity = songDao.getById(id) ?: continue
                if (deleteFiles) {
                    runCatching {
                        val uri = Uri.parse(entity.uri)
                        if (uri.scheme == "content") {
                            DocumentFile.fromSingleUri(context, uri)?.delete()
                        } else {
                            uri.path?.let { File(it).delete() }
                        }
                    }
                }
                playlistDao.deleteSongFromAllPlaylists(id)
                songDao.delete(id)
                removed++
            }
            removed
        }

    /**
     * 清理曲库中的重复歌曲（精确内容指纹）：
     *  - 每组保留最早导入的记录，删除其余（含歌单关联）
     * @return 删除的重复歌曲数量
     */
    suspend fun findAndRemoveDuplicates(): Int = withContext(Dispatchers.IO) {
        val result = findDuplicateGroupsExact()
        val toDelete = result.groups.flatMap { it.duplicates.map { d -> d.song.id } }
        if (toDelete.isEmpty()) return@withContext 0
        for (id in toDelete) {
            playlistDao.deleteSongFromAllPlaylists(id)
            songDao.delete(id)
        }
        toDelete.size
    }

    /**
     * 编辑歌曲元数据：修改 ID3/Vorbis tag 并重命名文件。
     * 成功后同步数据库的 title/artist/album 字段以及 URI。
     * @return [MusicMetadataEditor.Result]
     */
    suspend fun updateSongMetadata(
        songId: Long,
        title: String,
        artist: String,
        album: String
    ): MusicMetadataEditor.Result {
        val entity = songDao.getById(songId) ?: return MusicMetadataEditor.Result.Failed("歌曲不存在")
        val uri = Uri.parse(entity.uri)
        Log.i(TAG, "updateSongMetadata: songId=$songId, oldUri=${entity.uri}, newTitle=$title")
        val result = metadataEditor.editMetadata(uri, title, artist, album)
        if (result is MusicMetadataEditor.Result.Success) {
            // 始终使用原子事务 updateMetadataAndUri，杜绝中间态 Flow 发射旧 URI
            val newUriStr = result.newUri.toString()
            Log.i(TAG, "updateSongMetadata: editMetadata 返回 newUri=$newUriStr, oldUri=${entity.uri}")
            songDao.updateMetadataAndUri(songId, title, artist, album, newUriStr)
            Log.i(TAG, "updateSongMetadata: 已调用 updateMetadataAndUri（原子事务）")
        } else {
            Log.w(TAG, "updateSongMetadata: editMetadata 失败: $result")
        }
        return result
    }

    // ---------- 歌单 ----------

    suspend fun createPlaylist(name: String): Long =
        playlistDao.insertPlaylist(Playlist(name = name.trim()))

    suspend fun deletePlaylist(playlistId: Long) {
        if (playlistId == com.localmusic.app.data.model.ALL_SONGS_PLAYLIST_ID) return
        if (playlistId == com.localmusic.app.data.model.FAVORITES_PLAYLIST_ID) return
        playlistDao.deletePlaylist(playlistId)
    }

    suspend fun renamePlaylist(playlistId: Long, name: String) {
        playlistDao.renamePlaylist(playlistId, name.trim())
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) {
        playlistDao.addSongToPlaylist(PlaylistSong(playlistId = playlistId, songId = songId))
    }

    /** 批量添加歌曲到歌单（单事务）。 */
    suspend fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>) {
        if (songIds.isEmpty()) return
        playlistDao.addSongsToPlaylist(
            songIds.map { PlaylistSong(playlistId = playlistId, songId = it) }
        )
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        playlistDao.removeSongFromPlaylist(playlistId, songId)
    }

    suspend fun getPlaylist(playlistId: Long): Playlist? = playlistDao.getPlaylist(playlistId)

    suspend fun getPlaylistSongs(playlistId: Long): List<Song> {
        val ids = playlistDao.getSongIdsInPlaylist(playlistId)
        return getSongsByIds(ids)
    }

    // ---------- 迁移 ----------

    private val migrationManager by lazy { MigrationManager(songDao, playlistDao) }

    /** 导出所有歌单为 JSON 字符串。 */
    suspend fun exportPlaylistsToJson(
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): String {
        val pkg = migrationManager.export(includeSongs = true, onProgress = onProgress)
        return migrationManager.toJson(pkg)
    }

    /** 从 JSON 字符串导入歌单。 */
    suspend fun importPlaylistsFromJson(
        json: String,
        onProgress: (ImportProgress) -> Unit
    ): ImportSummary? {
        val pkg = migrationManager.fromJson(json) ?: return null
        return migrationManager.import(pkg, onProgress)
    }
}
