package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.ai.AiEngine
import com.example.core.ai.HardwareReport
import com.example.core.ai.LocalCompanionBrain
import com.example.core.ai.LocalEmbeddingEngine
import com.example.core.ai.ModelManager
import com.example.core.ingestion.DocumentIngestor
import com.example.core.ingestion.MediaIngestor
import com.example.core.ingestion.PdfHelper
import com.example.core.memory.MemoryManager
import com.example.core.model.Citation
import com.example.core.model.FileType
import com.example.core.model.GGUFModelInfo
import com.example.core.model.LanguageMode
import com.example.core.model.MemoryTier
import com.example.core.model.PersonalityTraits
import com.example.core.personality.PersonalityAndEmotionEngines
import com.example.database.CompanionDatabase
import com.example.database.entities.BookmarkEntity
import com.example.database.entities.ConversationEntity
import com.example.database.entities.DocumentEntity
import com.example.database.entities.MemoryEntity
import com.example.database.entities.MessageEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class IngestionProgressState(
    val isIngesting: Boolean = false,
    val progress: Float = 0f,
    val statusText: String = "",
    val fileName: String = ""
)

class CompanionViewModel(application: Application) : AndroidViewModel(application) {

    private val db = CompanionDatabase.getInstance(application)
    private val conversationDao = db.conversationDao()
    private val messageDao = db.messageDao()
    private val documentDao = db.documentDao()
    private val knowledgeDao = db.knowledgeDao()
    private val memoryDao = db.memoryDao()
    private val personalityDao = db.personalityDao()
    private val bookmarkDao = db.bookmarkDao()

    val modelManager = ModelManager(application)
    val modelProvisioner = com.example.core.ai.PreinstalledModelProvisioner(application, modelManager)
    val provisioningProgress = modelProvisioner.progressState
    val pdfHelper = PdfHelper(application)
    val aiEngine: AiEngine = LocalCompanionBrain(modelManager)
    val personalityEngine = PersonalityAndEmotionEngines(personalityDao)
    val memoryManager = MemoryManager(memoryDao)

    val visionEngine = com.example.core.ai.QwenVisionEngine(application)
    val whisperEngine = com.example.core.ai.WhisperAudioEngine(application)

    val documentIngestor = DocumentIngestor(
        application, documentDao, knowledgeDao, memoryDao, aiEngine, personalityEngine, pdfHelper
    )
    val mediaIngestor = MediaIngestor(
        application, documentDao, knowledgeDao, memoryDao, aiEngine, personalityEngine, pdfHelper, visionEngine, whisperEngine
    )

    private val _attachedImageUri = MutableStateFlow<Uri?>(null)
    val attachedImageUri: StateFlow<Uri?> = _attachedImageUri.asStateFlow()

    fun attachImage(uri: Uri?) {
        _attachedImageUri.value = uri
    }

    // Flow states
    val conversations = conversationDao.getAllConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val documents = documentDao.getAllDocuments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memories = memoryDao.getAllMemories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentConversationId = MutableStateFlow<Long?>(null)
    val currentConversationId: StateFlow<Long?> = _currentConversationId.asStateFlow()

    private val _currentMessages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val currentMessages: StateFlow<List<MessageEntity>> = _currentMessages.asStateFlow()

    private val _streamingMessageText = MutableStateFlow("")
    val streamingMessageText: StateFlow<String> = _streamingMessageText.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _languageMode = MutableStateFlow(LanguageMode.ENGLISH)
    val languageMode: StateFlow<LanguageMode> = _languageMode.asStateFlow()

    private val _ingestionState = MutableStateFlow(IngestionProgressState())
    val ingestionState: StateFlow<IngestionProgressState> = _ingestionState.asStateFlow()

    // Active Reader State
    private val _activeReaderDocument = MutableStateFlow<DocumentEntity?>(null)
    val activeReaderDocument: StateFlow<DocumentEntity?> = _activeReaderDocument.asStateFlow()

    private val _readerCurrentPage = MutableStateFlow(1)
    val readerCurrentPage: StateFlow<Int> = _readerCurrentPage.asStateFlow()

    private val _readerPageBitmap = MutableStateFlow<Bitmap?>(null)
    val readerPageBitmap: StateFlow<Bitmap?> = _readerPageBitmap.asStateFlow()

