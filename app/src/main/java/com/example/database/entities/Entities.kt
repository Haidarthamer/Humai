package com.example.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.core.model.FileType
import com.example.core.model.IngestionStatus
import com.example.core.model.MemoryTier

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val languageCode: String = "en"
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId"])]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val isUser: Boolean,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val emotionTag: String? = null,
    val citationsJson: String = "[]", // Serialized list of citations
    val imageUri: String? = null // Optional attached image Uri for multimodal Qwen3-VL vision
)

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val fileType: FileType,
    val uriString: String,
    val localCachedPath: String? = null,
    val fileSizeBytes: Long = 0L,
    val importedAt: Long = System.currentTimeMillis(),
    val status: IngestionStatus = IngestionStatus.PENDING,
    val progress: Float = 0f,
    val summary: String = "",
    val pageCount: Int = 1,
    val wordCount: Int = 0,
    val lastReadPage: Int = 1,
    val tagsJson: String = "[]"
)

@Entity(
    tableName = "knowledge_chunks",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["documentId"])]
)
data class KnowledgeChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val chunkIndex: Int,
    val pageNumber: Int? = null,
    val timestampSeconds: Long? = null,
    val content: String,
    val embeddingVectorStr: String = "", // Comma-separated dense vector representation
    val keywords: String = "" // Space-separated key terms for BM25/keyword indexing
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tier: MemoryTier,
    val topic: String,
    val content: String,
    val importanceScore: Float = 0.5f, // 0.0 to 1.0
    val accessCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val sourceDocumentId: Long? = null,
    val sourceDetails: String? = null
)

@Entity(tableName = "personality_profile")
data class PersonalityProfileEntity(
    @PrimaryKey val id: Int = 1,
    val humor: Float = 0.55f,
    val curiosity: Float = 0.85f,
    val friendliness: Float = 0.90f,
    val patience: Float = 0.80f,
    val creativity: Float = 0.70f,
    val formality: Float = 0.40f,
    val enthusiasm: Float = 0.75f,
    val communicationStyle: String = "EMPATHETIC",
    val simulatedEmotion: String = "CURIOSITY",
    val emotionIntensity: Float = 0.7f,
    val lastEvolutionReason: String = "Initialized baseline neural matrix",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["documentId"])]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val pageNumber: Int,
    val title: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
