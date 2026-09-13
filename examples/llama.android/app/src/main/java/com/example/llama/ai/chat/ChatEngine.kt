package com.example.llama.ai.chat

import kotlinx.coroutines.flow.Flow

interface ChatEngine {

    /**
     * Load a GGUF model into the local inference engine.
     */
    suspend fun loadModel(path: String)

    /**
     * Set the system prompt used by the model.
     */
    suspend fun setSystemPrompt(prompt: String)

    /**
     * Reset the current llama.cpp conversation/KV context.
     *
     * This does NOT delete messages from Room.
     */
    suspend fun resetConversation()

    /**
     * Send a user message and receive generated tokens incrementally.
     */
    fun sendMessage(
        message: String,
        predictLength: Int = 1024
    ): Flow<String>

    /**
     * Stop the current generation.
     */
    fun stopGeneration()

    /**
     * Remove the currently loaded model while keeping the engine object alive.
     */
    fun unload()

    /**
     * Completely release the native inference engine.
     */
    fun destroy()
}
