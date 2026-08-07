package com.localmusic.app.creator.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.localmusic.app.LocalMusicApp
import com.localmusic.app.creator.data.model.CreatorWork
import com.localmusic.app.creator.data.model.MediaType
import com.localmusic.app.creator.data.storage.UserProfileStore
import com.localmusic.app.creator.data.storage.WorkStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 创作者个人主页 ViewModel —— 抖音主页的完整本地逻辑
 *
 * 职责（纯本地，无网络）：
 *  - 作品筛选：按 authorId 过滤，按创建时间倒序
 *  - Tab 切换：作品(全部) / 喜欢(userLiked)
 *  - 本地搜索：按标题/文案/标签过滤当前作者作品
 *  - 关注状态：按 authorId 持久化到 SharedPreferences（重启保留）
 *  - 自己识别：authorId == 当前登录用户 userId 时为「我的主页」
 *  - 统计聚合：获赞/粉丝取自作品字段，关注数按 authorId 稳定派生
 *
 * 与 CreatorViewModel 共享同一份 WorkStore/UserProfileStore 单例，
 * 编辑资料、点赞等变更实时同步到主页。
 */
class CreatorProfileViewModel(
    app: Application,
    private val authorId: String
) : AndroidViewModel(app) {

    private val application = app as LocalMusicApp
    private val workStore: WorkStore = application.workStore
    private val userProfileStore: UserProfileStore = application.userProfileStore

    /** 全部作品（用于作品点击时定位全局索引，跳转浏览页） */
    val allWorks: StateFlow<List<CreatorWork>> = workStore.works

    /** 该作者所有作品（按创建时间倒序） */
    val authorWorks: StateFlow<List<CreatorWork>> = workStore.works
        .map { list -> list.filter { it.authorId == authorId }.sortedByDescending { it.createdAt } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 作者资料（取首条作品；改名后通过共享 store 实时刷新） */
    val authorInfo: StateFlow<CreatorWork?> = authorWorks
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 当前登录用户资料（用于判断是否为自己） */
    val currentUser: StateFlow<com.localmusic.app.creator.data.storage.CreatorUserProfile> =
        userProfileStore.profile

    /** 是否为自己的主页（自己主页显示「编辑资料」而非「关注」） */
    val isSelf: StateFlow<Boolean> = currentUser
        .map { it.userId == authorId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 背景横幅媒体：第一个作品的封面（无则 null，UI 回退渐变） */
    val headerMedia: StateFlow<String?> = authorWorks
        .map { list -> list.firstNotNullOfOrNull { it.mediaList.firstOrNull() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ========================================================================
    // Tab 切换
    // ========================================================================
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    /** 当前 Tab 展示的作品：作品(全部) / 喜欢(userLiked) */
    val visibleWorks: StateFlow<List<CreatorWork>> =
        combine(authorWorks, _selectedTab) { all, tab ->
            when (tab) {
                1 -> all.filter { it.userLiked }
                else -> all
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 喜欢数（「喜欢」Tab 标签用） */
    val likedCount: StateFlow<Int> = authorWorks
        .map { list -> list.count { it.userLiked } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun selectTab(index: Int) {
        if (_selectedTab.value != index) _selectedTab.value = index
    }

    // ========================================================================
    // 本地搜索（按标题/文案/标签过滤当前作者作品）
    // ========================================================================
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val isSearching: StateFlow<Boolean> = _searchQuery
        .map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 搜索结果（搜索激活时替代 visibleWorks 展示） */
    val searchedWorks: StateFlow<List<CreatorWork>> =
        combine(authorWorks, _searchQuery) { all, q ->
            if (q.isBlank()) all
            else all.filter {
                it.title.contains(q, true) ||
                    it.caption.contains(q, true) ||
                    it.tags.any { tag -> tag.contains(q, true) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun clearSearch() { _searchQuery.value = "" }

    // ========================================================================
    // 关注状态（按 authorId 持久化）
    // ========================================================================
    private val followPrefs = getApplication<Application>()
        .getSharedPreferences("creator_follows", Context.MODE_PRIVATE)

    private val _isFollowing = MutableStateFlow(followPrefs.getBoolean("follow_$authorId", false))
    val isFollowing: StateFlow<Boolean> = _isFollowing.asStateFlow()

    /** 切换关注/取关，并持久化（重启后保留） */
    fun toggleFollow() {
        val newState = !_isFollowing.value
        _isFollowing.value = newState
        followPrefs.edit().putBoolean("follow_$authorId", newState).apply()
    }

    // ========================================================================
    // 统计聚合（取自作品字段，纯本地计算）
    // ========================================================================

    /** 获赞总数 = 该作者所有作品获赞之和 */
    val totalLikeCount: StateFlow<Int> = authorWorks
        .map { list -> list.sumOf { it.likeCount + (if (it.userLiked) 1 else 0) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** 粉丝数（取自作品 fanCount 字段；无作品时为 0） */
    val fanCount: StateFlow<Int> = authorWorks
        .map { list -> list.firstOrNull()?.fanCount ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * 关注数（该作者关注了多少人）。
     * 作品模型未存储此字段，按 authorId 稳定哈希派生一个固定值，
     * 保证同一作者每次进入主页数字一致（模拟数据）。
     */
    val followingCount: Int = run {
        val hash = authorId.hashCode()
        ((if (hash < 0) -hash else hash) % 480) + 20
    }

    /** 作品总数 */
    val workCount: StateFlow<Int> = authorWorks
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** 定位作品在全局列表中的索引（点击作品跳转浏览页用） */
    fun getGlobalIndex(workId: String): Int =
        allWorks.value.indexOfFirst { it.id == workId }

    companion object {
        fun factory(authorId: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        ?: error("Application not available")
                    CreatorProfileViewModel(app, authorId)
                }
            }
    }
}
