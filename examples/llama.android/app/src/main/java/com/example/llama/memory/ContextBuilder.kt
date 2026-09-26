package com.example.llama.memory

class ContextBuilder(
    private val memoryManager: MemoryManager
) {

    suspend fun buildContext(
        conversationId: Long,
        currentMessage: String,
        recentMessageLimit: Int
    ): String {
        memoryManager.ensureVectorIndex()

        return memoryManager.ragEngine.buildContext(
            conversationId = conversationId,
            currentMessage = currentMessage,
            recentMessageLimit = recentMessageLimit,
            memoryTopK = 5,
            retrievedMessageTopK = 5
        )
    }
}
