package com.omni.dj.playback

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.*
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.omni.dj.DjApplication
import com.omni.dj.core.*
import com.omni.dj.data.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PlaybackIssue { NONE, FILE_UNAVAILABLE, NEXT_UNAVAILABLE }
data class CompletedMix(val from: Song, val to: Song, val plan: MixPlan, val feedback: Boolean? = null)
data class PlaybackState(
    val current: Song? = null, val next: Song? = null, val queue: List<Song> = emptyList(),
    val playing: Boolean = false, val positionMs: Long = 0, val durationMs: Long = 0,
    val mixing: Boolean = false, val progress: Float = 0f, val plan: MixPlan? = null,
    val speed: Float = 1f, val manualCrossfade: Float? = null, val issue: PlaybackIssue = PlaybackIssue.NONE,
    val loopBeats: Int = 0,
    val lastMix: CompletedMix? = null,
    val effects: DeckEffects = DeckEffects(),
)

@UnstableApi
class MixEngine(private val app: DjApplication, private val onPlayerChanged: (ExoPlayer) -> Unit) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlaybackState())
    val state = _state.asStateFlow()
    private val processors = arrayOf(DeckAudioProcessor(), DeckAudioProcessor())
    private val attributes = AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build()
    private val players = Array(2) { i -> createPlayer(i) }
    private var deck = 0
    val sessionPlayer: ExoPlayer get() = players[deck]
    private val active get() = players[deck]
    private val incoming get() = players[1 - deck]
    private val waiting = mutableListOf<Song>()
    private var current: Song? = null
    private var prepared: Song? = null
    private var nextLocked: Long? = null
    private var transition: MixPlan? = null
    private var transitionElapsed = 0L
    private var transitionStarted = false
    private var scheduledPosition: Long? = null
    private var manualCrossfade: Float? = null
    private var manualEffects = DeckEffects()
    private var loopStart = 0L
    private var loopBeats = 0
    private var lastTick = SystemClock.elapsedRealtime()
    private var completedMix: CompletedMix? = null
    init {
        players.forEachIndexed { i, player ->
            player.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (i == deck) {
                        if (!isPlaying) incoming.pause()
                        else if (transitionStarted) incoming.play()
                    }
                }
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (i == deck) {
                        if (!playWhenReady) incoming.pause()
                        else if (transitionStarted) incoming.play()
                    }
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (i == deck && playbackState == Player.STATE_ENDED && current != null) {
                        if (transitionStarted && incoming.playbackState == Player.STATE_READY) promote()
                        else advance()
                    }
                }
                override fun onPlayerError(error: PlaybackException) {
                    if (i == deck) {
                        cancelTransition(); active.pause()
                        _state.value = _state.value.copy(issue = PlaybackIssue.FILE_UNAVAILABLE, playing = false)
                    } else {
                        val failed = prepared?.id
                        cancelTransition(); waiting.removeAll { it.id == failed }; prepared = null
                        _state.value = _state.value.copy(issue = PlaybackIssue.NEXT_UNAVAILABLE)
                    }
                }
            })
        }
        scope.launch {
            while (isActive) { tick(); delay(40) }
        }
        scope.launch {
            app.preferences.settings.collect {
                if (!it.autoMix && transitionStarted && manualCrossfade == null) cancelTransition()
            }
        }
    }
    private fun createPlayer(index: Int): ExoPlayer {
        val renderers = object : DefaultRenderersFactory(app) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context).setEnableFloatOutput(false).setEnableAudioTrackPlaybackParams(false)
                    .setAudioProcessors(arrayOf<AudioProcessor>(processors[index])).build()
        }
        return ExoPlayer.Builder(app, renderers).build().apply {
            setAudioAttributes(attributes, index == 0)
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_LOCAL)
        }
    }
    private fun item(song: Song) = MediaItem.Builder().setMediaId(song.id.toString()).setUri(song.uri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist)
            .setAlbumTitle(song.album).setArtworkUri(song.artwork).build()).build()
    fun start(songs: List<Song>, song: Song) {
        cancelTransition(); active.pause(); incoming.stop()
        waiting.clear()
        val index = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        waiting.addAll((songs.drop(index + 1) + songs.take(index)).filter { it.id != song.id }.distinctBy { it.id })
        current = song; prepared = null; nextLocked = null; loopBeats = 0
        _state.value = PlaybackState(current = song, durationMs = song.durationMs)
        active.setMediaItem(item(song)); active.prepare(); active.playbackParameters = PlaybackParameters.DEFAULT
        applySingleDeckGain(); active.play(); app.preferences.remember(song.id)
    }
    fun toggle() {
        if (active.playWhenReady) { active.pause(); incoming.pause() }
        else { active.play(); if (transitionStarted) incoming.play() }
    }
    fun play() { active.play(); if (transitionStarted) incoming.play() }
    fun pause() { active.pause(); incoming.pause() }
    fun seek(position: Long) { cancelTransition(); loopBeats = 0; active.seekTo(position.coerceIn(0, current?.durationMs ?: 0)); applySingleDeckGain() }
    fun previous() { seek(0) }
    fun next() { cancelTransition(); advance() }
    fun clearIssue() { _state.value = _state.value.copy(issue = PlaybackIssue.NONE) }
    fun rateLastMix(liked: Boolean) {
        val mix = completedMix ?: return
        app.mixMemory.rate(mix.from, mix.to, mix.plan, liked)
        completedMix = mix.copy(feedback = liked)
    }
    fun queueNext(song: Song) {
        if (song.id == current?.id) return
        cancelTransition(); waiting.removeAll { it.id == song.id }; waiting.add(0, song); nextLocked = song.id
        prepared = null
    }
    fun moveUp(id: Long) {
        if (transitionStarted || scheduledPosition != null) cancelTransition()
        val i = waiting.indexOfFirst { it.id == id }
        if (i > 0) { val s = waiting.removeAt(i); waiting.add(i - 1, s); nextLocked = waiting.firstOrNull()?.id; prepared = null }
    }
    fun removeQueued(id: Long) {
        if (prepared?.id == id) { cancelTransition(); prepared = null }
        waiting.removeAll { it.id == id }; if (nextLocked == id) nextLocked = null
    }
    fun mixNow() { loopBeats = 0; prepareNext(); scheduleTransition() }
    fun setSpeed(speed: Float) {
        cancelTransition(); active.playbackParameters = PlaybackParameters(speed.coerceIn(.9f, 1.1f), 1f)
    }
    fun setEffects(low: Float, mid: Float, high: Float, filter: Float, echo: Float) {
        manualEffects = DeckEffects(low.coerceIn(0f, 1.5f), mid.coerceIn(0f, 1.5f), high.coerceIn(0f, 1.5f),
            filter.coerceIn(0f, 1f), echo.coerceIn(0f, 1f))
        processors[deck].effects = manualEffects
        _state.value = _state.value.copy(effects = manualEffects)
    }
    fun resetControls() { cancelTransition(); manualEffects = DeckEffects(); processors[deck].effects = manualEffects; active.playbackParameters = PlaybackParameters.DEFAULT; loopBeats = 0 }
    fun setCrossfade(value: Float) {
        if (current == null) return
        loopBeats = 0
        prepareNext()
        val candidate = prepared ?: return
        if (incoming.playbackState != Player.STATE_READY) return
        if (!transitionStarted) {
            transition = MixPlanner.plan(app.analysis.analyses.value[current?.id], app.analysis.analyses.value[candidate.id], app.preferences.settings.value, candidate.durationMs)
            incoming.seekTo(transition!!.incomingCueMs); incoming.playbackParameters = PlaybackParameters(transition!!.incomingSpeed, 1f)
            transitionStarted = true; if (active.isPlaying) incoming.play()
        }
        scheduledPosition = null; manualCrossfade = value.coerceIn(0f, 1f)
        applyTransition(manualCrossfade!!)
        if (value >= .995f) promote()
        else if (value <= 0f) cancelTransition()
    }
    fun setLoop(beats: Int) {
        cancelTransition()
        val a = app.analysis.analyses.value[current?.id]
        if (beats == 0 || a?.hasReliableBeat != true) { loopBeats = 0; return }
        loopStart = active.currentPosition
        loopBeats = beats.coerceIn(1, 16)
    }
    private fun advance() {
        val song = selectedNext() ?: run { active.pause(); return }
        waiting.removeAll { it.id == song.id }; current = song; nextLocked = null; prepared = null; loopBeats = 0
        _state.value = _state.value.copy(issue = PlaybackIssue.NONE)
        active.setMediaItem(item(song)); active.playbackParameters = PlaybackParameters.DEFAULT
        active.prepare(); applySingleDeckGain(); active.play(); app.preferences.remember(song.id)
    }
    private fun selectedNext(): Song? {
        if (nextLocked != null) return waiting.firstOrNull { it.id == nextLocked } ?: waiting.firstOrNull()
        if (!app.preferences.settings.value.smartOrder) return waiting.firstOrNull()
        val analyses = app.analysis.analyses.value
        return waiting.take(12).maxByOrNull { MixPlanner.candidateScore(analyses[current?.id], analyses[it.id], app.preferences.settings.value.mood) }
    }
    private fun prepareNext() {
        if (transitionStarted || scheduledPosition != null) return
        val song = selectedNext() ?: return
        if (prepared?.id == song.id) return
        prepared = song
        incoming.volume = 0f; incoming.setMediaItem(item(song)); incoming.playbackParameters = PlaybackParameters.DEFAULT
        processors[1 - deck].effects = DeckEffects()
        incoming.prepare()
    }
    private fun scheduleTransition() {
        if (transitionStarted || scheduledPosition != null || current == null) return
        val song = prepared ?: return
        transition = buildPlan(song)
        scheduledPosition = active.currentPosition + if (transition!!.alignToBeat) MixPlanner.delayToBeat(active.currentPosition, app.analysis.analyses.value[current?.id]) else 0
    }
    private fun buildPlan(song: Song): MixPlan {
        val settings = app.preferences.settings.value
        val analysis = app.analysis.analyses.value[current?.id]
        val playingAnalysis = analysis?.copy(bpm = analysis.bpm * active.playbackParameters.speed)
        val saved = if (settings.style == TransitionStyle.AUTO) app.mixMemory.find(current, song) else null
        val effective = if (saved?.liked == true) settings.copy(style = saved.style, transitionSeconds = (saved.durationMs / 1000).toInt().coerceIn(2, 16)) else settings
        var plan = MixPlanner.plan(playingAnalysis, app.analysis.analyses.value[song.id], effective, song.durationMs)
        if (saved?.liked == false && saved.style == plan.style) plan = MixPlanner.plan(playingAnalysis,
            app.analysis.analyses.value[song.id], settings.copy(style = TransitionStyle.SMOOTH), song.durationMs)
        // Conservative overlap cap, not vocal detection.
        return if (settings.preserveVocals && plan.durationMs > 6000) plan.copy(durationMs = 6000) else plan
    }
    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        val delta = (now - lastTick).coerceIn(0, 150); lastTick = now
        val song = current ?: return
        val playing = active.isPlaying
        app.analysis.playbackActive = active.playWhenReady
        if (playing) {
            prepareNext()
            if (loopBeats > 0) {
                val bpm = app.analysis.analyses.value[song.id]?.bpm ?: 0f
                if (bpm > 0 && active.currentPosition >= loopStart + (60000 / bpm * loopBeats).toLong()) active.seekTo(loopStart)
            } else {
                val remaining = (song.durationMs - active.currentPosition) / active.playbackParameters.speed
                val nextPlan = prepared?.let { buildPlan(it) }
                if (app.preferences.settings.value.autoMix && !transitionStarted && scheduledPosition == null &&
                    nextPlan != null && remaining <= nextPlan.durationMs + 600 && remaining > 200 &&
                    incoming.playbackState == Player.STATE_READY) scheduleTransition()
                if (!transitionStarted && scheduledPosition?.let { active.currentPosition >= it } == true && incoming.playbackState == Player.STATE_READY) {
                    incoming.seekTo(transition!!.incomingCueMs); incoming.playbackParameters = PlaybackParameters(transition!!.incomingSpeed, 1f)
                    transitionElapsed = 0; transitionStarted = true; incoming.play()
                }
                if (transitionStarted && manualCrossfade == null && incoming.isPlaying) {
                    transitionElapsed += delta
                    val p = (transitionElapsed.toFloat() / (transition?.durationMs ?: 1)).coerceIn(0f, 1f)
                    applyTransition(p)
                    if (p >= 1) promote()
                }
            }
        }
        if (!transitionStarted) applySingleDeckGain()
        val next = if (transitionStarted) prepared else selectedNext()
        _state.value = _state.value.copy(current = current, next = next,
            queue = listOfNotNull(next) + waiting.filter { it.id != next?.id }, playing = active.isPlaying,
            positionMs = active.currentPosition.coerceAtLeast(0), durationMs = current?.durationMs ?: 0,
            mixing = transitionStarted, plan = transition,
            progress = manualCrossfade ?: if (transitionStarted) (transitionElapsed.toFloat() / (transition?.durationMs ?: 1)).coerceIn(0f, 1f) else 0f,
            speed = active.playbackParameters.speed, manualCrossfade = manualCrossfade, loopBeats = loopBeats, lastMix = completedMix, effects = manualEffects)
    }
    private fun applySingleDeckGain() {
        active.volume = MixPlanner.normalizedGain(app.analysis.analyses.value[current?.id], app.preferences.settings.value.normalize)
        incoming.volume = 0f
        processors[deck].effects = manualEffects
    }
    private fun applyTransition(p: Float) {
        val (outGain, inGain) = MixPlanner.gains(p)
        active.volume = outGain * MixPlanner.normalizedGain(app.analysis.analyses.value[current?.id], app.preferences.settings.value.normalize)
        incoming.volume = inGain * MixPlanner.normalizedGain(app.analysis.analyses.value[prepared?.id], app.preferences.settings.value.normalize)
        when (transition?.style) {
            TransitionStyle.BASS_SWAP -> {
                processors[deck].effects = manualEffects.copy(low = (1 - p * 2).coerceAtLeast(0f) * manualEffects.low)
                processors[1 - deck].effects = DeckEffects(low = ((p - .3f) / .7f).coerceIn(0f, 1f))
            }
            TransitionStyle.FILTER_SWEEP -> {
                processors[deck].effects = manualEffects.copy(filter = (1 - p).coerceAtLeast(.04f))
                processors[1 - deck].effects = DeckEffects(filter = p.coerceAtLeast(.04f))
            }
            TransitionStyle.ECHO_OUT -> processors[deck].effects = manualEffects.copy(echo = p)
            else -> Unit
        }
    }
    private fun promote() {
        val song = prepared ?: return
        val from = current
        val completedPlan = transition
        if (from != null && completedPlan != null) completedMix = CompletedMix(from, song, completedPlan)
        val outgoing = active
        // Switch identity before pause listeners run, otherwise outgoing pause also pauses the new deck.
        deck = 1 - deck
        outgoing.pause(); outgoing.volume = 0f; outgoing.setAudioAttributes(attributes, false)
        active.setAudioAttributes(attributes, true)
        onPlayerChanged(active)
        outgoing.stop(); outgoing.clearMediaItems()
        current = song; waiting.removeAll { it.id == song.id }; nextLocked = null; prepared = null
        transition = null; transitionStarted = false; transitionElapsed = 0; manualCrossfade = null; scheduledPosition = null
        manualEffects = DeckEffects(); processors[deck].effects = manualEffects
        applySingleDeckGain(); app.preferences.remember(song.id)
    }
    private fun cancelTransition() {
        incoming.pause(); incoming.volume = 0f
        processors[1 - deck].effects = DeckEffects(); processors[deck].effects = manualEffects
        transition = null; scheduledPosition = null; transitionStarted = false; transitionElapsed = 0; manualCrossfade = null
        applySingleDeckGain()
    }
    fun release() {
        app.analysis.playbackActive = false
        scope.cancel(); players.forEach { it.release() }
    }
}
