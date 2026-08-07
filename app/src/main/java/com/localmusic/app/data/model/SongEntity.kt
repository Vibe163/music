package com.localmusic.app.data.model

import android.net.Uri
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 持久化的曲库条目（即"主收藏歌单"的内容）。
 * 通过 SAF 选中的文件 / 文件夹导入后会写入此表，重启不丢失。
 */
@Entity(
    tableName = "songs",
    indices = [Index(value = ["uri"], unique = true)]
)
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: String,
    val albumArtPath: String? = null,
    val dateAdded: Long = System.currentTimeMillis(),
    val playCount: Int = 0,
    val favorite: Boolean = false,
    val md5: String? = null,
    /** 最近一次播放完成时间戳；0 = 从未播放（最近播放列表 = lastPlayedAt > 0 按此倒序）。 */
    val lastPlayedAt: Long = 0
)

/** 转换为播放器/UI 使用的领域模型。 */
fun SongEntity.toSong(): Song = Song(
    id = id,
    title = title,
    artist = artist,
    album = album,
    duration = duration,
    uri = Uri.parse(uri),
    albumId = 0L,
    albumArtPath = albumArtPath,
    playCount = playCount,
    favorite = favorite,
    md5 = md5
)
