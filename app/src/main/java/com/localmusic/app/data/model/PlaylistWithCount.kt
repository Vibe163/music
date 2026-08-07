package com.localmusic.app.data.model

/** 虚拟"曲库"歌单的固定 id；其内容 = 整个 songs 表（全部导入音乐）。 */
const val ALL_SONGS_PLAYLIST_ID: Long = -1L

/** 虚拟"最近播放"歌单的固定 id；其内容 = 播放完成过的歌曲（按时间倒序，上限 50 首）。 */
const val RECENTLY_PLAYED_PLAYLIST_ID: Long = -2L

/** 虚拟"主收藏"歌单的固定 id；其内容 = 红心收藏（favorite = true）的歌曲。
 *  用 0L 避免与 Room autoGenerate（从 1 开始）的真实歌单 id 冲突。 */
const val FAVORITES_PLAYLIST_ID: Long = 0L

data class PlaylistWithCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val songCount: Int,
    val isBuiltIn: Boolean = false
)
