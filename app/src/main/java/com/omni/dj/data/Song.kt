package com.omni.dj.data

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val modified: Long,
    val size: Long,
    val uri: Uri,
) {
    val artwork: Uri get() = Uri.parse("content://media/external/audio/albumart/$albumId")
    val cacheKey get() = "${id}_${modified}_${size}"
}

enum class LibrarySort { TITLE, ARTIST, RECENT }
enum class LibraryFilter { ALL, FAVORITES, ALBUMS }
