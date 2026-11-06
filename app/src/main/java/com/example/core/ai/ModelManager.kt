package com.example.core.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.example.core.model.GGUFModelInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

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

enum class ModelLoadState {
    UNLOADED,
    READY_TO_LOAD,
    LOADED,
    ERROR
}

class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"
        const val DEFAULT_MODEL_ID = "qwen3-4b"
    }

    val llamaBridge = LlamaBridge()

    private val _availableModels = MutableStateFlow<List<GGUFModelInfo>>(emptyList())
    val availableModels: StateFlow<List<GGUFModelInfo>> = _availableModels.asStateFlow()

    private val _activeModel = MutableStateFlow<GGUFModelInfo?>(null)
    val activeModel: StateFlow<GGUFModelInfo?> = _activeModel.asStateFlow()

    private val _modelLoadState = MutableStateFlow(ModelLoadState.UNLOADED)
    val modelLoadState: StateFlow<ModelLoadState> = _modelLoadState.asStateFlow()

    private val _modelStatusDetail = MutableStateFlow("Initializing engine...")
    val modelStatusDetail: StateFlow<String> = _modelStatusDetail.asStateFlow()

    private val _temperature = MutableStateFlow(0.7f)
    val temperature: StateFlow<Float> = _temperature.asStateFlow()

    private val _contextWindow = MutableStateFlow(4096) // Default 4096 for Snapdragon 8 Gen 3
    val contextWindow: StateFlow<Int> = _contextWindow.asStateFlow()

    private val _gpuAcceleration = MutableStateFlow(false) // CPU-only llama.cpp build; Vulkan is not linked

    val gpuAcceleration: StateFlow<Boolean> = _gpuAcceleration.asStateFlow()

    init {
        refreshModelCatalog()
    }

    fun getHardwareReport(): HardwareReport {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)

        val totalRamMb = memInfo.totalMem / (1024 * 1024)
        val availRamMb = memInfo.availMem / (1024 * 1024)

        val stat = StatFs(Environment.getDataDirectory().path)
        val totalBytes = stat.blockCountLong * stat.blockSizeLong
        val availBytes = stat.availableBlocksLong * stat.blockSizeLong
        val totalStorageGb = totalBytes / (1024f * 1024f * 1024f)
        val availStorageGb = availBytes / (1024f * 1024f * 1024f)

        val cores = Runtime.getRuntime().availableProcessors()
        val abis = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"

        val deviceName = "${Build.MANUFACTURER.capitalize()} ${Build.MODEL}"
        val isSnapdragon8Gen3 = Build.VERSION.SDK_INT >= 31 && Build.SOC_MODEL.contains("8650", ignoreCase = true)

        return HardwareReport(
            deviceName = if (isSnapdragon8Gen3) "$deviceName (Snapdragon 8 Gen 3)" else deviceName,
            processorName = if (isSnapdragon8Gen3) "Qualcomm Snapdragon 8 Gen 3 (Adreno 750 GPU)" else "ARM64 Octa-Core",
            totalRamMb = totalRamMb,
            availableRamMb = availRamMb,
            isLowRamDevice = memInfo.lowMemory,
            totalStorageGb = totalStorageGb,
            availableStorageGb = availStorageGb,
            cpuCores = cores,
            supportedAbi = abis,
            recommendedModelId = DEFAULT_MODEL_ID,
            recommendedThreads = 6, // 6 worker threads for optimal throughput & thermal control
            gpuOffloadLayers = 0    // No GPU backend is linked in this build
        )
    }

    fun refreshModelCatalog() {
        val modelsDir = File(context.filesDir, "models").apply { mkdirs() }

        val qwen3File = File(modelsDir, "qwen3-4b-instruct-q4_k_m.gguf")
        val qwen3VlFile = File(modelsDir, "qwen3-vl-4b-instruct-q4_k_m.gguf")
        val mmprojFile = File(modelsDir, "mmproj-qwen3-vl-4b-f16.gguf")
        val qwen25File = File(modelsDir, "qwen-2.5-1.5b.gguf")
        val llama32File = File(modelsDir, "llama-3.2-1b.gguf")

        val qwen3Valid = isUsableModelFile(qwen3File, 100L * 1024L * 1024L)
        val qwen3VlValid = isUsableModelFile(qwen3VlFile, 100L * 1024L * 1024L)
        val mmprojValid = isUsableModelFile(mmprojFile, 50L * 1024L * 1024L)
        val qwen25Valid = isUsableModelFile(qwen25File, 25L * 1024L * 1024L)
        val llama32Valid = isUsableModelFile(llama32File, 25L * 1024L * 1024L)

        val predefined = listOf(
            GGUFModelInfo(
                id = "qwen3-4b",
                name = "Qwen3-4B Instruct",
                architecture = "Qwen 3 (Dense)",
                parameterCount = "4.0B",
                quantization = "Q4_K_M",
                fileSizeBytes = if (qwen3File.isFile) qwen3File.length() else 0L,
                isInstalled = qwen3Valid,
                isLoaded = _activeModel.value?.id == "qwen3-4b" && llamaBridge.isModelLoaded,
                localFilePath = qwen3File.absolutePath,
                isPreinstalled = false,
                modelRole = com.example.core.model.ModelRole.TEXT_DEFAULT,
                compatibility = "Requires a real GGUF file and a working native runtime; not hardware-verified",
                integrityVerified = qwen3Valid
            ),
            GGUFModelInfo(
                id = "qwen3-vl-4b",
                name = "Qwen3-VL 4B Vision",
                architecture = "Qwen 3 Vision-Language",
                parameterCount = "4.2B",
                quantization = "Q4_K_M",
                fileSizeBytes = if (qwen3VlFile.isFile) qwen3VlFile.length() else 0L,
                isInstalled = qwen3VlValid,
                isLoaded = _activeModel.value?.id == "qwen3-vl-4b" && llamaBridge.isModelLoaded,
                localFilePath = qwen3VlFile.absolutePath,
                isPreinstalled = false,
                modelRole = com.example.core.model.ModelRole.VISION_MULTIMODAL,
                compatibility = "Requires compatible vision GGUF files and a multimodal native runtime; not verified",
                integrityVerified = qwen3VlValid
            ),
            GGUFModelInfo(
                id = "mmproj-qwen3-vl-4b",
                name = "Qwen3-VL Multimodal Projector",
                architecture = "CLIP Vision Projector (mmproj)",
                parameterCount = "Vision Adapter",
                quantization = "F16",
                fileSizeBytes = if (mmprojFile.isFile) mmprojFile.length() else 0L,
                isInstalled = mmprojValid,
                isLoaded = false,
                localFilePath = mmprojFile.absolutePath,
                isPreinstalled = false,
                modelRole = com.example.core.model.ModelRole.VISION_PROJECTOR,
                compatibility = "Requires a compatible multimodal native runtime; not verified",
                integrityVerified = mmprojValid
            ),
            GGUFModelInfo(
                id = "qwen-2.5-1.5b",
                name = "Qwen 2.5 1.5B Instruct",
                architecture = "Qwen 2.5",
                parameterCount = "1.5B",
                quantization = "Q4_K_M",
                fileSizeBytes = if (qwen25File.isFile) qwen25File.length() else 0L,
                isInstalled = qwen25Valid,
                isLoaded = _activeModel.value?.id == "qwen-2.5-1.5b" && llamaBridge.isModelLoaded,
                localFilePath = qwen25File.absolutePath,
                isPreinstalled = false,
                modelRole = com.example.core.model.ModelRole.OPTIONAL_TEXT,
                compatibility = "Low RAM / Mid-Tier Fallback",
                integrityVerified = qwen25Valid
            ),
            GGUFModelInfo(
                id = "llama-3.2-1b",
                name = "Llama 3.2 1B Instruct",
                architecture = "Llama 3.2",
                parameterCount = "1.2B",
                quantization = "Q4_K_M",
                fileSizeBytes = if (llama32File.isFile) llama32File.length() else 0L,
                isInstalled = llama32Valid,
                isLoaded = _activeModel.value?.id == "llama-3.2-1b" && llamaBridge.isModelLoaded,
                localFilePath = llama32File.absolutePath,
                isPreinstalled = false,
                modelRole = com.example.core.model.ModelRole.OPTIONAL_TEXT,
                compatibility = "Low RAM / Mid-Tier Fallback",
                integrityVerified = llama32Valid
            )
        )

        _availableModels.value = predefined
        if (_activeModel.value == null) {
            val defaultModel = predefined.first() // Qwen3-4B
            _activeModel.value = defaultModel
            loadSelectedModel(defaultModel)
        }
    }

    fun selectModel(model: GGUFModelInfo) {
        if (model.modelRole == com.example.core.model.ModelRole.VISION_MULTIMODAL ||
            model.modelRole == com.example.core.model.ModelRole.VISION_PROJECTOR
        ) {
            _modelLoadState.value = ModelLoadState.ERROR
            _modelStatusDetail.value = "Vision inference is not integrated in this build; this file cannot be selected as a text model."
            return
        }
        _activeModel.value = model
        loadSelectedModel(model)
        refreshModelCatalog()
    }

    private fun loadSelectedModel(model: GGUFModelInfo) {
        val file = model.localFilePath?.let { File(it) } ?: File(context.filesDir, "models/${model.id}.gguf")
        if (file.exists()) {
            val result = llamaBridge.loadModel(
                modelFile = file,
                nGpuLayers = 0,
                contextSize = _contextWindow.value,
                nThreads = 6
            )
            if (result.isSuccess) {
                _modelLoadState.value = ModelLoadState.LOADED
                _modelStatusDetail.value = "Loaded in memory (${model.quantization}, 4096 ctx)"
            } else {
                _modelLoadState.value = ModelLoadState.ERROR
                _modelStatusDetail.value = result.exceptionOrNull()?.message ?: "Load failed"
            }
        } else {
            // Check if model file needs to be imported
            _modelLoadState.value = ModelLoadState.READY_TO_LOAD
            _modelStatusDetail.value = "Model file not installed on disk. Import '${file.name}' to deploy."
        }
    }

    fun updateTemperature(value: Float) {
        _temperature.value = value.coerceIn(0.1f, 1.5f)
    }

    fun updateContextWindow(size: Int) {
        _contextWindow.value = size.coerceIn(1024, 8192)
    }

    fun setGpuAcceleration(enabled: Boolean) {
        // Prevent the UI from claiming GPU offload before a Vulkan backend is built.
        _gpuAcceleration.value = false
    }

    private fun isUsableModelFile(file: File, minimumBytes: Long): Boolean {
        if (!file.isFile || !file.canRead() || file.length() < minimumBytes) return false
        return GGUFParser.parse(file).isValid
    }

    fun registerImportedModel(file: File): GGUFModelInfo {
        require(file.isFile && file.canRead()) { "Selected model file is missing or unreadable." }
        require(file.length() >= 1L * 1024L * 1024L) {
            "Selected file is too small to be a real model. The repository's bundled 1.2 KB files are placeholders, not GGUF weights."
        }
        val meta = GGUFParser.parse(file)
        require(meta.isValid) { "Selected file is not a valid GGUF model: ${meta.errorMessage}" }
        val model = GGUFModelInfo(
            id = "custom-${file.nameWithoutExtension}",
            name = if (meta.isValid) meta.modelName else file.name,
            architecture = if (meta.isValid) meta.architecture else "Custom GGUF",
            parameterCount = "${meta.tensorCount} tensors",
            quantization = if (meta.isValid) meta.quantization else "GGUF",
            fileSizeBytes = file.length(),
            isInstalled = true,
            isLoaded = false,
            localFilePath = file.absolutePath
        )
        _activeModel.value = model
        _availableModels.value = _availableModels.value + model
        loadSelectedModel(model)
        return model
    }
}
