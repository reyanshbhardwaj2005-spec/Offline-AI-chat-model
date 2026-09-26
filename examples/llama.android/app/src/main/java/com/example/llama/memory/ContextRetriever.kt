package com.example.llama.memory

import com.example.llama.embedding.EmbeddingEngine
import com.example.llama.memory.vector.VectorStore

class ContextRetriever(
    private val memoryManager: MemoryManager,
    private val embeddingEngine: EmbeddingEngine,
    private val vectorStore: VectorStore
) {

    suspend fun retrieve(
        query: String,
        conversationId: Long,
        memoryTopK: Int = 5,
        messageTopK: Int = 5,
        minimumScore: Float = 0.20f
    ): RagContext {
        if (query.isBlank()) {
            return RagContext(emptyList(), emptyList())
        }

        // The concrete LocalEmbeddingEngine applies the E5 `query:` prefix.
        // Keeping this call generic lets the RAG layer remain model-agnostic.
        val queryVector = embeddingEngine.embed(query)

        val memoryResults = vectorStore.search(
            queryVector = queryVector,
            topK = memoryTopK,
            sourceTypes = setOf(RagSource.MEMORY),
            conversationId = null,
            minimumScore = minimumScore
        )

        // Search across all conversations intentionally: this is long-term
        // semantic memory. RagEngineImpl removes messages already present in
        // the current recent context.
        val messageResults = vectorStore.search(
            queryVector = queryVector,
            topK = messageTopK,
            sourceTypes = setOf(RagSource.MESSAGE),
            conversationId = null,
            minimumScore = minimumScore
        )

        val memories = memoryResults
            .mapNotNull { memoryManager.getMemoryById(it.sourceId) }

        val messages = messageResults
            .mapNotNull { memoryManager.getMessageById(it.sourceId) }

        return RagContext(
            memories = memories,
            messages = messages
        )
    }
}
