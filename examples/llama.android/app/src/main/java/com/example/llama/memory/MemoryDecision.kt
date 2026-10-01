package com.example.llama.memory

/**
 * Structured decision produced by the local LLM memory analyzer.
 *
 * key is a stable identity for one memory topic, not merely the category.
 * Example: learning_spring_boot, current_project, preferred_language.
 */
data class MemoryDecision(
    val shouldRemember: Boolean,
    val key: String?,
    val memory: String?,
    val category: String?,
    val importance: Int,
    val action: Action
) {
    enum class Action {
        IGNORE,
        CREATE,
        UPDATE,
        MERGE
    }
}
