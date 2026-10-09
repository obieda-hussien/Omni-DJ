package com.omni.dj.core

import org.junit.Assert.*
import org.junit.Test

class TapTempoTest {
    @Test fun needsFourTapsAndEstimatesTempo() {
        val tap = TapTempo()
        assertNull(tap.tap(0)); assertNull(tap.tap(500)); assertNull(tap.tap(1000))
        assertEquals(120f, tap.tap(1500)!!, .01f)
    }
    @Test fun longPauseResetsEstimate() {
        val tap = TapTempo(); listOf(0L, 500L, 1000L, 1500L).forEach { tap.tap(it) }
        assertNull(tap.tap(5000)); assertNull(tap.tap(5500))
    }
    @Test fun medianResistsOneUnevenTap() {
        val tap = TapTempo(); listOf(0L, 500L, 1000L, 1650L).forEach { tap.tap(it) }
        assertEquals(120f, tap.tap(2150)!!, .01f)
    }
}
