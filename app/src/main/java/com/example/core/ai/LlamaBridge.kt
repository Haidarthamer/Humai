package com.example.core.ai

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class ModelNotLoadedException(message: String) : Exception(message)

/**
 * Fail-closed JNI boundary for local GGUF inference.
 *
 * A valid GGUF header is not proof that a model is resident in memory. The
 * native backend must return a non-zero handle before this class reports a
 * successful load or emits any generated text.
 */
class LlamaBridge {

    companion object {
        private const val TAG = "LlamaBridge"
        private var isLibraryLoaded = false

        init {
            try {
                System.loadLibrary("llama-android")
                isLibraryLoaded = true
                Log.i(TAG, "Native llama library loaded.")
            } catch (error: UnsatisfiedLinkError) {
                isLibraryLoaded = false
                Log.w(TAG, "Native llama backend is not packaged: ${error.message}")
            }
        }

        fun isNativeAvailable(): Boolean = isLibraryLoaded
    }

    private var modelPointer: Long = 0L
    private var contextPointer: Long = 0L
    private var loadedModelPath: String? = null
    private var modelMetadata: GGUFMetadata? = null

    val isModelLoaded: Boolean
        get() = isLibraryLoaded && modelPointer != 0L && contextPointer != 0L

    val currentModelPath: String?
        get() = loadedModelPath

    val currentMetadata: GGUFMetadata?
        get() = modelMetadata

    @Synchronized
    fun loadModel(
        modelFile: File,
        nGpuLayers: Int = 0,
        contextSize: Int = 4096,
        nThreads: Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
    ): Result<GGUFMetadata> {
        if (!modelFile.isFile || !modelFile.canRead()) {
            return Result.failure(
                ModelNotLoadedException("Model file is missing or unreadable: ${modelFile.absolutePath}")
            )
        }

        val meta = GGUFParser.parse(modelFile)
        if (!meta.isValid) {
            return Result.failure(
                ModelNotLoadedException("Invalid GGUF file: ${meta.errorMessage}")
            )
        }

        if (!isLibraryLoaded) {
            return Result.failure(
                ModelNotLoadedException(
                    "The GGUF file is valid, but Humai's native llama.cpp runtime is not included in this build. " +
                        "No model was loaded and no simulated response will be generated."
                )
            )
        }

        unloadModel()

        return try {
            val handle = nativeLoadModel(
                modelFile.absolutePath,
                nGpuLayers.coerceAtLeast(0),
                contextSize.coerceIn(512, 8192),
                nThreads.coerceIn(1, 8)
            )
            if (handle == 0L) {
                Result.failure(
                    ModelNotLoadedException(
                        "The native backend rejected the model. Check that this build contains a working llama.cpp implementation."
                    )
                )
            } else {
                // The current JNI contract only returns a model handle. It does
                // not create/return a llama_context, so inference is deliberately
                // not marked ready until that contract is implemented.
                nativeUnloadModel(handle, 0L)
                Result.failure(
                    ModelNotLoadedException(
                        "Native model loading returned a handle, but context creation is not implemented. " +
                            "Inference is disabled rather than reporting a false success."
                    )
                )
            }
        } catch (error: UnsatisfiedLinkError) {
            Log.e(TAG, "JNI entry point missing", error)
            Result.failure(ModelNotLoadedException("Native llama JNI entry point is missing: ${error.message}"))
        } catch (error: Exception) {
            Log.e(TAG, "Model load failed", error)
            Result.failure(ModelNotLoadedException("Model load failed: ${error.message}"))
        }
    }

    @Synchronized
    fun unloadModel() {
        if (modelPointer != 0L && isLibraryLoaded) {
            runCatching { nativeUnloadModel(modelPointer, contextPointer) }
                .onFailure { Log.w(TAG, "Native unload failed", it) }
        }
        modelPointer = 0L
        contextPointer = 0L
        loadedModelPath = null
        modelMetadata = null
    }

    fun streamTokens(
        formattedPrompt: String,
        temperature: Float = 0.7f,
        maxTokens: Int = 1024,
        isCancelled: AtomicBoolean
    ): Flow<String> = flow {
        if (!isModelLoaded) {
            throw ModelNotLoadedException(
                "No working local inference session is loaded. Import a real GGUF model and install a build with the native llama.cpp runtime."
            )
        }

        try {
            val promptTokens = nativeTokenize(modelPointer, formattedPrompt)
            if (promptTokens.isEmpty()) {
                throw ModelNotLoadedException("The native tokenizer returned no prompt tokens.")
            }

            for (token in promptTokens) {
                if (isCancelled.get()) throw CancellationException("Generation stopped by user.")
                if (nativeEval(contextPointer, token) != 0) {
                    throw ModelNotLoadedException("Native prompt evaluation failed.")
                }
            }

            var generated = 0
            while (generated < maxTokens.coerceAtLeast(1)) {
                if (isCancelled.get()) throw CancellationException("Generation stopped by user.")
                val nextToken = nativeSample(contextPointer, temperature.coerceIn(0.0f, 2.0f), 0.9f)
                if (nextToken <= 0 || nextToken == 151645) break

                val piece = nativeTokenToPiece(modelPointer, nextToken)
                if (piece.isNotEmpty()) emit(piece)
                if (nativeEval(contextPointer, nextToken) != 0) {
                    throw ModelNotLoadedException("Native token evaluation failed.")
                }
                generated++
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: UnsatisfiedLinkError) {
            throw ModelNotLoadedException("Native inference function is unavailable: ${error.message}")
        }
    }

    private external fun nativeLoadModel(path: String, gpuLayers: Int, ctxSize: Int, threads: Int): Long
    private external fun nativeUnloadModel(modelPtr: Long, ctxPtr: Long)
    private external fun nativeEval(ctxPtr: Long, token: Int): Int
    private external fun nativeSample(ctxPtr: Long, temp: Float, topP: Float): Int
    private external fun nativeTokenToPiece(modelPtr: Long, token: Int): String
    private external fun nativeTokenize(modelPtr: Long, text: String): IntArray
}