    private val _readerZoom = MutableStateFlow(1.0f)
    val readerZoom: StateFlow<Float> = _readerZoom.asStateFlow()

    private val _readerBookmarks = MutableStateFlow<List<BookmarkEntity>>(emptyList())
    val readerBookmarks: StateFlow<List<BookmarkEntity>> = _readerBookmarks.asStateFlow()

    private val _pdfAssistantResponse = MutableStateFlow("")
    val pdfAssistantResponse: StateFlow<String> = _pdfAssistantResponse.asStateFlow()

    private val _pdfAssistantLoading = MutableStateFlow(false)
    val pdfAssistantLoading: StateFlow<Boolean> = _pdfAssistantLoading.asStateFlow()

    private var activeInferenceJob: Job? = null
    private var activeIngestionJob: Job? = null
    private var messagesCollectionJob: Job? = null
    private var bookmarkCollectionJob: Job? = null
    private var pdfAssistantJob: Job? = null

    init {
        viewModelScope.launch {
            personalityEngine.loadProfile()
            initDefaultConversationIfNeeded()
            modelProvisioner.provisionBundledModelsIfNeeded()
        }
    }

    private suspend fun initDefaultConversationIfNeeded() {
        val convList = conversationDao.getConversationById(1)
        if (convList == null) {
            val id = conversationDao.insertConversation(
                ConversationEntity(
                    id = 1,
                    title = "Primary Neural Dialogue",
                    languageCode = _languageMode.value.code
                )
            )
            _currentConversationId.value = id
            // Add initial greeting from AI
            messageDao.insertMessage(
                MessageEntity(
                    conversationId = id,
                    isUser = false,
                    content = "Hello! I am COMPANION AI. Chat generation runs locally after you import and load a compatible GGUF text model in Brain.\n\nI can index selectable-text PDFs, DOCX, TXT, Markdown, and notes. Image understanding, audio transcription, and video analysis are not available in this build yet. Conversations and memories are stored in the app on this device.",
                    emotionTag = "CURIOSITY"
                )
            )
            loadMessagesForConversation(id)
        } else {
            _currentConversationId.value = 1
            loadMessagesForConversation(1)
        }
    }

    fun selectConversation(id: Long) {
        _currentConversationId.value = id
        loadMessagesForConversation(id)
    }

    fun createNewConversation(title: String = "New Neural Session") {
        viewModelScope.launch {
            val id = conversationDao.insertConversation(
                ConversationEntity(
                    title = title,
                    languageCode = _languageMode.value.code
                )
            )
            _currentConversationId.value = id
            loadMessagesForConversation(id)
        }
    }

    fun renameConversation(id: Long, newTitle: String) {
        viewModelScope.launch {
            conversationDao.renameConversation(id, newTitle)
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            conversationDao.deleteConversation(id)
            if (_currentConversationId.value == id) {
                initDefaultConversationIfNeeded()
            }
        }
    }

    private fun loadMessagesForConversation(conversationId: Long) {
        messagesCollectionJob?.cancel()
        messagesCollectionJob = viewModelScope.launch {
            messageDao.getMessagesForConversation(conversationId).collect { msgs ->
                _currentMessages.value = msgs
            }
        }
    }

    fun setLanguage(lang: LanguageMode) {
        _languageMode.value = lang
    }

