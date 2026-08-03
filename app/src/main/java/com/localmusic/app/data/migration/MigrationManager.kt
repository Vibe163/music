package com.localmusic.app.data.migration

import android.net.Uri
import com.localmusic.app.data.local.PlaylistDao
import com.localmusic.app.data.local.SongDao
import com.localmusic.app.data.model.FAVORITES_PLAYLIST_ID
import com.localmusic.app.data.model.Playlist
import com.localmusic.app.data.model.PlaylistSong
import com.localmusic.app.data.model.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** 负责歌单迁移：导出为 JSON 字符串 / 从 JSON 字符串导入。 */
class MigrationManager(
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao
) {

    // ===================== 导出 =====================

    /**
     * 导出当前所有歌单为 [MigrationPackage]。
     * @param includeSongs 每个歌单是否包含歌曲引用。
     */
    suspend fun export(
        includeSongs: Boolean = true,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): MigrationPackage = withContext(Dispatchers.IO) {
        val allSongs = songDao.getAllForMigration()
        val allPlaylists = getAllPlaylistsSync()

        val exported = mutableListOf<ExportPlaylist>()
        var done = 0
        val total = allPlaylists.size

        for (pwc in allPlaylists) {
            val songIds = playlistDao.getSongIdsInPlaylist(pwc.id)
            val songs = if (includeSongs) {
                val entities = if (songIds.isNotEmpty()) {
                    songDao.getByIds(songIds)
                } else emptyList()
                entities.map { entity ->
                    val fileName = extractFileName(entity.uri)
                    ExportSongRef(
                        originalId = entity.id,
                        title = entity.title,
                        artist = entity.artist,
                        album = entity.album,
                        duration = entity.duration,
                        fileName = fileName,
                        pathHint = entity.uri,
                        playCount = entity.playCount,
                        favorite = entity.favorite,
                        md5 = entity.md5
                    )
                }
            } else emptyList()

            exported.add(
                ExportPlaylist(
                    id = pwc.id,
                    name = pwc.name,
                    createdAt = pwc.createdAt,
                    isFavorites = pwc.id == FAVORITES_PLAYLIST_ID,
                    songs = songs
                )
            )
            done++
            withContext(Dispatchers.Main) { onProgress(done, total) }
        }

        MigrationPackage(playlists = exported)
    }

    /** 序列化为 JSON 字符串。 */
    fun toJson(pkg: MigrationPackage): String {
        val root = JSONObject().apply {
            put("version", pkg.version)
            put("exportTime", pkg.exportTime)
            put("appName", pkg.appName)
        }
        val arr = JSONArray()
        for (pl in pkg.playlists) {
            val plObj = JSONObject().apply {
                put("id", pl.id)
                put("name", pl.name)
                put("createdAt", pl.createdAt)
                put("isFavorites", pl.isFavorites)
            }
            val sArr = JSONArray()
            for (s in pl.songs) {
                sArr.put(
                    JSONObject().apply {
                        put("originalId", s.originalId)
                        put("title", s.title)
                        put("artist", s.artist)
                        put("album", s.album)
                        put("duration", s.duration)
                        put("fileName", s.fileName)
                        if (s.pathHint != null) put("pathHint", s.pathHint)
                        put("playCount", s.playCount)
                        put("favorite", s.favorite)
                        if (s.md5 != null) put("md5", s.md5)
                    }
                )
            }
            plObj.put("songs", sArr)
            arr.put(plObj)
        }
        root.put("playlists", arr)
        return root.toString(2)
    }

    // ===================== 导入 =====================

    /**
     * 从 JSON 字符串解析 [MigrationPackage]。
     * @return 解析失败返回 null。
     */
    fun fromJson(json: String): MigrationPackage? {
        return runCatching {
            val root = JSONObject(json)
            val version = root.optString("version", "1.0")
            val appName = root.optString("appName", "LocalMusic")
            val exportTime = root.optLong("exportTime", 0L)

            val arr = root.optJSONArray("playlists") ?: JSONArray()
            val playlists = mutableListOf<ExportPlaylist>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val sArr = obj.optJSONArray("songs") ?: JSONArray()
                val songs = mutableListOf<ExportSongRef>()
                for (j in 0 until sArr.length()) {
                    val s = sArr.getJSONObject(j)
                    songs.add(
                        ExportSongRef(
                            originalId = s.optLong("originalId", -1L),
                            title = s.optString("title", ""),
                            artist = s.optString("artist", ""),
                            album = s.optString("album", ""),
                            duration = s.optLong("duration", 0L),
                            fileName = s.optString("fileName", ""),
                            pathHint = s.optString("pathHint").ifBlank { null },
                            playCount = s.optInt("playCount", 0),
                            favorite = s.optBoolean("favorite", false),
                            md5 = s.optString("md5").ifBlank { null }
                        )
                    )
                }
                playlists.add(
                    ExportPlaylist(
                        id = obj.optLong("id", 0L),
                        name = obj.optString("name", "未命名歌单"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        isFavorites = obj.optBoolean("isFavorites", false),
                        songs = songs
                    )
                )
            }
            MigrationPackage(
                version = version,
                exportTime = exportTime,
                appName = appName,
                playlists = playlists
            )
        }.getOrNull()
    }

    /**
     * 执行导入：创建歌单并尽量匹配歌曲。
     * @param pkg 从 JSON 解析的迁移包。
     * @param onProgress 进度回调。
     * @return 导入结果摘要。
     */
    suspend fun import(
        pkg: MigrationPackage,
        onProgress: (ImportProgress) -> Unit
    ): ImportSummary = withContext(Dispatchers.IO) {
        val allSongs = songDao.getAllForMigration()
        val fileNameIndex = buildFileNameIndex(allSongs)
        val exactMatchIndex = buildExactMatchIndex(allSongs)
        val md5Index = buildMd5Index(allSongs)

        val createdPlaylists = mutableListOf<Pair<Long, String>>()
        val matchedSongs = mutableMapOf<Long, MutableList<Pair<Long, ExportSongRef>>>() // playlistId -> list of (newSongId, ref)
        val unmatchedSongs = mutableListOf<ImportMatchResult.Unmatched>()
        val playlistResults = mutableListOf<PlaylistImportResult>()
        var totalSongs = 0
        var doneSongs = 0

        for (pl in pkg.playlists) {
            // 创建歌单（除非是收藏夹，收藏夹已经存在）
            val newPlId = if (pl.isFavorites) {
                FAVORITES_PLAYLIST_ID
            } else {
                playlistDao.insertPlaylist(Playlist(name = pl.name, createdAt = pl.createdAt))
            }
            createdPlaylists.add(newPlId to pl.name)

            var matchedCount = 0
            var unmatchedCount = 0
            val unmatchedNames = mutableListOf<String>()

            // 匹配歌曲
            for (songRef in pl.songs) {
                totalSongs++
                val matchResult = matchSong(songRef, md5Index, fileNameIndex, exactMatchIndex, allSongs)
                when (matchResult) {
                    is ImportMatchResult.Matched -> {
                        matchedCount++
                        if (!matchedSongs.containsKey(newPlId)) matchedSongs[newPlId] = mutableListOf()
                        matchedSongs[newPlId]!!.add(matchResult.newSongId to songRef)
                    }
                    is ImportMatchResult.Unmatched -> {
                        unmatchedCount++
                        unmatchedSongs.add(matchResult)
                        unmatchedNames.add(songRef.title.ifBlank { songRef.fileName })
                    }
                }
                doneSongs++
                onProgress(
                    ImportProgress(
                        phase = "匹配歌曲",
                        current = doneSongs,
                        total = totalSongs,
                        matchedCount = matchedSongs.values.sumOf { it.size },
                        unmatchedCount = unmatchedSongs.size
                    )
                )
            }

            playlistResults.add(
                PlaylistImportResult(
                    playlistName = pl.name,
                    totalSongs = pl.songs.size,
                    matchedSongs = matchedCount,
                    unmatchedSongs = unmatchedCount,
                    unmatchedSongNames = unmatchedNames
                )
            )
        }

        // 写入库：关联 playlist_songs
        var totalToInsert = matchedSongs.values.sumOf { it.size }
        var inserted = 0
        for ((plId, items) in matchedSongs) {
            val pss = items.map { (newSongId, ref) ->
                PlaylistSong(playlistId = plId, songId = newSongId, addedAt = ref.originalId)
            }
            playlistDao.addSongsToPlaylist(pss)
            inserted += pss.size
            onProgress(
                ImportProgress(
                    phase = "写入数据库",
                    current = inserted,
                    total = totalToInsert,
                    matchedCount = matchedSongs.values.sumOf { it.size },
                    unmatchedCount = unmatchedSongs.size
                )
            )
        }

        // 更新匹配歌曲的 playCount 和 favorite
        for ((_, items) in matchedSongs) {
            for ((newSongId, ref) in items) {
                if (ref.playCount > 0) {
                    songDao.incrementPlayCountForImport(newSongId, ref.playCount)
                }
                if (ref.favorite) {
                    songDao.updateFavorite(newSongId, true)
                }
            }
        }

        ImportSummary(
            createdPlaylists = createdPlaylists,
            totalSongsImported = matchedSongs.values.sumOf { it.size },
            unmatchedSongs = unmatchedSongs,
            totalPlaylists = pkg.playlists.size,
            playlistResults = playlistResults
        )
    }

    // ===================== 内部工具 =====================

    /** 从 URI 中提取文件名。 */
    private fun extractFileName(uriStr: String): String {
        return runCatching {
            val uri = Uri.parse(uriStr)
            val last = uri.lastPathSegment ?: ""
            last.substringAfterLast('/')
        }.getOrDefault(uriStr.substringAfterLast('/'))
    }

    /** 从 URI 中提取文件扩展名。 */
    private fun extractFileExt(uriStr: String): String {
        return runCatching {
            val fileName = extractFileName(uriStr)
            fileName.substringAfterLast('.', "").lowercase()
        }.getOrDefault("")
    }

    /** 构建文件名索引：fileName -> list of SongEntity。 */
    private fun buildFileNameIndex(songs: List<SongEntity>): Map<String, List<SongEntity>> {
        val idx = mutableMapOf<String, MutableList<SongEntity>>()
        for (s in songs) {
            val fn = extractFileName(s.uri)
            if (fn.isNotBlank()) {
                idx.getOrPut(fn) { mutableListOf() }.add(s)
                // 同时建立一个去扩展名的索引
                val fnNoExt = fn.substringBeforeLast('.', fn)
                if (fnNoExt != fn) {
                    idx.getOrPut(fnNoExt) { mutableListOf() }.add(s)
                }
            }
        }
        return idx
    }

    /** 构建 MD5 索引：md5 -> SongEntity。 */
    private fun buildMd5Index(songs: List<SongEntity>): Map<String, SongEntity> {
        val idx = mutableMapOf<String, SongEntity>()
        for (s in songs) {
            s.md5?.let { md5 ->
                idx[md5] = s
            }
        }
        return idx
    }

    /** 构建"标题+艺术家+时长"精确索引。 */
    private fun buildExactMatchIndex(songs: List<SongEntity>): Map<String, SongEntity> {
        val idx = mutableMapOf<String, SongEntity>()
        for (s in songs) {
            val key = buildString {
                append(s.title.lowercase().trim())
                append('|')
                append(s.artist.lowercase().trim())
                append('|')
                append(s.duration)
            }
            idx[key] = s
        }
        return idx
    }

    /** 尝试匹配一首歌。优先级：MD5 > 标题+艺术家+时长 > 文件名+时长 > 仅时长 */
    private fun matchSong(
        ref: ExportSongRef,
        md5Index: Map<String, SongEntity>,
        fileNameIndex: Map<String, List<SongEntity>>,
        exactMatchIndex: Map<String, SongEntity>,
        allSongs: List<SongEntity>
    ): ImportMatchResult {
        // 策略 0（优先）: MD5 精确匹配（基于文件内容，不受文件名/路径影响）
        ref.md5?.let { md5 ->
            md5Index[md5]?.let {
                return ImportMatchResult.Matched(
                    playlistId = 0L,
                    newSongId = it.id,
                    originalSong = ref
                )
            }
        }

        // 策略 1: 精确匹配（标题 + 艺术家 + 时长）
        val key = buildString {
            append(ref.title.lowercase().trim())
            append('|')
            append(ref.artist.lowercase().trim())
            append('|')
            append(ref.duration)
        }
        exactMatchIndex[key]?.let {
            return ImportMatchResult.Matched(
                playlistId = 0L,
                newSongId = it.id,
                originalSong = ref
            )
        }

        // 策略 2: 文件名精确匹配 + 时长差 <= 2s
        val fileName = ref.fileName
        if (fileName.isNotBlank()) {
            val candidates = fileNameIndex[fileName]
            if (!candidates.isNullOrEmpty()) {
                val exact = candidates.find { it.duration == ref.duration }
                if (exact != null) {
                    return ImportMatchResult.Matched(
                        playlistId = 0L,
                        newSongId = exact.id,
                        originalSong = ref
                    )
                }
                // 时长差 <= 2s
                val near = candidates.minByOrNull { kotlin.math.abs(it.duration - ref.duration) }
                if (near != null && kotlin.math.abs(near.duration - ref.duration) <= 2000L) {
                    return ImportMatchResult.Matched(
                        playlistId = 0L,
                        newSongId = near.id,
                        originalSong = ref
                    )
                }
            }
        }

        // 策略 3: 去扩展名的文件名 + 时长差 <= 5s
        val fnNoExt = fileName.substringBeforeLast('.', fileName)
        if (fnNoExt.isNotBlank()) {
            val candidates = fileNameIndex[fnNoExt]
            if (!candidates.isNullOrEmpty()) {
                val near = candidates.minByOrNull { kotlin.math.abs(it.duration - ref.duration) }
                if (near != null && kotlin.math.abs(near.duration - ref.duration) <= 5000L) {
                    return ImportMatchResult.Matched(
                        playlistId = 0L,
                        newSongId = near.id,
                        originalSong = ref
                    )
                }
            }
        }

        // 策略 4: 仅按时长匹配（兜底，可能会误匹配但概率低）
        if (allSongs.isNotEmpty()) {
            val nearList = allSongs.filter { kotlin.math.abs(it.duration - ref.duration) <= 1000L }
            if (nearList.size == 1) {
                return ImportMatchResult.Matched(
                    playlistId = 0L,
                    newSongId = nearList.first().id,
                    originalSong = ref
                )
            }
        }

        return ImportMatchResult.Unmatched(
            originalPlaylistId = -1L,
            originalSong = ref,
            reason = "未找到匹配的本地歌曲"
        )
    }

    /** 获取全部歌单（含收藏夹）。 */
    private suspend fun getAllPlaylistsSync(): List<com.localmusic.app.data.model.PlaylistWithCount> {
        return withContext(Dispatchers.IO) {
            val result = mutableListOf<com.localmusic.app.data.model.PlaylistWithCount>()
            val playlistEntities = playlistDao.getAllPlaylistsRaw()
            for (entity in playlistEntities) {
                val count = playlistDao.countSongsInPlaylist(entity.id)
                result.add(
                    com.localmusic.app.data.model.PlaylistWithCount(
                        id = entity.id,
                        name = entity.name,
                        createdAt = entity.createdAt,
                        songCount = count,
                        isBuiltIn = entity.id == FAVORITES_PLAYLIST_ID
                    )
                )
            }
            result
        }
    }
}

/** 导入摘要结果。 */
data class ImportSummary(
    val createdPlaylists: List<Pair<Long, String>>,
    val totalSongsImported: Int,
    val unmatchedSongs: List<ImportMatchResult.Unmatched>,
    val totalPlaylists: Int,
    val playlistResults: List<PlaylistImportResult>
)
