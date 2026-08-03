package com.localmusic.app.data.migration

/** 迁移包版本。升级时保持向后兼容。 */
const val MIGRATION_VERSION = "1.0"

/** 导出的迁移包根对象。 */
data class MigrationPackage(
    val version: String = MIGRATION_VERSION,
    val exportTime: Long = System.currentTimeMillis(),
    val appName: String = "LocalMusic",
    val playlists: List<ExportPlaylist>
)

/** 导出的单个歌单。 */
data class ExportPlaylist(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val isFavorites: Boolean,
    val songs: List<ExportSongRef>
)

/** 导出的歌曲引用。用文件名 + 时长作为匹配键，跨设备通用。 */
data class ExportSongRef(
    /** 旧设备歌曲 ID（仅用于日志，新设备会重建）。 */
    val originalId: Long,
    val title: String,
    val artist: String,
    val album: String,
    /** 时长（毫秒），用于精确匹配。 */
    val duration: Long,
    /** 文件名（不含路径），用于精确 / 模糊匹配。 */
    val fileName: String,
    /** 可选：旧设备上的文件路径片段。 */
    val pathHint: String? = null,
    val playCount: Int,
    val favorite: Boolean,
    /** 文件 MD5 哈希值，用于内容级精确匹配（优先级最高）。 */
    val md5: String? = null
)

/** 导入匹配结果。 */
sealed class ImportMatchResult {
    /** 成功匹配到新设备上的歌曲。 */
    data class Matched(
        val playlistId: Long,
        val newSongId: Long,
        val originalSong: ExportSongRef
    ) : ImportMatchResult()

    /** 未找到匹配，标记为失败。 */
    data class Unmatched(
        val originalPlaylistId: Long,
        val originalSong: ExportSongRef,
        val reason: String
    ) : ImportMatchResult()
}

/** 导入进度回调。 */
data class ImportProgress(
    val phase: String,
    val current: Int,
    val total: Int,
    val matchedCount: Int,
    val unmatchedCount: Int
)

/** 单个歌单的导入结果详情。 */
data class PlaylistImportResult(
    val playlistName: String,
    val totalSongs: Int,
    val matchedSongs: Int,
    val unmatchedSongs: Int,
    val unmatchedSongNames: List<String>
)
