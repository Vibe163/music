package com.localmusic.app.creator.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * 创作者训练空间的作品类型
 */
@Serializable
enum class MediaType { IMAGE, VIDEO }

/**
 * 创作者训练空间的模拟作品数据模型
 *
 * 设计理念：作品 = 框架 + 变量
 * 框架是抖音信息流的固定样式，变量（头像/昵称/文案/标签/BGM/媒体）由用户随时修改
 * 改变量 = 改作品，无需重新发布
 */
@Immutable
@Serializable
data class CreatorWork(
    val id: String,
    val authorId: String,
    val authorName: String,
    val authorAvatarUri: String? = null,
    val authorBio: String = "",
    val mediaList: List<String>,
    val mediaType: MediaType,
    val title: String = "",
    val caption: String,
    val tags: List<String> = emptyList(),
    val bgmSongId: Long? = null,
    val bgmTitle: String = "",
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val collectCount: Int = 0,
    val shareCount: Int = 0,
    val fanCount: Int = 0,
    val totalLikeCount: Int = 0,
    val createdAt: Long,
    val userLiked: Boolean = false,
    val userCollected: Boolean = false
)
