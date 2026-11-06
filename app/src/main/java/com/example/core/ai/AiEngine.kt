package com.example.core.ai

import com.example.core.model.Citation
import com.example.core.model.EmotionState
import com.example.core.model.LanguageMode
import com.example.core.model.PersonalityTraits
import kotlinx.coroutines.flow.Flow

interface AiEngine {
    /**
     * Streams generated tokens asynchronously.
     */
    fun streamResponse(
        userPrompt: String,
        systemPrompt: String,
        recentHistory: List<Pair<Boolean, String>>, // isUser to text
        relevantMemories: List<String>,
        relevantCitations: List<Citation>,
        personality: PersonalityTraits,
        emotion: EmotionState,
        language: LanguageMode,
        temperature: Float = 0.7f,
        imagePrompt: String? = null
    ): Flow<String>

    /**
     * Generates a concise summary for imported text/document.
     */
    suspend fun generateSummary(text: String, language: LanguageMode): String

    /**
     * Extracts key concepts and entities from content.
     */
    suspend fun extractKeyConcepts(text: String): List<String>

    /**
     * Cancel ongoing inference.
     */
    fun cancelInference()
}
