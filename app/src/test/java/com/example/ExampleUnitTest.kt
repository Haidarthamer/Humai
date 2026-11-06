package com.example

import com.example.core.ai.GGUFParser
import com.example.core.ai.LocalEmbeddingEngine
import com.example.core.ai.PreinstalledModelProvisioner
import com.example.core.ai.QwenTokenizer
import com.example.core.ai.TextStreamHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ExampleUnitTest {

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testArabicNormalizationAndLexicalTokenization() {
        val original = "الْحَوْسَبَةُ الْكَمُّومِيَّةُ"
        val normalized = LocalEmbeddingEngine.normalizeArabic(original)
        assertFalse(normalized.contains("\u064E"))
        assertTrue(LocalEmbeddingEngine.tokenize(original).isNotEmpty())
    }

    @Test
    fun testLexicalFingerprintIsDeterministic() {
        val first = LocalEmbeddingEngine.generateEmbedding("local offline model")
        val second = LocalEmbeddingEngine.generateEmbedding("local offline model")
        assertEquals(1.0f, LocalEmbeddingEngine.cosineSimilarity(first, second), 0.001f)
    }

    @Test
    fun testCorpusAwareBm25PrefersChunksWithRelevantRareTerms() {
        val documents = listOf(
            "A general introduction to astronomy and stars.",
            "Quasar physics explains energetic galactic nuclei.",
            "Quasar observations in modern astrophysics."
        )

        val ranked = LocalEmbeddingEngine.rankByBm25(
            query = "quasar physics",
            documents = documents,
            textOf = { it },
            limit = 3
        )

        assertEquals(2, ranked.size)
        assertEquals(documents[1], ranked.first().first)
        assertTrue(ranked.first().second > ranked.last().second)
    }

    @Test
    fun testCorpusAwareBm25ReturnsNoMatchForUnrelatedQuery() {
        val ranked = LocalEmbeddingEngine.rankByBm25(
            query = "volcano",
            documents = listOf("local model", "offline document"),
            textOf = { it },
            limit = 3
        )
        assertTrue(ranked.isEmpty())
    }

    @Test
    fun testChatMLFormatting() {
        val chatML = QwenTokenizer.formatChatML(
            systemPrompt = "You are a local assistant.",
            recentHistory = listOf(true to "Hello", false to "Hi!"),
            userMessage = "Explain this document",
            relevantMemories = listOf("A saved note"),
            citations = listOf("Physics_Notes.pdf (Page 2): Source excerpt")
        )

        assertTrue(chatML.contains("<|im_start|>system"))
        assertTrue(chatML.contains("A saved note"))
        assertTrue(chatML.contains("Physics_Notes.pdf (Page 2)"))
        assertTrue(chatML.contains("<|im_start|>user"))
        assertTrue(chatML.contains("Explain this document"))
        assertTrue(chatML.endsWith("<|im_start|>assistant\n"))
    }

    @Test
    fun testGGUFParserRejectsNonexistentFile() {
        val result = GGUFParser.parse(File("/tmp/non_existent_model.gguf"))
        assertFalse(result.isValid)
    }

    @Test
    fun testGGUFParserSkipsStringArraysAndFindsMetadataAfterThem() {
        val file = File.createTempFile("humai-gguf-test", ".gguf")
        try {
            val bytes = ByteArrayOutputStream()
            val out = DataOutputStream(bytes)

            out.write(byteArrayOf(0x47, 0x47, 0x55, 0x46)) // GGUF
            writeIntLE(out, 3) // version
            writeLongLE(out, 1L) // one tensor
            writeLongLE(out, 3L) // three metadata entries

            writeMetadataString(out, "general.architecture", "qwen3")

            writeString(out, "tokenizer.ggml.tokens")
            writeIntLE(out, 9) // ARRAY
            writeIntLE(out, 8) // STRING element type
            writeLongLE(out, 2L)
            writeString(out, "hello")
            writeString(out, "world")

            writeMetadataString(out, "general.name", "Humai parser fixture")

            file.writeBytes(bytes.toByteArray())
            val parsed = GGUFParser.parse(file)

            assertTrue(parsed.errorMessage ?: "Expected valid GGUF metadata", parsed.isValid)
            assertEquals("qwen3", parsed.architecture)
            assertEquals("Humai parser fixture", parsed.modelName)
            assertEquals(1L, parsed.tensorCount)
            assertEquals(3L, parsed.metadataKvCount)
        } finally {
            file.delete()
        }
    }

    @Test
    fun testNoModelWeightsAreClaimedAsBundled() {
        assertTrue(PreinstalledModelProvisioner.BUNDLED_MODELS.isEmpty())
    }

    @Test
    fun testTextStreamHelperPreservesMixedLanguageText() {
        val samples = listOf(
            "Hello, world! How are you doing today?",
            "مرحبا، شلونك؟ هذا نموذج محلي؛ يعمل 100% بدون إنترنت.",
            "Qwen3 model ready! مرحبا بك، النظام يعمل أوفلاين.",
            "Line 1.\nLine 2!\nLine 3؟"
        )

        samples.forEach { sample ->
            val pieces = TextStreamHelper.splitIntoStreamPieces(sample)
            assertEquals(sample, pieces.joinToString(""))
        }
        assertTrue(TextStreamHelper.splitIntoStreamPieces("").isEmpty())
    }

    private fun writeMetadataString(out: DataOutputStream, key: String, value: String) {
        writeString(out, key)
        writeIntLE(out, 8) // STRING
        writeString(out, value)
    }

    private fun writeString(out: DataOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeLongLE(out, bytes.size.toLong())
        out.write(bytes)
    }

    private fun writeIntLE(out: DataOutputStream, value: Int) {
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())
    }

    private fun writeLongLE(out: DataOutputStream, value: Long) {
        out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array())
    }
}
