package com.example.core.ai

/**
 * Robust text tokenization and streaming splitter for AI response generation.
 * Handles English, Modern Standard Arabic, Iraqi Arabic, punctuation (. ! ? , ; ، ؛ ؟),
 * newlines, and whitespace without relying on fragile or unsupported nested regex lookbehinds.
 */
object TextStreamHelper {

    private val PUNCTUATIONS = setOf(
        '.', '!', '?', ',', ';', ':',
        '،', '؛', '؟', // Arabic punctuation marks
        '\n', '\r', '\t'
    )

    /**
     * Splits response text into streamable pieces/tokens preserving delimiters and whitespace.
     * Safe against regex pattern syntax errors and lookbehind limitations on Android JVM.
     */
    fun splitIntoStreamPieces(text: String): List<String> {
        if (text.isEmpty()) return emptyList()

        val pieces = mutableListOf<String>()
        val currentPiece = StringBuilder()

        var i = 0
        while (i < text.length) {
            val char = text[i]
            currentPiece.append(char)

            // Break piece at whitespace or punctuation boundary
            if (char == ' ' || char == '\n' || char in PUNCTUATIONS) {
                pieces.add(currentPiece.toString())
                currentPiece.clear()
            }
            i++
        }

        if (currentPiece.isNotEmpty()) {
            pieces.add(currentPiece.toString())
        }

        return pieces
    }

    /**
     * Splits text into sentences safely for summaries and chunking.
     * Accurately supports English (. ! ?) and Arabic (. ! ؟ ؛) terminal punctuation.
     */
    fun splitIntoSentences(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        val terminalMarks = setOf('.', '!', '?', '؟', '؛', '\n')
        val sentences = mutableListOf<String>()
        val current = StringBuilder()

        for (char in text) {
            current.append(char)
            if (char in terminalMarks) {
                val s = current.toString().trim()
                if (s.isNotEmpty()) {
                    sentences.add(s)
                }
                current.clear()
            }
        }

        val remainder = current.toString().trim()
        if (remainder.isNotEmpty()) {
            sentences.add(remainder)
        }

        return sentences
    }
}
