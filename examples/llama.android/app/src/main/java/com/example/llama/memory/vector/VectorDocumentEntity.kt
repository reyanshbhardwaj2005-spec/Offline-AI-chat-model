package com.example.llama.memory.vector

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "vector_documents",
    indices = [
        Index(value = ["sourceType", "sourceId"], unique = true),
        Index(value = ["conversationId"])
    ]
)
data class VectorDocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceType: String,
    val sourceId: Long,
    val conversationId: Long? = null,
    val content: String,
    val vector: ByteArray,
    val dimension: Int,
    val model: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
