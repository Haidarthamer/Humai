package com.example.core.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.example.core.model.GGUFModelInfo
import com.example.core.model.ModelRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale

data class HardwareReport(
    val deviceName: String,
    val processorName: String,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val isLowRamDevice: Boolean,
    val totalStorageGb: Float,
    val availableStorageGb: Float,
    val cpuCores: Int,
    val supportedAbi: String,
    val recommendedModelId: String,
    val recommendedThreads: Int,
    val gpuOffloadLayers: Int
)

enum class ModelLoadState { UNLOADED, READY_TO_LOAD, LOADED, ERROR }

class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"
        const val DEFAULT_MODEL_ID = "qwen3-4b"
        private const val MINIMUM_MODEL_BYTES = 1L * 1024L * 1024L
    }

    val llamaBridge = LlamaBridge()

    private val _availableModels = MutableStateFlow<List<GGUFModelInfo>>(emptyList())
    val availableModels: StateFlow<List<GGUFModelInfo>> = _availableModels.asStateFlow()
    private val _activeModel = MutableStateFlow<GGUFModelInfo?>(null)
    val activeModel: StateFlow<GGUFModelInfo?> = _activeModel.asStateFlow()
    private val _modelLoadState = MutableStateFlow(ModelLoadState.UNLOADED)
    val modelLoadState: StateFlow<ModelLoadState> = _modelLoadState.asStateFlow()
    private val _modelStatusDetail = MutableStateFlow("Initializing local model catalogue...")
    val modelStatusDetail: StateFlow<String> = _modelStatusDetail.asStateFlow()
    private val _temperature = MutableStateFlow(0.7f)
    val temperature: StateFlow<Float> = _temperature.asStateFlow()
    private val _contextWindow = MutableStateFlow(4096)
    val contextWindow: StateFlow<Int> = _contextWindow.asStateFlow()
    private val _gpuAcceleration = MutableStateFlow(false)
    val gpuAcceleration: StateFlow<Boolean> = _gpuAcceleration.asStateFlow()

    init { refreshModelCatalog() }

    fun getHardwareReport(): HardwareReport {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memory)
        val totalRamMb = memory.totalMem / (1024L * 1024L)
        val availableRamMb = memory.availMem / (1024L * 1024L)

        val stat = StatFs(Environment.getDataDirectory().path)
        val totalBytes = stat.blockCountLong * stat.blockSizeLong
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        val cores = Runtime.getRuntime().availableProcessors()
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        val isSnapdragon8Gen3 =
            Build.VERSION.SDK_INT >= 31 && Build.SOC_MODEL.contains("8650", ignoreCase = true)
        val processor = if (isSnapdragon8Gen3) {
            "Qualcomm Snapdragon 8 Gen 3 / Adreno 750"
        } else {
            "Android CPU (" + Build.SOC_MODEL.ifBlank { "unknown SoC" } + ")"
        }
        val recommended = _availableModels.value.firstOrNull {
            it.modelRole == ModelRole.TEXT_DEFAULT || it.modelRole == ModelRole.OPTIONAL_TEXT
        }

        return HardwareReport(
            deviceName = Build.MANUFACTURER + " " + Build.MODEL,
            processorName = processor,
            totalRamMb = totalRamMb,
            availableRamMb = availableRamMb,
            isLowRamDevice = memory.lowMemory,
            totalStorageGb = totalBytes / (1024f * 1024f * 1024f),
            availableStorageGb = availableBytes / (1024f * 1024f * 1024f),
            cpuCores = cores,
            supportedAbi = abi,
            recommendedModelId = recommended?.id ?: DEFAULT_MODEL_ID,
            recommendedThreads = cores.coerceIn(2, 6),
            gpuOffloadLayers = 0
        )
    }

    /** Scan app-private storage so imported models remain visible after restart. */
    fun refreshModelCatalog() {
        val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
        val files = modelsDir.listFiles { file ->
            file.isFile && file.extension.equals("gguf", ignoreCase = true)
        }.orEmpty()

        val models = files.mapNotNull { file ->
            if (!file.canRead() || file.length() < MINIMUM_MODEL_BYTES) {
                Log.w(TAG, "Ignoring unreadable or undersized model: " + file.name)
                return@mapNotNull null
            }

            val metadata = GGUFParser.parse(file)
            if (!metadata.isValid) {
                Log.w(TAG, "Ignoring invalid GGUF " + file.name + ": " + metadata.errorMessage)
                return@mapNotNull null
            }

            val role = inferModelRole(metadata.architecture, file.name)
            val normalizedId = file.nameWithoutExtension.lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9._-]+"), "-").trim('-')
            val isDefault = file.name.equals("qwen3-4b-instruct-q4_k_m.gguf", true)
            val isLoaded = _activeModel.value?.localFilePath == file.absolutePath &&
                llamaBridge.isModelLoaded

            GGUFModelInfo(
                id = if (isDefault) DEFAULT_MODEL_ID else "local-" + normalizedId,
                name = metadata.modelName.takeIf { it.isNotBlank() && it != "unknown" }
                    ?: file.nameWithoutExtension,
                architecture = metadata.architecture,
                parameterCount = "Not reported",
                quantization = metadata.quantization,
                fileSizeBytes = file.length(),
                isInstalled = true,
                isLoaded = isLoaded,
                localFilePath = file.absolutePath,
                isPreinstalled = false,
                modelRole = role,
                compatibility = when (role) {
                    ModelRole.VISION_MULTIMODAL -> "Vision runtime is not integrated in this build"
                    ModelRole.VISION_PROJECTOR -> "Projector file; requires a compatible multimodal runtime"
                    else -> if (LlamaBridge.isNativeAvailable()) {
                        "Text model; CPU llama.cpp runtime"
                    } else {
                        "Text model; native runtime must be built into the APK"
                    }
                },
                // Parser accepted the GGUF header/metadata. This is not a full
                // checksum verification of every tensor block.
                integrityVerified = true
            )
        }.sortedWith(
            compareByDescending<GGUFModelInfo> { it.id == DEFAULT_MODEL_ID }
                .thenBy { it.name.lowercase(Locale.ROOT) }
        )

        _availableModels.value = models
        val oldPath = _activeModel.value?.localFilePath
        val selected = models.firstOrNull { it.localFilePath == oldPath } ?: models.firstOrNull {
            it.modelRole == ModelRole.TEXT_DEFAULT || it.modelRole == ModelRole.OPTIONAL_TEXT
        }
        _activeModel.value = selected

        if (selected == null) {
            _modelLoadState.value = ModelLoadState.READY_TO_LOAD
            _modelStatusDetail.value = "No valid local GGUF model imported. Import a text GGUF model to begin."
        } else if (llamaBridge.isModelLoaded && llamaBridge.currentModelPath == selected.localFilePath) {
            _modelLoadState.value = ModelLoadState.LOADED
            _modelStatusDetail.value = "Loaded in memory (" + selected.quantization + ")"
        } else {
            _modelLoadState.value = ModelLoadState.READY_TO_LOAD
            _modelStatusDetail.value = "Found " + selected.name + "; select it to load into memory."
        }
    }

    private fun inferModelRole(architecture: String, fileName: String): ModelRole {
        val marker = architecture.lowercase(Locale.ROOT) + " " + fileName.lowercase(Locale.ROOT)
        return when {
            marker.contains("mmproj") || marker.contains("projector") || marker.contains("clip") ->
                ModelRole.VISION_PROJECTOR
            marker.contains("vision") || marker.contains("-vl") || marker.contains("_vl") ||
                marker.contains("qwen3vl") || marker.contains("qwen2vl") || marker.contains("multimodal") -> ModelRole.VISION_MULTIMODAL
            marker.contains("qwen3") -> ModelRole.TEXT_DEFAULT
            else -> ModelRole.OPTIONAL_TEXT
        }
    }

    fun selectModel(model: GGUFModelInfo) {
        if (model.modelRole == ModelRole.VISION_MULTIMODAL ||
            model.modelRole == ModelRole.VISION_PROJECTOR
        ) {
            _modelLoadState.value = ModelLoadState.ERROR
            _modelStatusDetail.value =
                "Vision inference is not integrated in this build; this file cannot be selected as a text model."
            return
        }

        val latest = _availableModels.value.firstOrNull { it.localFilePath == model.localFilePath }
        if (latest == null) {
            _modelLoadState.value = ModelLoadState.ERROR
            _modelStatusDetail.value = "Model file is no longer present. Refresh the catalogue and import it again."
            return
        }
        _activeModel.value = latest
        loadSelectedModel(latest)
    }

    private fun loadSelectedModel(model: GGUFModelInfo) {
        val file = model.localFilePath?.let(::File)
        if (file == null || !file.isFile) {
            _modelLoadState.value = ModelLoadState.READY_TO_LOAD
            _modelStatusDetail.value = "Model file is not present on disk."
            return
        }

        _modelLoadState.value = ModelLoadState.UNLOADED
        _modelStatusDetail.value = "Loading " + file.name + " with the local CPU runtime..."
        val result = llamaBridge.loadModel(
            modelFile = file,
            nGpuLayers = 0,
            contextSize = _contextWindow.value,
            nThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
        )
        if (result.isSuccess) {
            _modelLoadState.value = ModelLoadState.LOADED
            _modelStatusDetail.value =
                "Loaded in memory (" + model.quantization + ", " + _contextWindow.value + " context)"
            refreshModelCatalogFlagsOnly()
        } else {
            _modelLoadState.value = ModelLoadState.ERROR
            _modelStatusDetail.value = result.exceptionOrNull()?.message ?: "Model load failed."
        }
    }

    private fun refreshModelCatalogFlagsOnly() {
        _availableModels.value = _availableModels.value.map { model ->
            model.copy(isLoaded = model.localFilePath == llamaBridge.currentModelPath && llamaBridge.isModelLoaded)
        }
    }

    fun updateTemperature(value: Float) {
        _temperature.value = value.coerceIn(0.1f, 1.5f)
    }

    fun updateContextWindow(size: Int) {
        _contextWindow.value = size.coerceIn(1024, 8192)
    }

    fun setGpuAcceleration(enabled: Boolean) {
        _gpuAcceleration.value = false
    }

    fun registerImportedModel(file: File): GGUFModelInfo {
        require(file.isFile && file.canRead()) { "Selected model file is missing or unreadable." }
        require(file.length() >= MINIMUM_MODEL_BYTES) { "Selected file is too small to be a real GGUF model." }
        val metadata = GGUFParser.parse(file)
        require(metadata.isValid) {
            "Selected file is not a valid GGUF model: " + metadata.errorMessage
        }

        val role = inferModelRole(metadata.architecture, file.name)
        refreshModelCatalog()
        val model = _availableModels.value.firstOrNull { it.localFilePath == file.absolutePath }
            ?: GGUFModelInfo(
                id = "local-" + file.nameWithoutExtension.lowercase(Locale.ROOT),
                name = metadata.modelName.takeIf { it.isNotBlank() && it != "unknown" }
                    ?: file.nameWithoutExtension,
                architecture = metadata.architecture,
                parameterCount = "Not reported",
                quantization = metadata.quantization,
                fileSizeBytes = file.length(),
                isInstalled = true,
                isLoaded = false,
                localFilePath = file.absolutePath,
                isPreinstalled = false,
                modelRole = role,
                compatibility = "Imported local model",
                integrityVerified = true
            )

        if (role == ModelRole.VISION_MULTIMODAL || role == ModelRole.VISION_PROJECTOR) {
            _modelStatusDetail.value =
                "Vision file imported, but multimodal inference is not integrated in this build."
            return model
        }

        _activeModel.value = model
        loadSelectedModel(model)
        return model
    }
}
