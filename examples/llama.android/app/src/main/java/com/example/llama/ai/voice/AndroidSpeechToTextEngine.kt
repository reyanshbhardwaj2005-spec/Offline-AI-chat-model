package com.example.llama.ai.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class AndroidSpeechToTextEngine(
    context: Context,
    private val listener: Listener
) : SpeechToTextEngine {

    interface Listener {
        fun onReady()
        fun onBeginningOfSpeech()
        fun onPartialResult(text: String)
        fun onFinalResult(text: String)
        fun onEndOfSpeech()
        fun onError(error: String)
    }

    private val appContext = context.applicationContext

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    private val recognizerIntent = Intent(
        RecognizerIntent.ACTION_RECOGNIZE_SPEECH
    ).apply {

        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )

        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE,
            Locale.getDefault()
        )

        putExtra(
            RecognizerIntent.EXTRA_PARTIAL_RESULTS,
            true
        )

        putExtra(
            RecognizerIntent.EXTRA_MAX_RESULTS,
            1
        )
    }

    override fun startListening() {

        if (isListening) {
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            listener.onError(
                "Speech recognition is not available on this device"
            )
            return
        }

        // Important:
        // Create a NEW recognizer for every session.
        destroyRecognizer()

        speechRecognizer =
            SpeechRecognizer.createSpeechRecognizer(appContext)

        speechRecognizer?.setRecognitionListener(
            createRecognitionListener()
        )

        isListening = true

        try {

            speechRecognizer?.startListening(recognizerIntent)

        } catch (e: Exception) {

            isListening = false
            destroyRecognizer()

            listener.onError(
                e.message ?: "Failed to start speech recognition"
            )
        }
    }

    override fun stopListening() {

        if (!isListening) {
            return
        }

        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
        }
    }

    override fun cancelListening() {

        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }

        isListening = false
        destroyRecognizer()
    }

    override fun destroy() {

        isListening = false
        destroyRecognizer()
    }

    private fun createRecognitionListener(): RecognitionListener {

        return object : RecognitionListener {

            override fun onReadyForSpeech(
                params: Bundle?
            ) {
                listener.onReady()
            }

            override fun onBeginningOfSpeech() {
                listener.onBeginningOfSpeech()
            }

            override fun onRmsChanged(
                rmsdB: Float
            ) {
            }

            override fun onBufferReceived(
                buffer: ByteArray?
            ) {
            }

            override fun onEndOfSpeech() {

                isListening = false

                listener.onEndOfSpeech()

                // Don't destroy immediately here.
                // onResults/onError will finish the session.
            }

            override fun onError(
                error: Int
            ) {

                isListening = false

                listener.onError(
                    getErrorMessage(error)
                )

                destroyRecognizer()
            }

            override fun onResults(
                results: Bundle?
            ) {

                isListening = false

                val matches =
                    results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )

                val text =
                    matches
                        ?.firstOrNull()
                        ?.trim()
                        ?: ""

                if (text.isNotEmpty()) {
                    listener.onFinalResult(text)
                }

                destroyRecognizer()
            }

            override fun onPartialResults(
                partialResults: Bundle?
            ) {

                val matches =
                    partialResults?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )

                val text =
                    matches
                        ?.firstOrNull()
                        ?.trim()
                        ?: ""

                if (text.isNotEmpty()) {
                    listener.onPartialResult(text)
                }
            }

            override fun onEvent(
                eventType: Int,
                params: Bundle?
            ) {
            }
        }
    }

    private fun destroyRecognizer() {

        try {
            speechRecognizer?.setRecognitionListener(null)
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }

        speechRecognizer = null
    }

    private fun getErrorMessage(
        error: Int
    ): String {

        return when (error) {

            SpeechRecognizer.ERROR_AUDIO ->
                "Audio recording error"

            SpeechRecognizer.ERROR_CLIENT ->
                "Speech recognition client error"

            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "Microphone permission is required"

            SpeechRecognizer.ERROR_NETWORK ->
                "Speech recognition network error"

            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "Speech recognition network timeout"

            SpeechRecognizer.ERROR_NO_MATCH ->
                "Could not understand speech"

            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                "Speech recognizer is busy"

            SpeechRecognizer.ERROR_SERVER ->
                "Speech recognition server error"

            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                "No speech detected"

            else ->
                "Speech recognition error: $error"
        }
    }
}
