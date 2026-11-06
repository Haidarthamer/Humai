package com.example.core.ai

import android.content.Context
import java.io.File

data class TranscriptionSegment(
    val startSeconds: Long,
    val endSeconds: Long,
    val text: String
)

/**
 * Whisper.cpp adapter boundary.
 * Audio duration is not a transcript. Until the native library, model weights,
 * PCM conversion and segment decoder are integrated, fail explicitly.
 */
class WhisperAudioEngine(private val context: Context) {

    companion object {
        fun isNativeAvailable(): Boolean = false
    }

    suspend fun transcribeAudioFile(file: File, isArabic: Boolean): List<TranscriptionSegment> {
        throw ModelNotLoadedException(
            "Offline speech transcription is not available in this build. The Whisper.cpp native runtime and a real Whisper model must be installed. No transcript was fabricated."
        )
    }
}
