package com.localmusic.app.data.model

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri,
    val albumId: Long = 0,
    val albumArtPath: String? = null,
    val playCount: Int = 0,
    val favorite: Boolean = false,
    val md5: String? = null
)
