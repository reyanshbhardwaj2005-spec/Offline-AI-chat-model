package com.example.llama.memory.vector

interface VectorStore {
    suspend fun upsert(
        sourceType: String,
        sourceId: Long,
        conversationId: Long?,
        content: String,
        vector: FloatArray,
        model: String
    )

    suspend fun search(
        queryVector: FloatArray,
        topK: Int = 5,
        sourceTypes: Set<String>? = null,
        conversationId: Long? = null,
        minimumScore: Float = 0.20f
    ): List<VectorSearchResult>

    suspend fun delete(sourceType: String, sourceId: Long)
    suspend fun deleteMany(sourceType: String, sourceIds: List<Long>)
    suspend fun deleteForConversation(conversationId: Long)
    suspend fun clear()
}
