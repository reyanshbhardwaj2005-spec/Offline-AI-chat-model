package com.example.llama.memory

class MemoryExtractor {

    data class ExtractedMemory(
        val key: String,
        val value: String,
        val importance: Int
    )

    fun extract(message: String): List<ExtractedMemory> {

        val text = message.trim()
        val memories = mutableListOf<ExtractedMemory>()

        // Name
        Regex(
            """(?i)\bmy name is\s+([A-Za-z][A-Za-z ]{0,40})"""
        ).find(text)?.let {

            val name = it.groupValues[1]
                .trim()
                .removeSuffix(".")

            memories.add(
                ExtractedMemory(
                    key = "name",
                    value = name,
                    importance = 10
                )
            )
        }

        // Programming language
        Regex(
            """(?i)\bi(?:'m| am)\s+(?:learning|studying)\s+([A-Za-z0-9+#. -]{2,30})"""
        ).find(text)?.let {

            val subject = it.groupValues[1]
                .trim()
                .removeSuffix(".")

            memories.add(
                ExtractedMemory(
                    key = "learning",
                    value = subject,
                    importance = 8
                )
            )
        }

        // "I use X"
        Regex(
            """(?i)\bi use\s+([A-Za-z0-9+#. -]{2,30})"""
        ).find(text)?.let {

            val technology = it.groupValues[1]
                .trim()
                .removeSuffix(".")

            memories.add(
                ExtractedMemory(
                    key = "uses",
                    value = technology,
                    importance = 5
                )
            )
        }

        // "I like X"
        Regex(
            """(?i)\bi like\s+(.{2,50})"""
        ).find(text)?.let {

            val preference = it.groupValues[1]
                .trim()
                .removeSuffix(".")

            memories.add(
                ExtractedMemory(
                    key = "likes",
                    value = preference,
                    importance = 5
                )
            )
        }

        return memories
    }
}
