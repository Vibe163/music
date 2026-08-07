package com.localmusic.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 导入日志：每次导入完成后记录一条，包含重复（跳过）和失败的歌曲名。
 * 名字以换行符分隔存储在 [duplicateNames] / [failedNames] 中。
 */
@Entity(tableName = "import_logs")
data class ImportLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val addedCount: Int,
    val duplicateNames: String = "",
    val failedNames: String = ""
)
