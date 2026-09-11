package com.example.llama.memory

import androidx.room3.Database
import androidx.room3.RoomDatabase

@Database(
    entities = [
        MemoryEntity::class,
        MessageEntity::class,
        ConversationSummaryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun memoryDao(): MemoryDao

    abstract fun messageDao(): MessageDao

    abstract fun conversationSummaryDao(): ConversationSummaryDao
}
