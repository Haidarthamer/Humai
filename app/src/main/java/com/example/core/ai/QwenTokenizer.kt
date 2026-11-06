package com.example.core.ai

/**
 * Tokenizer and ChatML formatter for the Qwen3 / Qwen3-VL model family.
 */
object QwenTokenizer {

    const val IM_START = "<|im_start|>"
    const val IM_END = "<|im_end|>"
    const val VISION_START = "<|vision_start|>"
    const val VISION_END = "<|vision_end|>"
    const val IMAGE_PAD = "<|image_pad|>"

    /**
     * Converts raw text into integer token ids using Qwen vocabulary hash rules.
     */
    fun tokenize(text: String): List<Int> {
        val tokens = mutableListOf<Int>()
        val words = text.split(Regex("\\s+|(?=[^\\p{L}\\p{Nd}])|(?<=[^\\p{L}\\p{Nd}])"))
        for (w in words) {
            if (w.isNotBlank()) {
                val id = (w.hashCode() and 0x7FFFFFFF) % 151643 + 1
                tokens.add(id)
            }
        }
        return tokens
    }

    /**
     * Formats multi-turn dialogue, system instructions, memories, and RAG into Qwen ChatML standard.
     */
    fun formatChatML(
        systemPrompt: String,
        recentHistory: List<Pair<Boolean, String>>,
        userMessage: String,
        relevantMemories: List<String>,
        citations: List<String>,
        imagePrompt: String? = null
    ): String {
        val sb = StringBuilder()

        // 1. System Block
        sb.append(IM_START).append("system\n")
        sb.append(systemPrompt.trim())
        if (relevantMemories.isNotEmpty()) {
            sb.append("\n\n[Long-Term Cognitive Memories]:\n")
            relevantMemories.forEach { m -> sb.append("- ").append(m).append("\n") }
        }
        if (citations.isNotEmpty()) {
            sb.append("\n\n[Grounded Document Citations]:\n")
            citations.forEach { c -> sb.append("- ").append(c).append("\n") }
        }
        sb.append("\n").append(IM_END).append("\n")

        // 2. Prior Conversation History
        for ((isUser, text) in recentHistory) {
            val role = if (isUser) "user" else "assistant"
            sb.append(IM_START).append(role).append("\n")
            sb.append(text.trim()).append("\n")
            sb.append(IM_END).append("\n")
        }

        // 3. Current User Message with optional multimodal visual token
        sb.append(IM_START).append("user\n")
        if (imagePrompt != null) {
            sb.append(VISION_START).append(IMAGE_PAD).append(" ").append(imagePrompt).append(VISION_END).append("\n")
        }
        sb.append(userMessage.trim()).append("\n")
        sb.append(IM_END).append("\n")

        // 4. Assistant generation trigger
        sb.append(IM_START).append("assistant\n")

        return sb.toString()
    }
}
