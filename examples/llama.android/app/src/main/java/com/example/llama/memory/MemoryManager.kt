package com.example.llama.memory

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.example.llama.embedding.EmbeddingEngine
import com.example.llama.memory.vector.LocalVectorStore
import com.example.llama.memory.vector.VectorStore

class MemoryManager(
    context: Context,
    private val embeddingEngine: EmbeddingEngine
) {

    private val database = Room.databaseBuilder<AppDatabase>(
        context.applicationContext, "ai_memory.db"
    )
        .setDriver(AndroidSQLiteDriver())
        .addMigrations(
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4
        )
        .build()

    private val memoryDao = database.memoryDao()
    private val conversationDao = database.conversationDao()
    private val messageDao = database.messageDao()
    private val conversationSummaryDao = database.conversationSummaryDao()
    private val vectorStore: VectorStore =
        LocalVectorStore(database.vectorDocumentDao())

    private val contextRetriever = ContextRetriever(
        memoryManager = this,
        embeddingEngine = embeddingEngine,
        vectorStore = vectorStore
    )

    val ragEngine: RagEngine = RagEngineImpl(
        memoryManager = this,
        contextRetriever = contextRetriever
    )

    @Volatile
    private var vectorIndexInitialized = false

    // =============================================================
    // CONVERSATIONS
    // =============================================================

    suspend fun createConversation(title: String = "New Chat"): ConversationEntity {
        val now = System.currentTimeMillis()
        val id = conversationDao.insert(
            ConversationEntity(
                title = title,
                createdAt = now,
                updatedAt = now
            )
        )

        return ConversationEntity(
            id = id,
            title = title,
            createdAt = now,
            updatedAt = now
        )
    }

    suspend fun getOrCreateCurrentConversation(): ConversationEntity {
        return conversationDao.getMostRecent() ?: createConversation()
    }

    suspend fun getConversation(conversationId: Long): ConversationEntity? =
        conversationDao.getById(conversationId)

    suspend fun getAllConversations(): List<ConversationEntity> =
        conversationDao.getAll()

    suspend fun deleteConversation(conversationId: Long) {
        messageDao.deleteForConversation(conversationId)
        conversationSummaryDao.deleteSummary(conversationId)
        vectorStore.deleteForConversation(conversationId)
        conversationDao.deleteById(conversationId)
    }

    // =============================================================
    // CONVERSATION SUMMARY
    // =============================================================

    suspend fun saveConversationSummary(
        conversationId: Long,
        summary: String
    ) {
        conversationSummaryDao.save(
            ConversationSummaryEntity(
                conversationId = conversationId,
                summary = summary,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getConversationSummary(conversationId: Long): String? =
        conversationSummaryDao.getSummary(conversationId)?.summary

    suspend fun clearConversationSummary(conversationId: Long) {
        conversationSummaryDao.deleteSummary(conversationId)
    }

    // =============================================================
    // PERSISTENT MEMORY
    // =============================================================

    suspend fun saveMemory(
        key: String,
        value: String,
        importance: Int = 1
    ) {
        val existing = memoryDao.getMemory(key)

        val memory = if (existing != null) {
            val updated = existing.copy(
                value = value,
                importance = importance,
                updatedAt = System.currentTimeMillis()
            )
            memoryDao.update(updated)
            updated
        } else {
            val id = memoryDao.insert(
                MemoryEntity(
                    key = key,
                    value = value,
                    importance = importance
                )
            )
            memoryDao.getMemoryById(id)
        }

        memory?.let {
            indexMemory(it)
        }
    }

    suspend fun getMemory(key: String): MemoryEntity? =
        memoryDao.getMemory(key)

    suspend fun getMemoryById(id: Long): MemoryEntity? =
        memoryDao.getMemoryById(id)

    suspend fun getAllMemories(): List<MemoryEntity> =
        memoryDao.getAllMemories()

    suspend fun searchMemories(query: String): List<MemoryEntity> =
        memoryDao.searchMemories(query)

    private suspend fun indexMemory(memory: MemoryEntity) {
        val text = "${memory.key}: ${memory.value}"
        val vector = if (embeddingEngine is com.example.llama.embedding.LocalEmbeddingEngine) {
            embeddingEngine.embedPassage(text)
        } else {
            embeddingEngine.embed("passage: $text")
        }

        vectorStore.upsert(
            sourceType = RagSource.MEMORY,
            sourceId = memory.id,
            conversationId = null,
            content = text,
            vector = vector,
            model = embeddingEngine.modelName
        )
    }

    // =============================================================
    // CONVERSATION MESSAGES
    // =============================================================

    suspend fun saveMessage(
        conversationId: Long,
        role: String,
        content: String
    ) {
        val messageId = messageDao.insert(
            MessageEntity(
                conversationId = conversationId,
                role = role,
                content = content
            )
        )

        val savedMessage = messageDao.getMessageById(messageId)
        if (savedMessage != null && content.isNotBlank()) {
            indexMessage(savedMessage)
        }

        val now = System.currentTimeMillis()

        if (role == "user") {
            val conversation = conversationDao.getById(conversationId)

            if (conversation != null && conversation.title == "New Chat") {
                conversationDao.updateTitle(
                    conversationId,
                    createConversationTitle(content),
                    now
                )
                return
            }
        }

        conversationDao.touch(conversationId, now)
    }

    private suspend fun indexMessage(message: MessageEntity) {
        val text = "${message.role}: ${message.content}"
        val vector = if (embeddingEngine is com.example.llama.embedding.LocalEmbeddingEngine) {
            embeddingEngine.embedPassage(text)
        } else {
            embeddingEngine.embed("passage: $text")
        }

        vectorStore.upsert(
            sourceType = RagSource.MESSAGE,
            sourceId = message.id,
            conversationId = message.conversationId,
            content = text,
            vector = vector,
            model = embeddingEngine.modelName
        )
    }

    suspend fun getAllMessages(conversationId: Long): List<MessageEntity> =
        messageDao.getAllMessages(conversationId)

    suspend fun getMessageById(id: Long): MessageEntity? =
        messageDao.getMessageById(id)

    suspend fun getRecentMessages(
        conversationId: Long,
        limit: Int
    ): List<MessageEntity> =
        messageDao.getRecentMessages(conversationId, limit).reversed()

    suspend fun getMessageCount(conversationId: Long): Int =
        messageDao.getMessageCount(conversationId)

    suspend fun getOldMessages(
        conversationId: Long,
        limit: Int
    ): List<MessageEntity> =
        messageDao.getOldMessages(conversationId, limit)

    suspend fun deleteOldMessages(
        conversationId: Long,
        limit: Int
    ) {
        val oldMessages = messageDao.getOldMessages(conversationId, limit)
        messageDao.deleteOldMessages(conversationId, limit)
        vectorStore.deleteMany(
            RagSource.MESSAGE,
            oldMessages.map { it.id }
        )
    }

    suspend fun clearMessages(conversationId: Long) {
        messageDao.deleteForConversation(conversationId)
        vectorStore.deleteForConversation(conversationId)
        conversationDao.touch(
            conversationId,
            System.currentTimeMillis()
        )
    }

    // =============================================================
    // VECTOR INDEX
    // =============================================================

    /**
     * Rebuilds the local vector index from the authoritative Room data.
     * This is useful after upgrading from the pre-RAG database or changing
     * the embedding model.
     */
    suspend fun rebuildVectorIndex() {
        vectorStore.clear()

        memoryDao.getAllMemories().forEach { indexMemory(it) }

        conversationDao.getAll().forEach { conversation ->
            messageDao.getAllMessages(conversation.id).forEach { message ->
                if (message.content.isNotBlank()) {
                    indexMessage(message)
                }
            }
        }

        vectorIndexInitialized = true
    }

    suspend fun ensureVectorIndex() {
        if (vectorIndexInitialized) return

        val expected =
            memoryDao.getAllMemories().size +
            conversationDao.getAll().sumOf { conversation ->
                messageDao.getMessageCount(conversation.id)
            }

        val vectorDao = database.vectorDocumentDao()
        val actualTotal = vectorDao.count()
        val actualForCurrentModel = vectorDao.countByModel(embeddingEngine.modelName)

        // A previous development embedding model may have produced vectors with
        // the same dimension. Count-only validation is therefore not enough.
        if (expected != actualTotal || expected != actualForCurrentModel) {
            rebuildVectorIndex()
        } else {
            vectorIndexInitialized = true
        }
    }

    // =============================================================
    // TITLE
    // =============================================================

    private fun createConversationTitle(message: String): String {
        val cleaned = message
            .replace(Regex("""\s+"""), " ")
            .trim()

        if (cleaned.isEmpty()) return "New Chat"

        return if (cleaned.length <= 42) {
            cleaned
        } else {
            cleaned.take(42).trimEnd() + "..."
        }
    }

    // =============================================================
    // GLOBAL MEMORY
    // =============================================================

    suspend fun clearMemory() {
        memoryDao.deleteAll()
        vectorStore.clear()
    }

    fun close() {
        embeddingEngine.close()
        database.close()
    }

    suspend fun printAllMemories() {
        memoryDao.getAllMemories().forEach {
            println(
                "MEMORY DEBUG -> id=${it.id}, " +
                    "key=${it.key}, " +
                    "value=${it.value}"
            )
        }
    }
}
