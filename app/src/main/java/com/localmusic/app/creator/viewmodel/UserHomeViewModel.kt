package com.localmusic.app.creator.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.localmusic.app.creator.data.model.CreatorWork
import com.localmusic.app.creator.data.model.MediaType
import com.localmusic.app.creator.data.storage.WorkStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 用户主页 ViewModel —— 纯本地数据逻辑
 *
 * 职责边界（只做本地，不写任何网络请求/远程拉取）：
 *  - 本地数据筛选：从本地作品存储（SharedPreferences+JSON，纯离线）筛出该 userId 的作品
 *  - 本地路径整理：作品内的媒体路径即本地 file:// URI，直接供 Coil/VideoFrameDecoder 加载
 *  - 列表缓存：stateIn 缓存结果，滑动不重复扫描
 *
 * 协程仅用于本地 Flow 合并/映射，不访问网络。
 */
class UserHomeViewModel(
    app: Application,
    private val userId: String
) : AndroidViewModel(app) {

    private val workStore = WorkStore(app)

    /** 该用户所有本地作品（按创建时间倒序） */
    val works: StateFlow<List<CreatorWork>> = workStore.works
        .map { list ->
            list.filter { it.authorId == userId }.sortedByDescending { it.createdAt }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    fun selectTab(index: Int) {
        if (_selectedTab.value != index) _selectedTab.value = index
    }

    /**
     * 当前 Tab 展示的本地媒体列表：
     *  - 作品：全部本地作品
     *  - 日常：图片类作品
     *  - 收藏：本地标记收藏的作品
     */
    val visibleWorks: StateFlow<List<CreatorWork>> =
        combine(works, _selectedTab) { all, tab ->
            when (tab) {
                1 -> all.filter { it.mediaType == MediaType.IMAGE }
                2 -> all.filter { it.userCollected }
                else -> all
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 背景横幅本地媒体：第一个作品的本地封面（无则 null，UI 回退渐变） */
    val headerMedia: StateFlow<String?> = works.map { list ->
        list.firstNotNullOfOrNull { it.mediaList.firstOrNull() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 本地聚合统计：获赞数 = 该用户所有作品点赞之和（纯本地计算） */
    val likeCount: StateFlow<Int> = works.map { list -> list.sumOf { it.likeCount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    companion object {
        fun factory(userId: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        ?: error("Application not available")
                    UserHomeViewModel(app, userId)
                }
            }
    }
}
