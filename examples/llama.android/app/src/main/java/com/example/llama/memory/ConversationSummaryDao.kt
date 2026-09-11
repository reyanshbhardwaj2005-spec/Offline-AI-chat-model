package com.example.llama.memory

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query

@Dao
interface ConversationSummaryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(summary: ConversationSummaryEntity)

    @Query("SELECT * FROM conversation_summary WHERE id = 1 LIMIT 1")
    suspend fun getSummary(): ConversationSummaryEntity?

    @Query("DELETE FROM conversation_summary")
    suspend fun deleteSummary()
}
