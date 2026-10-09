package com.omni.dj.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.omni.dj.DjApplication
import com.omni.dj.core.MixSettings
import com.omni.dj.data.*
import com.omni.dj.playback.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@UnstableApi
class DjViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DjApplication
    val library = app.library.state
    val settings = app.preferences.settings
    val favorites = app.preferences.favorites
    val analyses = app.analysis.analyses
    val analysisProgress = app.analysis.progress
    val search = MutableStateFlow("")
    val filter = MutableStateFlow(LibraryFilter.ALL)
    val sort = MutableStateFlow(LibrarySort.TITLE)
    private val _playback = MutableStateFlow(PlaybackState())
    val playback = _playback.asStateFlow()
    var engine: MixEngine? = null
        private set
    private var engineJob: Job? = null
    private var pendingSong: Song? = null
    val visibleSongs = combine(library, search, filter, sort, favorites) { state, query, tab, order, stars ->
        val list = state.songs.filter { song ->
            (tab != LibraryFilter.FAVORITES || song.id in stars) &&
                (query.isBlank() || song.title.contains(query, true) || song.artist.contains(query, true) || song.album.contains(query, true))
        }
        when (order) {
            LibrarySort.TITLE -> list.sortedBy { it.title.lowercase() }
            LibrarySort.ARTIST -> list.sortedWith(compareBy<Song> { it.artist.lowercase() }.thenBy { it.title.lowercase() })
            LibrarySort.RECENT -> list.sortedByDescending { it.modified }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    init { refresh() }
    fun refresh(forceAnalysis: Boolean = false) = viewModelScope.launch {
        app.library.refresh()
        if (app.library.state.value.permission) app.analysis.analyze(app.library.state.value.songs, forceAnalysis)
        else { app.analysis.cancel(); engine?.pause() }
    }
    fun attach(engine: MixEngine?) {
        this.engine = engine
        engineJob?.cancel()
        if (engine != null) {
            engineJob = viewModelScope.launch { engine.state.collect { _playback.value = it } }
            pendingSong?.let { play(it); pendingSong = null }
        }
    }
    fun play(song: Song) {
        val connected = engine ?: run { pendingSong = song; return }
        if (playback.value.current?.id == song.id) connected.toggle()
        else connected.start(visibleSongs.value.ifEmpty { library.value.songs }, song)
    }
    fun favorite(song: Song) = app.preferences.favorite(song.id)
    fun startSession() {
        val songs = visibleSongs.value
        val song = songs.firstOrNull() ?: return
        val connected = engine ?: run { pendingSong = song; return }
        connected.start(songs, song)
    }
    fun updateSettings(transform: (MixSettings) -> MixSettings) = app.preferences.update(transform)
    fun playNext(song: Song) {
        val connected = engine ?: return
        if (connected.state.value.current == null) connected.start(library.value.songs, song)
        else connected.queueNext(song)
    }
    fun clearError() = engine?.clearIssue()
    fun correctGrid(song: Song, bpm: Float, offset: Long) = app.analysis.correctGrid(song, bpm, offset)
}
