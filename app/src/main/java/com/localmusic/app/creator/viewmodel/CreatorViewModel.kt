package com.localmusic.app.creator.viewmodel

import android.app.Application
import android.content.ContentUris
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localmusic.app.LocalMusicApp
import com.localmusic.app.creator.data.model.CreatorWork
import com.localmusic.app.creator.data.storage.CreatorUserProfile
import com.localmusic.app.creator.data.storage.UserProfileStore
import com.localmusic.app.creator.data.storage.WorkStore
import com.localmusic.app.data.model.FAVORITES_PLAYLIST_ID
import com.localmusic.app.data.model.PlaylistWithCount
import com.localmusic.app.data.model.Song
import com.localmusic.app.data.model.toSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.random.Random

/**
 * 相册里的一条媒体（图/视频）。
 * @param contentUri 用 ContentUris.withAppendedId 拼出的 MediaStore content URI
 * @param isVideo 是否是视频（用于Step1 Tab过滤）
 * @param dateAdded 用于按时间倒序
 */
data class AlbumMediaItem(
    val id: Long,
    val contentUri: Uri,
    val isVideo: Boolean,
    val dateAdded: Long
)

/**
 * 创作者训练空间统一 ViewModel
 *
 * 隔离边界：仅通过 LocalMusicApp.repository 只读调用
 * 不引用 MusicPlayerService、PlayerController、SongDao 等音乐模块内部实现
 */
class CreatorViewModel(app: Application) : AndroidViewModel(app) {

    private companion object {
        const val TAG = "CreatorViewModel"
        // 只在旧SAF临时URI迁移期间为true，迁移完成后保持false（否则每次进创作者空间/冷启动作品被清）
        const val FORCE_CLEAR_WORKS_ON_INIT = false
    }

    private val workStore = WorkStore(app)
    private val userProfileStore = UserProfileStore(app)
    private val repository = (app as LocalMusicApp).repository

    init {
        if (FORCE_CLEAR_WORKS_ON_INIT) {
            clearAllWorks()
        }
    }

    /** 所有作品，按创建时间倒序 */
    val works: StateFlow<List<CreatorWork>> = workStore.works

    /** 创作者用户资料（在「我的」页面设置） */
    val userProfile: StateFlow<com.localmusic.app.creator.data.storage.CreatorUserProfile> = userProfileStore.profile

