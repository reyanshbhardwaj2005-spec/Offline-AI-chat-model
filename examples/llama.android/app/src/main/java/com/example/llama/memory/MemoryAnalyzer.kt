package com.example.llama.memory

/**
 * Converts a completed user/assistant turn into a structured memory decision.
 * The analyzer receives a generation function so it does not own persistence
 * and can remain decoupled from MemoryManager.
 */
interface MemoryAnalyzer {
    suspend fun analyze(
        userMessage: String,
        assistantResponse: String,
        generate: suspend (prompt: String, maxTokens: Int) -> String
    ): MemoryDecision
}
