package com.omni.dj.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class MixPlannerTest {
    private fun track(bpm: Float, energy: Float = .5f) = TrackAnalysis(bpm, .85f, 120, energy)
    @Test fun unknownBeatUsesSafeUnstretchedFade() {
        val p = MixPlanner.plan(null, track(128f), MixSettings(), 180000)
        assertEquals(TransitionStyle.SMOOTH, p.style); assertFalse(p.alignToBeat); assertEquals(1f, p.incomingSpeed, 0f)
    }
    @Test fun incompatibleTempoDoesNotForceSynchronization() {
        val p = MixPlanner.plan(track(90f), track(140f), MixSettings(), 180000)
        assertEquals(TransitionStyle.ECHO_OUT, p.style); assertEquals(1f, p.incomingSpeed, 0f)
    }
    @Test fun compatibleEnergeticTracksChooseBassSwap() {
        val p = MixPlanner.plan(track(124f, .8f), track(128f, .8f), MixSettings(), 180000)
        assertEquals(TransitionStyle.BASS_SWAP, p.style); assertTrue(p.alignToBeat)
        assertEquals(124f / 128f, p.incomingSpeed, .001f)
    }
    @Test fun durationAndCueFitShortIncomingTrack() {
        val p = MixPlanner.plan(track(120f), track(120f), MixSettings(transitionSeconds = 16), 900)
        assertTrue(p.durationMs <= 300); assertEquals(0L, p.incomingCueMs)
    }
    @Test fun explicitOverrideWinsButUnsafeTempoIsNotStretched() {
        val p = MixPlanner.plan(track(80f), track(160f), MixSettings(style = TransitionStyle.BASS_SWAP), 180000)
        assertEquals(TransitionStyle.BASS_SWAP, p.style); assertFalse(p.alignToBeat)
    }
    @Test fun gainEnvelopeHasEndpointsAndOverlapHeadroom() {
        val first = MixPlanner.gains(0f); val last = MixPlanner.gains(1f)
        assertEquals(1f, first.first, .0001f); assertEquals(0f, first.second, .0001f)
        assertTrue(abs(last.first) < .0001); assertEquals(1f, last.second, .0001f)
        val mid = MixPlanner.gains(.5f); assertTrue(mid.first + mid.second < 1.01f)
    }
    @Test fun beatAlignmentHandlesOffsetAndNearBeat() {
        assertEquals(0L, MixPlanner.delayToBeat(120, track(120f)))
        assertEquals(250L, MixPlanner.delayToBeat(370, track(120f)))
        assertEquals(120L, MixPlanner.delayToBeat(0, track(120f)))
    }
    @Test fun silenceDoesNotInventBpm() {
        val analyzer = SignalAnalyzer(1000); repeat(10000) { analyzer.accept(0f) }
        val a = analyzer.finish(); assertEquals(0f, a.bpm, 0f); assertFalse(a.hasReliableBeat)
    }
    @Test fun periodicClickProducesCredibleBeat() {
        val analyzer = SignalAnalyzer(1000)
        repeat(20000) { analyzer.accept(if (it % 500 < 20) .8f else 0f) }
        val a = analyzer.finish(); assertEquals(120f, a.bpm, 1f); assertTrue(a.hasReliableBeat)
    }
}
