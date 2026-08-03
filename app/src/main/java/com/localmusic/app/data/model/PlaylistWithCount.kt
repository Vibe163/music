package com.localmusic.app.data.model

/** 虚拟"主收藏"歌单的固定 id；其内容 = 整个 songs 表（曲库）。
 *  用 0L 避免与 Room autoGenerate（从 1 开始）的真实歌单 id 冲突。 */
const val FAVORITES_PLAYLIST_ID: Long = 0L

data class PlaylistWithCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val songCount: Int,
    val isBuiltIn: Boolean = false
)
