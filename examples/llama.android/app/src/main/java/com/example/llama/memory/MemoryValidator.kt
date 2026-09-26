package com.example.llama.memory

class MemoryValidator {
    fun isValid(key: String, value: String): Boolean {
        return key.isNotBlank() && value.isNotBlank()
    }
}
