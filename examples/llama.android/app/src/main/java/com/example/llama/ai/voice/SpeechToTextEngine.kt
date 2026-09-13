package com.example.llama.ai.voice

interface SpeechToTextEngine {

    fun startListening()

    fun stopListening()

    fun cancelListening()

    fun destroy()
}
