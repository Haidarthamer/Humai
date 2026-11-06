package com.example.core.ai

import android.content.Context
import android.net.Uri

data class ImageAnalysisResult(
    val width: Int,
    val height: Int,
    val format: String,
    val detectedText: String,
    val sceneDescription: String,
    val isChartOrDiagram: Boolean
)

/**
 * Qwen3-VL adapter boundary.
 * The multimodal native runtime and projector are not included in this build.
 * Never infer image content from dimensions, edges, or hard-coded labels.
 */
class QwenVisionEngine(private val context: Context) {

    companion object {
        fun isNativeAvailable(): Boolean = false
    }

    suspend fun analyzeImage(uri: Uri): ImageAnalysisResult {
        throw ModelNotLoadedException(
            "Image understanding is not available in this build. A real Qwen3-VL model, compatible mmproj file, and multimodal native runtime are required. No visual description or OCR result was fabricated."
        )
    }
}
