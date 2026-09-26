package com.example.llama.memory

data class RagContext(
    val memories: List<MemoryEntity>,
    val messages: List<MessageEntity>
)
