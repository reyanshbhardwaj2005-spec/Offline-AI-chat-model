package com.example.llama.memory

/**
 * Compatibility facade. Semantic retrieval is now handled by ContextRetriever.
 */
class MemoryRetriever(
    private val contextRetriever: ContextRetriever
) {
    suspend fun retrieve(query: String, conversationId: Long, topK: Int = 5): List<MemoryEntity> {
        return contextRetriever.retrieve(
            query = query,
            conversationId = conversationId,
            memoryTopK = topK,
            messageTopK = 0
        ).memories
    }
}
