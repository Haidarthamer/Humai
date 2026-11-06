package com.example.core.ai

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.floor

data class TranscriptionSegment(
    val startSeconds: Long,
    val endSeconds: Long,
    val text: String
)

/** Fully local whisper.cpp transcription. Model weights are kept in filesDir/models. */
class WhisperAudioEngine(private val context: Context) {

    companion object {
        private const val TAG = "WhisperAudioEngine"
        private const val TARGET_RATE = 16_000
        private val nativeAvailable: Boolean = try {
            System.loadLibrary("whisper-android")
            true
        } catch (error: UnsatisfiedLinkError) {
            Log.e(TAG, "Whisper native runtime unavailable", error)
            false
        }

        fun isNativeAvailable(): Boolean = nativeAvailable
    }

    suspend fun transcribeAudioFile(file: File, isArabic: Boolean): List<TranscriptionSegment> =
        withContext(Dispatchers.IO) {
            require(file.isFile && file.canRead()) { "Audio file is missing or unreadable." }
            if (!nativeAvailable) {
                throw ModelNotLoadedException("Whisper.cpp is not present in this APK. Rebuild the native targets.")
            }

            val modelsDir = File(context.filesDir, "models")
            val model = listOf(
                "ggml-base.bin",
                "ggml-small.bin",
                "ggml-tiny.bin",
                "ggml-medium.bin"
            ).asSequence().map { File(modelsDir, it) }.firstOrNull {
                it.isFile && it.length() > 10L * 1024L * 1024L
            } ?: throw ModelNotLoadedException(
                "No Whisper model found. Place a genuine ggml-base.bin, ggml-small.bin, or ggml-tiny.bin in app-private files/models."
            )

            val pcm = decodeToMono16k(file)
            if (pcm.isEmpty()) throw IllegalArgumentException("The selected audio contains no decodable samples.")
            val rows = nativeTranscribe(
                model.absolutePath,
                pcm,
                if (isArabic) "ar" else "auto",
                Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
            ) ?: throw ModelNotLoadedException("Whisper inference failed. Check the model file and audio format.")

            rows.mapNotNull { row ->
                val parts = row.split('\t', limit = 3)
                if (parts.size != 3) return@mapNotNull null
                val startMs = parts[0].toLongOrNull() ?: return@mapNotNull null
                val endMs = parts[1].toLongOrNull() ?: return@mapNotNull null
                val text = parts[2].trim()
                if (text.isBlank()) null else TranscriptionSegment(
                    startSeconds = (startMs / 1000L).coerceAtLeast(0L),
                    endSeconds = (endMs / 1000L).coerceAtLeast(startMs / 1000L),
                    text = text
                )
            }
        }

    /** Decode supported Android audio formats, downmix, then linearly resample to Whisper's 16 kHz PCM. */
    private fun decodeToMono16k(file: File): FloatArray {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                val mime = candidate.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    track = i
                    format = candidate
                    break
                }
            }
            val selectedFormat = format ?: throw IllegalArgumentException("No supported audio track was found.")
            require(track >= 0) { "No supported audio track was found." }
            extractor.selectTrack(track)
            val mime = selectedFormat.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalArgumentException("Audio codec MIME type is missing.")
            val codec = MediaCodec.createDecoderByType(mime)
            decoder = codec
            codec.configure(selectedFormat, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            val mono = FloatAccumulator(TARGET_RATE * 30)
            var inputEnded = false
            var outputEnded = false
            var sampleRate = selectedFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = selectedFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

            while (!outputEnded) {
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                            ?: throw IllegalStateException("Decoder input buffer unavailable.")
                        val size = extractor.readSampleData(inputBuffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codec.outputFormat
                        sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            pcmEncoding = outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        val output = codec.getOutputBuffer(outputIndex)
                        if (output != null && info.size > 0) {
                            val buffer = output.duplicate().apply {
                                position(info.offset)
                                limit(info.offset + info.size)
                            }
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            appendDownmixedSamples(mono, bytes, channels, pcmEncoding)
                        }
                        outputEnded = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
            require(mono.size <= TARGET_RATE * 60 * 15) {
                "Audio is longer than the 15-minute local transcription limit."
            }
            return resample(mono.toFloatArray(), sampleRate, TARGET_RATE)
        } finally {
            try { decoder?.stop() } catch (_: Exception) {}
            try { decoder?.release() } catch (_: Exception) {}
            extractor.release()
        }
    }

    private fun appendDownmixedSamples(
        output: FloatAccumulator,
        bytes: ByteArray,
        channels: Int,
        encoding: Int
    ) {
        val bytesPerSample = when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            AudioFormat.ENCODING_PCM_8BIT -> 1
            else -> 2
        }
        val frameBytes = bytesPerSample * channels.coerceAtLeast(1)
        var offset = 0
        while (offset + frameBytes <= bytes.size) {
            var sum = 0f
            for (channel in 0 until channels.coerceAtLeast(1)) {
                val index = offset + channel * bytesPerSample
                val value = when (encoding) {
                    AudioFormat.ENCODING_PCM_FLOAT -> {
                        java.nio.ByteBuffer.wrap(bytes, index, 4)
                            .order(java.nio.ByteOrder.LITTLE_ENDIAN).float.coerceIn(-1f, 1f)
                    }
                    AudioFormat.ENCODING_PCM_8BIT -> ((bytes[index].toInt() and 0xff) - 128) / 128f
                    else -> {
                        val sample = ((bytes[index + 1].toInt() shl 8) or (bytes[index].toInt() and 0xff)).toShort()
                        sample / 32768f
                    }
                }
                sum += value
            }
            output.add(sum / channels.coerceAtLeast(1))
            offset += frameBytes
        }
    }

    private class FloatAccumulator(initialCapacity: Int) {
        private var values = FloatArray(initialCapacity.coerceAtLeast(1024))
        var size: Int = 0
            private set

        fun add(value: Float) {
            if (size == values.size) values = values.copyOf(values.size * 2)
            values[size++] = value
        }

        fun toFloatArray(): FloatArray = values.copyOf(size)
    }

    private fun resample(input: FloatArray, sourceRate: Int, targetRate: Int): FloatArray {
        require(sourceRate > 0) { "Invalid decoded audio sample rate." }
        if (sourceRate == targetRate) return input
        if (input.isEmpty()) return input
        val outputSize = (input.size.toLong() * targetRate / sourceRate).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return FloatArray(outputSize) { index ->
            val sourcePosition = index.toDouble() * sourceRate / targetRate
            val left = floor(sourcePosition).toInt().coerceIn(0, input.lastIndex)
            val right = (left + 1).coerceAtMost(input.lastIndex)
            val fraction = (sourcePosition - left).toFloat()
            input[left] * (1f - fraction) + input[right] * fraction
        }
    }

    private external fun nativeTranscribe(
        modelPath: String,
        pcm16kMono: FloatArray,
        language: String,
        threads: Int
    ): Array<String>?
}
