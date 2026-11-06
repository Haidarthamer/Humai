package com.example.core.ai

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class ModelNotLoadedException(message: String) : Exception(message)

/** Kotlin owner for one real llama.cpp model/context session. */
class LlamaBridge {

    companion object {
        private const val TAG = "LlamaBridge"
        private val isLibraryLoaded: Boolean = try {
            System.loadLibrary("llama-android")
            Log.i(TAG, "Native llama.cpp library loaded.")
            true
        } catch (error: UnsatisfiedLinkError) {
            Log.e(TAG, "Native llama.cpp library unavailable: ${error.message}")
            false
        }

        fun isNativeAvailable(): Boolean = isLibraryLoaded
    }

    private val inferenceMutex = Mutex()
    private var modelPointer: Long = 0L
    private var contextPointer: Long = 0L
    private var loadedModelPath: String? = null
    private var modelMetadata: GGUFMetadata? = null

    val isModelLoaded: Boolean
        get() = modelPointer != 0L && contextPointer != 0L && isLibraryLoaded
    val currentModelPath: String? get() = loadedModelPath
    val currentMetadata: GGUFMetadata? get() = modelMetadata

    fun loadModel(
        modelFile: File,
        nGpuLayers: Int = 0,
        contextSize: Int = 4096,
        nThreads: Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
    ): Result<GGUFMetadata> {
        if (!modelFile.isFile || !modelFile.canRead()) {
            return Result.failure(ModelNotLoadedException("Model file is missing or unreadable: ${modelFile.absolutePath}"))
        }
        if (modelFile.length() < 1L * 1024L * 1024L) {
            return Result.failure(ModelNotLoadedException("Selected file is too small to be a real GGUF model."))
        }
        val metadata = GGUFParser.parse(modelFile)
        if (!metadata.isValid) {
            return Result.failure(ModelNotLoadedException("Invalid GGUF model: ${metadata.errorMessage}"))
        }
        if (!isLibraryLoaded) {
            return Result.failure(ModelNotLoadedException("The llama.cpp native runtime is missing from this APK. Build the CMake target before loading a model."))
        }
        if (!inferenceMutex.tryLock()) {
            return Result.failure(ModelNotLoadedException("The local model is busy generating a response. Try loading the model again after generation finishes."))
        }

        try {
            releaseSession()
            val session = nativeLoadModel(
                modelFile.absolutePath,
                nGpuLayers.coerceAtLeast(0),
                contextSize.coerceIn(512, 8192),
                nThreads.coerceIn(1, 8)
            )
            if (session == 0L) {
                return Result.failure(ModelNotLoadedException("llama.cpp failed to load the model or create its inference context. Check Logcat for native details."))
            }

            modelPointer = session
            contextPointer = session
            loadedModelPath = modelFile.absolutePath
            modelMetadata = metadata
            Log.i(TAG, "Loaded ${metadata.modelName} with a real llama.cpp context.")
            return Result.success(metadata)
        } catch (error: UnsatisfiedLinkError) {
            releaseSession()
            return Result.failure(ModelNotLoadedException("Missing native llama.cpp JNI function: ${error.message}"))
        } catch (error: Exception) {
            releaseSession()
            return Result.failure(ModelNotLoadedException("Model load failed: ${error.message}"))
        } finally {
            inferenceMutex.unlock()
        }
    }

    fun unloadModel() {
        if (!inferenceMutex.tryLock()) {
            Log.w(TAG, "Model unload skipped because an inference operation is active.")
            return
        }
        try {
            releaseSession()
        } finally {
            inferenceMutex.unlock()
        }
    }

    private fun releaseSession() {
        val handle = modelPointer
        modelPointer = 0L
        contextPointer = 0L
        loadedModelPath = null
        modelMetadata = null
        if (handle != 0L && isLibraryLoaded) {
            try {
                nativeUnloadModel(handle, handle)
            } catch (error: Exception) {
                Log.w(TAG, "Native model unload failed: ${error.message}", error)
            }
        }
    }

    fun streamTokens(
        formattedPrompt: String,
        temperature: Float = 0.7f,
        maxTokens: Int = 1024,
        isCancelled: AtomicBoolean
    ): Flow<String> = flow {
        inferenceMutex.withLock {
        if (!isModelLoaded) throw ModelNotLoadedException("No real local llama.cpp inference session is loaded.")
        val handle = contextPointer

        try {
            if (nativeResetContext(handle) != 0) {
                throw ModelNotLoadedException("Could not reset the native inference context.")
            }
            if (nativeSetSampling(handle, temperature, 0.9f) != 0) {
                throw ModelNotLoadedException("Could not initialize the native sampler.")
            }

            val tokens = nativeTokenize(modelPointer, formattedPrompt)
            if (tokens.isEmpty()) {
                throw ModelNotLoadedException("The native tokenizer could not encode this prompt.")
            }
            for (token in tokens) {
                if (isCancelled.get()) throw CancellationException("Generation stopped by user.")
                if (nativeEval(handle, token) != 0) {
                    throw ModelNotLoadedException("llama.cpp failed while evaluating the prompt; the context may be full.")
                }
            }

            var generated = 0
            while (generated < maxTokens.coerceIn(1, 4096)) {
                if (isCancelled.get()) throw CancellationException("Generation stopped by user.")
                val token = nativeSample(handle, temperature, 0.9f)
                if (token < 0) break
                val piece = nativeTokenToPiece(modelPointer, token)
                if (piece.isNotEmpty()) emit(piece)
                if (nativeEval(handle, token) != 0) {
                    throw ModelNotLoadedException("llama.cpp failed while evaluating a generated token.")
                }
                generated++
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: UnsatisfiedLinkError) {
            throw ModelNotLoadedException("Native inference function unavailable: ${error.message}")
        }
        }
    }

    private external fun nativeLoadModel(path: String, gpuLayers: Int, ctxSize: Int, threads: Int): Long
    private external fun nativeUnloadModel(modelPtr: Long, ctxPtr: Long)
    private external fun nativeResetContext(contextPtr: Long): Int
    private external fun nativeSetSampling(contextPtr: Long, temperature: Float, topP: Float): Int
    private external fun nativeEval(contextPtr: Long, token: Int): Int
    private external fun nativeSample(contextPtr: Long, temp: Float, topP: Float): Int
    private external fun nativeTokenToPiece(modelPtr: Long, token: Int): String
    private external fun nativeTokenize(modelPtr: Long, text: String): IntArray
}
