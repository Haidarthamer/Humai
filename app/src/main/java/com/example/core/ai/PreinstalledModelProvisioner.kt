package com.example.core.ai

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

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
 * Reports model availability without pretending that tiny placeholder assets
 * are multi-gigabyte model weights. Model binaries are intentionally not
 * committed to this repository or embedded in the APK.
 */
class PreinstalledModelProvisioner(
    private val context: Context,
    private val modelManager: ModelManager
) {
    companion object {
        private const val MINIMUM_QWEN3_BYTES = 100L * 1024L * 1024L

        // No model weights are currently bundled in the repository.
        val BUNDLED_MODELS: List<BundledModelSpec> = emptyList()
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

    fun areBundledModelsInstalled(): Boolean {
        val defaultModel = File(modelsDir, "qwen3-4b-instruct-q4_k_m.gguf")
        return defaultModel.isFile &&
            defaultModel.length() >= MINIMUM_QWEN3_BYTES &&
            GGUFParser.parse(defaultModel).isValid
    }

    fun checkStorageAvailability(): Pair<StorageStatus, Long> {
        val stat = StatFs(context.filesDir.path)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        val status = when {
            availableBytes < 300L * 1024L * 1024L -> StorageStatus.INSUFFICIENT
            availableBytes < 1_500L * 1024L * 1024L -> StorageStatus.LOW_WARNING
            else -> StorageStatus.SUFFICIENT
        }
        return status to availableBytes
    }

    /**
     * This project currently contains no real bundled model weights and no
     * automatic model downloader. Keep this operation honest and direct users
     * to import a genuine GGUF file from the Brain screen.
     */
    suspend fun provisionBundledModelsIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        modelManager.refreshModelCatalog()

        if (areBundledModelsInstalled()) {
            _progressState.value = ProvisioningProgress(
                isInstalling = false,
                isCompleted = false,
                statusMessage = "A real Qwen3 GGUF file is present, but the native inference runtime is not ready."
            )
            return@withContext false
        }

        val message =
            "No real model weights are bundled with this build. The small files previously placed in assets/models were placeholders, not usable GGUF models. Import a genuine GGUF model from the Brain screen."
        _progressState.value = ProvisioningProgress(
            isInstalling = false,
            isCompleted = false,
            error = message,
            statusMessage = "Model weights not installed"
        )
        false
    }
}
