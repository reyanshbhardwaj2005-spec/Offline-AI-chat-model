package com.example.llama.memory

data class Memory(
    val id: Long,
    val key: String,
    val value: String,
    val importance: Int,
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        fun fromEntity(entity: MemoryEntity): Memory = Memory(
            id = entity.id,
            key = entity.key,
            value = entity.value,
            importance = entity.importance,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
}
