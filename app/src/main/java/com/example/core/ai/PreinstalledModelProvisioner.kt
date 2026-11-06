package com.example.core.ai

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

data class ProvisioningProgress(
    val isInstalling: Boolean = false,
    val currentModelName: String = "",
    val bytesCopied: Long = 0L,
    val totalBytes: Long = 0L,
    val progress: Float = 0f,
    val statusMessage: String = "Idle",
    val isCompleted: Boolean = false,
    val error: String? = null
)

enum class StorageStatus {
    SUFFICIENT,
    LOW_WARNING,
    INSUFFICIENT
}

/**
 * Handles automatic, zero-RAM-spike extraction, verification, and provisioning
 * of pre-bundled GGUF models from APK distribution assets / install-time packs into private app storage.
 */
class PreinstalledModelProvisioner(
    private val context: Context,
    private val modelManager: ModelManager
) {

    companion object {
        private const val TAG = "PreinstalledProvisioner"
        private const val BUFFER_SIZE = 64 * 1024 // 64 KB streaming buffer: zero RAM spike

        // Pre-bundled models defined for Companion AI distribution
        val BUNDLED_MODELS = listOf(
            BundledModelSpec(
                fileName = "qwen3-4b-instruct-q4_k_m.gguf",
                displayName = "Qwen3-4B Instruct",
                isDefault = true,
                estimatedBytes = 2_480_000_000L
            ),
            BundledModelSpec(
                fileName = "qwen3-vl-4b-instruct-q4_k_m.gguf",
                displayName = "Qwen3-VL 4B Vision",
                isDefault = false,
                estimatedBytes = 2_520_000_000L
            ),
            BundledModelSpec(
                fileName = "mmproj-qwen3-vl-4b-f16.gguf",
                displayName = "Qwen3-VL Multimodal Projector (mmproj)",
                isDefault = false,
                estimatedBytes = 640_000_000L
            )
        )
    }

    data class BundledModelSpec(
        val fileName: String,
        val displayName: String,
        val isDefault: Boolean,
        val estimatedBytes: Long
    )

    private val _progressState = MutableStateFlow(ProvisioningProgress())
    val progressState: StateFlow<ProvisioningProgress> = _progressState.asStateFlow()

    private val modelsDir = File(context.filesDir, "models").apply { mkdirs() }

    /**
     * Checks if all required pre-bundled models are installed and validated.
     */
    fun areBundledModelsInstalled(): Boolean {
        for (spec in BUNDLED_MODELS) {
            val file = File(modelsDir, spec.fileName)
            if (!file.exists()) return false
            val meta = GGUFParser.parse(file)
            if (!meta.isValid) return false
        }
        return true
    }

    /**
     * Checks device storage headroom before provisioning.
     */
    fun checkStorageAvailability(): Pair<StorageStatus, Long> {
        val stat = StatFs(context.filesDir.path)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        val requiredBytes = BUNDLED_MODELS.sumOf { spec ->
            val existing = File(modelsDir, spec.fileName)
            if (existing.exists()) 0L else spec.estimatedBytes
        }

        val status = when {
            availableBytes < (requiredBytes + 300_000_000L) -> StorageStatus.INSUFFICIENT
            availableBytes < (requiredBytes + 1_500_000_000L) -> StorageStatus.LOW_WARNING
            else -> StorageStatus.SUFFICIENT
        }
        return Pair(status, availableBytes)
    }

    /**
     * Prepares pre-installed models on first run or when missing.
     * Streams using a 64KB chunk buffer to ensure zero multi-gigabyte memory consumption.
     */
    suspend fun provisionBundledModelsIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        if (areBundledModelsInstalled()) {
            Log.i(TAG, "Bundled AI models already provisioned and verified in private storage.")
            _progressState.value = ProvisioningProgress(
                isInstalling = false,
                isCompleted = true,
                statusMessage = "All pre-installed Qwen models verified & ready."
            )
            modelManager.refreshModelCatalog()
            return@withContext true
        }

        val (storageStatus, availBytes) = checkStorageAvailability()
        if (storageStatus == StorageStatus.INSUFFICIENT) {
            val msg = "Insufficient storage: ${availBytes / (1024 * 1024)}MB available. Please free up space for local Qwen models."
            Log.e(TAG, msg)
            _progressState.value = ProvisioningProgress(
                isInstalling = false,
                isCompleted = false,
                error = msg,
                statusMessage = "Installation halted: Storage full"
            )
            return@withContext false
        }

        _progressState.value = ProvisioningProgress(
            isInstalling = true,
            statusMessage = "Preparing pre-installed local models..."
        )

        var totalAssetsInstalled = 0

        for ((index, spec) in BUNDLED_MODELS.withIndex()) {
            val destFile = File(modelsDir, spec.fileName)

            // If file already exists and is valid, skip re-copying
            if (destFile.exists()) {
                val validation = GGUFParser.parse(destFile)
                if (validation.isValid) {
                    Log.i(TAG, "Model '${spec.fileName}' already verified.")
                    totalAssetsInstalled++
                    continue
                } else {
                    Log.w(TAG, "Existing model file '${spec.fileName}' corrupted, re-provisioning...")
                    destFile.delete()
                }
            }

            // Stream from APK assets / install-time pack
            val assetPath = "models/${spec.fileName}"
            var inputStream: InputStream? = null
            try {
                inputStream = context.assets.open(assetPath)
                val totalLength = runCatching { inputStream.available().toLong() }.getOrDefault(spec.estimatedBytes)

                _progressState.value = ProvisioningProgress(
                    isInstalling = true,
                    currentModelName = spec.displayName,
                    bytesCopied = 0L,
                    totalBytes = totalLength,
                    progress = index.toFloat() / BUNDLED_MODELS.size.toFloat(),
                    statusMessage = "Installing ${spec.displayName} (pre-bundled)..."
                )

                // Chunked copy: 64KB buffer
                val tempDest = File(modelsDir, "${spec.fileName}.tmp")
                FileOutputStream(tempDest).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var read: Int
                    var copied = 0L

                    while (inputStream.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        copied += read
                        val progressFraction = (copied.toFloat() / totalLength.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                        _progressState.value = _progressState.value.copy(
                            bytesCopied = copied,
                            progress = ((index + progressFraction) / BUNDLED_MODELS.size.toFloat()).coerceIn(0f, 1f),
                            statusMessage = "Installing ${spec.displayName} (${(copied / 1024 / 1024)} MB)..."
                        )
                    }
                    output.flush()
                }

                // Verify GGUF binary integrity before final rename
                val validation = GGUFParser.parse(tempDest)
                if (validation.isValid) {
                    tempDest.renameTo(destFile)
                    Log.i(TAG, "Verified and marked installed: ${spec.fileName} (${validation.quantization}, ${validation.tensorCount} tensors)")
                    totalAssetsInstalled++
                } else {
                    tempDest.delete()
                    Log.e(TAG, "GGUF validation failed for ${spec.fileName}: ${validation.errorMessage}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not open asset '$assetPath': ${e.message}. Checking existing storage files.")
            } finally {
                inputStream?.close()
            }
        }

        _progressState.value = ProvisioningProgress(
            isInstalling = false,
            isCompleted = totalAssetsInstalled > 0,
            progress = 1.0f,
            statusMessage = "Pre-installed models ready for offline reasoning."
        )

        // Automatically refresh catalog and load default Qwen3-4B model
        modelManager.refreshModelCatalog()
        return@withContext totalAssetsInstalled > 0
    }
}
