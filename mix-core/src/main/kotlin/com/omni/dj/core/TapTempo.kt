package com.omni.dj.core

/** Robust short tap history; a long pause starts a new estimate. */
class TapTempo {
    private val intervals = ArrayDeque<Long>()
    private var last: Long? = null
    fun tap(nowMs: Long): Float? {
        val previous = last
        last = nowMs
        if (previous == null) return null
        val interval = nowMs - previous
        if (interval !in 300..1000) { intervals.clear(); return null }
        intervals.addLast(interval)
        while (intervals.size > 6) intervals.removeFirst()
        if (intervals.size < 3) return null
        val sorted = intervals.sorted()
        val median = sorted[sorted.size / 2]
        return (60000f / median).takeIf { it in 65f..180f }
    }
}