    /** 本地音乐库（主收藏全部歌曲，BGM 备选源）——只读引用 */
    val bgmLibrary: StateFlow<List<Song>> =
        repository.observeSongs().map { entities ->
            entities.map { it.toSong() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 所有歌单（主收藏虚拟歌单 + 用户歌单），BGM 选择时先选歌单再选歌 */
    val playlists: StateFlow<List<PlaylistWithCount>> =
        combine(repository.observePlaylists(), repository.observeSongs()) { userPlaylists, songs ->
            // 主收藏是虚拟歌单（id=0L），不在 playlists 表里，需手动拼到列表最前
            val favorites = PlaylistWithCount(
                id = FAVORITES_PLAYLIST_ID,
                name = "主收藏",
                createdAt = 0L,
                songCount = songs.size,
                isBuiltIn = true
            )
            listOf(favorites) + userPlaylists
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentWorkIndex = MutableStateFlow(0)
    val currentWorkIndex: StateFlow<Int> = _currentWorkIndex.asStateFlow()

    // ========================================================================
    // 发布流程草稿状态（Step1→Step3→Step4 之间传递的中间数据）
    // ========================================================================

    /** 草稿：Step1 选中的相册媒体（顺序=用户点击顺序） */
    private val _draftSelection = MutableStateFlow<List<AlbumMediaItem>>(emptyList())
    val draftSelection: StateFlow<List<AlbumMediaItem>> = _draftSelection.asStateFlow()

    /** 草稿：Step3 选中的 BGM */
    private val _draftBgm = MutableStateFlow<Song?>(null)
    val draftBgm: StateFlow<Song?> = _draftBgm.asStateFlow()

    /** 草稿：Step4 作品标题 */
    private val _draftTitle = MutableStateFlow("")
    val draftTitle: StateFlow<String> = _draftTitle.asStateFlow()

    /** 草稿：Step4 作品描述 */
    private val _draftCaption = MutableStateFlow("")
    val draftCaption: StateFlow<String> = _draftCaption.asStateFlow()

    /** 草稿：Step4 话题标签（列表） */
    private val _draftTags = MutableStateFlow<List<String>>(emptyList())
    val draftTags: StateFlow<List<String>> = _draftTags.asStateFlow()

    /** 草稿：Step4 地点文本 */
    private val _draftLocation = MutableStateFlow<String?>(null)
    val draftLocation: StateFlow<String?> = _draftLocation.asStateFlow()

    /** 发布中状态（下沉到VM，避免Composable重组丢失导致重复发布） */
    private val _isPublishing = MutableStateFlow(false)
    val isPublishing: StateFlow<Boolean> = _isPublishing.asStateFlow()

    fun setDraftSelection(items: List<AlbumMediaItem>) { _draftSelection.value = items }
    fun setDraftBgm(song: Song?) { _draftBgm.value = song }
    fun setDraftTitle(v: String) { _draftTitle.value = v }
    fun setDraftCaption(v: String) { _draftCaption.value = v }
    fun setDraftTags(v: List<String>) { _draftTags.value = v }
    fun setDraftLocation(v: String?) { _draftLocation.value = v }

    /** 每次重新进入发布页时重置草稿 */
    fun resetDraft() {
        _draftSelection.value = emptyList()
        _draftBgm.value = null
        _draftTitle.value = ""
        _draftCaption.value = ""
        _draftTags.value = emptyList()
        _draftLocation.value = null
        _isPublishing.value = false
    }

    /**
     * 查询相册：返回 ALL / IMAGE / VIDEO / GIF（动图）四种类型。
     * 按 dateAdded 倒序。
     */
    suspend fun queryAlbum(filter: AlbumFilter = AlbumFilter.ALL): List<AlbumMediaItem> =
        withContext(Dispatchers.IO) {
            val ctx = getApplication<LocalMusicApp>()
            val out = mutableListOf<AlbumMediaItem>()
            val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

            // —— 图片 ——
            if (filter == AlbumFilter.ALL || filter == AlbumFilter.IMAGE || filter == AlbumFilter.GIF) {
                val imageUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val proj = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.MIME_TYPE
                )
                runCatching {
                    ctx.contentResolver.query(imageUri, proj, null, null, sortOrder)?.use { c ->
                        val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                        val mimeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                        while (c.moveToNext()) {
                            val id = c.getLong(idCol)
                            val date = c.getLong(dateCol)
                            val mime = c.getString(mimeCol) ?: ""
                            val isGif = mime.contains("gif", true)
                            val isImage = !isGif
                            val uri = ContentUris.withAppendedId(imageUri, id)
                            val accept = when (filter) {
                                AlbumFilter.ALL -> true
                                AlbumFilter.IMAGE -> isImage
                                AlbumFilter.GIF -> isGif
                                else -> false
                            }
                            if (accept) out += AlbumMediaItem(id, uri, isVideo = false, dateAdded = date)
                        }
                    }
                }
            }
            // —— 视频 ——
            if (filter == AlbumFilter.ALL || filter == AlbumFilter.VIDEO) {
                val videoUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                val proj = arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DATE_ADDED
                )
                runCatching {
                    ctx.contentResolver.query(videoUri, proj, null, null, sortOrder)?.use { c ->
                        val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                        val dateCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                        while (c.moveToNext()) {
                            val id = c.getLong(idCol)
                            val date = c.getLong(dateCol)
                            val uri = ContentUris.withAppendedId(videoUri, id)
                            out += AlbumMediaItem(id, uri, isVideo = true, dateAdded = date)
                        }
                    }
                }
            }
            // 再按 dateAdded 倒序一次（图片和视频混合时）
            out.sortByDescending { it.dateAdded }
            Log.i(TAG, "queryAlbum($filter) 返回 ${out.size} 项")
            out
        }

    /**
     * 用草稿数据发布最终作品：
     * 1) 把 Step1 选中的相册媒体复制到 App 私有目录
     * 2) 构造 CreatorWork 并写入
     * 返回是否成功
     */
    suspend fun publishFromDraft(onProgress: (msg: String) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        // 防重复发布
        if (_isPublishing.value) return@withContext false
        _isPublishing.value = true

        try {
            val sel = _draftSelection.value
            if (sel.isEmpty()) return@withContext false
            val profile = userProfileStore.profile.value
            if (!userProfileStore.isComplete()) return@withContext false

            val workId = UUID.randomUUID().toString()
            onProgress("复制媒体文件...")
            val copiedUris = mutableListOf<String>()
            for ((index, item) in sel.withIndex()) {
                val copied = copyMediaToInternal(workId, item.contentUri, index)
                if (copied != null) copiedUris.add(copied)
            }
            if (copiedUris.isEmpty()) {
                // 失败时清理已复制的孤儿文件
                cleanupWorkFiles(workId)
                return@withContext false
            }

            onProgress("保存作品...")
            val mediaType = if (sel.any { it.isVideo })
                com.localmusic.app.creator.data.model.MediaType.VIDEO
            else com.localmusic.app.creator.data.model.MediaType.IMAGE
            val bgm = _draftBgm.value
            val work = CreatorWork(
                id = workId,
                authorId = profile.userId,  // 用稳定 userId，改名不影响旧作品
                authorName = profile.nickname,
                authorAvatarUri = profile.avatarUri,
                authorBio = profile.bio,
                mediaList = copiedUris,
                mediaType = mediaType,
                title = _draftTitle.value.trim(),
                caption = _draftCaption.value.trim(),
                tags = _draftTags.value,
                bgmSongId = bgm?.id,
                bgmTitle = bgm?.title ?: "",
                likeCount = Random.nextInt(100, 9999),
                commentCount = Random.nextInt(10, 999),
                collectCount = Random.nextInt(10, 999),
                shareCount = Random.nextInt(10, 999),
                fanCount = Random.nextInt(100, 99999),
                totalLikeCount = Random.nextInt(1000, 99999),
                createdAt = System.currentTimeMillis(),
                userLiked = false,
                userCollected = false
            )
            addWork(work)
            resetDraft()
            true
        } finally {
            _isPublishing.value = false
        }
    }

    /** 清理指定 workId 的媒体文件（发布失败时用） */
    private fun cleanupWorkFiles(workId: String) {
        runCatching {
            val appContext = getApplication<LocalMusicApp>()
            val workDir = File(appContext.filesDir, "works/$workId")
            if (workDir.exists()) workDir.deleteRecursively()
        }
    }

    /** 更新创作者资料，并同步头像/昵称到该用户所有已发布作品（浏览页立即生效） */
    fun updateUserProfile(avatarUri: String?, nickname: String, bio: String) {
        val cleanNickname = nickname.trim()
        val current = userProfileStore.profile.value
        Log.i(TAG, "updateUserProfile: userId=${current.userId}, nickname=$cleanNickname, avatar=$avatarUri")
        userProfileStore.update(
            com.localmusic.app.creator.data.storage.CreatorUserProfile(
                avatarUri = avatarUri,
                nickname = cleanNickname,
                bio = bio.trim()
            )
        )
        // 同步作者资料到所有作品（authorId 匹配），浏览页互动栏/作者主页/拍同款头像全部一致
        workStore.updateAuthorInfo(current.userId, cleanNickname, avatarUri)
    }

    /**
     * 将 SAF 返回的头像 URI 复制到 App 私有目录，返回稳定的 file:// URI 字符串。
     *
     * 必要性：OpenDocument 返回的 content:// URI 是临时授权，进程重启后权限失效，
     * 导致头像读不出来。复制到 filesDir/avatars/ 后完全自主可控，重启后仍可读。
     *
     * @return 复制成功返回 file:// URI 字符串；失败返回 null
     */
    suspend fun copyAvatarToInternal(sourceUri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val appContext = getApplication<LocalMusicApp>()
            val avatarDir = File(appContext.filesDir, "avatars").apply { mkdirs() }
            // 用时间戳命名，避免覆盖
            val ext = when (appContext.contentResolver.getType(sourceUri)) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val destFile = File(avatarDir, "avatar_${System.currentTimeMillis()}.$ext")
            appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@runCatching null
            Log.i(TAG, "copyAvatarToInternal: ${sourceUri} -> ${destFile.absolutePath}")
            Uri.fromFile(destFile).toString()
        }.onFailure {
            Log.e(TAG, "copyAvatarToInternal failed", it)
        }.getOrNull()
    }

