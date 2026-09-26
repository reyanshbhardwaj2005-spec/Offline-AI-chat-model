package com.example.llama.memory

class MemoryUpdater(
    private val memoryManager: MemoryManager
) {
    suspend fun update(
        key: String,
        value: String,
        importance: Int = 1
    ) {
        memoryManager.saveMemory(key, value, importance)
    }
}
