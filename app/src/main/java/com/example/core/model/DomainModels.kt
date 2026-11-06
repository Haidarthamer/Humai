package com.example.core.model

enum class LanguageMode(val code: String, val displayName: String, val nativeName: String) {
    ENGLISH("en", "English", "English"),
    ARABIC_MSA("ar", "Modern Standard Arabic", "العربية الفصحى"),
    ARABIC_IRAQI("ar_iq", "Iraqi Arabic", "اللهجة العراقية")
}

enum class FileType {
    PDF, TXT, DOCX, MARKDOWN, IMAGE, VIDEO, AUDIO, NOTE;

    companion object {
        fun fromExtension(ext: String): FileType {
            return when (ext.lowercase()) {
                "pdf" -> PDF
                "txt" -> TXT
                "docx", "doc" -> DOCX
                "md", "markdown" -> MARKDOWN
                "jpg", "jpeg", "png", "webp", "gif", "bmp" -> IMAGE
                "mp4", "mkv", "mov", "webm", "avi" -> VIDEO
                "mp3", "wav", "m4a", "ogg", "aac", "flac" -> AUDIO
                else -> TXT
            }
        }
    }
}

enum class IngestionStatus {
    PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED
}

enum class MemoryTier {
    WORKING,     // Active short-term scratchpad & conversational buffer
    EPISODIC,    // Past significant events, milestones, and user interactions
    SEMANTIC,    // Factual knowledge, concepts learned from documents/media
    PERSONALITY  // User preferences, communication traits, recurring topics
}

enum class SimulatedEmotion(val displayName: String, val arabicName: String, val description: String) {
    CALMNESS("Calm", "هادئ", "Balanced neural equilibrium and steady processing"),
    CURIOSITY("Curious", "فضولي", "Eager to explore new concepts and connect information"),
    HAPPINESS("Joyful", "مسرور", "High satisfaction from successful synthesis and bonding"),
    EXCITEMENT("Excited", "متحمس", "High arousal upon discovering complex patterns"),
    INTEREST("Interested", "مهتم", "Deep analytical engagement in the current subject"),
    CONFUSION("Puzzled", "متحير", "Encountering ambiguous or contradictory inputs"),
    SADNESS("Melancholy", "حزين", "Reflective empathy regarding distressing topics"),
    FRUSTRATION("Strained", "مجهد", "Encountering parsing friction or constrained context")
}

enum class CommunicationStyle(val label: String, val arabicLabel: String) {
    EMPATHETIC("Empathetic & Warm", "ودود ومتعاطف"),
    ANALYTICAL("Analytical & Precise", "تحليلي ودقيق"),
    PLAYFUL("Playful & Creative", "مرح ومبتكر"),
    DIRECT("Direct & Concise", "موجز ومباشر")
}

data class PersonalityTraits(
    val humor: Float = 0.55f,        // 0.0 to 1.0
    val curiosity: Float = 0.85f,
    val friendliness: Float = 0.90f,
    val patience: Float = 0.80f,
    val creativity: Float = 0.70f,
    val formality: Float = 0.40f,
    val enthusiasm: Float = 0.75f,
    val communicationStyle: CommunicationStyle = CommunicationStyle.EMPATHETIC
)

data class EmotionState(
    val current: SimulatedEmotion = SimulatedEmotion.CURIOSITY,
    val intensity: Float = 0.7f, // 0.0 to 1.0
    val lastTrigger: String = "System initialized and ready to learn",
    val triggerTimestamp: Long = System.currentTimeMillis()
)

data class Citation(
    val documentTitle: String,
    val pageNumber: Int? = null,
    val timestampSeconds: Long? = null,
    val snippet: String
)

enum class ModelRole {
    TEXT_DEFAULT,
    VISION_MULTIMODAL,
    VISION_PROJECTOR,
    OPTIONAL_TEXT
}

data class GGUFModelInfo(
    val id: String,
    val name: String,
    val architecture: String, // Qwen 2.5, Qwen 3, Llama 3.2
    val parameterCount: String, // 0.5B, 1.5B, 4.0B
    val quantization: String, // Q4_K_M, Q5_K_M, F16
    val fileSizeBytes: Long,
    val isInstalled: Boolean,
    val isLoaded: Boolean,
    val localFilePath: String? = null,
    val isPreinstalled: Boolean = false,
    val modelRole: ModelRole = ModelRole.TEXT_DEFAULT,
    val compatibility: String = "Snapdragon 8 Gen 3 & 12GB RAM",
    val integrityVerified: Boolean = false
)
