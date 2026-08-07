package com.localmusic.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 音频指纹缓存表：记录每首歌最后成功计算出的 chromaprint 指纹，
 * 避免重复扫描时对整首歌曲重复解码。 freshness 复用上一个摘要说明
 * （uri + lastModified + size 三者一致则缓存有效）。
 */
@Entity(tableName = "fingerprint_cache")
data class FingerprintCacheEntity(
    @PrimaryKey val songId: Long,
    val uri: String,
    val lastModified: Long,
    val size: Long,
    /** chromaprint 指纹 BLOB（每 4 字节一个 32 位子指纹，小端序）。 */
    val fingerprint: ByteArray,
    val updatedAt: Long = System.currentTimeMillis()
)