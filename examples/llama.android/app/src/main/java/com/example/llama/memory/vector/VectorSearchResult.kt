package com.example.llama.memory.vector

data class VectorSearchResult(
    val sourceType: String,
    val sourceId: Long,
    val conversationId: Long?,
    val content: String,
    val score: Float
)
