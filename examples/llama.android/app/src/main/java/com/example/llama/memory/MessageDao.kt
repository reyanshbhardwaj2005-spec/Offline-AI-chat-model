package com.example.llama.memory

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query

@Dao
interface MessageDao {

    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Query("""
        SELECT * FROM messages
        WHERE conversationId = :conversationId
        ORDER BY timestamp ASC, id ASC
    """)
    suspend fun getAllMessages(conversationId: Long): List<MessageEntity>

    @Query("""
        SELECT * FROM messages
        WHERE conversationId = :conversationId
        ORDER BY timestamp DESC, id DESC
        LIMIT :limit
    """)
    suspend fun getRecentMessages(
        conversationId: Long,
        limit: Int
    ): List<MessageEntity>

    @Query("""
        DELETE FROM messages
        WHERE conversationId = :conversationId
    """)
    suspend fun deleteForConversation(conversationId: Long)

    @Query("""
        SELECT COUNT(*) FROM messages
        WHERE conversationId = :conversationId
    """)
    suspend fun getMessageCount(conversationId: Long): Int

    @Query("""
        SELECT * FROM messages
        WHERE conversationId = :conversationId
        ORDER BY timestamp ASC, id ASC
        LIMIT :limit
    """)
    suspend fun getOldMessages(
        conversationId: Long,
        limit: Int
    ): List<MessageEntity>

    @Query("""
        DELETE FROM messages
        WHERE conversationId = :conversationId
        AND id IN (
            SELECT id
            FROM messages
            WHERE conversationId = :conversationId
            ORDER BY timestamp ASC, id ASC
            LIMIT :limit
        )
    """)
    suspend fun deleteOldMessages(
        conversationId: Long,
        limit: Int
    )
}
