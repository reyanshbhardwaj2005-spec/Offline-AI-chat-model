package com.example.llama.ai.chat

import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.flow.Flow

class LlamaChatEngine(context: Context) : ChatEngine {
    private val inferenceEngine: InferenceEngine =
        AiChat.getInferenceEngine(context.applicationContext)

    override suspend fun loadModel(path: String) {
        inferenceEngine.loadModel(path)
    }

    override suspend fun initMultimodal(mmprojPath: String): Boolean {
        return inferenceEngine.initMultimodal(mmprojPath)
    }

    override suspend fun setSystemPrompt(prompt: String) {
        inferenceEngine.setSystemPrompt(prompt)
    }

    override suspend fun resetConversation() {
        inferenceEngine.resetConversation()
    }

    override fun sendMessage(
        message: String,
        predictLength: Int
    ): Flow<String> {
        return inferenceEngine.sendUserPrompt(
            message = message,
            predictLength = predictLength
        )
    }

    override fun sendImageMessage(
        imagePath: String,
        message: String,
        predictLength: Int
    ): Flow<String> {
        return inferenceEngine.sendImagePrompt(
            imagePath = imagePath,
            message = message,
            predictLength = predictLength
        )
    }

    override fun stopGeneration() {
        inferenceEngine.stopGeneration()
    }

    override fun unload() {
        inferenceEngine.cleanUp()
    }

    override fun destroy() {
        inferenceEngine.destroy()
    }
}
