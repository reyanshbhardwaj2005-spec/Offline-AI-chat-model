package com.example.llama.ai.chat

import kotlinx.coroutines.flow.Flow

interface ChatEngine {
    suspend fun loadModel(path: String)

    suspend fun initMultimodal(mmprojPath: String): Boolean

    suspend fun setSystemPrompt(prompt: String)
    suspend fun resetConversation()

    fun sendMessage(
        message: String,
        predictLength: Int = 1024
    ): Flow<String>

    fun sendImageMessage(
        imagePath: String,
        message: String,
        predictLength: Int = 1024
    ): Flow<String>

    fun stopGeneration()
    fun unload()
    fun destroy()
}
