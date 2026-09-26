package com.example.llama.memory

interface RagEngine {
    suspend fun buildContext(
        conversationId: Long,
        currentMessage: String,
        recentMessageLimit: Int = 12,
        memoryTopK: Int = 5,
        retrievedMessageTopK: Int = 5
    ): String
}
