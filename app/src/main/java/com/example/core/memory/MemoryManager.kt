package com.example.core.memory

import com.example.core.ai.LocalEmbeddingEngine
import com.example.core.model.MemoryTier
import com.example.database.dao.MemoryDao
import com.example.database.entities.MemoryEntity
import kotlinx.coroutines.flow.Flow

class MemoryManager(
    private val memoryDao: MemoryDao
) {

    fun getMemories(tier: MemoryTier? = null): Flow<List<MemoryEntity>> {
        return if (tier != null) {
            memoryDao.getMemoriesByTier(tier)
        } else {
            memoryDao.getAllMemories()
        }
    }

    suspend fun addMemory(
        tier: MemoryTier,
        topic: String,
        content: String,
        importanceScore: Float = 0.6f,
        sourceDocId: Long? = null,
        sourceDetails: String? = null
    ): Long {
        // Deduplication check: check if similar topic & tier exists
        val existing = memoryDao.getMemoriesListByTier(tier)
        val cleanTopic = topic.trim().lowercase()
        val match = existing.find { it.topic.trim().lowercase() == cleanTopic }

        if (match != null) {
            // Update existing memory with merged content and boosted importance
            val updated = match.copy(
                content = content,
                importanceScore = (match.importanceScore + importanceScore) / 2f,
                accessCount = match.accessCount + 1,
                updatedAt = System.currentTimeMillis()
            )
            memoryDao.updateMemory(updated)
            return match.id
        }

        val entity = MemoryEntity(
            tier = tier,
            topic = topic.trim(),
            content = content.trim(),
            importanceScore = importanceScore.coerceIn(0.1f, 1.0f),
            accessCount = 1,
            sourceDocumentId = sourceDocId,
            sourceDetails = sourceDetails
        )
        return memoryDao.insertMemory(entity)
    }

    suspend fun updateMemory(memory: MemoryEntity) {
        memoryDao.updateMemory(memory.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteMemory(id: Long) {
        memoryDao.deleteMemory(id)
    }

    suspend fun clearTier(tier: MemoryTier) {
        memoryDao.clearMemoriesByTier(tier)
    }

    /**
     * Retrieves the most relevant memories across tiers for a given user query.
     */
    suspend fun retrieveRelevantMemories(query: String, maxCount: Int = 4): List<MemoryEntity> {
        val queryVector = LocalEmbeddingEngine.generateEmbedding(query)
        val queryTokens = LocalEmbeddingEngine.tokenize(query)

        // Query candidates from all tiers
        val working = memoryDao.getMemoriesListByTier(MemoryTier.WORKING)
        val episodic = memoryDao.getMemoriesListByTier(MemoryTier.EPISODIC)
        val semantic = memoryDao.getMemoriesListByTier(MemoryTier.SEMANTIC)
        val personality = memoryDao.getMemoriesListByTier(MemoryTier.PERSONALITY)

        val all = working + episodic + semantic + personality
        if (all.isEmpty()) return emptyList()

        val scored = all.map { memory ->
            val memVector = LocalEmbeddingEngine.generateEmbedding(memory.topic + " " + memory.content)
            val cosine = LocalEmbeddingEngine.cosineSimilarity(queryVector, memVector)
            val bm25 = LocalEmbeddingEngine.computeBm25Score(
                queryTokens,
                LocalEmbeddingEngine.tokenize(memory.topic + " " + memory.content)
            )
            // Combined relevance: hybrid score boosted by importanceScore
            val relevance = (cosine * 0.45f) + ((bm25 / 5f).coerceIn(0f, 0.45f)) + (memory.importanceScore * 0.1f)
            Pair(memory, relevance)
        }

        val top = scored.filter { it.second > 0.15f }
            .sortedByDescending { it.second }
            .take(maxCount)
            .map { it.first }

        // Increment access count
        top.forEach { memoryDao.incrementAccessCount(it.id) }
        return top
    }
}
