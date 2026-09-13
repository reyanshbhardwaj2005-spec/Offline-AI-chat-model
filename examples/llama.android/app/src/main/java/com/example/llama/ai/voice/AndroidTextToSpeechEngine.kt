package com.example.llama.ai.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class AndroidTextToSpeechEngine(
    context: Context,
    private val listener: Listener
) : TextToSpeechEngine {

    interface Listener {
        fun onReady()
        fun onStart()
        fun onDone()
        fun onError(error: String)
    }

    private val appContext = context.applicationContext

    private var textToSpeech: TextToSpeech? = null
    private var initialized = false

    init {
        textToSpeech = TextToSpeech(
            appContext
        ) { status ->

            if (status == TextToSpeech.SUCCESS) {

                textToSpeech?.language = Locale.getDefault()

                textToSpeech?.setSpeechRate(1.0f)
                textToSpeech?.setPitch(1.0f)

                textToSpeech?.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {

                        override fun onStart(
                            utteranceId: String?
                        ) {
                            listener.onStart()
                        }

                        override fun onDone(
                            utteranceId: String?
                        ) {
                            listener.onDone()
                        }

                        override fun onError(
                            utteranceId: String?
                        ) {
                            listener.onError(
                                "Text-to-speech error"
                            )
                        }
                    }
                )

                initialized = true
                listener.onReady()

            } else {

                initialized = false

                listener.onError(
                    "Text-to-speech initialization failed"
                )
            }
        }
    }

    override fun speak(text: String) {

        if (!initialized) {
            listener.onError(
                "Text-to-speech is not ready"
            )
            return
        }

        if (text.isBlank()) {
            return
        }

        textToSpeech?.stop()

        val result = textToSpeech?.speak(
            text.trim(),
            TextToSpeech.QUEUE_FLUSH,
            null,
            "assistant_response"
        )

        if (result == TextToSpeech.ERROR) {
            listener.onError(
                "Failed to speak response"
            )
        }
    }

    override fun stop() {
        textToSpeech?.stop()
    }

    override fun isSpeaking(): Boolean {
        return textToSpeech?.isSpeaking == true
    }

    override fun destroy() {

        textToSpeech?.stop()
        textToSpeech?.shutdown()

        textToSpeech = null
        initialized = false
    }
}
