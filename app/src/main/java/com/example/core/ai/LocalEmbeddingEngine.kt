package com.example.core.ai

import kotlin.math.ln
import kotlin.math.sqrt
import java.util.Locale

/**
 * Offline lexical retrieval helpers for RAG.
 * The 64-dimensional vectors below are deterministic word/character-hash fingerprints,
 * NOT neural semantic embeddings. They can supplement keyword/BM25 matching but do not
 * understand synonyms or meaning. A trained multilingual embedding model is still required
 * for true semantic retrieval.
 */
object LocalEmbeddingEngine {

    private const val VECTOR_DIM = 64

    /**
     * Normalizes text for multi-lingual tokenization (English + Arabic).
     */
    fun tokenize(text: String): List<String> {
        val clean = normalizeArabic(text.lowercase())
        return clean.split(Regex("[^\\p{L}\\p{Nd}]+"))
            .filter { it.length > 1 }
    }

    /**
     * Strips Arabic diacritics (tashkeel), normalizes alef variants, ya/alif maqsura, teh marbuta.
     */
    fun normalizeArabic(input: String): String {
        var str = input
        str = str.replace(Regex("[\u064B-\u065F\u0670]"), "")
        str = str.replace(Regex("[\u0622\u0623\u0625\u0671]"), "\u0627")
        str = str.replace('\u0649', '\u064A')
        str = str.replace('\u0629', '\u0647')
        return str
    }

    /**
     * Creates a deterministic lexical fingerprint using character tri-grams and word hashes.
     * This is not a model-generated embedding.
     */
    fun generateEmbedding(text: String): FloatArray {
        val tokens = tokenize(text)
        val vector = FloatArray(VECTOR_DIM) { 0f }
        if (tokens.isEmpty()) return vector

        for (token in tokens) {
            // Whole word feature
            val wordHash = token.hashCode()
            val wIdx = (wordHash and 0x7FFFFFFF) % VECTOR_DIM
            val sign = if ((wordHash and 1) == 0) 1.0f else -1.0f
            vector[wIdx] += 1.5f * sign

            // Character n-gram subwords (e.g., "qub", "ubi", "bit")
            if (token.length >= 3) {
                for (j in 0..(token.length - 3)) {
                    val gram = token.substring(j, j + 3)
                    val gHash = gram.hashCode()
                    val gIdx = (gHash and 0x7FFFFFFF) % VECTOR_DIM
                    val gSign = if ((gHash and 1) == 0) 1.0f else -1.0f
                    vector[gIdx] += 0.8f * gSign
                }
            }
        }

        // L2 Normalize
        var sumSquares = 0.0f
        for (v in vector) {
            sumSquares += v * v
        }
        val norm = sqrt(sumSquares.toDouble()).toFloat()
        if (norm > 0.00001f) {
            for (i in vector.indices) {
                vector[i] /= norm
            }
        }
        return vector
    }

    /**
     * Computes Cosine Similarity between two L2-normalized float vectors. Range: [-1.0, 1.0]
     */
    fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        if (v1.size != v2.size || v1.isEmpty()) return 0f
        var dot = 0f
        for (i in v1.indices) {
            dot += v1[i] * v2[i]
        }
        return dot.coerceIn(-1.0f, 1.0f)
    }

    /**
     * Serializes FloatArray to string for Room storage.
     */
    fun vectorToString(vector: FloatArray): String {
        return vector.joinToString(",") { String.format(Locale.US, "%.4f", it) }
    }

    /**
     * Deserializes string from Room storage back to FloatArray.
     */
    fun stringToVector(str: String): FloatArray {
        if (str.isBlank()) return FloatArray(VECTOR_DIM) { 0f }
        return str.split(",")
            .mapNotNull { it.trim().toFloatOrNull() }
            .toFloatArray()
    }

    /**
     * Computes BM25 keyword score between a user query and a document chunk text.
     */
    fun computeBm25Score(queryTokens: List<String>, chunkTokens: List<String>): Float {
        if (queryTokens.isEmpty() || chunkTokens.isEmpty()) return 0f
        val k1 = 1.5f
        val b = 0.75f
        val avgDocLen = 80f
        val docLen = chunkTokens.size.toFloat()

        val chunkFreqMap = mutableMapOf<String, Int>()
        for (t in chunkTokens) {
            chunkFreqMap[t] = (chunkFreqMap[t] ?: 0) + 1
        }

        var score = 0f
        for (q in queryTokens) {
            val tf = (chunkFreqMap[q] ?: 0).toFloat()
            if (tf > 0f) {
                val idf = ln(1.0 + 10.0).toFloat()
                val numerator = tf * (k1 + 1f)
                val denominator = tf + k1 * (1f - b + b * (docLen / avgDocLen))
                score += idf * (numerator / denominator)
            }
        }
        return score
    }

    /**
     * Extracts top 12 unique key concept words from text for indexing.
     */
    fun extractKeywords(text: String): String {
        val stopWords = setOf(
            "the", "is", "at", "which", "on", "and", "a", "an", "in", "to", "of", "for", "with",
            "as", "by", "that", "this", "it", "from", "be", "are", "was", "were", "or",
            "في", "من", "على", "إلى", "عن", "مع", "هذا", "هذه", "التي", "الذي", "كان", "أن", "ان",
            "هو", "هي", "لا", "ما", "كل", "أو", "او", "قد", "لقد", "ذلك", "تلك", "اكو", "ماكو", "شكو", "هسه"
        )
        val tokens = tokenize(text).filter { it !in stopWords && it.length >= 3 }
        val counts = mutableMapOf<String, Int>()
        tokens.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        return counts.entries.sortedByDescending { it.value }
            .take(12)
            .joinToString(" ") { it.key }
    }
}
