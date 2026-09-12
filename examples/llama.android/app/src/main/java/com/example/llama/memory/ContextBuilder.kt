package com.example.llama.memory

class ContextBuilder(private val memoryManager: MemoryManager) {

    suspend fun buildContext(conversationId: Long, currentMessage: String, recentMessageLimit: Int): String {

        val memories = findRelevantMemories(currentMessage)
        val summary = memoryManager.getConversationSummary(conversationId)
        val messages = memoryManager.getRecentMessages(conversationId, recentMessageLimit)

        return buildString {

            if (memories.isNotEmpty()) {
                appendLine("RELEVANT USER MEMORY:")
                memories.forEach { memory ->
                    appendLine("- ${memory.key}: ${memory.value}")
                }
                appendLine()
            }

            if (!summary.isNullOrBlank()) {
                appendLine("CONVERSATION SUMMARY:")
                appendLine(summary)
                appendLine()
            }

            if (messages.isNotEmpty()) {
                appendLine("RECENT CONVERSATION:")
                messages.forEach { message ->
                    val role = if (message.role == "user") "User" else "Assistant"
                    appendLine("$role: ${message.content}")
                }
                appendLine()
            }
        }
    }

    private suspend fun findRelevantMemories(message: String): List<MemoryEntity> {

        val words = normalizeWords(message)
        if (words.isEmpty()) {
            return memoryManager
                .getAllMemories()
                .take(5)
        }

        val results = mutableMapOf<Long, MemoryEntity>()

        for (word in words) {
            memoryManager.searchMemories(word).forEach { memory ->
                results[memory.id] = memory
            }
        }

        return results.values
            .sortedWith(compareByDescending<MemoryEntity> { it.importance }
                    .thenByDescending { it.updatedAt }).take(5)
    }

    private fun normalizeWords(message: String): List<String> {

        val text = message.lowercase()
        val words = mutableListOf<String>()

        words += text
            .split(Regex("[^a-zA-Z0-9+#]+"))
            .filter { it.length >= 3 }

        if (text.contains("learn") || text.contains("learning") || text.contains("studying") || text.contains("study")) {
            words.add("learning")
        }

        if (text.contains("name") || text.contains("who am i")) {
            words.add("name")
        }

        if (text.contains("age") || text.contains("old")) {
            words.add("age")
        }

        if (
            text.contains("live") ||
            text.contains("location") ||
            text.contains("city")
        ) {
            words.add("live")
        }

        if (text.contains("favorite") || text.contains("favourite")) {
            words.add("favorite")
        }

        if (
            text.contains("use") ||
            text.contains("using") ||
            text.contains("technology")
        ) {
            words.add("uses")
        }

        return words.distinct()
    }
}
