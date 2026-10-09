package com.omni.dj.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

@UnstableApi
class DeckAudioProcessorTest {
    private fun process(effects: DeckEffects, sample: Short): Short {
        val processor = DeckAudioProcessor()
        processor.effects = effects
        processor.configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT))
        processor.flush()
        val input = ByteBuffer.allocateDirect(400).order(ByteOrder.nativeOrder())
        repeat(200) { input.putShort(sample) }; input.flip()
        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())
        assertEquals(400, output.remaining())
        return output.short
    }
    @Test fun defaultEffectsPreserveModerateSamples() {
        assertTrue(kotlin.math.abs(12000 - process(DeckEffects(), 12000).toInt()) <= 2)
    }
    @Test fun trimmedDeckIsAttenuated() {
        assertTrue(kotlin.math.abs(3000 - process(DeckEffects(trim = .25f), 12000).toInt()) <= 2)
    }
    @Test fun highGainNeverWrapsPcm() {
        val sample = process(DeckEffects(low = 1.5f, mid = 1.5f, high = 1.5f), 30000)
        assertTrue(sample > 0); assertTrue(sample.toInt() <= 32113)
    }
}
