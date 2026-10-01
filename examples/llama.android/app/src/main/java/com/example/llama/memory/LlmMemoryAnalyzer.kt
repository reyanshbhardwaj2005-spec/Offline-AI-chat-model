package com.example.llama.memory

import org.json.JSONObject

/**
 * Local-LLM based memory extraction.
 *
 * The model is explicitly instructed to return JSON only. Parsing is kept
 * defensive because a small local model may occasionally add markdown fences
 * or extra text around the JSON object.
 */
class LlmMemoryAnalyzer : MemoryAnalyzer {

    override suspend fun analyze(
        userMessage: String,
        assistantResponse: String,
        generate: suspend (prompt: String, maxTokens: Int) -> String
    ): MemoryDecision {
        if (!isCandidate(userMessage)) {
            return ignore()
        }

        val prompt = buildPrompt(userMessage, assistantResponse)
        val raw = generate(prompt, 180)

        return parseDecision(raw)
    }

    private fun isCandidate(message: String): Boolean {
        val text = message.lowercase()
        val signals = listOf(
            "i am", "i'm", "my ", "i like", "i love", "i prefer",
            "i want", "i need", "i use", "i know", "i learned",
            "i'm learning", "i am learning", "i finished", "i completed",
            "my goal", "my project", "remember", "don't forget",
            "do not forget", "from now on", "i usually", "i always",
            "i work", "i study"
        )
        return text.length >= 8 && signals.any { text.contains(it) }
    }

    private fun buildPrompt(userMessage: String, assistantResponse: String): String =
        """
        You are the memory extraction component of an offline AI assistant.
        Decide whether this conversation turn contains durable information
        about the user that will help future conversations.

        Return ONLY one valid JSON object. No markdown. No explanation.

        JSON schema:
        {
          "shouldRemember": true,
          "key": "stable_memory_key",
          "memory": "short factual statement about the user",
          "category": "identity|preference|learning|skill|project|goal|habit|technical|other",
          "importance": 1,
          "action": "IGNORE|CREATE|UPDATE|MERGE"
        }

        Rules:
        1. Store durable user facts, preferences, learning progress, skills, goals,
           projects and stable technical preferences.
        2. Do not store temporary requests, one-off questions, generic facts,
           assistant facts, or information about unrelated people.
        3. Never invent a fact.
        4. key must be short, lowercase, stable and specific to the memory topic.
        5. Do NOT use only the category as the key.
           Bad: "learning".
           Good: "learning_spring_boot".
        6. CREATE is for a new topic. UPDATE replaces an existing fact on the
           same topic. MERGE combines compatible existing/new information into
           one concise fact. If there is no durable memory, use IGNORE.
        7. If the USER MESSAGE is only a question/request and contains no new
           durable information about the user, use:
           "shouldRemember": false and "action": "IGNORE".
        8. Do not treat information merely repeated by the ASSISTANT RESPONSE
           as a new user memory.
        9. "shouldRemember": false MUST use action "IGNORE".
           "shouldRemember": true MUST NOT use action "IGNORE".
        10. importance must be 1 to 5. Use 5 for core identity/goals/projects,
           4 for meaningful long-term preferences/progress, 3 for useful facts,
           1-2 for minor information.
        8. Keep memory under 25 words and write it as a factual statement.

        USER MESSAGE:
        $userMessage

        ASSISTANT RESPONSE:
        $assistantResponse
        """.trimIndent()

    private fun parseDecision(raw: String): MemoryDecision {
        return try {
            val jsonText = extractJson(raw)
            val json = JSONObject(jsonText)
            val action = runCatching {
                MemoryDecision.Action.valueOf(
                    json.optString("action", "IGNORE").uppercase()
                )
            }.getOrDefault(MemoryDecision.Action.IGNORE)

            val shouldRemember = json.optBoolean("shouldRemember", false)
            val key = json.optString("key", "").trim().ifBlank { null }
            val memory = json.optString("memory", "").trim().ifBlank { null }
            val category = json.optString("category", "other").trim().ifBlank { "other" }
            val importance = json.optInt("importance", 1).coerceIn(1, 5)

            if (!shouldRemember || action == MemoryDecision.Action.IGNORE ||
                key == null || memory == null
            ) {
                ignore()
            } else {
                MemoryDecision(
                    shouldRemember = true,
                    key = key,
                    memory = memory,
                    category = category,
                    importance = importance,
                    action = action
                )
            }
        } catch (_: Exception) {
            ignore()
        }
    }

    private fun extractJson(raw: String): String {
        val cleaned = raw
            .replace("```json", "", ignoreCase = true)
            .replace("```", "")
            .trim()

        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        require(start >= 0 && end > start) { "No JSON object found" }
        return cleaned.substring(start, end + 1)
    }

    private fun ignore() = MemoryDecision(
        shouldRemember = false,
        key = null,
        memory = null,
        category = null,
        importance = 1,
        action = MemoryDecision.Action.IGNORE
    )
}