    /** 资料是否已配置完整 */
    fun isProfileComplete(): Boolean = userProfileStore.isComplete()

    /**
     * 保存裁剪后的头像 Bitmap 到 App 私有目录，返回稳定的 file:// URI 字符串。
     * 裁剪结果统一为 300×300 JPEG（与列表缩略图尺寸一致，解码快）。
     */
    suspend fun saveCroppedAvatar(avatar: Bitmap): String? = withContext(Dispatchers.IO) {
        runCatching {
            val appContext = getApplication<LocalMusicApp>()
            val avatarDir = File(appContext.filesDir, "avatars").apply { mkdirs() }
            val destFile = File(avatarDir, "avatar_${System.currentTimeMillis()}.jpg")
            destFile.outputStream().use { fos ->
                avatar.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }
            Log.i(TAG, "saveCroppedAvatar: -> ${destFile.absolutePath}")
            Uri.fromFile(destFile).toString()
        }.onFailure {
            Log.e(TAG, "saveCroppedAvatar failed", it)
        }.getOrNull()
    }

    /**
     * 将 SAF 返回的媒体 URI（图片/视频）复制到 App 私有目录，返回稳定的 file:// URI。
     *
     * 必要性：OpenDocument 返回的 content:// URI 是临时授权，进程重启后权限失效，
     * 导致图片/视频读不出来（中间全黑。复制到 filesDir/works/<workId>/ 完全自主可控，重启后仍可读。
     *
     * @param workId 作品ID（用于子目录隔离不同作品的文件）
     * @param sourceUri 原始 SAF content:// URI
     * @param index 第几个媒体（多图时按顺序命名）
     * @return 复制成功返回 "file:///..." 字符串，失败返回 null
     */
    suspend fun copyMediaToInternal(
        workId: String,
        sourceUri: Uri,
        index: Int
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val appContext = getApplication<LocalMusicApp>()
            val workDir = File(File(appContext.filesDir, "works"), workId).apply { mkdirs() }
            // 从 MIME 类型推断扩展名（兜底 jpg/mp4）
            val mimeType = appContext.contentResolver.getType(sourceUri)
            val ext = when {
                mimeType?.startsWith("video/") == true -> mimeType.substringAfterLast("/", "mp4")
                mimeType == "image/png" -> "png"
                mimeType == "image/webp" -> "webp"
                mimeType == "image/gif" -> "gif"
                else -> "jpg"
            }
            val destFile = File(workDir, "media_${index}.${ext}")
            appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@runCatching null
            Log.i(TAG, "copyMediaToInternal: work=$workId idx=$index ${sourceUri} -> ${destFile.absolutePath}")
            Uri.fromFile(destFile).toString()
        }.onFailure {
            Log.e(TAG, "copyMediaToInternal failed (work=$workId idx=$index)", it)
        }.getOrNull()
    }

    /** 新增作品 */
    fun addWork(work: CreatorWork) {
        workStore.add(work)
    }

    /** 清空所有作品，并删除私有目录里的媒体文件 */
    fun clearAllWorks() {
        viewModelScope.launch(Dispatchers.IO) {
            // 先删文件再删记录，避免孤儿文件
            runCatching {
                val appContext = getApplication<LocalMusicApp>()
                val worksDir = File(appContext.filesDir, "works")
                if (worksDir.deleteRecursively()) {
                    Log.i(TAG, "已清空作品媒体目录: ${worksDir.absolutePath}")
                }
            }
            workStore.clearAll()
        }
    }

    /** 更新作品（修改变量） */
    fun updateWork(work: CreatorWork) {
        workStore.update(work)
    }

    /** 删除作品记录及其私有目录媒体文件 */
    fun deleteWork(workId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            cleanupWorkFiles(workId)
            workStore.delete(workId)
        }
    }

    /** 按 songId 查询歌曲（更改配音时预选中当前BGM），协程挂起查数据库 */
    suspend fun findSongById(songId: Long): com.localmusic.app.data.model.Song? = withContext(Dispatchers.IO) {
        repository.getSongById(songId)
    }

    /** 复制作品为新作品 */
    fun duplicateWork(workId: String) {
        workStore.duplicate(workId)
    }

    /** 设置当前浏览位置 */
    fun setCurrentIndex(index: Int) {
        _currentWorkIndex.value = index
    }

    /** 根据 bgmSongId 实时查询最新歌曲数据（处理改名/删除情况） */
    suspend fun getBgmSong(songId: Long?): Song? {
        if (songId == null) return null
        return repository.getSongById(songId)
    }

    /** 获取指定歌单的歌曲列表（BGM 选择时用）
     *  主收藏（id=0L）是虚拟歌单，不在 playlist_songs 表里，需走 observeSongs 全量查询
     */
    suspend fun getPlaylistSongs(playlistId: Long): List<Song> =
        if (playlistId == FAVORITES_PLAYLIST_ID) {
            repository.getAllSongs()
        } else {
            repository.getPlaylistSongs(playlistId)
        }
}

enum class AlbumFilter { ALL, VIDEO, IMAGE, GIF }
