package com.example.llama.memory

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver

class MemoryManager(context: Context) {

    private val database = Room.databaseBuilder<AppDatabase>(
        context.applicationContext, "ai_memory.db"
    ).setDriver(AndroidSQLiteDriver()).addMigrations(AppDatabase.MIGRATION_2_3).build()

    private val memoryDao = database.memoryDao()
    private val conversationDao = database.conversationDao()
    private val messageDao = database.messageDao()
    private val conversationSummaryDao = database.conversationSummaryDao()

    // =============================================================
    // CONVERSATIONS
    // =============================================================

    suspend fun createConversation(
        title: String = "New Chat"
    ): ConversationEntity {

        val now = System.currentTimeMillis()

        val id = conversationDao.insert(
            ConversationEntity(
                title = title, createdAt = now, updatedAt = now
            )
        )

        return ConversationEntity(
            id = id, title = title, createdAt = now, updatedAt = now
        )
    }

    suspend fun getOrCreateCurrentConversation(): ConversationEntity {
        return conversationDao.getMostRecent() ?: createConversation()
    }

    suspend fun getConversation(
        conversationId: Long
    ): ConversationEntity? {
        return conversationDao.getById(conversationId)
    }

    suspend fun getAllConversations(): List<ConversationEntity> {
        return conversationDao.getAll()
    }

    suspend fun deleteConversation(
        conversationId: Long
    ) {
        messageDao.deleteForConversation(conversationId)
        conversationSummaryDao.deleteSummary(conversationId)
        conversationDao.deleteById(conversationId)
    }

    // =============================================================
    // CONVERSATION SUMMARY
    // =============================================================

    suspend fun saveConversationSummary(
        conversationId: Long, summary: String
    ) {
        conversationSummaryDao.save(
            ConversationSummaryEntity(
                conversationId = conversationId,
                summary = summary,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getConversationSummary(
        conversationId: Long
    ): String? {
        return conversationSummaryDao.getSummary(conversationId)?.summary
    }

    suspend fun clearConversationSummary(
        conversationId: Long
    ) {
        conversationSummaryDao.deleteSummary(conversationId)
    }

    // =============================================================
    // PERSISTENT MEMORY
    // =============================================================

    suspend fun saveMemory(
        key: String, value: String, importance: Int = 1
    ) {

        val existing = memoryDao.getMemory(key)

        if (existing != null) {

            memoryDao.update(
                existing.copy(
                    value = value, importance = importance, updatedAt = System.currentTimeMillis()
                )
            )

        } else {

            memoryDao.insert(
                MemoryEntity(
                    key = key, value = value, importance = importance
                )
            )
        }
    }

    suspend fun getMemory(
        key: String
    ): MemoryEntity? {
        return memoryDao.getMemory(key)
    }

    suspend fun getAllMemories(): List<MemoryEntity> {
        return memoryDao.getAllMemories()
    }

    suspend fun searchMemories(
        query: String
    ): List<MemoryEntity> {
        return memoryDao.searchMemories(query)
    }

    // =============================================================
    // CONVERSATION MESSAGES
    // =============================================================

    suspend fun saveMessage(
        conversationId: Long, role: String, content: String
    ) {

        messageDao.insert(
            MessageEntity(
                conversationId = conversationId, role = role, content = content
            )
        )

        val now = System.currentTimeMillis()

        if (role == "user") {

            val conversation = conversationDao.getById(conversationId)

            if (conversation != null && conversation.title == "New Chat") {

                conversationDao.updateTitle(
                    conversationId, createConversationTitle(content), now
                )

                return
            }
        }

        conversationDao.touch(
            conversationId, now
        )
    }

    suspend fun getAllMessages(
        conversationId: Long
    ): List<MessageEntity> {
        return messageDao.getAllMessages(conversationId)
    }

    suspend fun getRecentMessages(
        conversationId: Long, limit: Int
    ): List<MessageEntity> {
        return messageDao.getRecentMessages(conversationId, limit).reversed()
    }

    suspend fun getMessageCount(
        conversationId: Long
    ): Int {
        return messageDao.getMessageCount(conversationId)
    }

    suspend fun getOldMessages(
        conversationId: Long, limit: Int
    ): List<MessageEntity> {
        return messageDao.getOldMessages(
            conversationId, limit
        )
    }

    suspend fun deleteOldMessages(
        conversationId: Long, limit: Int
    ) {
        messageDao.deleteOldMessages(
            conversationId, limit
        )
    }

    suspend fun clearMessages(
        conversationId: Long
    ) {
        messageDao.deleteForConversation(conversationId)
        conversationDao.touch(
            conversationId, System.currentTimeMillis()
        )
    }

    // =============================================================
    // TITLE
    // =============================================================

    private fun createConversationTitle(
        message: String
    ): String {

        val cleaned = message.replace(Regex("""\s+"""), " ").trim()

        if (cleaned.isEmpty()) {
            return "New Chat"
        }

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
    }

    fun close() {
        database.close()
    }

    suspend fun printAllMemories() {
        val memories = memoryDao.getAllMemories()

        memories.forEach {
            println(
                "MEMORY DEBUG -> id=${it.id}, " + "key=${it.key}, " + "value=${it.value}"
            )
        }
    }
}
