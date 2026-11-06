package com.example.core.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

data class ImageAnalysisResult(
    val width: Int,
    val height: Int,
    val format: String,
    val detectedText: String,
    val sceneDescription: String,
    val isChartOrDiagram: Boolean
)

/** Runs Qwen-VL through llama.cpp's native mtmd multimodal runtime. */
class QwenVisionEngine(private val context: Context) {

    companion object {
        private val nativeAvailable: Boolean = try {
            System.loadLibrary("llama-android")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        }

        fun isNativeAvailable(): Boolean = nativeAvailable
    }

    suspend fun analyzeImage(uri: Uri): ImageAnalysisResult = withContext(Dispatchers.IO) {
        if (!nativeAvailable) {
            throw ModelNotLoadedException("The llama.cpp multimodal native runtime is not included in this APK.")
        }

        val modelsDir = File(context.filesDir, "models")
        val files = modelsDir.listFiles().orEmpty().filter { it.isFile && it.extension.equals("gguf", true) }
        val visionModel = files.firstOrNull {
            val name = it.name.lowercase()
            (name.contains("qwen3vl") || name.contains("qwen3-vl")) &&
                !name.contains("mmproj") && it.length() > 100L * 1024L * 1024L
        } ?: throw ModelNotLoadedException(
            "Qwen3-VL weights not found. Import a compatible Qwen3-VL GGUF model into app-private files/models."
        )
        val projector = files.firstOrNull {
            val name = it.name.lowercase()
            name.contains("mmproj") && (name.contains("qwen3vl") || name.contains("qwen3-vl"))
        } ?: files.firstOrNull { it.name.lowercase().contains("mmproj") }
            ?: throw ModelNotLoadedException(
                "A compatible Qwen-VL mmproj GGUF file is missing from app-private files/models."
            )

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw IllegalArgumentException("Could not open the selected image.")
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "The selected file is not a supported image." }

        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: throw IllegalArgumentException("Could not decode the selected image.")
        val scaled = resizeForInference(bitmap, 1536)
        if (scaled !== bitmap) bitmap.recycle()

        val imageData = try {
            ByteArrayOutputStream().use { output ->
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, 88, output)) {
                    throw IllegalStateException("Could not encode the image for local inference.")
                }
                output.toByteArray()
            }
        } finally {
            scaled.recycle()
        }
        require(imageData.size <= 32 * 1024 * 1024) { "Image is too large for local vision inference." }

        val prompt = """<|im_start|>user
<__media__>
Analyze this image carefully. Return only a valid JSON object with exactly these keys:
{"sceneDescription":"detailed description of visible content","detectedText":"all legible text, preserving its language and line breaks","isChartOrDiagram":false}
Set isChartOrDiagram to true only if the image is a chart, graph, schematic, or diagram. Do not guess text that is not legible.
<|im_end|>
<|im_start|>assistant
"""
        val response = nativeAnalyzeImage(
            visionModel.absolutePath,
            projector.absolutePath,
            imageData,
            prompt,
            Runtime.getRuntime().availableProcessors().coerceIn(2, 6),
            512
        ) ?: throw ModelNotLoadedException(
            "Qwen-VL inference failed. Verify that the model and mmproj are a compatible pair and that enough RAM is available."
        )

        val parsed = parseModelResponse(response)
        ImageAnalysisResult(
            width = bounds.outWidth,
            height = bounds.outHeight,
            format = when {
                bounds.outMimeType.equals("image/png", true) -> "PNG"
                bounds.outMimeType.equals("image/webp", true) -> "WEBP"
                bounds.outMimeType.equals("image/jpeg", true) -> "JPEG"
                else -> bounds.outMimeType?.substringAfter("/")?.uppercase() ?: "UNKNOWN"
            },
            detectedText = parsed.optString("detectedText", ""),
            sceneDescription = parsed.optString("sceneDescription").ifBlank { response.trim() },
            isChartOrDiagram = parsed.optBoolean("isChartOrDiagram", false)
        )
    }

    private fun resizeForInference(source: Bitmap, maxDimension: Int): Bitmap {
        val largest = maxOf(source.width, source.height)
        if (largest <= maxDimension) return source
        val scale = maxDimension.toFloat() / largest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    private fun parseModelResponse(response: String): JSONObject {
        val start = response.indexOf('{')
        val end = response.lastIndexOf('}')
        if (start < 0 || end <= start) return JSONObject()
        return try { JSONObject(response.substring(start, end + 1)) } catch (_: Exception) { JSONObject() }
    }

    private external fun nativeAnalyzeImage(
        modelPath: String,
        projectorPath: String,
        imageBytes: ByteArray,
        prompt: String,
        threads: Int,
        maxTokens: Int
    ): String?
}
