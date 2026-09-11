package com.example.llama.memory

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update

@Dao
interface MemoryDao {

    @Insert
    suspend fun insert(memory: MemoryEntity): Long

    @Update
    suspend fun update(memory: MemoryEntity)

    @Delete
    suspend fun delete(memory: MemoryEntity)

    @Query("""
        SELECT * FROM memories
        ORDER BY importance DESC, updatedAt DESC
    """)
    suspend fun getAllMemories(): List<MemoryEntity>

    @Query("""
        SELECT * FROM memories
        WHERE `key` = :key
        LIMIT 1
    """)
    suspend fun getMemory(key: String): MemoryEntity?

    @Query("""
        SELECT * FROM memories
        WHERE `key` LIKE '%' || :query || '%'
        OR `value` LIKE '%' || :query || '%'
        ORDER BY importance DESC, updatedAt DESC
    """)
    suspend fun searchMemories(query: String): List<MemoryEntity>

    @Query("DELETE FROM memories")
    suspend fun deleteAll()
}
