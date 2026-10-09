package com.omni.dj.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.*

/** PCM16 effects; parameters cross threads as one immutable snapshot. No allocation per audio frame. */
data class DeckEffects(val low: Float = 1f, val mid: Float = 1f, val high: Float = 1f,
    val filter: Float = 1f, val echo: Float = 0f, val trim: Float = 1f)

@UnstableApi
class DeckAudioProcessor : BaseAudioProcessor() {
    @Volatile var effects = DeckEffects()
    private var bass = FloatArray(2)
    private var trebleLow = FloatArray(2)
    private var filtered = FloatArray(2)
    private var delays = FloatArray(1)
    private var cursor = 0
    private var lowCoefficient = .03f
    private var highCoefficient = .3f
    private var smoothLow = 1f
    private var smoothMid = 1f
    private var smoothHigh = 1f
    private var smoothEcho = 0f
    private var smoothTrim = 1f
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        val channels = inputAudioFormat.channelCount
        bass = FloatArray(channels); trebleLow = FloatArray(channels); filtered = FloatArray(channels)
        delays = FloatArray((inputAudioFormat.sampleRate * .375f).toInt().coerceAtLeast(1) * channels)
        lowCoefficient = (1 - exp(-2 * PI * 180 / inputAudioFormat.sampleRate)).toFloat()
        highCoefficient = (1 - exp(-2 * PI * 3500 / inputAudioFormat.sampleRate)).toFloat()
        return inputAudioFormat
    }
    override fun queueInput(inputBuffer: ByteBuffer) {
        val output = replaceOutputBuffer(inputBuffer.remaining())
        val target = effects
        val channels = inputAudioFormat.channelCount
        val filterCoefficient = (1 - exp(-2 * PI * (200 + 18000 * target.filter.coerceIn(0f, 1f)) / inputAudioFormat.sampleRate)).toFloat()
        while (inputBuffer.remaining() >= channels * 2) {
            // Smooth parameter changes to avoid clicks when sliders or automation move.
            smoothLow += (target.low - smoothLow) * .005f
            smoothMid += (target.mid - smoothMid) * .005f
            smoothHigh += (target.high - smoothHigh) * .005f
            smoothEcho += (target.echo - smoothEcho) * .005f
            smoothTrim += (target.trim - smoothTrim) * .005f
            for (channel in 0 until channels) {
                val x = inputBuffer.short / 32768f
                bass[channel] += lowCoefficient * (x - bass[channel])
                trebleLow[channel] += highCoefficient * (x - trebleLow[channel])
                val low = bass[channel]; val high = x - trebleLow[channel]; val mid = x - low - high
                val eq = low * smoothLow + mid * smoothMid + high * smoothHigh
                filtered[channel] += filterCoefficient * (eq - filtered[channel])
                val dry = if (target.filter > .995f) eq else filtered[channel]
                val delayed = delays[cursor]
                delays[cursor] = dry + delayed * .35f * smoothEcho
                cursor = (cursor + 1) % delays.size
                val y = ((dry + delayed * smoothEcho * .45f) * smoothTrim).coerceIn(-.98f, .98f)
                output.putShort((y * 32767).roundToInt().toShort())
            }
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }
    override fun onFlush() {
        bass.fill(0f); trebleLow.fill(0f); filtered.fill(0f); delays.fill(0f); cursor = 0
        val e = effects
        smoothLow = e.low; smoothMid = e.mid; smoothHigh = e.high; smoothEcho = e.echo; smoothTrim = e.trim
    }
    override fun onReset() { effects = DeckEffects(); onFlush() }
}
