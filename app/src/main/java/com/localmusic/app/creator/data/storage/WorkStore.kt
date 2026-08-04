package com.localmusic.app.creator.data.storage

import android.content.Context
import android.util.Log
import com.localmusic.app.creator.data.model.CreatorWork
import com.localmusic.app.creator.data.model.MediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * 创作者作品的本地持久化存储
 *
 * 物理隔离：使用 SharedPreferences + JSON，与 Room 数据库（local_music.db）完全隔离
 * 不引用任何 music 模块的 DAO 或 Entity，仅通过 music 模块的 Repository 只读查询 BGM 元数据
 */
class WorkStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _works = MutableStateFlow<List<CreatorWork>>(emptyList())
    val works: StateFlow<List<CreatorWork>> = _works.asStateFlow()

    init {
        loadFromDisk()
        // 只在首次安装时 seed 示例作品，用户删空后不再复活
        if (_works.value.isEmpty() && !prefs.getBoolean(KEY_SEEDED, false)) {
            seedSampleWorks()
        }
    }

    /** 从磁盘加载作品列表 */
    private fun loadFromDisk() {
        runCatching {
            val raw = prefs.getString(KEY_WORKS, null) ?: return
            val list = json.decodeFromString<List<CreatorWork>>(raw)
            _works.value = list.sortedByDescending { it.createdAt }
        }.onFailure {
            Log.e(TAG, "加载作品失败", it)
        }
    }

    /** 持久化到磁盘（用 commit 同步写入，避免 App 被杀时丢数据） */
    private fun persist() {
        runCatching {
            val raw = json.encodeToString(_works.value)
            prefs.edit().putString(KEY_WORKS, raw).commit()
        }.onFailure {
            Log.e(TAG, "保存作品失败", it)
        }
    }

    /** 新增作品 */
    fun add(work: CreatorWork) {
        _works.value = (_works.value + work).sortedByDescending { it.createdAt }
        persist()
    }

    /** 更新作品（修改变量） */
    fun update(work: CreatorWork) {
        _works.value = _works.value.map { if (it.id == work.id) work else it }
        persist()
    }

    /** 同步作者资料到其所有作品（修改头像/昵称后，浏览页所有场景立即生效） */
    fun updateAuthorInfo(authorId: String, newName: String, newAvatarUri: String?) {
        var matched = 0
        _works.value = _works.value.map {
            if (it.authorId == authorId) {
                matched++
                it.copy(authorName = newName, authorAvatarUri = newAvatarUri)
            } else it
        }
        Log.i(TAG, "updateAuthorInfo: authorId=$authorId, matched=$matched, total=${_works.value.size}, newName=$newName")
        persist()
    }

    /** 删除作品 */
    fun delete(workId: String) {
        _works.value = _works.value.filter { it.id != workId }
        persist()
    }

    /** 复制作品为新作品（用于「复制为新作品」操作） */
    fun duplicate(workId: String): CreatorWork? {
        val src = _works.value.firstOrNull { it.id == workId } ?: return null
        val newWork = src.copy(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            userLiked = false,
            userCollected = false
        )
        add(newWork)
        return newWork
    }

    /** 清空所有作品记录 */
    fun clearAll() {
        _works.value = emptyList()
        persist()
    }

    /** 获取指定作者的所有作品 */
    fun getByAuthor(authorId: String): List<CreatorWork> =
        _works.value.filter { it.authorId == authorId }

    /** 获取单个作品 */
    fun getById(workId: String): CreatorWork? =
        _works.value.firstOrNull { it.id == workId }

    /** 首次安装时预置示例作品，让用户立刻看到效果 */
    private fun seedSampleWorks() {
        val now = System.currentTimeMillis()
        val samples = listOf(
            CreatorWork(
                id = UUID.randomUUID().toString(),
                authorId = "sample_author_1",
                authorName = "示例创作者",
                authorBio = "这是示例作品，点击各元素可编辑\n长按可删除或复制为新作品",
                mediaList = emptyList(),
                mediaType = MediaType.IMAGE,
                caption = "欢迎来到创作者训练空间\n点击右下角 + 发布你的第一个作品\n点击下方任意文字或头像可以修改",
                tags = listOf("示例", "训练空间"),
                bgmSongId = null,
                bgmTitle = "未选择 BGM",
                likeCount = 1288,
                commentCount = 66,
                collectCount = 233,
                shareCount = 99,
                fanCount = 5800,
                totalLikeCount = 12800,
                createdAt = now
            )
        )
        _works.value = samples
        persist()
        prefs.edit().putBoolean(KEY_SEEDED, true).commit()
    }

    private companion object {
        const val TAG = "WorkStore"
        const val PREFS_NAME = "creator_works"
        const val KEY_WORKS = "works_json"
        const val KEY_SEEDED = "seeded"
    }
}
