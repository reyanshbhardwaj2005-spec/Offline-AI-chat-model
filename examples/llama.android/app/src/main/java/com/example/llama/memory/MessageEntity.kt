package com.example.llama.memory

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [Index(value = ["conversationId", "timestamp"])]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val conversationId: Long,

    val role: String,

    val content: String,

    val timestamp: Long = System.currentTimeMillis()
)
