package com.omni.dj

import android.app.Application
import com.omni.dj.analysis.AnalysisRepository
import com.omni.dj.data.DjPreferences
import com.omni.dj.data.MusicLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class DjApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val preferences by lazy { DjPreferences(this) }
    val library by lazy { MusicLibrary(this) }
    val analysis by lazy { AnalysisRepository(this, scope) }
}
