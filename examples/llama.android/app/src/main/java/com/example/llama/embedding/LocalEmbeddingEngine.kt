package com.example.llama.embedding

import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Real on-device embedding engine backed by llama.cpp.
 *
 * Expected model: e5-small-v2 GGUF (384 dimensions).
 * The model is intentionally separate from the chat model.
 */
class LocalEmbeddingEngine(
    context: Context, private val modelPath: String = defaultModelPath(context)
) : EmbeddingEngine {
    private val appContext = context.applicationContext

    companion object {
        const val MODEL_NAME = "e5-small-v2"
        const val DIMENSION = 384
        const val MODEL_FILE_NAME = "e5-small-v2-q8_0.gguf"
        fun defaultModelPath(context: Context): String = File(
            context.applicationContext.filesDir, "embeddings/$MODEL_FILE_NAME"
        ).absolutePath
    }

    private val inferenceEngine: InferenceEngine = AiChat.getInferenceEngine(appContext)
    private val loadMutex = Mutex()

    @Volatile
    private var loaded = false
    override val modelName: String = MODEL_NAME
    override val dimension: Int = DIMENSION
    private fun ensureEmbeddingModelFile(): String {
        val modelFile = File(modelPath)

        android.util.Log.d(
            "EmbeddingDebug", "Target model path: ${modelFile.absolutePath}"
        )

        android.util.Log.d(
            "EmbeddingDebug", "Model exists before copy: ${modelFile.exists()}"
        )

        if (modelFile.exists()) {
            require(modelFile.isFile && modelFile.canRead()) {
                "Embedding model cannot be read: $modelPath"
            }

            android.util.Log.d(
                "EmbeddingDebug", "Using existing model. Size=${modelFile.length()} bytes"
            )

            return modelFile.absolutePath
        }

        modelFile.parentFile?.mkdirs()
        val tempFile = File(
            modelFile.parentFile, "${modelFile.name}.tmp"
        )

        try {
            android.util.Log.d(
                "EmbeddingDebug", "Opening asset: embeddings/$MODEL_FILE_NAME"
            )

            appContext.assets.open(
                "embeddings/$MODEL_FILE_NAME"
            ).use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }

            android.util.Log.d(
                "EmbeddingDebug", "Temporary copy completed. Size=${tempFile.length()} bytes"
            )

            require(tempFile.exists() && tempFile.length() > 0) {
                "Temporary embedding model copy is empty"
            }

            if (modelFile.exists()) {
                modelFile.delete()
            }

            check(tempFile.renameTo(modelFile)) {
                "Could not rename temporary embedding model file"
            }

            android.util.Log.d(
                "EmbeddingDebug", "Final copy completed. Size=${modelFile.length()} bytes"
            )
        } catch (exception: Exception) {
            android.util.Log.e(
                "EmbeddingDebug", "Embedding model copy failed", exception
            )

            tempFile.delete()

            throw IllegalStateException(
                "Failed to copy embedding model from assets", exception
            )
        }

        require(modelFile.exists() && modelFile.isFile && modelFile.canRead()) {
            "Embedding model does not exist after copying: ${modelFile.absolutePath}"
        }

        return modelFile.absolutePath
    }

    private suspend fun ensureLoaded() {
        if (loaded) return

        loadMutex.withLock {
            if (loaded) return

            android.util.Log.d(
                "EmbeddingDebug", "===== EMBEDDING LOAD START ====="
            )

            android.util.Log.d(
                "EmbeddingDebug", "Embedding model path = $modelPath"
            )
            val actualModelPath = ensureEmbeddingModelFile()

            android.util.Log.d(
                "EmbeddingDebug", "Calling loadEmbeddingModel()"
            )

            inferenceEngine.loadEmbeddingModel(actualModelPath)

            android.util.Log.d(
                "EmbeddingDebug", "Embedding model loaded"
            )

            android.util.Log.d(
                "EmbeddingDebug", "Calling prepareEmbedding()"
            )

            inferenceEngine.prepareEmbedding()

            android.util.Log.d(
                "EmbeddingDebug", "Embedding prepared"
            )
            val nativeDimension = inferenceEngine.getEmbeddingDimension()

            android.util.Log.d(
                "EmbeddingDebug", "Embedding dimension = $nativeDimension"
            )

            require(nativeDimension == DIMENSION) {
                "Unexpected embedding dimension: $nativeDimension"
            }

            loaded = true

            android.util.Log.d(
                "EmbeddingDebug", "===== EMBEDDING LOAD COMPLETE ====="
            )
        }
    }

    override suspend fun embed(text: String): FloatArray {
        require(text.isNotBlank()) {
            "Cannot embed blank text"
        }

        ensureLoaded()
        val input = if (text.startsWith("query: ")) text
        else "query: $text"

        return inferenceEngine.createEmbedding(input)
    }

    suspend fun embedPassage(text: String): FloatArray {
        require(text.isNotBlank()) {
            "Cannot embed blank passage"
        }

        ensureLoaded()
        val input = if (text.startsWith("passage: ")) text
        else "passage: $text"

        return inferenceEngine.createEmbedding(input)
    }

    override suspend fun embedBatch(
        texts: List<String>
    ): List<FloatArray> = texts.map { embed(it) }

    fun embeddingModelPath(): String = modelPath
    override fun close() {
        if (!loaded) return

        kotlinx.coroutines.runBlocking {
            inferenceEngine.unloadEmbeddingModel()
        }

        loaded = false
    }
}
