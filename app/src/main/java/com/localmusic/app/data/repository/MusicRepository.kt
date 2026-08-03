package com.localmusic.app.data.repository

import android.content.Context
import android.net.Uri
import com.localmusic.app.data.importer.ImportResult
import com.localmusic.app.data.importer.MusicImporter
import com.localmusic.app.data.importer.MusicMetadataEditor
import com.localmusic.app.data.local.PlaylistDao
import com.localmusic.app.data.local.SongDao
import com.localmusic.app.data.migration.ImportProgress
import com.localmusic.app.data.migration.ImportSummary
import com.localmusic.app.data.migration.MigrationManager
import com.localmusic.app.data.migration.MigrationPackage
import com.localmusic.app.data.model.Playlist
import com.localmusic.app.data.model.PlaylistSong
import com.localmusic.app.data.model.PlaylistWithCount
import com.localmusic.app.data.model.Song
import com.localmusic.app.data.model.SongEntity
import com.localmusic.app.data.model.toSong
import kotlinx.coroutines.flow.Flow

class MusicRepository(
    private val context: Context,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val importer: MusicImporter = MusicImporter(context, songDao),
    private val metadataEditor: MusicMetadataEditor = MusicMetadataEditor(context)
) {

    /** 观察整个曲库（即"主收藏"内容），持久化流。 */
    fun observeSongs(): Flow<List<SongEntity>> = songDao.observeAll()

    fun observePlaylists(): Flow<List<PlaylistWithCount>> = playlistDao.observePlaylists()

    fun observePlaylistSongIds(playlistId: Long): Flow<List<Long>> =
        playlistDao.observeSongIdsInPlaylist(playlistId)

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

    // ---------- 歌曲 ----------

    suspend fun getSongById(songId: Long): Song? = songDao.getById(songId)?.toSong()

    suspend fun getSongsByIds(songIds: List<Long>): List<Song> =
        if (songIds.isEmpty()) emptyList() else songDao.getByIds(songIds).map { it.toSong() }

    suspend fun getAllSongs(): List<Song> = songDao.getAll().map { it.toSong() }

    /** 删除曲库中的一首歌（主收藏移除）。 */
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
        val result = metadataEditor.editMetadata(uri, title, artist, album)
        if (result is MusicMetadataEditor.Result.Success) {
            songDao.updateMetadata(songId, title, artist, album)
            // 关键：如果文件被重命名了（URI 变化），必须更新数据库
            if (result.newUri != uri) {
                songDao.updateUri(songId, result.newUri.toString())
            }
        }
        return result
    }

    // ---------- 歌单 ----------

    suspend fun createPlaylist(name: String): Long =
        playlistDao.insertPlaylist(Playlist(name = name.trim()))

    suspend fun deletePlaylist(playlistId: Long) {
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
