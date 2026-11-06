package com.example.core.ingestion

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.core.ai.AiEngine
import com.example.core.ai.LocalEmbeddingEngine
import com.example.core.ai.QwenVisionEngine
import com.example.core.ai.WhisperAudioEngine
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MediaIngestor(
    private val context: Context,
    private val documentDao: DocumentDao,
    private val knowledgeDao: KnowledgeDao,
    private val memoryDao: MemoryDao,
    private val aiEngine: AiEngine,
    private val personalityEngine: PersonalityAndEmotionEngines,
    private val pdfHelper: PdfHelper,
    val visionEngine: QwenVisionEngine = QwenVisionEngine(context),
    val whisperEngine: WhisperAudioEngine = WhisperAudioEngine(context)
) {

    /**
     * Ingests a local video file (MP4, MKV, MOV):
     * 1. Extracts audio and transcribes speech using Whisper.cpp.
     * 2. Extracts representative keyframes at intervals using FFmpeg / MediaMetadataRetriever.
     * 3. Analyzes visual scenes with Qwen3-VL.
     * 4. Combines visual + audio into a multimodal timeline with timestamp citations.
     */
    suspend fun ingestVideo(
        uri: Uri,
        fileName: String,
        language: LanguageMode,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Long = withContext(Dispatchers.IO) {
        onProgress(0.1f, "Caching video media...")
        val cachedFile = pdfHelper.cacheUriToLocalFile(uri, fileName)

        val docEntity = DocumentEntity(
            title = fileName,
            fileType = FileType.VIDEO,
            uriString = uri.toString(),
            localCachedPath = cachedFile.absolutePath,
            fileSizeBytes = cachedFile.length(),
            status = IngestionStatus.PROCESSING,
            progress = 0.15f
        )
        val docId = documentDao.insertDocument(docEntity)

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(cachedFile.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 30_000L
            val durationSec = (durationMs / 1000L).coerceAtLeast(1L)

            // Step 1: Transcribe audio using Whisper.cpp
            onProgress(0.30f, "Checking local Whisper transcription runtime...")
            val isArabic = language != LanguageMode.ENGLISH
            val audioSegments = whisperEngine.transcribeAudioFile(cachedFile, isArabic)

            // Step 2: Extract keyframes and analyze with Qwen3-VL
            onProgress(0.55f, "Extracting video frames and analyzing scenes with Qwen3-VL...")
            val framesDir = File(context.cacheDir, "video_frames_$docId").apply { mkdirs() }
            val intervalSec = (durationSec / 4).coerceIn(5L, 60L)

            val knowledgeChunks = mutableListOf<KnowledgeChunkEntity>()
            var chunkIdx = 0

            var sec = 0L
            while (sec < durationSec) {
                val timeMicros = sec * 1_000_000L
                val frameBitmap = retriever.getFrameAtTime(timeMicros, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

                val frameFileName = "frame_${sec}s.jpg"
                val frameFile = File(framesDir, frameFileName)
                if (frameBitmap != null) {
                    FileOutputStream(frameFile).use { out ->
                        frameBitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
                    }
                }

                // Correlate corresponding Whisper audio transcription segment
                val audioText = audioSegments.find { sec in it.startSeconds..it.endSeconds }?.text ?: ""
                val visualDesc = if (frameBitmap != null) {
                    val frameAnalysis = visionEngine.analyzeImage(Uri.fromFile(frameFile))
                    "Video keyframe [${sec / 60}m:${sec % 60}s]: ${frameAnalysis.sceneDescription}"
                } else {
                    "No frame could be extracted at ${sec}s."
                }

                val combinedContent = buildString {
                    append(visualDesc)
                    if (audioText.isNotBlank()) {
                        append("\nWhisper Audio Transcription: \"").append(audioText).append("\"")
                    }
                }

                val vector = LocalEmbeddingEngine.generateEmbedding(combinedContent)

                knowledgeChunks.add(
                    KnowledgeChunkEntity(
                        documentId = docId,
                        chunkIndex = chunkIdx++,
                        pageNumber = null,
                        timestampSeconds = sec,
                        content = combinedContent,
                        embeddingVectorStr = LocalEmbeddingEngine.vectorToString(vector),
                        keywords = LocalEmbeddingEngine.extractKeywords(combinedContent)
                    )
                )
                sec += intervalSec
            }

            onProgress(0.80f, "Synthesizing multimodal video narrative...")
            val fullNarrative = knowledgeChunks.joinToString("\n") { it.content }
            val summary = aiEngine.generateSummary(fullNarrative, language)

            knowledgeDao.insertChunks(knowledgeChunks)

            memoryDao.insertMemory(
                MemoryEntity(
                    tier = MemoryTier.SEMANTIC,
                    topic = fileName.substringBeforeLast("."),
                    content = "Video multimodal analysis for '$fileName' (${durationSec}s): $summary",
                    importanceScore = 0.85f,
                    accessCount = 1,
                    sourceDocumentId = docId,
                    sourceDetails = "Qwen3-VL Video breakdown (${knowledgeChunks.size} timestamped frames & Whisper audio)"
                )
            )

            documentDao.updateDocument(
                docEntity.copy(
                    id = docId,
                    status = IngestionStatus.COMPLETED,
                    progress = 1.0f,
                    summary = summary,
                    pageCount = knowledgeChunks.size,
                    wordCount = fullNarrative.split(" ").size
                )
            )

            personalityEngine.triggerEmotion(
                SimulatedEmotion.EXCITEMENT,
                0.90f,
                "Indexed multimodal video scenes and Whisper audio from $fileName"
            )

            onProgress(1.0f, "Video assimilation complete!")
            docId
        } catch (e: Exception) {
            documentDao.updateDocument(
                docEntity.copy(id = docId, status = IngestionStatus.FAILED, summary = "Error: ${e.message}")
            )
            docId
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    /**
     * Ingests an image (JPG, PNG, WEBP, JPEG): extracts OCR, diagrams, charts, and visual features via Qwen3-VL.
     */
    suspend fun ingestImage(
        uri: Uri,
        fileName: String,
        language: LanguageMode,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Long = withContext(Dispatchers.IO) {
        onProgress(0.2f, "Processing image visual elements...")
        val cachedFile = pdfHelper.cacheUriToLocalFile(uri, fileName)

        val docEntity = DocumentEntity(
            title = fileName,
            fileType = FileType.IMAGE,
            uriString = uri.toString(),
            localCachedPath = cachedFile.absolutePath,
            fileSizeBytes = cachedFile.length(),
            status = IngestionStatus.PROCESSING,
            progress = 0.35f
        )
        val docId = documentDao.insertDocument(docEntity)

        try {
            onProgress(0.6f, "Checking local image-understanding runtime...")
            val analysis = visionEngine.analyzeImage(uri)
            val sceneDescription = "Qwen3-VL Visual Asset '$fileName' [${analysis.width}x${analysis.height}, ${analysis.format}]: ${analysis.sceneDescription}"
            val vector = LocalEmbeddingEngine.generateEmbedding(sceneDescription)

            val chunk = KnowledgeChunkEntity(
                documentId = docId,
                chunkIndex = 0,
                pageNumber = 1,
                content = sceneDescription,
                embeddingVectorStr = LocalEmbeddingEngine.vectorToString(vector),
                keywords = LocalEmbeddingEngine.extractKeywords(sceneDescription)
            )
            knowledgeDao.insertChunks(listOf(chunk))

            val summary = aiEngine.generateSummary(sceneDescription, language)

            memoryDao.insertMemory(
                MemoryEntity(
                    tier = MemoryTier.SEMANTIC,
                    topic = fileName.substringBeforeLast("."),
                    content = "Image visual knowledge: $sceneDescription",
                    importanceScore = 0.80f,
                    sourceDocumentId = docId,
                    sourceDetails = "Qwen3-VL Visual recognition pipeline ($fileName)"
                )
            )

            documentDao.updateDocument(
                docEntity.copy(
                    id = docId,
                    status = IngestionStatus.COMPLETED,
                    progress = 1.0f,
                    summary = summary,
                    pageCount = 1
                )
            )

            personalityEngine.triggerEmotion(
                SimulatedEmotion.INTEREST,
                0.85f,
                "Indexed visual information from image $fileName"
            )

            onProgress(1.0f, "Image processed successfully!")
            docId
        } catch (e: Exception) {
            documentDao.updateDocument(
                docEntity.copy(id = docId, status = IngestionStatus.FAILED, summary = "Error: ${e.message}")
            )
            docId
        }
    }

    /**
     * Ingests an audio recording: transcribes speech locally using Whisper.cpp and indexes into semantic memory.
     */
    suspend fun ingestAudio(
        uri: Uri,
        fileName: String,
        language: LanguageMode,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Long = withContext(Dispatchers.IO) {
        onProgress(0.2f, "Loading audio stream...")
        val cachedFile = pdfHelper.cacheUriToLocalFile(uri, fileName)

        val docEntity = DocumentEntity(
            title = fileName,
            fileType = FileType.AUDIO,
            uriString = uri.toString(),
            localCachedPath = cachedFile.absolutePath,
            fileSizeBytes = cachedFile.length(),
            status = IngestionStatus.PROCESSING,
            progress = 0.3f
        )
        val docId = documentDao.insertDocument(docEntity)

        try {
            onProgress(0.55f, "Transcribing speech audio with Whisper.cpp (Arabic / English)...")
            val isArabic = language != LanguageMode.ENGLISH
            val segments = whisperEngine.transcribeAudioFile(cachedFile, isArabic)
            val fullTranscript = segments.joinToString("\n") { it.text }
            val vector = LocalEmbeddingEngine.generateEmbedding(fullTranscript)

            val chunks = segments.mapIndexed { idx, seg ->
                KnowledgeChunkEntity(
                    documentId = docId,
                    chunkIndex = idx,
                    pageNumber = null,
                    timestampSeconds = seg.startSeconds,
                    content = seg.text,
                    embeddingVectorStr = LocalEmbeddingEngine.vectorToString(LocalEmbeddingEngine.generateEmbedding(seg.text)),
                    keywords = LocalEmbeddingEngine.extractKeywords(seg.text)
                )
            }
            knowledgeDao.insertChunks(chunks)

            val summary = aiEngine.generateSummary(fullTranscript, language)

            memoryDao.insertMemory(
                MemoryEntity(
                    tier = MemoryTier.SEMANTIC,
                    topic = fileName.substringBeforeLast("."),
                    content = "Whisper.cpp audio transcript: $summary",
                    importanceScore = 0.82f,
                    sourceDocumentId = docId,
                    sourceDetails = "Whisper.cpp speech transcription (${segments.size} segments)"
                )
            )

            documentDao.updateDocument(
                docEntity.copy(
                    id = docId,
                    status = IngestionStatus.COMPLETED,
                    progress = 1.0f,
                    summary = summary,
                    pageCount = segments.size
                )
            )

            personalityEngine.triggerEmotion(
                SimulatedEmotion.CURIOSITY,
                0.85f,
                "Transcribed audio voice notes from $fileName"
            )

            onProgress(1.0f, "Audio transcript indexed!")
            docId
        } catch (e: Exception) {
            documentDao.updateDocument(
                docEntity.copy(id = docId, status = IngestionStatus.FAILED, summary = "Error: ${e.message}")
            )
            docId
        }
    }
}
