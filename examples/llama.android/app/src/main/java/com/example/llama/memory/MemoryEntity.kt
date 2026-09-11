package com.example.llama.memory

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "memories")
data class MemoryEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val key: String,

    val value: String,

    val importance: Int = 1,

    val createdAt: Long = System.currentTimeMillis(),

    val updatedAt: Long = System.currentTimeMillis()
)
