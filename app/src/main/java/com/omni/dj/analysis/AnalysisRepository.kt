package com.omni.dj.analysis

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import com.omni.dj.core.SignalAnalyzer
import com.omni.dj.core.TrackAnalysis
import com.omni.dj.data.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteOrder

data class AnalysisProgress(val done: Int = 0, val total: Int = 0, val running: Boolean = false, val failed: Int = 0)

class AnalysisRepository(private val context: Context, private val scope: CoroutineScope) {
    private val directory = File(context.filesDir, "analysis-v1").apply { mkdirs() }
    private val _analyses = MutableStateFlow<Map<Long, TrackAnalysis>>(emptyMap())
    val analyses = _analyses.asStateFlow()
    private val _progress = MutableStateFlow(AnalysisProgress())
    val progress = _progress.asStateFlow()
    @Volatile var playbackActive = false
    private var job: Job? = null
    private var keys: Set<String> = emptySet()
    fun analyze(songs: List<Song>, force: Boolean = false) {
        val nextKeys = songs.map { it.cacheKey }.toSet()
        if (!force && nextKeys == keys) return
        keys = nextKeys
        job?.cancel()
        job = scope.launch(Dispatchers.IO) {
            _progress.value = AnalysisProgress(total = songs.size, running = true)
            val cache = buildMap {
                songs.forEach { song -> read(song)?.let { put(song.id, it) } }
            }
            _analyses.value = cache
            var done = 0; var failed = 0
            for (song in songs) {
                ensureActive()
                try {
                    if (song.id !in _analyses.value) {
                        // Do not compete with two live decoders on entry-level phones.
                        while (playbackActive) { delay(750); ensureActive() }
                        val analysis = decode(song)
                        write(song, analysis)
                        _analyses.value = _analyses.value + (song.id to analysis)
                    }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { failed++ }
                done++
                _progress.value = AnalysisProgress(done, songs.size, running = true, failed = failed)
                delay(40)
            }
            directory.listFiles()?.filter { it.name.endsWith(".json") && it.name.removeSuffix(".json") !in nextKeys }?.forEach { it.delete() }
            _progress.value = AnalysisProgress(done, songs.size, failed = failed)
        }
    }
    fun cancel() { job?.cancel(); keys = emptySet(); _progress.value = _progress.value.copy(running = false) }
    private fun read(song: Song): TrackAnalysis? = runCatching {
        val json = JSONObject(File(directory, "${song.cacheKey}.json").readText())
        if (json.getInt("version") != 1) return null
        val wave = json.getJSONArray("wave")
        TrackAnalysis(json.getDouble("bpm").toFloat(), json.getDouble("confidence").toFloat(), json.getLong("offset"),
            json.getDouble("energy").toFloat(), json.getDouble("rms").toFloat(), json.getLong("intro"), json.getLong("outro"),
            List(wave.length()) { wave.getDouble(it).toFloat() })
    }.getOrNull()
    private fun write(song: Song, a: TrackAnalysis) {
        val json = JSONObject().put("version", 1).put("bpm", a.bpm).put("confidence", a.confidence)
            .put("offset", a.beatOffsetMs).put("energy", a.energy).put("rms", a.rms)
            .put("intro", a.introMs).put("outro", a.outroMs).put("wave", JSONArray(a.waveform))
        val temp = File(directory, "${song.cacheKey}.tmp")
        temp.writeText(json.toString())
        check(temp.renameTo(File(directory, "${song.cacheKey}.json")))
    }
    private suspend fun decode(song: Song): TrackAnalysis {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, song.uri, null)
            val index = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            extractor.selectTrack(index)
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Missing audio MIME")
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            var analyzer = SignalAnalyzer(sampleRate)
            val decoder = MediaCodec.createDecoderByType(mime).also { codec = it }
            decoder.configure(format, null, null, 0); decoder.start()
            var inputDone = false; var outputDone = false
            val info = MediaCodec.BufferInfo()
            var lastWork = SystemClock.elapsedRealtime()
            while (!outputDone) {
                currentCoroutineContext().ensureActive()
                while (playbackActive) delay(750)
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(10000)
                    if (inputIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inputIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0 || extractor.sampleTime > 20 * 60 * 1000000L) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0); extractor.advance()
                        }
                        lastWork = SystemClock.elapsedRealtime()
                    }
                }
                when (val outputIndex = decoder.dequeueOutputBuffer(info, 10000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val output = decoder.outputFormat
                        channels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        val rate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        if (rate != sampleRate) { sampleRate = rate; analyzer = SignalAnalyzer(rate) }
                        if (output.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding = output.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        check(encoding == AudioFormat.ENCODING_PCM_16BIT || encoding == AudioFormat.ENCODING_PCM_FLOAT)
                    }
                    else -> if (outputIndex >= 0) {
                        val buffer = decoder.getOutputBuffer(outputIndex)!!.order(ByteOrder.nativeOrder())
                        buffer.position(info.offset); buffer.limit(info.offset + info.size)
                        val bytes = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                        while (buffer.remaining() >= channels * bytes) {
                            var mono = 0f
                            repeat(channels) { mono += if (bytes == 4) buffer.float else buffer.short / 32768f }
                            analyzer.accept(mono / channels)
                        }
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        decoder.releaseOutputBuffer(outputIndex, false)
                        lastWork = SystemClock.elapsedRealtime()
                    }
                }
                check(SystemClock.elapsedRealtime() - lastWork < 10000) { "Decoder stalled" }
            }
            return analyzer.finish()
        } finally {
            runCatching { codec?.stop() }; codec?.release(); extractor.release()
        }
    }
}
