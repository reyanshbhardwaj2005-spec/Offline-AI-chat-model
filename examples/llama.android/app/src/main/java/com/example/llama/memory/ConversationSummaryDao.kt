package com.example.llama.memory

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query

@Dao
interface ConversationSummaryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(summary: ConversationSummaryEntity)

    @Query("""
        SELECT * FROM conversation_summary
        WHERE conversationId = :conversationId
        LIMIT 1
    """)
    suspend fun getSummary(conversationId: Long): ConversationSummaryEntity?

    @Query("""
        DELETE FROM conversation_summary
        WHERE conversationId = :conversationId
    """)
    suspend fun deleteSummary(conversationId: Long)

    @Query("DELETE FROM conversation_summary")
    suspend fun deleteAll()
}
