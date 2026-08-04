package com.localmusic.app.creator.data.storage

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 创作者用户资料（在「我的」页面统一设置，发布作品时自动引用）
 *
 * 物理隔离：使用 SharedPreferences，与 Room 数据库完全隔离
 */
@Immutable
data class CreatorUserProfile(
    val avatarUri: String? = null,
    val nickname: String = "",
    val bio: String = "",
    /** 固定用户ID（首次生成后持久化，改名不影响旧作品归属） */
    val userId: String = ""
)

/**
 * 创作者用户资料持久化存储
 */
class UserProfileStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _profile = MutableStateFlow(loadFromDisk())
    val profile: StateFlow<CreatorUserProfile> = _profile.asStateFlow()

    private fun loadFromDisk(): CreatorUserProfile {
        val savedAvatar = prefs.getString(KEY_AVATAR, null)
        val validAvatar = savedAvatar?.takeIf { it.startsWith("file://") || it.startsWith("/") }
        // 首次加载时生成固定 userId 并持久化，后续改名不影响旧作品归属
        val userId = prefs.getString(KEY_USER_ID, null) ?: run {
            val newId = "user_${java.util.UUID.randomUUID()}"
            prefs.edit().putString(KEY_USER_ID, newId).apply()
            newId
        }
        return CreatorUserProfile(
            avatarUri = validAvatar,
            nickname = prefs.getString(KEY_NICKNAME, "") ?: "",
            bio = prefs.getString(KEY_BIO, "") ?: "",
            userId = userId
        )
    }

    /** 更新资料并持久化（userId 不随 nickname 变化） */
    fun update(profile: CreatorUserProfile) {
        val stableUserId = _profile.value.userId.ifBlank {
            "user_${java.util.UUID.randomUUID()}"
        }
        val updated = profile.copy(userId = stableUserId)
        _profile.value = updated
        prefs.edit()
            .putString(KEY_AVATAR, updated.avatarUri)
            .putString(KEY_NICKNAME, updated.nickname)
            .putString(KEY_BIO, updated.bio)
            .putString(KEY_USER_ID, updated.userId)
            .apply()
    }

    /** 资料是否已配置完整（头像和昵称都不为空） */
    fun isComplete(): Boolean =
        _profile.value.avatarUri != null && _profile.value.nickname.isNotBlank()

    private companion object {
        const val PREFS_NAME = "creator_user_profile"
        const val KEY_AVATAR = "avatar_uri"
        const val KEY_NICKNAME = "nickname"
        const val KEY_BIO = "bio"
        const val KEY_USER_ID = "user_id"
    }
}
