package com.example.llama.memory

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver

class MemoryManager(context: Context) {

    private val database = Room.databaseBuilder<AppDatabase>(context.applicationContext, "ai_memory.db")
        .setDriver(AndroidSQLiteDriver())
        .build()
    private val memoryDao = database.memoryDao()
    private val messageDao = database.messageDao()
    private val conversationSummaryDao = database.conversationSummaryDao()

    suspend fun getMessageCount(): Int {
        return messageDao.getMessageCount()
    }

    suspend fun getOldMessages(limit: Int): List<MessageEntity> {
        return messageDao.getOldMessages(limit)
    }

    suspend fun deleteOldMessages(limit: Int) {
        messageDao.deleteOldMessages(limit)
    }

    // -------------------------
    // Conversation Summary
    // -------------------------

    suspend fun saveConversationSummary(summary: String) {
        conversationSummaryDao.save(ConversationSummaryEntity(id = 1, summary = summary, updatedAt = System.currentTimeMillis()))
    }

    suspend fun getConversationSummary(): String? {
        return conversationSummaryDao.getSummary()?.summary
    }

    suspend fun clearConversationSummary() {
        conversationSummaryDao.deleteSummary()
    }

    // -------------------------
    // Persistent Memory
    // -------------------------

    suspend fun saveMemory(key: String, value: String, importance: Int = 1) {

        val existing = memoryDao.getMemory(key)

        if (existing != null) {
            memoryDao.update(existing.copy(value = value, importance = importance, updatedAt = System.currentTimeMillis()))
        } else {
            memoryDao.insert(MemoryEntity(key = key, value = value, importance = importance))
        }
    }


    suspend fun getMemory(key: String): MemoryEntity? {
        return memoryDao.getMemory(key)
    }

    suspend fun getAllMemories(): List<MemoryEntity> {
        return memoryDao.getAllMemories()
    }

    suspend fun searchMemories(query: String): List<MemoryEntity> {
        return memoryDao.searchMemories(query)
    }

    // -------------------------
    // Conversation Messages
    // -------------------------

    suspend fun saveMessage(role: String, content: String) {
        messageDao.insert(MessageEntity(role = role, content = content))
    }
    suspend fun getAllMessages(): List<MessageEntity> {
        return messageDao.getAllMessages()
    }

    suspend fun getRecentMessages(limit: Int): List<MessageEntity> {
        return messageDao.getRecentMessages(limit).reversed()
    }

    suspend fun clearMessages() {
        messageDao.deleteAll()
    }

    suspend fun clearMemory() {
        memoryDao.deleteAll()
    }

    fun close() {
        database.close()
    }

    suspend fun printAllMemories() {
        val memories = memoryDao.getAllMemories()
        memories.forEach { println("MEMORY DEBUG -> id=${it.id}, key=${it.key}, value=${it.value}")
        }
    }


}
