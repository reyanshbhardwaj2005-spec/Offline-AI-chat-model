package com.example.llama.ai.voice

interface TextToSpeechEngine {

    fun speak(text: String)

    fun stop()

    fun isSpeaking(): Boolean

    fun destroy()
}
