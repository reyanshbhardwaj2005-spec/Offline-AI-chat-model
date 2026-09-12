package com.example.llama.memory

import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(
    entities = [MemoryEntity::class, ConversationEntity::class, MessageEntity::class, ConversationSummaryEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun memoryDao(): MemoryDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun conversationSummaryDao(): ConversationSummaryDao
    companion object {

        /*
         * Version 2 -> 3
         *
         * Existing messages are assigned to one migrated
         * conversation (id = 1), preserving the old data.
         *
         * Existing global summary is also moved to that
         * conversation.
         */

        val MIGRATION_2_3 = object : Migration(2, 3) {

            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS conversations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                connection.execSQL(
                    """
                    INSERT OR IGNORE INTO conversations
                    (id, title, createdAt, updatedAt)
                    VALUES
                    (1, 'New Chat', strftime('%s','now') * 1000,
                     strftime('%s','now') * 1000)
                    """.trimIndent()
                )

                connection.execSQL(
                    """
                    ALTER TABLE messages
                    ADD COLUMN conversationId INTEGER NOT NULL DEFAULT 1
                    """.trimIndent()
                )

                connection.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    index_messages_conversationId_timestamp
                    ON messages(conversationId, timestamp)
                    """.trimIndent()
                )

                connection.execSQL(
                    """
                    UPDATE conversations
                    SET
                        title = 'Previous Chat',
                        updatedAt = (
                            SELECT MAX(timestamp)
                            FROM messages
                            WHERE conversationId = 1
                        )
                    WHERE id = 1
                    AND EXISTS (
                        SELECT 1
                        FROM messages
                        WHERE conversationId = 1
                    )
                    """.trimIndent()
                )

                connection.execSQL(
                    """
                    CREATE TABLE conversation_summary_new (
                        conversationId INTEGER NOT NULL,
                        summary TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(conversationId)
                    )
                    """.trimIndent()
                )

                connection.execSQL(
                    """
                    INSERT INTO conversation_summary_new
                    (conversationId, summary, updatedAt)
                    SELECT id, summary, updatedAt
                    FROM conversation_summary
                    """.trimIndent()
                )

                connection.execSQL(
                    "DROP TABLE conversation_summary"
                )

                connection.execSQL(
                    """
                    ALTER TABLE conversation_summary_new
                    RENAME TO conversation_summary
                    """.trimIndent()
                )
            }
        }
    }
}
