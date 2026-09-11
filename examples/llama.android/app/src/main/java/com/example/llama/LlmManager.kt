package com.example.llama

import android.content.Context
import com.arm.aichat.AiChat
import com.example.llama.memory.ContextBuilder
import com.example.llama.memory.MemoryExtractor
import com.example.llama.memory.MemoryManager
import com.example.llama.memory.MessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LlmManager(context: Context) {

    private val engine = AiChat.getInferenceEngine(context.applicationContext)
    private val memoryManager = MemoryManager(context.applicationContext)
    private val memoryExtractor = MemoryExtractor()
    private val contextBuilder = ContextBuilder(memoryManager)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engineMutex = Mutex()

    @Volatile
    private var generationStopped = false

    private companion object {
        const val SUMMARY_TRIGGER = 20
        const val SUMMARY_MESSAGE_COUNT = 8
        const val RECENT_MESSAGE_COUNT = 12
        const val SUMMARY_MAX_TOKENS = 300
        const val UI_UPDATE_INTERVAL_MS = 50L
        const val THINKING_SIGNAL = "__THINKING__"
    }

    interface LoadCallback {
        fun onSuccess()
        fun onError(error: Exception)
    }

    interface ChatCallback {
        fun onToken(token: String)
        fun onComplete()
        fun onStopped()
        fun onError(error: Exception)
    }

    fun loadModel(path: String, callback: LoadCallback) {
        scope.launch {
            try {
                engineMutex.withLock {
                    generationStopped = false
                    withContext(Dispatchers.IO) {
                        engine.loadModel(path)
                    }
                    restoreConversation()
                }
                withContext(Dispatchers.Main) { callback.onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { callback.onError(e) }
            }
        }
    }

    private suspend fun restoreConversation() {
        val context = withContext(Dispatchers.IO) {
            contextBuilder.buildContext("", RECENT_MESSAGE_COUNT)
        }

        if (context.isBlank()) return

        engine.setSystemPrompt("""
            You are an offline AI assistant.

            You have access to persistent information about the user,
            a summary of older conversations, and recent conversation history.

            Use this information to maintain continuity.

            IMPORTANT:
            - Treat persistent memory as facts about the user.
            - Treat conversation summary as previous conversation context.
            - Treat recent conversation as actual previous dialogue.
            - Do not claim to remember something that is not present.
            - Do not invent missing information.
            - If information conflicts, prefer the most recently updated information.
            - Answer the user's current question normally.

            $context
        """.trimIndent())
    }

    fun sendMessage(message: String, callback: ChatCallback) {
        generationStopped = false

        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    memoryManager.saveMessage("user", message)
                }

                val memories = memoryExtractor.extract(message)

                withContext(Dispatchers.IO) {
                    memories.forEach { memory ->
                        memoryManager.saveMemory(
                            memory.key,
                            memory.value,
                            memory.importance
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    callback.onToken(THINKING_SIGNAL)
                }

                val responseBuilder = StringBuilder()
                val uiBuffer = StringBuilder()
                var lastUiUpdate = System.currentTimeMillis()

                engine.sendUserPrompt(message).collect { token ->
                    if (generationStopped) return@collect

                    responseBuilder.append(token)
                    uiBuffer.append(token)

                    val now = System.currentTimeMillis()

                    if (now - lastUiUpdate >= UI_UPDATE_INTERVAL_MS) {
                        val textToSend = uiBuffer.toString()
                        uiBuffer.clear()
                        lastUiUpdate = now

                        if (textToSend.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                callback.onToken(textToSend)
                            }
                        }
                    }
                }

                if (generationStopped) {
                    withContext(Dispatchers.Main) { callback.onStopped() }
                    return@launch
                }

                if (uiBuffer.isNotEmpty()) {
                    val remaining = uiBuffer.toString()
                    uiBuffer.clear()

                    withContext(Dispatchers.Main) {
                        callback.onToken(remaining)
                    }
                }

                val completeResponse = responseBuilder.toString()

                withContext(Dispatchers.IO) {
                    memoryManager.saveMessage("assistant", completeResponse)
                }

                withContext(Dispatchers.Main) {
                    callback.onComplete()
                }
            } catch (e: Exception) {
                if (generationStopped) {
                    withContext(Dispatchers.Main) { callback.onStopped() }
                } else {
                    withContext(Dispatchers.Main) { callback.onError(e) }
                }
            }
        }
    }

    fun stopGeneration() {
        generationStopped = true
        engine.stopGeneration()
    }

    private suspend fun generateConversationSummary(conversation: String): String {
        val prompt = """
            You are creating a memory summary for another AI assistant.

            Summarize the conversation below for future use.

            Preserve only information useful for future conversations.

            Preserve:
            - important user facts
            - projects
            - goals
            - decisions
            - technical context
            - unresolved problems
            - preferences
            - important previous discussion

            Do not invent information.
            Do not answer the conversation.
            Do not add commentary.
            Write a concise factual summary.

            CONVERSATION:

            $conversation
        """.trimIndent()

        val result = StringBuilder()

        engine.sendUserPrompt(
            prompt,
            predictLength = SUMMARY_MAX_TOKENS
        ).collect { token -> result.append(token) }

        return result.toString().trim()
    }

    private suspend fun buildSummaryInput(
        previousSummary: String?,
        messages: List<MessageEntity>
    ): String {
        if (messages.isEmpty()) return ""

        return buildString {
            appendLine("Summarize the following conversation for future use by an offline AI assistant.")
            appendLine()
            appendLine("Preserve:")
            appendLine("- important user facts")
            appendLine("- user's goals")
            appendLine("- projects")
            appendLine("- decisions")
            appendLine("- problems")
            appendLine("- preferences")
            appendLine("- important technical context")
            appendLine()
            appendLine("Do not invent information.")
            appendLine()

            if (!previousSummary.isNullOrBlank()) {
                appendLine("PREVIOUS SUMMARY:")
                appendLine(previousSummary)
                appendLine()
            }

            appendLine("OLDER CONVERSATION:")

            messages.forEach { message ->
                val role = if (message.role == "user") "User" else "Assistant"
                appendLine("$role: ${message.content}")
            }
        }
    }

    private suspend fun updateConversationSummary() {
        val messageCount = withContext(Dispatchers.IO) {
            memoryManager.getMessageCount()
        }

        if (messageCount < SUMMARY_TRIGGER) return

        val oldMessages = withContext(Dispatchers.IO) {
            memoryManager.getOldMessages(SUMMARY_MESSAGE_COUNT)
        }

        if (oldMessages.isEmpty()) return

        val previousSummary = withContext(Dispatchers.IO) {
            memoryManager.getConversationSummary()
        }

        val summaryInput = buildSummaryInput(previousSummary, oldMessages)
        if (summaryInput.isBlank()) return

        val summary = generateConversationSummary(summaryInput)
        if (summary.isBlank()) return

        withContext(Dispatchers.IO) {
            memoryManager.saveConversationSummary(summary)
            memoryManager.deleteOldMessages(SUMMARY_MESSAGE_COUNT)
        }

        rebuildConversationContext()
    }

    private suspend fun rebuildConversationContext() {
        val context = withContext(Dispatchers.IO) {
            contextBuilder.buildContext("", RECENT_MESSAGE_COUNT)
        }

        if (context.isBlank()) return

        engine.setSystemPrompt("""
            You are an offline AI assistant.

            Maintain continuity with the user using the information provided below.

            IMPORTANT RULES:
            - Persistent memory contains facts about the user.
            - Conversation summary contains important older context.
            - Recent conversation contains the latest dialogue.
            - Use information only when relevant.
            - Do not invent missing information.
            - If information conflicts, prefer the most recently updated persistent memory.
            - Answer the user's current question normally.

            $context
        """.trimIndent())
    }

    fun setSystemPrompt(prompt: String, callback: LoadCallback) {
        scope.launch {
            try {
                engineMutex.withLock {
                    engine.setSystemPrompt(prompt)
                }
                withContext(Dispatchers.Main) { callback.onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { callback.onError(e) }
            }
        }
    }

    fun saveMemory(key: String, value: String, importance: Int = 1) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    memoryManager.saveMemory(key, value, importance)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun destroy() {
        generationStopped = true
        engine.stopGeneration()
        engine.destroy()
        memoryManager.close()
        scope.cancel()
    }
}
