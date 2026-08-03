package com.localmusic.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.localmusic.app.data.model.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Query("SELECT * FROM songs ORDER BY dateAdded DESC")
    fun observeAll(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY dateAdded DESC")
    suspend fun getAll(): List<SongEntity>

    @Query("SELECT COUNT(*) FROM songs")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getById(id: Long): SongEntity?

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<SongEntity>

    @Query("SELECT id FROM songs WHERE uri = :uri")
    suspend fun getIdByUri(uri: String): Long?

    /** 冲突时忽略；返回新插入 id，已存在则返回 -1。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(song: SongEntity): Long

    @Query("UPDATE songs SET albumArtPath = :path WHERE id = :id")
    suspend fun updateAlbumArtPath(id: Long, path: String?)

    /** 更新歌曲元数据（编辑信息后同步数据库）。 */
    @Query("UPDATE songs SET title = :title, artist = :artist, album = :album WHERE id = :id")
    suspend fun updateMetadata(id: Long, title: String, artist: String, album: String)

    /** 更新歌曲 URI（SAF 重命名后原 URI 失效，需写回新 URI）。 */
    @Query("UPDATE songs SET uri = :uri WHERE id = :id")
    suspend fun updateUri(id: Long, uri: String)

    /** 原子更新元数据 + URI，避免中间态导致 Flow 发射旧 URI。 */
    @Transaction
    suspend fun updateMetadataAndUri(id: Long, title: String, artist: String, album: String, uri: String) {
        updateMetadata(id, title, artist, album)
        updateUri(id, uri)
    }

    /** 递增播放次数。 */
    @Query("UPDATE songs SET playCount = playCount + 1 WHERE id = :id")
    suspend fun incrementPlayCount(id: Long)

    /** 切换喜欢状态。 */
    @Query("UPDATE songs SET favorite = :favorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, favorite: Boolean)

    /** 按 URI 路径前缀查找文件名。用于迁移匹配。 */
    @Query("SELECT * FROM songs WHERE uri LIKE :pathPrefix || '%' ORDER BY title")
    suspend fun findByUriPathPrefix(pathPrefix: String): List<SongEntity>

    /** 按文件名（URI 最后一段）+ 时长查找歌曲。精确匹配用。 */
    @Query("SELECT * FROM songs WHERE duration BETWEEN :minDuration AND :maxDuration ORDER BY id")
    suspend fun findByDurationRange(minDuration: Long, maxDuration: Long): List<SongEntity>

    /** 取全部歌曲用于内存中二次匹配。 */
    @Query("SELECT * FROM songs")
    suspend fun getAllForMigration(): List<SongEntity>

    /** 按 MD5 查找歌曲（精确匹配，用于迁移）。 */
    @Query("SELECT * FROM songs WHERE md5 = :md5 LIMIT 1")
    suspend fun findByMd5(md5: String): SongEntity?

    /** 更新 MD5 值（当歌曲首次计算 MD5 后回填）。 */
    @Query("UPDATE songs SET md5 = :md5 WHERE id = :id")
    suspend fun updateMd5(id: Long, md5: String)

    /** 导入时累加播放次数。 */
    @Query("UPDATE songs SET playCount = playCount + :count WHERE id = :id")
    suspend fun incrementPlayCountForImport(id: Long, count: Int)

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM songs")
    suspend fun deleteAll()
}
