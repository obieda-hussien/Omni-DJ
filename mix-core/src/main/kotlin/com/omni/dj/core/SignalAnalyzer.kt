package com.omni.dj.core

import kotlin.math.*

/** Streaming mono PCM accumulator. Stores 50 energy frames/sec instead of retaining decoded songs. */
class SignalAnalyzer(private val sampleRate: Int) {
    private val window = (sampleRate / 50).coerceAtLeast(1)
    private val levels = ArrayList<Float>()
    private var sum = 0.0
    private var total = 0.0
    private var count = 0
    private var samples = 0L
    fun accept(sample: Float) {
        val v = sample.coerceIn(-1f, 1f)
        sum += v * v; total += v * v; count++; samples++
        if (count >= window) { levels.add(sqrt(sum / count).toFloat()); sum = 0.0; count = 0 }
    }
    fun finish(): TrackAnalysis {
        if (count > 0) levels.add(sqrt(sum / count).toFloat())
        val rms = if (samples > 0) sqrt(total / samples).toFloat() else 0f
        val waveform = (0 until minOf(120, levels.size)).map { i ->
            val start = i * levels.size / minOf(120, levels.size)
            val end = (i + 1) * levels.size / minOf(120, levels.size)
            (start until end).maxOfOrNull { levels[it] } ?: 0f
        }
        if (levels.size < 250 || rms < .002f) return TrackAnalysis(rms = rms, waveform = waveform)
        val onsets = FloatArray(levels.size) { i ->
            if (i == 0) 0f else max(0f, levels[i] - levels[i - 1])
        }
        var bestLag = 0
        var best = 0.0
        var second = 0.0
        for (lag in 17..46) {
            var product = 0.0; var x2 = 0.0; var y2 = 0.0
            for (i in lag until onsets.size) {
                val x = onsets[i].toDouble(); val y = onsets[i - lag].toDouble()
                product += x * y; x2 += x * x; y2 += y * y
            }
            val correlation = product / (sqrt(x2 * y2) + 1e-12)
            if (correlation > best) { second = best; best = correlation; bestLag = lag }
            else if (correlation > second) second = correlation
        }
        val confidence = (best * .8 + (best - second) * 2).coerceIn(0.0, 1.0).toFloat()
        val offset = if (bestLag > 0) (0 until bestLag).maxByOrNull { phase ->
            var value = 0f; var i = phase
            while (i < onsets.size) { value += onsets[i]; i += bestLag }; value
        } ?: 0 else 0
        val threshold = rms * .55f
        val intro = levels.take(1500).indexOfFirst { it > threshold }.coerceAtLeast(0) * 20L
        val trailing = levels.asReversed().take(1500).takeWhile { it < threshold }.size * 20L
        return TrackAnalysis(if (bestLag > 0) 3000f / bestLag else 0f, confidence,
            offset * 20L, (rms * 4f).coerceIn(0f, 1f), rms, intro, trailing, waveform)
    }
}
