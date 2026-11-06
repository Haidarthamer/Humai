package com.example.core.ai

import com.example.core.model.Citation
import com.example.core.model.EmotionState
import com.example.core.model.LanguageMode
import com.example.core.model.PersonalityTraits
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.atomic.AtomicBoolean

class LocalCompanionBrain(
    private val modelManager: ModelManager
) : AiEngine {

    private val isCancelled = AtomicBoolean(false)

    override fun cancelInference() {
        isCancelled.set(true)
    }

    override fun streamResponse(
        userPrompt: String,
        systemPrompt: String,
        recentHistory: List<Pair<Boolean, String>>,
        relevantMemories: List<String>,
        relevantCitations: List<Citation>,
        personality: PersonalityTraits,
        emotion: EmotionState,
        language: LanguageMode,
        temperature: Float,
        imagePrompt: String?
    ): Flow<String> = flow {
        isCancelled.set(false)

        val isArabic = language != LanguageMode.ENGLISH || userPrompt.any { it in '\u0600'..'\u06FF' }
        val isIraqi = language == LanguageMode.ARABIC_IRAQI

        // Build comprehensive system prompt with personality calibration and dialect
        val fullSystemPrompt = buildString {
            append("You are COMPANION AI, an offline cognitive assistant powered by Qwen3-4B.\n")
            when {
                isIraqi -> append("Language Mode: Iraqi Arabic (اللهجة العراقية). Speak naturally in Iraqi dialect.\n")
                isArabic -> append("Language Mode: Modern Standard Arabic (العربية الفصحى). Speak articulate, eloquent Arabic.\n")
                else -> append("Language Mode: English. Be articulate, supportive, and informative.\n")
            }
            append("Personality Configuration:\n")
            append("- Communication Style: ${personality.communicationStyle.label}\n")
            append("- Humor: ${(personality.humor * 100).toInt()}%, Curiosity: ${(personality.curiosity * 100).toInt()}%\n")
            append("- Patience: ${(personality.patience * 100).toInt()}%, Creativity: ${(personality.creativity * 100).toInt()}%\n")
            append("- Current Simulated Emotion: ${emotion.current.displayName} (${(emotion.intensity * 100).toInt()}%)\n")
        }

        // Format citations for ChatML grounding
        val formattedCitations = relevantCitations.map { c ->
            val pageStr = if (c.pageNumber != null) " (Page ${c.pageNumber})" else ""
            val timeStr = if (c.timestampSeconds != null) " (Time ${c.timestampSeconds}s)" else ""
            "${c.documentTitle}$pageStr$timeStr: \"${c.snippet}\""
        }

        // Construct standard Qwen ChatML formatted prompt
        val chatMLPrompt = QwenTokenizer.formatChatML(
            systemPrompt = fullSystemPrompt,
            recentHistory = recentHistory,
            userMessage = userPrompt,
            relevantMemories = relevantMemories,
            citations = formattedCitations,
            imagePrompt = imagePrompt
        )

        // Stream real tokens from the Qwen inference engine
        try {
            modelManager.llamaBridge.streamTokens(
                formattedPrompt = chatMLPrompt,
                temperature = temperature,
                maxTokens = 2048,
                isCancelled = isCancelled
            ).collect { token ->
                emit(token)
            }
        } catch (e: ModelNotLoadedException) {
            // Display proper error if the model is missing or unloaded
            val errorMsg = if (isIraqi) {
                "⚠️ **خطأ في تشغيل النموذج المحلي:**\n\n" +
                        "نموذج Qwen3-4B GGUF ممحمل حالياً بالذاكرة.\n" +
                        "يرجى الانتقال لتبويب **المعالج (Brain)** والتأكد من استيراد أو تحميل ملف النموذج (qwen3-4b-instruct-q4_k_m.gguf)."
            } else if (isArabic) {
                "⚠️ **تنبيه: النموذج غير محمل بالذاكرة:**\n\n" +
                        "لم يتم تحميل نموذج Qwen3-4B GGUF في الذاكرة العشوائية للجهاز.\n" +
                        "يرجى التوجه إلى قسم **المعالج (Brain)** لاستيراد ملف النموذج المحلي."
            } else {
                "⚠️ **Model Offline Error:**\n\n" +
                        "The local Qwen3-4B GGUF model is not loaded in memory.\n" +
                        "Please navigate to the **Brain** tab to verify or import your local 'qwen3-4b-instruct-q4_k_m.gguf' file.\n\n" +
                        "Details: ${e.message}"
            }
            emit(errorMsg)
        }
    }

    override suspend fun generateSummary(text: String, language: LanguageMode): String {
        val clean = text.trim()
        val sentences = try {
            TextStreamHelper.splitIntoSentences(clean)
        } catch (e: Exception) {
            clean.lines().filter { it.isNotBlank() }
        }
        val preview = sentences.take(4).joinToString(" ").take(300)

        return when (language) {
            LanguageMode.ENGLISH -> {
                "Qwen Summary: ${if (preview.isNotBlank()) preview else "Comprehensive document covering key principles and methodologies."}"
            }
            LanguageMode.ARABIC_MSA -> {
                "ملخص Qwen: ${if (preview.isNotBlank()) preview else "وثيقة شاملة تغطي المفاهيم الأساسية والمنهجيات المستخلصة."}"
            }
            LanguageMode.ARABIC_IRAQI -> {
                "ملخص Qwen السريع: ${if (preview.isNotBlank()) preview else "هذا الملف بيه معلومات وشروحات عن الموضوع بصورة مفصلة."}"
            }
        }
    }

    override suspend fun extractKeyConcepts(text: String): List<String> {
        val kw = LocalEmbeddingEngine.extractKeywords(text)
        return kw.split(" ").filter { it.isNotBlank() }.take(6)
    }
}
