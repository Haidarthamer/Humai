package com.example.core.ingestion

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.example.core.ai.AiEngine
import com.example.core.ai.LocalEmbeddingEngine
import com.example.core.model.FileType
import com.example.core.model.IngestionStatus
import com.example.core.model.LanguageMode
import com.example.core.model.MemoryTier
import com.example.core.model.SimulatedEmotion
import com.example.core.personality.PersonalityAndEmotionEngines
import com.example.database.dao.DocumentDao
import com.example.database.dao.KnowledgeDao
import com.example.database.dao.MemoryDao
import com.example.database.entities.DocumentEntity
import com.example.database.entities.KnowledgeChunkEntity
import com.example.database.entities.MemoryEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser

class DocumentIngestor(
    private val context: Context,
    private val documentDao: DocumentDao,
    private val knowledgeDao: KnowledgeDao,
    private val memoryDao: MemoryDao,
    private val aiEngine: AiEngine,
    private val personalityEngine: PersonalityAndEmotionEngines,
    private val pdfHelper: PdfHelper
) {

    suspend fun ingestDocument(
        uri: Uri,
        fileName: String,
        fileType: FileType,
        language: LanguageMode,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Long = withContext(Dispatchers.IO) {
        onProgress(0.05f, "Preparing file cache...")
        val cachedFile = pdfHelper.cacheUriToLocalFile(uri, fileName)
        val fileSize = cachedFile.length()

        val initialEntity = DocumentEntity(
            title = fileName,
            fileType = fileType,
            uriString = uri.toString(),
            localCachedPath = cachedFile.absolutePath,
            fileSizeBytes = fileSize,
            status = IngestionStatus.PROCESSING,
            progress = 0.1f
        )
        val docId = documentDao.insertDocument(initialEntity)

        try {
            onProgress(0.20f, "Extracting text content...")
            val pagesData: List<Pair<Int, String>> = when (fileType) {
                FileType.PDF -> {
                    val pages = pdfHelper.extractTextByPages(cachedFile)
                    if (pages.none { it.second.isNotBlank() }) {
                        throw IllegalStateException(
                            "This PDF has no selectable text. It may be scanned/image-only; offline OCR is not integrated yet."
                        )
                    }
                    pages
                }
                FileType.DOCX -> {
                    val text = extractDocxText(cachedFile)
                    chunkTextIntoPages(text)
                }
                FileType.TXT, FileType.MARKDOWN, FileType.NOTE -> {
                    val text = cachedFile.readText()
                    chunkTextIntoPages(text)
                }
                else -> {
                    throw IllegalArgumentException("Unsupported document type for text ingestion: $fileType")
                }
            }

            currentCoroutineContext().ensureActive()

            onProgress(0.40f, "Generating neural summary and concepts...")
            val fullText = pagesData.joinToString("\n") { it.second }
            val summary = aiEngine.generateSummary(fullText, language)
            val concepts = aiEngine.extractKeyConcepts(fullText)

            onProgress(0.60f, "Synthesizing searchable knowledge chunks...")
            val chunks = mutableListOf<KnowledgeChunkEntity>()
            var chunkIndex = 0

            for ((pageNumber, pageText) in pagesData) {
                currentCoroutineContext().ensureActive()
                val words = pageText.split(Regex("\\s+")).filter { it.isNotBlank() }
                val windowSize = 120
                val overlap = 20

                var i = 0
                while (i < words.size) {
                    currentCoroutineContext().ensureActive()
                    val chunkWords = words.subList(i, (i + windowSize).coerceAtMost(words.size))
                    val chunkText = chunkWords.joinToString(" ")
                    val vector = LocalEmbeddingEngine.generateEmbedding(chunkText)
                    val keywords = LocalEmbeddingEngine.extractKeywords(chunkText)

                    chunks.add(
                        KnowledgeChunkEntity(
                            documentId = docId,
                            chunkIndex = chunkIndex++,
                            pageNumber = pageNumber,
                            content = chunkText,
                            embeddingVectorStr = LocalEmbeddingEngine.vectorToString(vector),
                            keywords = keywords
                        )
                    )
                    i += (windowSize - overlap).coerceAtLeast(1)
                }
            }

            onProgress(0.85f, "Persisting knowledge into local cognitive matrix...")
            knowledgeDao.insertChunks(chunks)

            // Only write a semantic summary memory when the local model actually
            // produced one. Do not save an error notice as if it were learned knowledge.
            val summaryWasGenerated = summary.isNotBlank() &&
                !summary.startsWith("Summary unavailable:", ignoreCase = true) &&
                !summary.startsWith("الملخص غير متاح") &&
                !summary.startsWith("الملخص مو متاح")
            if (summaryWasGenerated) {
                val memoryContent = "Learned from document '$fileName': $summary (Key concepts: ${concepts.joinToString(", ")})"
                memoryDao.insertMemory(
                    MemoryEntity(
                        tier = MemoryTier.SEMANTIC,
                        topic = fileName.removeSuffix(".pdf").removeSuffix(".txt").removeSuffix(".docx"),
                        content = memoryContent,
                        importanceScore = 0.85f,
                        accessCount = 1,
                        sourceDocumentId = docId,
                        sourceDetails = "Auto-indexed $fileName (${pagesData.size} pages)"
                    )
                )
            }

            // Update document status
            val wordCount = fullText.split(Regex("\\s+")).size
            val completedEntity = initialEntity.copy(
                id = docId,
                status = IngestionStatus.COMPLETED,
                progress = 1.0f,
                summary = summary,
                pageCount = pagesData.size,
                wordCount = wordCount,
                tagsJson = concepts.joinToString(",")
            )
            documentDao.updateDocument(completedEntity)

            // Trigger companion emotion: Excitement / Curiosity
            personalityEngine.triggerEmotion(
                SimulatedEmotion.EXCITEMENT,
                0.88f,
                "Integrated new knowledge from $fileName into local memory"
            )

            onProgress(1.0f, "Knowledge assimilation complete!")
            docId
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                documentDao.updateDocument(
                    initialEntity.copy(
                        id = docId,
                        status = IngestionStatus.CANCELLED,
                        progress = 0f,
                        summary = "Import cancelled."
                    )
                )
            }
            throw cancelled
        } catch (e: Exception) {
            val failedEntity = initialEntity.copy(
                id = docId,
                status = IngestionStatus.FAILED,
                progress = 0f,
                summary = "Failed to ingest: ${e.message}"
            )
            documentDao.updateDocument(failedEntity)
            personalityEngine.triggerEmotion(
                SimulatedEmotion.CONFUSION,
                0.7f,
                "Encountered parsing issue with $fileName"
            )
            docId
        }
    }

    private fun extractDocxText(file: File): String {
        require(file.isFile && file.canRead()) { "DOCX file is missing or unreadable." }
        val output = StringBuilder()
        var foundDocumentXml = false

        ZipInputStream(file.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    foundDocumentXml = true
                    val parser = Xml.newPullParser()
                    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                    parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
                    parser.setInput(zip, "UTF-8")

                    var event = parser.eventType
                    while (event != XmlPullParser.END_DOCUMENT) {
                        when (event) {
                            XmlPullParser.START_TAG -> when (parser.name) {
                                "tab" -> output.append('\t')
                                "br" -> output.append('\n')
                            }
                            XmlPullParser.TEXT -> output.append(parser.text)
                            XmlPullParser.END_TAG -> if (parser.name == "p") output.append('\n')
                        }
                        event = parser.next()
                    }
                    break
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        require(foundDocumentXml) { "This file is not a valid DOCX document (word/document.xml is missing)." }
        val extracted = output.toString()
            .replace('\u0000', ' ')
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex(" *\\n *"), "\n")
            .trim()
        require(extracted.isNotBlank()) { "No selectable text was found in this DOCX file." }
        return extracted
    }

    private fun chunkTextIntoPages(text: String): List<Pair<Int, String>> {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val wordsPerPage = 300
        val pages = mutableListOf<Pair<Int, String>>()
        var p = 1
        var i = 0
        while (i < words.size) {
            val pageSlice = words.subList(i, (i + wordsPerPage).coerceAtMost(words.size))
            pages.add(Pair(p++, pageSlice.joinToString(" ")))
            i += wordsPerPage
        }
        require(pages.isNotEmpty()) { "The selected document contains no extractable text." }
        return pages
    }
}
