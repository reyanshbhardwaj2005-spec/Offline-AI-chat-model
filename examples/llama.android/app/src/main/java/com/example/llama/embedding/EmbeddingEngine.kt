package com.example.llama.embedding

/**
 * Offline semantic embedding provider used by the vector/RAG layer.
 */
interface EmbeddingEngine {
    suspend fun embed(text: String): FloatArray
    suspend fun embedBatch(texts: List<String>): List<FloatArray>

    val modelName: String
    val dimension: Int

    /** Releases the native embedding model/context if it was loaded. */
    fun close()
}
