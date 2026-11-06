package com.example

import com.example.core.ai.GGUFParser
import com.example.core.ai.LocalEmbeddingEngine
import com.example.core.ai.QwenResponseGenerator
import com.example.core.ai.QwenTokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testArabicNormalizationAndTokenization() {
        val original = "الْحَوْسَبَةُ الْكَمُّومِيَّةُ"
        val normalized = LocalEmbeddingEngine.normalizeArabic(original)
        assertTrue(!normalized.contains("\u064E"))
        val tokens = LocalEmbeddingEngine.tokenize(original)
        assertTrue(tokens.isNotEmpty())
    }

    @Test
    fun testSemanticEmbeddingAndCosineSimilarity() {
        val v1 = LocalEmbeddingEngine.generateEmbedding("quantum computing superposition qubits")
        val simSelf = LocalEmbeddingEngine.cosineSimilarity(v1, v1)
        val v2 = LocalEmbeddingEngine.generateEmbedding("quantum computing superposition qubits")
        val simIdentical = LocalEmbeddingEngine.cosineSimilarity(v1, v2)

        assertEquals(1.0f, simSelf, 0.001f)
        assertEquals(1.0f, simIdentical, 0.001f)
    }

    @Test
    fun testQwenChatMLFormatting() {
        val chatML = QwenTokenizer.formatChatML(
            systemPrompt = "You are Qwen3-4B.",
            recentHistory = listOf(true to "Hello", false to "Hi!"),
            userMessage = "Explain quantum superposition",
            relevantMemories = listOf("User studies quantum mechanics"),
            citations = listOf("Physics_Notes.pdf (Page 2): Superposition formula")
        )

        assertTrue(chatML.contains("<|im_start|>system"))
        assertTrue(chatML.contains("You are Qwen3-4B."))
        assertTrue(chatML.contains("<|im_start|>user"))
        assertTrue(chatML.contains("Explain quantum superposition"))
        assertTrue(chatML.contains("<|im_start|>assistant"))
    }

    @Test
    fun testQwenResponseGenerationNeverReturnsAcknowledged() {
        val chatML = QwenTokenizer.formatChatML(
            systemPrompt = "You are Qwen3-4B.",
            recentHistory = emptyList(),
            userMessage = "What is the capital of France?",
            relevantMemories = emptyList(),
            citations = emptyList()
        )

        val response = QwenResponseGenerator.generate(chatML, null)

        assertNotEquals("Acknowledged.", response.trim())
        assertNotEquals("Acknowledged", response.trim())
        assertTrue(response.isNotBlank())
        assertTrue(response.contains("Qwen3-4B") || response.length > 20)
    }

    @Test
    fun testQwenIraqiArabicResponse() {
        val chatML = QwenTokenizer.formatChatML(
            systemPrompt = "You are Qwen3-4B. Language Mode: Iraqi Arabic.",
            recentHistory = emptyList(),
            userMessage = "شلونك اليوم؟",
            relevantMemories = emptyList(),
            citations = emptyList()
        )

        val response = QwenResponseGenerator.generate(chatML, null)
        assertTrue(response.isNotBlank())
        assertNotEquals("Acknowledged.", response)
    }

    @Test
    fun testQwenVisionPromptGeneration() {
        val chatML = QwenTokenizer.formatChatML(
            systemPrompt = "You are Qwen3-VL.",
            recentHistory = emptyList(),
            userMessage = "What is shown in this chart?",
            relevantMemories = emptyList(),
            citations = emptyList(),
            imagePrompt = "Bar chart showing annual GDP growth from 2020 to 2025"
        )

        assertTrue(chatML.contains("<|vision_start|>"))
        assertTrue(chatML.contains("<|image_pad|>"))

        val response = QwenResponseGenerator.generate(chatML, null)
        assertTrue(response.contains("Qwen3-VL") || response.contains("Visual"))
        assertNotEquals("Acknowledged.", response)
    }

    @Test
    fun testGGUFParserNonExistentFile() {
        val fakeFile = File("/tmp/non_existent_model.gguf")
        val meta = GGUFParser.parse(fakeFile)
        assertTrue(!meta.isValid)
    }

    @Test
    fun testPreinstalledModelSpecsConfiguration() {
        val specs = com.example.core.ai.PreinstalledModelProvisioner.BUNDLED_MODELS
        assertEquals(3, specs.size)

        val defaultModel = specs.find { it.isDefault }
        assertTrue(defaultModel != null)
        assertEquals("qwen3-4b-instruct-q4_k_m.gguf", defaultModel?.fileName)

        val visionModel = specs.find { it.fileName == "qwen3-vl-4b-instruct-q4_k_m.gguf" }
        assertTrue(visionModel != null)

        val projectorModel = specs.find { it.fileName == "mmproj-qwen3-vl-4b-f16.gguf" }
        assertTrue(projectorModel != null)
    }

    @Test
    fun testBundledGGUFHeaderValidation() {
        val assetsDir = File("src/main/assets/models")
        if (assetsDir.exists()) {
            val qwen3File = File(assetsDir, "qwen3-4b-instruct-q4_k_m.gguf")
            if (qwen3File.exists()) {
                val meta = GGUFParser.parse(qwen3File)
                assertTrue("Qwen3-4B GGUF header must be valid", meta.isValid)
                assertEquals("qwen3", meta.architecture)
                assertEquals("Q4_K_M", meta.quantization)
            }

            val qwen3VlFile = File(assetsDir, "qwen3-vl-4b-instruct-q4_k_m.gguf")
            if (qwen3VlFile.exists()) {
                val meta = GGUFParser.parse(qwen3VlFile)
                assertTrue("Qwen3-VL GGUF header must be valid", meta.isValid)
                assertEquals("qwen3-vl", meta.architecture)
                assertEquals("Q4_K_M", meta.quantization)
            }

            val mmprojFile = File(assetsDir, "mmproj-qwen3-vl-4b-f16.gguf")
            if (mmprojFile.exists()) {
                val meta = GGUFParser.parse(mmprojFile)
                assertTrue("mmproj GGUF header must be valid", meta.isValid)
                assertEquals("clip", meta.architecture)
                assertEquals("F16", meta.quantization)
            }
        }
    }

    @Test
    fun testTextStreamHelperSplitting() {
        val englishText = "Hello, world! How are you doing today? I am fine."
        val pieces = com.example.core.ai.TextStreamHelper.splitIntoStreamPieces(englishText)
        assertTrue(pieces.isNotEmpty())
        assertEquals(englishText, pieces.joinToString(""))

        val arabicText = "مرحبا، شلونك؟ هذا نموذج محلي؛ يعمل 100% بدون إنترنت."
        val arabicPieces = com.example.core.ai.TextStreamHelper.splitIntoStreamPieces(arabicText)
        assertTrue(arabicPieces.isNotEmpty())
        assertEquals(arabicText, arabicPieces.joinToString(""))

        val mixedText = "Qwen3-4B is ready! مرحبا بك، النظام يعمل أوفلاين."
        val mixedPieces = com.example.core.ai.TextStreamHelper.splitIntoStreamPieces(mixedText)
        assertEquals(mixedText, mixedPieces.joinToString(""))

        val multilineText = "Line 1.\nLine 2!\nLine 3؟\nLine 4."
        val multilinePieces = com.example.core.ai.TextStreamHelper.splitIntoStreamPieces(multilineText)
        assertEquals(multilineText, multilinePieces.joinToString(""))

        val emptyPieces = com.example.core.ai.TextStreamHelper.splitIntoStreamPieces("")
        assertTrue(emptyPieces.isEmpty())
    }

    @Test
    fun testTextStreamHelperSentenceSplitting() {
        val mixedDoc = "First point. Second point! هل تفهم العربية؟ نعم، بالتأكيد؛ تم التوثيق."
        val sentences = com.example.core.ai.TextStreamHelper.splitIntoSentences(mixedDoc)
        assertTrue(sentences.size >= 4)
        assertTrue(sentences.any { it.contains("First point") })
        assertTrue(sentences.any { it.contains("العربية") })
    }

    @Test
    fun testQwenGeneratesGenuineResponseForHi() {
        val chatML = com.example.core.ai.QwenTokenizer.formatChatML(
            systemPrompt = "You are Qwen3-4B.",
            recentHistory = emptyList(),
            userMessage = "hi",
            relevantMemories = emptyList(),
            citations = emptyList()
        )

        val response = com.example.core.ai.QwenResponseGenerator.generate(chatML, null)
        assertTrue(response.isNotBlank())
        assertNotEquals("Acknowledged.", response.trim())
        assertNotEquals("Acknowledged", response.trim())
        assertTrue(response.contains("COMPANION AI") || response.contains("Qwen3-4B") || response.contains("help"))
    }

    @Test
    fun testQwenGeneratesGenuineResponseForIraqiArabicQuery() {
        val chatML = com.example.core.ai.QwenTokenizer.formatChatML(
            systemPrompt = "You are COMPANION AI. Language Mode: Iraqi Arabic.",
            recentHistory = emptyList(),
            userMessage = "شلونك؟ شنو تكدر تسوي؟",
            relevantMemories = emptyList(),
            citations = emptyList()
        )

        val response = com.example.core.ai.QwenResponseGenerator.generate(chatML, null)
        assertTrue(response.isNotBlank())
        assertNotEquals("Acknowledged.", response.trim())
        assertTrue(response.contains("Qwen3-4B") || response.contains("PDF") || response.contains("الصور") || response.contains("أكدر") || response.contains("تمام"))
    }
}
