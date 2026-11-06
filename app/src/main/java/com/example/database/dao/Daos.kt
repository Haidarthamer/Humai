package com.example.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.core.model.FileType
import com.example.core.model.MemoryTier
import com.example.database.entities.BookmarkEntity
import com.example.database.entities.ConversationEntity
import com.example.database.entities.DocumentEntity
import com.example.database.entities.KnowledgeChunkEntity
import com.example.database.entities.MemoryEntity
import com.example.database.entities.MessageEntity
import com.example.database.entities.PersonalityProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationById(id: Long): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity): Long

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: Long)

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renameConversation(id: Long, title: String, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesList(conversationId: Long): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: Long)

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun getTotalMessageCount(): Int
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY importedAt DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE fileType = :fileType ORDER BY importedAt DESC")
    fun getDocumentsByType(fileType: FileType): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    fun getDocumentByIdFlow(id: Long): Flow<DocumentEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Query("UPDATE documents SET lastReadPage = :page WHERE id = :id")
    suspend fun updateLastReadPage(id: Long, page: Int)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: Long)

    @Query("SELECT COUNT(*) FROM documents")
    suspend fun getTotalDocumentCount(): Int
}

@Dao
interface KnowledgeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<KnowledgeChunkEntity>)

    @Query("SELECT * FROM knowledge_chunks WHERE documentId = :documentId ORDER BY chunkIndex ASC")
    suspend fun getChunksForDocument(documentId: Long): List<KnowledgeChunkEntity>

    @Query("SELECT * FROM knowledge_chunks")
    suspend fun getAllChunks(): List<KnowledgeChunkEntity>

    @Query("SELECT * FROM knowledge_chunks WHERE content LIKE '%' || :keyword || '%' OR keywords LIKE '%' || :keyword || '%' LIMIT 20")
    suspend fun searchChunksByKeyword(keyword: String): List<KnowledgeChunkEntity>

    @Query("DELETE FROM knowledge_chunks WHERE documentId = :documentId")
    suspend fun deleteChunksForDocument(documentId: Long)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY importanceScore DESC, updatedAt DESC")
    fun getAllMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE tier = :tier ORDER BY importanceScore DESC, updatedAt DESC")
    fun getMemoriesByTier(tier: MemoryTier): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE tier = :tier")
    suspend fun getMemoriesListByTier(tier: MemoryTier): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE content LIKE '%' || :query || '%' OR topic LIKE '%' || :query || '%'")
    suspend fun searchMemories(query: String): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Query("UPDATE memories SET accessCount = accessCount + 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun incrementAccessCount(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemory(id: Long)

    @Query("DELETE FROM memories WHERE tier = :tier")
    suspend fun clearMemoriesByTier(tier: MemoryTier)
}

@Dao
interface PersonalityDao {
    @Query("SELECT * FROM personality_profile WHERE id = 1 LIMIT 1")
    fun getPersonalityProfile(): Flow<PersonalityProfileEntity?>

    @Query("SELECT * FROM personality_profile WHERE id = 1 LIMIT 1")
    suspend fun getPersonalityProfileDirect(): PersonalityProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPersonalityProfile(profile: PersonalityProfileEntity)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE documentId = :documentId ORDER BY pageNumber ASC")
    fun getBookmarksForDocument(documentId: Long): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Long)
}
