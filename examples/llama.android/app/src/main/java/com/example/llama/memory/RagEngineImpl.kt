package com.example.llama.memory

class RagEngineImpl(
    private val memoryManager: MemoryManager,
    private val contextRetriever: ContextRetriever
) : RagEngine {

    override suspend fun buildContext(
        conversationId: Long,
        currentMessage: String,
        recentMessageLimit: Int,
        memoryTopK: Int,
        retrievedMessageTopK: Int
    ): String {
        val summary = memoryManager.getConversationSummary(conversationId)
        val recentMessages = memoryManager.getRecentMessages(
            conversationId,
            recentMessageLimit
        )

        val rag = contextRetriever.retrieve(
            query = currentMessage,
            conversationId = conversationId,
            memoryTopK = memoryTopK,
            messageTopK = retrievedMessageTopK
        )

        val recentIds = recentMessages.map { it.id }.toSet()
        val retrievedMessages = rag.messages
            .filterNot { it.id in recentIds }
            .sortedBy { it.timestamp }

        return buildString {
            if (rag.memories.isNotEmpty()) {
                appendLine("RELEVANT USER MEMORY:")
                rag.memories.forEach { memory ->
                    appendLine("- ${memory.key}: ${memory.value}")
                }
                appendLine()
            }

            if (!summary.isNullOrBlank()) {
                appendLine("CONVERSATION SUMMARY:")
                appendLine(summary)
                appendLine()
            }

            if (retrievedMessages.isNotEmpty()) {
                appendLine("RELEVANT PREVIOUS CONVERSATION:")
                retrievedMessages.forEach { message ->
                    val role = if (message.role == "user") "User" else "Assistant"
                    appendLine("$role: ${message.content}")
                }
                appendLine()
            }

            if (recentMessages.isNotEmpty()) {
                appendLine("RECENT CONVERSATION:")
                recentMessages.forEach { message ->
                    val role = if (message.role == "user") "User" else "Assistant"
                    appendLine("$role: ${message.content}")
                }
                appendLine()
            }
        }
    }
}
