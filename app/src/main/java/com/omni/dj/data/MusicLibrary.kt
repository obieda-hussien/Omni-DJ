package com.omni.dj.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class LibraryState(val songs: List<Song> = emptyList(), val loading: Boolean = false, val permission: Boolean = false, val failed: Boolean = false)

class MusicLibrary(private val context: Context) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(LibraryState())
    val state = _state.asStateFlow()
    companion object {
        val permission: String get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    }
    fun hasPermission() = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    suspend fun refresh() = mutex.withLock {
        if (!hasPermission()) { _state.value = LibraryState(); return@withLock }
        _state.value = _state.value.copy(loading = true, permission = true, failed = false)
        try {
            val songs = withContext(Dispatchers.IO) {
                val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATE_MODIFIED, MediaStore.Audio.Media.SIZE)
                buildList {
                    context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection,
                        "${MediaStore.Audio.Media.DURATION} > 0", null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
                        while (c.moveToNext()) {
                            val id = c.getLong(0)
                            add(Song(id, c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getString(3).orEmpty(),
                                c.getLong(4), c.getLong(5), c.getLong(6), c.getLong(7),
                                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)))
                        }
                    }
                }
            }
            _state.value = LibraryState(songs, permission = true)
        } catch (_: SecurityException) { _state.value = LibraryState() }
        catch (_: Exception) { _state.value = _state.value.copy(loading = false, failed = true) }
    }
}
