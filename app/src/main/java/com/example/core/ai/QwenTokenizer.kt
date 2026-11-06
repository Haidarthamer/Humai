package com.example.core.ai

/**
 * ChatML prompt formatter. Actual token IDs come only from the loaded model vocabulary in native llama.cpp.
 */
object QwenTokenizer {

    const val IM_START = "<|im_start|>"
    const val IM_END = "<|im_end|>"
    const val VISION_START = "<|vision_start|>"
    const val VISION_END = "<|vision_end|>"
    const val IMAGE_PAD = "<|image_pad|>"

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
