package com.localmusic.app.util

import android.content.Context
import android.net.Uri
import java.security.MessageDigest

/**
 * 文件哈希值计算工具类。
 * 目前提供 MD5 计算，用于跨设备迁移时基于文件内容精确匹配。
 */
object FileHashUtils {

    /** 读取 URI 指向的文件内容并计算 MD5。 */
    fun computeMd5(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val digest = MessageDigest.getInstance("MD5")
                val buffer = ByteArray(8192) // 8KB buffer，平衡内存和速度
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
                digest.digest().toHexString()
            }
        }.getOrNull()
    }

    /** ByteArray → 十六进制字符串。 */
    private fun ByteArray.toHexString(): String {
        val hex = StringBuilder(size * 2)
        for (b in this) {
            hex.append(String.format("%02x", b))
        }
        return hex.toString()
    }
}
