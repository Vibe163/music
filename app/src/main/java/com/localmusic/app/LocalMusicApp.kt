package com.localmusic.app

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.localmusic.app.data.importer.StaleUriRepairer
import com.localmusic.app.data.local.AppDatabase
import com.localmusic.app.data.repository.MusicRepository
import com.localmusic.app.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LocalMusicApp : Application(), ImageLoaderFactory {

    lateinit var repository: MusicRepository
        private set

    lateinit var playerController: PlayerController
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getInstance(this)
        repository = MusicRepository(
            context = this,
            songDao = database.songDao(),
            playlistDao = database.playlistDao()
        )
        playerController = PlayerController(this)

        // 启动时修复历史失效 URI（旧版本重命名文件导致的脏数据）
        appScope.launch {
            val stats = StaleUriRepairer(this@LocalMusicApp, database.songDao()).repairAll()
            if (stats.repaired > 0 || stats.failed > 0) {
                Log.i("LocalMusicApp", "URI 修复完成：共 ${stats.total} 首，修复 ${stats.repaired} 首，失败 ${stats.failed} 首")
            }
        }
    }

    /**
     * 全局 Coil ImageLoader——针对本地音乐封面场景深度优化：
     *  - 25% 内存缓存 + 100MB 磁盘缓存，避免列表滑动重复解码
     *  - 关闭全局 crossfade：列表快速滑动时多个淡入动画堆叠是主线程卡顿主因，
     *    需要动画的单独场景（如 NowPlayingScreen）在 ImageRequest 里按需开启
     *  - 禁用网络相关功能（respectCacheHeaders），仅加载本地文件
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache {
            MemoryCache.Builder(this).maxSizePercent(0.25).build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(100L * 1024 * 1024)
                .build()
        }
        .components {
            // 视频缩略图解码器（相册/发布流程里视频 content:// URI 的第一帧缩略图）
            add(VideoFrameDecoder.Factory())
        }
        .respectCacheHeaders(false)
        .crossfade(false)
        .build()
}
