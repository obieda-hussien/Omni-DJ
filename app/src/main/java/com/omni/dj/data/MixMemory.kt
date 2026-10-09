package com.omni.dj.data

import android.content.Context
import com.omni.dj.core.MixPlan
import com.omni.dj.core.TransitionStyle
import org.json.JSONObject

data class SavedMix(val style: TransitionStyle, val durationMs: Long, val liked: Boolean)

/** Explicit feedback only; recipes are invalidated when either source file changes. */
class MixMemory(context: Context) {
    private val prefs = context.getSharedPreferences("mix_recipes", Context.MODE_PRIVATE)
    private val recipes = mutableMapOf<String, SavedMix>()
    init {
        prefs.all.forEach { (key, value) -> runCatching {
            val json = JSONObject(value as String)
            recipes[key] = SavedMix(TransitionStyle.valueOf(json.getString("style")), json.getLong("duration"), json.getBoolean("liked"))
        } }
    }
    private fun key(a: Song, b: Song) = "${a.cacheKey}__${b.cacheKey}"
    fun find(a: Song?, b: Song): SavedMix? = a?.let { recipes[key(it, b)] }
    fun rate(a: Song, b: Song, plan: MixPlan, liked: Boolean) {
        val key = key(a, b)
        val saved = SavedMix(plan.style, plan.durationMs, liked)
        recipes[key] = saved
        prefs.edit().putString(key, JSONObject().put("style", saved.style.name).put("duration", saved.durationMs).put("liked", liked).toString()).apply()
        // Bound retained history on devices with small storage.
        if (recipes.size > 500) recipes.keys.firstOrNull { it != key }?.let { oldest -> recipes.remove(oldest); prefs.edit().remove(oldest).apply() }
    }
}
