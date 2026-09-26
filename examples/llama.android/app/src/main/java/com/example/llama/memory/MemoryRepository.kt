package com.example.llama.memory

/**
 * Thin repository facade for memory operations.
 * Keep MemoryManager as the database owner for now; this class gives the
 * future agent/RAG layers a stable dependency boundary.
 */
class MemoryRepository(
    private val memoryManager: MemoryManager
) {
    suspend fun getAll(): List<MemoryEntity> = memoryManager.getAllMemories()
    suspend fun getById(id: Long): MemoryEntity? = memoryManager.getMemoryById(id)
    suspend fun getByKey(key: String): MemoryEntity? = memoryManager.getMemory(key)
    suspend fun save(key: String, value: String, importance: Int = 1) =
        memoryManager.saveMemory(key, value, importance)
}
