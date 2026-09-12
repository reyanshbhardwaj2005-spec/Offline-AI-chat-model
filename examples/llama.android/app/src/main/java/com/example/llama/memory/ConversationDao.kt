package com.example.llama.memory

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update

@Dao
interface ConversationDao {

    @Insert
    suspend fun insert(conversation: ConversationEntity): Long

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Delete
    suspend fun delete(conversation: ConversationEntity)

    @Query("""
        SELECT * FROM conversations
        ORDER BY updatedAt DESC
    """)
    suspend fun getAll(): List<ConversationEntity>

    @Query("""
        SELECT * FROM conversations
        WHERE id = :id
        LIMIT 1
    """)
    suspend fun getById(id: Long): ConversationEntity?

    @Query("""
        SELECT * FROM conversations
        ORDER BY updatedAt DESC
        LIMIT 1
    """)
    suspend fun getMostRecent(): ConversationEntity?

    @Query("""
        UPDATE conversations
        SET updatedAt = :updatedAt
        WHERE id = :id
    """)
    suspend fun touch(id: Long, updatedAt: Long)

    @Query("""
        UPDATE conversations
        SET title = :title, updatedAt = :updatedAt
        WHERE id = :id
    """)
    suspend fun updateTitle(id: Long, title: String, updatedAt: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM conversations")
    suspend fun count(): Int
}