    fun sendMessage(userText: String, imageUri: Uri? = null) {
        val imageToProcess = imageUri ?: _attachedImageUri.value
        if (userText.isBlank() && imageToProcess == null) return
        // Preserve the attachment when a previous generation is still active.
        if (_isGenerating.value) return
        _attachedImageUri.value = null
        // Set this synchronously, before scheduling work, to reject rapid duplicate sends.
        _isGenerating.value = true
        _streamingMessageText.value = ""
        val convId = _currentConversationId.value ?: 1L

        activeInferenceJob = viewModelScope.launch {
            try {
                messageDao.insertMessage(
                    MessageEntity(
                        conversationId = convId,
                        isUser = true,
                        content = userText.trim().ifBlank { "[Attached Image]" },
                        imageUri = imageToProcess?.toString()
                    )
                )

                personalityEngine.processDialogueEvolution(userText)

                var imagePrompt: String? = null
                if (imageToProcess != null) {
                    val analysis = try {
                        visionEngine.analyzeImage(imageToProcess)
                    } catch (error: Exception) {
                        val message = when (_languageMode.value) {
                            LanguageMode.ARABIC_IRAQI -> "تحليل الصور المحلي مو مفعّل بهذي النسخة بعد. ما راح أخمّن محتوى الصورة؛ جرّب المحادثة بدون مرفق حالياً."
                            LanguageMode.ARABIC_MSA -> "تحليل الصور المحلي غير متاح في هذا الإصدار. لن أخمّن محتوى الصورة."
                            LanguageMode.ENGLISH -> "Local image understanding is not available in this build. I won't guess what the image contains."
                        }
                        messageDao.insertMessage(
                            MessageEntity(
                                conversationId = convId,
                                isUser = false,
                                content = message,
                                emotionTag = "CONFUSION"
                            )
                        )
                        return@launch
                    }
                    imagePrompt = analysis.sceneDescription
                }

                // Corpus-aware BM25 search runs off the UI thread. We only pass
                // the highest-ranking excerpts to the model, never the whole library.
                val scoredCitations = withContext(Dispatchers.IO) {
                    val candidateChunks = knowledgeDao.getAllChunks()
                    val ranked = LocalEmbeddingEngine.rankByBm25(
                        query = userText,
                        documents = candidateChunks,
                        textOf = { it.content },
                        limit = 3
                    )
                    val titles = ranked.map { it.first.documentId }.distinct().associateWith { id ->
                        documentDao.getDocumentById(id)?.title ?: "Document #$id"
                    }
                    ranked.map { (chunk, _) ->
                        Citation(
                            documentTitle = titles[chunk.documentId] ?: "Document #${chunk.documentId}",
                            pageNumber = chunk.pageNumber,
                            timestampSeconds = chunk.timestampSeconds,
                            snippet = chunk.content.take(800)
                        )
                    }
                }

                val relevantMemories = withContext(Dispatchers.IO) {
                    memoryManager.retrieveRelevantMemories(userText, maxCount = 2)
                        .map { "${it.topic}: ${it.content}" }
                }

                val history = _currentMessages.value.takeLast(6).map { it.isUser to it.content }
                val fullAccumulator = StringBuilder()
                aiEngine.streamResponse(
                    userPrompt = userText,
                    systemPrompt = "Offline Companion AI",
                    recentHistory = history,
                    relevantMemories = relevantMemories,
                    relevantCitations = scoredCitations,
                    personality = personalityEngine.traits.value,
                    emotion = personalityEngine.emotion.value,
                    language = _languageMode.value,
                    temperature = modelManager.temperature.value,
                    imagePrompt = imagePrompt
                ).catch { error ->
                    fullAccumulator.append("\n[Local AI error: ${error.message ?: "Model execution failed"}]")
                    _streamingMessageText.value = fullAccumulator.toString()
                }.collect { token ->
                    fullAccumulator.append(token)
                    _streamingMessageText.value = fullAccumulator.toString()
                }

                val finalContent = fullAccumulator.toString().trim()
                val assistantContent = finalContent.ifBlank {
                    "[Local model returned no tokens. Check Brain Manager and model status.]"
                }
                messageDao.insertMessage(
                    MessageEntity(
                        conversationId = convId,
                        isUser = false,
                        content = assistantContent,
                        emotionTag = personalityEngine.emotion.value.current.name
                    )
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                messageDao.insertMessage(
                    MessageEntity(
                        conversationId = convId,
                        isUser = false,
                        content = "Local processing failed: ${error.message ?: "Unknown error"}",
                        emotionTag = "CONFUSION"
                    )
                )
            } finally {
                _streamingMessageText.value = ""
                _isGenerating.value = false
            }
        }
    }

    fun stopGeneration() {
        aiEngine.cancelInference()
        activeInferenceJob?.cancel()
        _isGenerating.value = false
        _streamingMessageText.value = ""
    }

    // --- File Ingestion ---
    fun ingestFile(uri: Uri, fileName: String, fileType: FileType) {
        // Prevent overlapping imports from racing over the shared progress state.
        if (_ingestionState.value.isIngesting) return
        activeIngestionJob = viewModelScope.launch {
            _ingestionState.value = IngestionProgressState(
                isIngesting = true,
                progress = 0.05f,
                statusText = "Analyzing $fileName...",
                fileName = fileName
            )

            try {
                val documentId = when (fileType) {
                    FileType.PDF, FileType.TXT, FileType.DOCX, FileType.MARKDOWN, FileType.NOTE ->
                        documentIngestor.ingestDocument(uri, fileName, fileType, _languageMode.value) { progress, status ->
                            _ingestionState.value = IngestionProgressState(true, progress, status, fileName)
                        }
                    FileType.VIDEO ->
                        mediaIngestor.ingestVideo(uri, fileName, _languageMode.value) { progress, status ->
                            _ingestionState.value = IngestionProgressState(true, progress, status, fileName)
                        }
                    FileType.IMAGE ->
                        mediaIngestor.ingestImage(uri, fileName, _languageMode.value) { progress, status ->
                            _ingestionState.value = IngestionProgressState(true, progress, status, fileName)
                        }
                    FileType.AUDIO ->
                        mediaIngestor.ingestAudio(uri, fileName, _languageMode.value) { progress, status ->
                            _ingestionState.value = IngestionProgressState(true, progress, status, fileName)
                        }
                }

                val document = documentDao.getDocumentById(documentId)
                _ingestionState.value = if (document?.status?.name == "COMPLETED") {
                    IngestionProgressState(
                        isIngesting = false,
                        progress = 1.0f,
                        statusText = "Imported and indexed successfully.",
                        fileName = fileName
                    )
                } else {
                    IngestionProgressState(
                        isIngesting = false,
                        progress = 0f,
                        statusText = document?.summary ?: "Import failed. The selected local media engine is unavailable.",
                        fileName = fileName
                    )
                }
            } catch (cancelled: CancellationException) {
                _ingestionState.value = IngestionProgressState(
                    isIngesting = false,
                    progress = 0f,
                    statusText = "Cancelled",
                    fileName = fileName
                )
                throw cancelled
            } catch (error: Exception) {
                _ingestionState.value = IngestionProgressState(
                    isIngesting = false,
                    progress = 0f,
                    statusText = "Import failed: " + (error.message ?: "Unknown error"),
                    fileName = fileName
                )
            } finally {
                activeIngestionJob = null
            }
        }
    }

    fun cancelIngestion() {
        activeIngestionJob?.cancel()
    }

    fun deleteDocument(id: Long) {
        viewModelScope.launch {
            documentDao.deleteDocument(id)
            knowledgeDao.deleteChunksForDocument(id)
        }
    }

    // --- PDF / Document Reader ---
    fun openDocumentInReader(document: DocumentEntity) {
        bookmarkCollectionJob?.cancel()
        viewModelScope.launch {
            _activeReaderDocument.value = document
            _readerCurrentPage.value = document.lastReadPage
            _readerZoom.value = 1.0f

            val cachedPath = document.localCachedPath
            if (cachedPath != null) {
                val file = File(cachedPath)
                if (file.exists() && document.fileType == FileType.PDF) {
                    val openedPages = withContext(Dispatchers.IO) { pdfHelper.openPdf(file) }
                    if (openedPages > 0) renderReaderPage(document.lastReadPage)
                }
            }

        }
        bookmarkCollectionJob = viewModelScope.launch {
            bookmarkDao.getBookmarksForDocument(document.id).collect { bookmarks ->
                _readerBookmarks.value = bookmarks
            }
        }
    }

    fun renderReaderPage(page: Int) {
        val doc = _activeReaderDocument.value ?: return
        val clampedPage = page.coerceIn(1, doc.pageCount.coerceAtLeast(1))
        _readerCurrentPage.value = clampedPage

        viewModelScope.launch {
            documentDao.updateLastReadPage(doc.id, clampedPage)
            if (doc.fileType == FileType.PDF) {
                val bitmap = withContext(Dispatchers.IO) {
                    pdfHelper.renderPage(clampedPage - 1, targetWidth = 1080)
                }
                _readerPageBitmap.value = bitmap
            }
        }
    }

    fun setReaderZoom(zoom: Float) {
        _readerZoom.value = zoom.coerceIn(0.75f, 3.0f)
    }

    fun addBookmark(note: String = "") {
        val doc = _activeReaderDocument.value ?: return
        val page = _readerCurrentPage.value
        viewModelScope.launch {
            bookmarkDao.insertBookmark(
                BookmarkEntity(
                    documentId = doc.id,
                    pageNumber = page,
                    title = "Page $page Bookmark",
                    note = note
                )
            )
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch {
            bookmarkDao.deleteBookmark(id)
        }
    }

    // --- AI PDF Assistant inside Document Reader ---
    fun askAiAboutCurrentDocument(prompt: String) {
        val doc = _activeReaderDocument.value ?: return
        pdfAssistantJob?.cancel()
        pdfAssistantJob = viewModelScope.launch {
            _pdfAssistantLoading.value = true
            _pdfAssistantResponse.value = ""
            try {
                // Keep source excerpts tied to their actual page numbers. When
                // the current page has no indexed chunks, use document excerpts
                // but never label them as belonging to the current page.
                val docChunks = knowledgeDao.getChunksForDocument(doc.id)
                val currentPage = _readerCurrentPage.value
                val pageChunks = docChunks.filter { it.pageNumber == currentPage }
                val contextChunks = (if (pageChunks.isNotEmpty()) pageChunks else docChunks)
                    .take(4)
                val citations = contextChunks.map { chunk ->
                    Citation(
                        documentTitle = doc.title,
                        pageNumber = chunk.pageNumber,
                        timestampSeconds = chunk.timestampSeconds,
                        snippet = chunk.content.take(800)
                    )
                }

                val full = StringBuilder()
                aiEngine.streamResponse(
                    userPrompt = prompt,
                    systemPrompt = "You are a built-in AI PDF reader companion analyzing ${doc.title} at page $currentPage. Ground answers in supplied excerpts; if no excerpts are supplied, say the selected page has no indexed text.",
                    recentHistory = emptyList(),
                    relevantMemories = listOf("Document: ${doc.title}"),
                    relevantCitations = citations,
                    personality = personalityEngine.traits.value,
                    emotion = personalityEngine.emotion.value,
                    language = _languageMode.value
                ).collect { token ->
                    full.append(token)
                    _pdfAssistantResponse.value = full.toString()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _pdfAssistantResponse.value = "Error: ${error.message ?: "Local PDF assistant failed."}"
            } finally {
                _pdfAssistantLoading.value = false
            }
        }
    }

    // --- Memory Operations ---
    fun addMemory(tier: MemoryTier, topic: String, content: String, importance: Float) {
        viewModelScope.launch {
            memoryManager.addMemory(tier, topic, content, importance)
        }
    }

    fun updateMemory(memory: MemoryEntity) {
        viewModelScope.launch {
            memoryManager.updateMemory(memory)
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            memoryManager.deleteMemory(id)
        }
    }

    fun clearMemories(tier: MemoryTier) {
        viewModelScope.launch {
            memoryManager.clearTier(tier)
        }
    }

    // --- Personality & Emotion ---
    fun updatePersonalityTraits(traits: PersonalityTraits) {
        viewModelScope.launch {
            personalityEngine.updateTraits(traits)
        }
    }

    fun resetPersonality() {
        viewModelScope.launch {
            personalityEngine.resetToBaseline()
        }
    }

    fun getHardwareDiagnostics(): HardwareReport {
        return modelManager.getHardwareReport()
    }

    fun updateContextWindow(size: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            modelManager.updateContextWindow(size)
        }
    }

    fun selectModel(model: GGUFModelInfo) {
        // Loading multi-gigabyte GGUF weights is blocking native work; keep it off Main.
        viewModelScope.launch(Dispatchers.IO) {
            modelManager.selectModel(model)
        }
    }

    fun retryModelProvisioning() {
        viewModelScope.launch {
            modelProvisioner.provisionBundledModelsIfNeeded()
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            db.clearAllTables()
            initDefaultConversationIfNeeded()
        }
    }

    override fun onCleared() {
        super.onCleared()
        pdfHelper.close()
    }
}
