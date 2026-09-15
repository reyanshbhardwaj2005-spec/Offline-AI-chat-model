package com.example.llama

import android.content.Context
import com.example.llama.ai.chat.LlamaChatEngine
import com.example.llama.memory.ContextBuilder
import com.example.llama.memory.ConversationEntity
import com.example.llama.memory.MemoryManager
import com.example.llama.memory.MessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LlmManager(context: Context) {

    private val appContext = context.applicationContext

    private val engine = LlamaChatEngine(appContext)

    /*
     * MemoryManager owns the Room database.
     */
    private val memoryManager = MemoryManager(appContext)

    private val contextBuilder = ContextBuilder(
        memoryManager = memoryManager
    )

    /*
     * Main manager scope.
     */
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default
    )

    /*
     * Only one operation may use the native
     * llama.cpp engine at a time.
     */
    private val engineMutex = Mutex()

    @Volatile
    private var generationStopped = false

    @Volatile
    private var currentConversationId: Long = -1L

    @Volatile
    private var modelLoaded = false

    private var summaryJob: Job? = null

    companion object {

        private const val SUMMARY_TRIGGER = 20

        private const val SUMMARY_MESSAGE_COUNT = 8

        private const val RECENT_MESSAGE_COUNT = 12

        private const val SUMMARY_MAX_TOKENS = 300

        private const val UI_UPDATE_INTERVAL_MS = 50L

        const val THINKING_SIGNAL = "__THINKING__"
    }

    // ========================================================================
    // CALLBACKS
    // ========================================================================

    interface LoadCallback {

        fun onSuccess()

        fun onError(exception: Exception)
    }

    interface ChatCallback {

        fun onToken(text: String)

        fun onComplete(fullResponse: String)

        fun onStopped()

        fun onError(exception: Exception)
    }

    interface ConversationCallback {

        fun onSuccess(conversation: ConversationEntity)

        fun onError(exception: Exception)
    }

    interface ConversationsCallback {

        fun onSuccess(conversations: List<ConversationEntity>)

        fun onError(exception: Exception)
    }

    interface MessagesCallback {

        fun onSuccess(messages: List<MessageEntity>)

        fun onError(exception: Exception)
    }

    // ========================================================================
    // LOAD MODEL
    // ========================================================================

    fun loadModel(
        modelPath: String,
        callback: LoadCallback
    ) {
        scope.launch {

            try {

                withMain {
                    // Loading state is represented by MainActivity itself.
                }

                engineMutex.withLock {

                    /*
                     * Load the model.
                     */
                    engine.loadModel(modelPath)

                    /*
                     * IMPORTANT:
                     *
                     * If a conversation has already been selected,
                     * KEEP IT.
                     *
                     * Only choose the most recent conversation when
                     * there is no active conversation yet.
                     */
                    val conversation =
                        if (currentConversationId != -1L) {

                            memoryManager.getConversation(
                                currentConversationId
                            ) ?: memoryManager.getOrCreateCurrentConversation()

                        } else {

                            memoryManager.getOrCreateCurrentConversation()
                        }

                    currentConversationId = conversation.id

                    modelLoaded = true

                    /*
                     * Native llama.cpp currently contains a fresh
                     * context because the model was just loaded.
                     *
                     * Rebuild it ONLY from this conversation.
                     */
                    rebuildConversationContext()
                }

                withMain {
                    callback.onSuccess()
                }

            } catch (e: Exception) {

                modelLoaded = false

                withMain {
                    callback.onError(e)
                }
            }
        }
    }

    // ========================================================================
    // SEND MESSAGE
    // ========================================================================

    fun sendMessage(
        message: String, callback: ChatCallback
    ) {

        scope.launch {

            try {

                if (!modelLoaded) {

                    withMain {
                        callback.onError(
                            IllegalStateException(
                                "Model is not loaded"
                            )
                        )
                    }

                    return@launch
                }

                /*
                 * Capture the conversation before starting.
                 */
                val conversationId = currentConversationId

                if (conversationId == -1L) {

                    withMain {
                        callback.onError(
                            IllegalStateException(
                                "No conversation is selected"
                            )
                        )
                    }

                    return@launch
                }
                generationStopped = false

                withMain {
                    callback.onToken(
                        THINKING_SIGNAL
                    )
                }

                val responseBuilder = StringBuilder()

                var lastUiUpdate = System.currentTimeMillis()

                engineMutex.withLock {

                    /*
                     * Verify that this conversation is still active.
                     */
                    if (
                        conversationId != currentConversationId ||
                        generationStopped
                    ) {
                        return@withLock
                    }

                    /*
                     * Save the user message only after we have exclusive
                     * ownership of the native engine.
                     */
                    memoryManager.saveMessage(
                        conversationId = conversationId,
                        role = "user",
                        content = message
                    )

                    /*
                     * Generate using the native context belonging to this
                     * conversation.
                     */
                    engine.sendMessage(
                        message
                    ).collect { token ->

                        if (generationStopped || conversationId != currentConversationId) {
                            return@collect
                        }

                        responseBuilder.append(token)

                        val now = System.currentTimeMillis()

                        /*
                         * Do not update the Android UI for
                         * every individual native token.
                         */
                        if (now - lastUiUpdate >= UI_UPDATE_INTERVAL_MS) {

                            val text = responseBuilder.toString()

                            withMain {
                                callback.onToken(text)
                            }

                            lastUiUpdate = now
                        }
                    }
                }

                /*
                 * Conversation was switched or generation
                 * was stopped.
                 */
                if (generationStopped || conversationId != currentConversationId) {

                    withMain {
                        callback.onStopped()
                    }

                    return@launch
                }

                val finalResponse = responseBuilder.toString()

                /*
                 * Make sure the final UI text is displayed.
                 */
                withMain {
                    callback.onToken(finalResponse)
                }

                /*
                 * Save assistant response into the SAME
                 * conversation.
                 */
                memoryManager.saveMessage(
                    conversationId = conversationId, role = "assistant", content = finalResponse
                )

                withMain {
                    callback.onComplete(finalResponse)
                }

                /*
                 * Summary runs after normal generation.
                 */
                updateSummaryIfNecessary(
                    conversationId
                )

            } catch (e: Exception) {

                if (generationStopped) {

                    withMain {
                        callback.onStopped()
                    }

                } else {

                    withMain {
                        callback.onError(e)
                    }
                }
            }
        }
    }

    // ========================================================================
    // STOP GENERATION
    // ========================================================================

    fun stopGeneration() {

        generationStopped = true

        engine.stopGeneration()
    }

    // ========================================================================
    // CREATE NEW CONVERSATION
    // ========================================================================

    fun createNewConversation(
        callback: ConversationCallback
    ) {

        scope.launch {

            try {

                generationStopped = true

                engine.stopGeneration()

                /*
                 * Create the new Room conversation first.
                 */
                val conversation = memoryManager.createConversation()

                if (modelLoaded) {

                    engineMutex.withLock {

                        /*
                         * Clear the native llama.cpp conversation
                         * while keeping the model loaded.
                         */
                        engine.resetConversation()

                        /*
                         * Select the new conversation.
                         */
                        currentConversationId = conversation.id

                        /*
                         * Build a fresh context.
                         */
                        rebuildConversationContext()
                    }

                } else {

                    currentConversationId = conversation.id
                }

                withMain {
                    callback.onSuccess(conversation)
                }

            } catch (e: Exception) {

                withMain {
                    callback.onError(e)
                }
            }
        }
    }

    // ========================================================================
    // SELECT EXISTING CONVERSATION
    // ========================================================================

    fun selectConversation(
        conversationId: Long, callback: ConversationCallback
    ) {

        scope.launch {

            try {

                generationStopped = true

                engine.stopGeneration()

                /*
                 * Make sure the conversation actually exists.
                 */
                val conversation = memoryManager.getConversation(
                    conversationId
                ) ?: throw IllegalArgumentException(
                    "Conversation not found: $conversationId"
                )

                if (modelLoaded) {

                    engineMutex.withLock {

                        /*
                         * IMPORTANT:
                         *
                         * Remove the previous conversation from
                         * the native llama.cpp context.
                         */
                        engine.resetConversation()

                        /*
                         * Change the active Room conversation.
                         */
                        currentConversationId = conversation.id

                        /*
                         * Rebuild native context using ONLY
                         * this conversation's data.
                         */
                        rebuildConversationContext()
                    }

                } else {

                    currentConversationId = conversation.id
                }

                withMain {
                    callback.onSuccess(conversation)
                }

            } catch (e: Exception) {

                withMain {
                    callback.onError(e)
                }
            }
        }
    }

    // ========================================================================
    // DELETE CONVERSATION
    // ========================================================================

    fun deleteConversation(
        conversationId: Long, callback: ConversationCallback
    ) {

        scope.launch {

            try {

                generationStopped = true

                engine.stopGeneration()

                memoryManager.deleteConversation(
                    conversationId
                )

                /*
                 * If the deleted conversation was active,
                 * choose/create another conversation.
                 */
                if (currentConversationId == conversationId) {

                    val newConversation = memoryManager.getOrCreateCurrentConversation()

                    if (modelLoaded) {

                        engineMutex.withLock {

                            engine.resetConversation()

                            currentConversationId = newConversation.id

                            rebuildConversationContext()
                        }

                    } else {

                        currentConversationId = newConversation.id
                    }

                    withMain {
                        callback.onSuccess(newConversation)
                    }

                } else {

                    withMain {
                        callback.onSuccess(
                            memoryManager.getConversation(
                                currentConversationId
                            ) ?: ConversationEntity(
                                id = currentConversationId,
                                title = "New Chat",
                                createdAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }

            } catch (e: Exception) {

                withMain {
                    callback.onError(e)
                }
            }
        }
    }

    // ========================================================================
    // CURRENT CONVERSATION
    // ========================================================================

    fun initializeConversation(
        callback: ConversationCallback
    ) {

        scope.launch {

            try {

                /*
                 * If a conversation has already been selected,
                 * don't overwrite it.
                 */
                val conversation =
                    if (currentConversationId != -1L) {

                        memoryManager.getConversation(
                            currentConversationId
                        ) ?: memoryManager.getOrCreateCurrentConversation()

                    } else {

                        memoryManager.getOrCreateCurrentConversation()
                    }

                currentConversationId = conversation.id

                withMain {
                    callback.onSuccess(conversation)
                }

            } catch (e: Exception) {

                withMain {
                    callback.onError(e)
                }
            }
        }
    }

    fun getCurrentConversationId(): Long {

        return currentConversationId
    }

    fun getCurrentConversation(
        callback: (ConversationEntity?) -> Unit
    ) {

        scope.launch {

            try {

                val conversation = memoryManager.getConversation(
                    currentConversationId
                )

                withMain {
                    callback(conversation)
                }

            } catch (_: Exception) {

                withMain {
                    callback(null)
                }
            }
        }
    }

    // ========================================================================
    // ALL CONVERSATIONS
    // ========================================================================

    /*
     * Name matches MainActivity.
     */
    fun getConversations(
        callback: ConversationsCallback
    ) {

        scope.launch {

            try {

                val conversations = memoryManager.getAllConversations()

                withMain {
                    callback.onSuccess(conversations)
                }

            } catch (e: Exception) {

                withMain {
                    callback.onError(e)
                }
            }
        }
    }

    /*
     * Keep this alias available as well.
     */
    fun getAllConversations(
        callback: ConversationsCallback
    ) {

        getConversations(callback)
    }

    // ========================================================================
    // GET CONVERSATION MESSAGES
    // ========================================================================

    /*
     * Name matches MainActivity.
     */
    fun getConversationMessages(
        conversationId: Long, callback: MessagesCallback
    ) {

        scope.launch {

            try {

                val messages = memoryManager.getAllMessages(
                    conversationId
                )

                withMain {
                    callback.onSuccess(messages)
                }

            } catch (e: Exception) {

                withMain {
                    callback.onError(e)
                }
            }
        }
    }

    /*
     * Keep this alias available too.
     */
    fun getMessages(
        conversationId: Long, callback: MessagesCallback
    ) {

        getConversationMessages(
            conversationId, callback
        )
    }

    // ========================================================================
    // REBUILD CONTEXT
    // ========================================================================

    private suspend fun rebuildConversationContext() {

        if (currentConversationId == -1L) {
            return
        }

        /*
         * ContextBuilder combines:
         *
         * 1. Global persistent memories
         * 2. Current conversation summary
         * 3. Recent messages from CURRENT conversation
         */
        val prompt = contextBuilder.buildContext(
            conversationId = currentConversationId,

            currentMessage = "",

            recentMessageLimit = RECENT_MESSAGE_COUNT
        )

        /*
         * A new conversation may have:
         *
         * - no memories
         * - no summary
         * - no messages
         *
         * In that case ContextBuilder can return an empty
         * prompt. llama.cpp requires a non-empty system prompt.
         */
        val finalPrompt = if (prompt.isBlank()) {

            """
            You are a helpful offline AI assistant.
            Answer the user's questions clearly and accurately.
            Be concise unless the user asks for more detail.
            Do not invent facts.
            """.trimIndent()

        } else {

            prompt
        }

        /*
         * Send the final system prompt to the LLM.
         */
        engine.setSystemPrompt(finalPrompt)
    }

    // ========================================================================
    // SUMMARY
    // ========================================================================

    private fun updateSummaryIfNecessary(
        conversationId: Long
    ) {

        summaryJob?.cancel()

        summaryJob = scope.launch {

            try {

                val count = memoryManager.getMessageCount(
                    conversationId
                )

                if (count < SUMMARY_TRIGGER) {
                    return@launch
                }

                generateConversationSummary(
                    conversationId
                )

            } catch (_: Exception) {/*
                     * Summary failure must never
                     * break normal chat.
                     */
            }
        }
    }

    private suspend fun generateConversationSummary(
        conversationId: Long
    ) {

        /*
         * Do not summarize a conversation that is no longer
         * the active conversation.
         */
        if (conversationId != currentConversationId) {
            return
        }

        val messages = memoryManager.getRecentMessages(
            conversationId, SUMMARY_MESSAGE_COUNT
        )

        if (messages.isEmpty()) {
            return
        }

        val promptBuilder = StringBuilder()

        promptBuilder.append(
            """
            Summarize the following conversation.

            Keep only important facts, decisions,
            user preferences, goals, and useful context.

            Do not add information that was not present.

            Conversation:

            """.trimIndent()
        )

        for (message in messages) {

            promptBuilder.append("\n")
            promptBuilder.append(message.role)
            promptBuilder.append(": ")
            promptBuilder.append(message.content)
        }

        val summaryBuilder = StringBuilder()

        /*
         * Summary inference uses the same native engine.
         *
         * Therefore we temporarily create a clean native
         * conversation, generate the summary, and then
         * RESTORE the actual active conversation.
         */
        engineMutex.withLock {

            if (conversationId != currentConversationId) {
                return@withLock
            }

            /*
             * Clear current chat context.
             */
            engine.resetConversation()

            /*
             * Give the summary generator a minimal
             * system prompt.
             */
            engine.setSystemPrompt(
                "You are a conversation summarizer. " + "Return only a concise factual summary."
            )

            engine.sendMessage(
                promptBuilder.toString(), predictLength = SUMMARY_MAX_TOKENS
            ).collect { token ->

                summaryBuilder.append(token)
            }

            val summary = summaryBuilder.toString().trim()

            if (summary.isNotEmpty()) {

                memoryManager.saveConversationSummary(
                    conversationId = conversationId, summary = summary
                )
            }

            /*
             * VERY IMPORTANT:
             *
             * Summary inference changed the native
             * conversation state.
             *
             * Restore the actual chat context before
             * releasing the mutex.
             */
            if (conversationId == currentConversationId) {

                engine.resetConversation()

                rebuildConversationContext()
            }
        }
    }

    // ========================================================================
    // CLEANUP
    // ========================================================================

    fun cleanUp() {

        generationStopped = true

        engine.stopGeneration()

        scope.launch {

            engineMutex.withLock {

                engine.unload()

                modelLoaded = false
            }
        }
    }

    // ========================================================================
    // DESTROY
    // ========================================================================

    fun destroy() {

        generationStopped = true

        engine.stopGeneration()

        summaryJob?.cancel()

        scope.launch {

            engineMutex.withLock {

                engine.destroy()

                modelLoaded = false
            }

            scope.coroutineContext.cancel()
        }
    }

    // ========================================================================
    // MAIN THREAD CALLBACK HELPER
    // ========================================================================

    private suspend fun withMain(
        block: suspend () -> Unit
    ) {
        kotlinx.coroutines.withContext(Dispatchers.Main) {
            block()
        }
    }
}
