package com.omni.dj.data

import android.content.Context
import com.omni.dj.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class DjPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("dj_preferences", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings = _settings.asStateFlow()
    private val _favorites = MutableStateFlow(prefs.getStringSet("favorites", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet())
    val favorites = _favorites.asStateFlow()
    private val _lastSong = MutableStateFlow(prefs.getLong("last_song", -1))
    val lastSong = _lastSong.asStateFlow()
    fun remember(id: Long) { prefs.edit().putLong("last_song", id).apply(); _lastSong.value = id }
    fun favorite(id: Long) {
        val updated = _favorites.value.toMutableSet().apply { if (!add(id)) remove(id) }.toSet()
        prefs.edit().putStringSet("favorites", updated.map { it.toString() }.toSet()).apply()
        _favorites.value = updated
    }
    fun update(transform: (MixSettings) -> MixSettings) {
        val s = transform(_settings.value).let { it.copy(transitionSeconds = it.transitionSeconds.coerceIn(2, 16), maxTempoPercent = it.maxTempoPercent.coerceIn(0, 10)) }
        prefs.edit().putBoolean("auto", s.autoMix).putBoolean("order", s.smartOrder)
            .putInt("seconds", s.transitionSeconds).putString("style", s.style.name)
            .putString("mood", s.mood.name).putBoolean("normalize", s.normalize)
            .putBoolean("vocals", s.preserveVocals).putInt("tempo", s.maxTempoPercent).apply()
        _settings.value = s
    }
    private fun read() = MixSettings(
        autoMix = prefs.getBoolean("auto", true), smartOrder = prefs.getBoolean("order", true),
        transitionSeconds = prefs.getInt("seconds", 8).coerceIn(2, 16),
        style = runCatching { TransitionStyle.valueOf(prefs.getString("style", "AUTO")!!) }.getOrDefault(TransitionStyle.AUTO),
        mood = runCatching { SessionMood.valueOf(prefs.getString("mood", "BALANCED")!!) }.getOrDefault(SessionMood.BALANCED),
        normalize = prefs.getBoolean("normalize", true), preserveVocals = prefs.getBoolean("vocals", true),
        maxTempoPercent = prefs.getInt("tempo", 6).coerceIn(0, 10),
    )
}
