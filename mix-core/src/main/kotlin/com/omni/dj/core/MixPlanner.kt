package com.omni.dj.core

import kotlin.math.*

enum class TransitionStyle { AUTO, SMOOTH, BEAT_BLEND, BASS_SWAP, FILTER_SWEEP, ECHO_OUT, QUICK_CUT }
enum class SessionMood { BALANCED, PARTY, WORKOUT, CHILL }

data class TrackAnalysis(
    val bpm: Float = 0f,
    val confidence: Float = 0f,
    val beatOffsetMs: Long = 0,
    val energy: Float = 0.5f,
    val rms: Float = 0.1f,
    val introMs: Long = 0,
    val outroMs: Long = 0,
    val waveform: List<Float> = emptyList(),
    val version: Int = 1,
) {
    val hasReliableBeat get() = bpm in 65f..180f && confidence >= 0.55f
}

data class MixSettings(
    val autoMix: Boolean = true,
    val smartOrder: Boolean = true,
    val transitionSeconds: Int = 8,
    val style: TransitionStyle = TransitionStyle.AUTO,
    val mood: SessionMood = SessionMood.BALANCED,
    val normalize: Boolean = true,
    val preserveVocals: Boolean = true,
    val maxTempoPercent: Int = 6,
)

data class MixPlan(
    val style: TransitionStyle,
    val durationMs: Long,
    val incomingCueMs: Long = 0,
    val incomingSpeed: Float = 1f,
    val alignToBeat: Boolean = false,
)

object MixPlanner {
    fun plan(a: TrackAnalysis?, b: TrackAnalysis?, settings: MixSettings, incomingDurationMs: Long): MixPlan {
        val reliable = a?.hasReliableBeat == true && b?.hasReliableBeat == true
        val ratio = if (reliable) a!!.bpm / b!!.bpm else 1f
        val tempoSafe = reliable && abs(ratio - 1) <= settings.maxTempoPercent.coerceIn(0, 10) / 100f
        val energyDifference = abs((a?.energy ?: .5f) - (b?.energy ?: .5f))
        val style = when {
            settings.style != TransitionStyle.AUTO -> settings.style
            !reliable -> TransitionStyle.SMOOTH
            !tempoSafe -> TransitionStyle.ECHO_OUT
            energyDifference > .3f -> TransitionStyle.FILTER_SWEEP
            (a?.energy ?: 0f) > .6f && (b?.energy ?: 0f) > .6f -> TransitionStyle.BASS_SWAP
            else -> TransitionStyle.BEAT_BLEND
        }
        val sync = tempoSafe && style in setOf(TransitionStyle.BEAT_BLEND, TransitionStyle.BASS_SWAP, TransitionStyle.FILTER_SWEEP)
        val desired = when (style) {
            TransitionStyle.QUICK_CUT -> 180L
            TransitionStyle.ECHO_OUT -> 2500L
            else -> settings.transitionSeconds.coerceIn(2, 16) * 1000L
        }
        val duration = if (sync) {
            val beat = 60000.0 / a!!.bpm
            (round(desired / beat / 4).coerceAtLeast(1.0) * 4 * beat).toLong()
        } else desired
        // The intro/outro markers are conservative low-energy estimates, not vocal recognition.
        val cue = if (sync) b!!.beatOffsetMs else 0L
        return MixPlan(style, duration.coerceAtMost((incomingDurationMs / 3).coerceAtLeast(1)),
            cue.coerceIn(0, (incomingDurationMs - 1000).coerceAtLeast(0)), if (sync) ratio else 1f, sync)
    }

    fun delayToBeat(positionMs: Long, analysis: TrackAnalysis?): Long {
        if (analysis?.hasReliableBeat != true) return 0
        val period = 60000.0 / analysis.bpm
        val elapsed = (positionMs - analysis.beatOffsetMs).toDouble()
        val phase = ((elapsed % period) + period) % period
        return if (phase < 12) 0 else (period - phase).toLong()
    }

    fun candidateScore(a: TrackAnalysis?, b: TrackAnalysis?, mood: SessionMood): Float {
        if (b == null) return 0f
        val target = when (mood) {
            SessionMood.PARTY -> .8f
            SessionMood.WORKOUT -> .9f
            SessionMood.CHILL -> .25f
            SessionMood.BALANCED -> a?.energy ?: .5f
        }
        val tempo = if (a?.hasReliableBeat == true && b.hasReliableBeat)
            (1 - abs(a.bpm - b.bpm) / 35f).coerceIn(0f, 1f) else .4f
        val flow = 1 - abs((a?.energy ?: target) - b.energy)
        return tempo * .45f + flow * .25f + (1 - abs(target - b.energy)) * .3f
    }

    fun gains(progress: Float): Pair<Float, Float> {
        val p = progress.coerceIn(0f, 1f)
        // Equal-power envelope with headroom during overlap. Each deck has a peak guard too.
        val outgoing = cos(p * PI / 2).toFloat().coerceAtLeast(0f)
        val incoming = sin(p * PI / 2).toFloat()
        val headroom = minOf(1f - .3f * sin(PI * p).toFloat(), 1f / (outgoing + incoming).coerceAtLeast(1f))
        return (outgoing * headroom) to (incoming * headroom)
    }

    fun normalizedGain(analysis: TrackAnalysis?, enabled: Boolean): Float =
        if (!enabled || analysis == null || analysis.rms <= .001f) 1f else (.14f / analysis.rms).coerceIn(.4f, 1f)
}
