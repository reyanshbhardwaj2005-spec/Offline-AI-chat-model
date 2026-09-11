package com.example.llama.memory

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query

@Dao
interface MessageDao {

    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    suspend fun getAllMessages(): List<MessageEntity>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int): List<MessageEntity>

    @Query("DELETE FROM messages")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun getMessageCount(): Int

    @Query("""
        SELECT * FROM messages
        ORDER BY timestamp ASC
        LIMIT :limit
    """)
    suspend fun getOldMessages(limit: Int): List<MessageEntity>

    @Query("""
        DELETE FROM messages
        WHERE id IN (
            SELECT id
            FROM messages
            ORDER BY timestamp ASC
            LIMIT :limit
        )
    """)
    suspend fun deleteOldMessages(limit: Int)
}
