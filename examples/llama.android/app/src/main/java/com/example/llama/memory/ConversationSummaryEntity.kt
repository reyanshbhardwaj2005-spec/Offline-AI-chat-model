package com.example.llama.memory

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "conversation_summary")
data class ConversationSummaryEntity(
    @PrimaryKey
    val id: Int = 1,
    val summary: String,
    val updatedAt: Long = System.currentTimeMillis()
)
