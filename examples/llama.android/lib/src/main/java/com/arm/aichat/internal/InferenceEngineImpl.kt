package com.arm.aichat.internal

import android.content.Context
import android.util.Log
import com.arm.aichat.InferenceEngine
import com.arm.aichat.UnsupportedArchitectureException
import dalvik.annotation.optimization.FastNative
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * JNI wrapper for the llama.cpp library providing Android-friendly access
 * to large language models.
 */
class InferenceEngineImpl private constructor(
    private val nativeLibDir: String
) : InferenceEngine {

    companion object {
        private val TAG = InferenceEngineImpl::class.java.simpleName

        @Volatile
        private var instance: InferenceEngine? = null

        /**
         * Create or obtain InferenceEngineImpl's single instance.
         */
        fun getInstance(context: Context) =
            instance ?: synchronized(this) {

                val nativeLibDir =
                    context.applicationInfo.nativeLibraryDir

                require(nativeLibDir.isNotBlank()) {
                    "Expected a valid native library path!"
                }

                try {

                    Log.i(
                        TAG,
                        "Instantiating InferenceEngineImpl..."
                    )

                    InferenceEngineImpl(nativeLibDir)
                        .also {
                            instance = it
                        }

                } catch (e: UnsatisfiedLinkError) {

                    Log.e(
                        TAG,
                        "Failed to load native library from $nativeLibDir",
                        e
                    )

                    throw e
                }
            }
    }

    // -------------------------------------------------------------------------
    // JNI METHODS
    // -------------------------------------------------------------------------

    @FastNative
    private external fun init(
        nativeLibDir: String
    )

    @FastNative
    private external fun load(
        modelPath: String
    ): Int

    @FastNative
    private external fun prepare(): Int

    @FastNative
    private external fun systemInfo(): String

    @FastNative
    private external fun benchModel(
        pp: Int,
        tg: Int,
        pl: Int,
        nr: Int
    ): String

    @FastNative
    private external fun processSystemPrompt(
        systemPrompt: String
    ): Int

    @FastNative
    private external fun processUserPrompt(
        userPrompt: String,
        predictLength: Int
    ): Int

    /**
     * Native method used to clear the current llama.cpp
     * conversation state and KV cache without unloading
     * the model.
     *
     * IMPORTANT:
     *
     * This is deliberately called nativeResetConversation()
     * instead of resetConversation().
     *
     * The latter is already the method required by the
     * InferenceEngine interface.
     */
    @FastNative
    private external fun nativeResetConversation()

    @FastNative
    private external fun generateNextToken(): String?

    @FastNative
    private external fun unload()

    @FastNative
    private external fun shutdown()

    // -------------------------------------------------------------------------
    // STATE
    // -------------------------------------------------------------------------

    private val _state =
        MutableStateFlow<InferenceEngine.State>(
            InferenceEngine.State.Uninitialized
        )

    override val state: StateFlow<InferenceEngine.State> =
        _state.asStateFlow()

    /**
     * True when the engine is allowed to process a system prompt.
     *
     * After a system prompt is processed this becomes false.
     *
     * resetConversation() changes it back to true.
     */
    private var _readyForSystemPrompt = false

    @Volatile
    private var _cancelGeneration = false

    // -------------------------------------------------------------------------
    // COROUTINES
    // -------------------------------------------------------------------------

    /**
     * All native llama.cpp operations are executed on a single thread.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val llamaDispatcher =
        Dispatchers.IO.limitedParallelism(1)

    private val llamaScope =
        CoroutineScope(
            llamaDispatcher + SupervisorJob()
        )

    // -------------------------------------------------------------------------
    // INITIALIZATION
    // -------------------------------------------------------------------------

    init {

        llamaScope.launch {

            try {

                check(
                    _state.value is
                        InferenceEngine.State.Uninitialized
                ) {
                    "Cannot load native library in " +
                        "${_state.value.javaClass.simpleName}!"
                }

                _state.value =
                    InferenceEngine.State.Initializing

                Log.i(
                    TAG,
                    "Loading native library..."
                )

                System.loadLibrary("ai-chat")

                init(nativeLibDir)

                _state.value =
                    InferenceEngine.State.Initialized

                Log.i(
                    TAG,
                    "Native library loaded! System info:\n" +
                        systemInfo()
                )

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Failed to load native library",
                    e
                )

                throw e
            }
        }
    }

    // -------------------------------------------------------------------------
    // LOAD MODEL
    // -------------------------------------------------------------------------

    override suspend fun loadModel(
        pathToModel: String
    ) = withContext(llamaDispatcher) {

        check(
            _state.value is InferenceEngine.State.Initialized
        ) {
            "Cannot load model in " +
                "${_state.value.javaClass.simpleName}!"
        }

        try {

            Log.i(
                TAG,
                "Checking access to model file...\n$pathToModel"
            )

            File(pathToModel).let {

                require(it.exists()) {
                    "File not found"
                }

                require(it.isFile) {
                    "Not a valid file"
                }

                require(it.canRead()) {
                    "Cannot read file"
                }
            }

            Log.i(
                TAG,
                "Loading model...\n$pathToModel"
            )

            _readyForSystemPrompt = false

            _state.value =
                InferenceEngine.State.LoadingModel

            load(pathToModel).let {

                if (it != 0) {
                    throw UnsupportedArchitectureException()
                }
            }

            prepare().let {

                if (it != 0) {
                    throw IOException(
                        "Failed to prepare resources"
                    )
                }
            }

            Log.i(
                TAG,
                "Model loaded!"
            )

            /*
             * A freshly loaded model is ready to receive
             * its system prompt.
             */
            _readyForSystemPrompt = true

            _cancelGeneration = false

            _state.value =
                InferenceEngine.State.ModelReady

        } catch (e: Exception) {

            Log.e(
                TAG,
                (e.message ?: "Error loading model") +
                    "\n" +
                    pathToModel,
                e
            )

            _state.value =
                InferenceEngine.State.Error(e)

            throw e
        }
    }

    // -------------------------------------------------------------------------
    // SYSTEM PROMPT
    // -------------------------------------------------------------------------

    override suspend fun setSystemPrompt(
        prompt: String
    ) = withContext(llamaDispatcher) {

        require(prompt.isNotBlank()) {
            "Cannot process empty system prompt!"
        }

        check(_readyForSystemPrompt) {
            "System prompt must be set RIGHT AFTER model loaded " +
                "or after resetting the conversation!"
        }

        check(
            _state.value is InferenceEngine.State.ModelReady
        ) {
            "Cannot process system prompt in " +
                "${_state.value.javaClass.simpleName}!"
        }

        try {

            Log.i(
                TAG,
                "Sending system prompt..."
            )

            _readyForSystemPrompt = false

            _state.value =
                InferenceEngine.State.ProcessingSystemPrompt

            processSystemPrompt(prompt).let { result ->

                if (result != 0) {

                    val exception =
                        RuntimeException(
                            "Failed to process system prompt: $result"
                        )

                    _state.value =
                        InferenceEngine.State.Error(exception)

                    throw exception
                }
            }

            Log.i(
                TAG,
                "System prompt processed! Awaiting user prompt..."
            )

            _state.value =
                InferenceEngine.State.ModelReady

        } catch (e: Exception) {

            if (_state.value !is InferenceEngine.State.Error) {
                _state.value =
                    InferenceEngine.State.Error(e)
            }

            throw e
        }
    }

    // -------------------------------------------------------------------------
    // RESET CONVERSATION
    // -------------------------------------------------------------------------

    /**
     * Reset only the current conversation state.
     *
     * The model remains loaded.
     *
     * Example:
     *
     * Chat A
     *   ↓
     * resetConversation()
     *   ↓
     * setSystemPrompt(Chat B context)
     *   ↓
     * Chat B
     *
     * The native llama.cpp KV cache and conversation state
     * are cleared by nativeResetConversation().
     */
    override suspend fun resetConversation() {

        withContext(llamaDispatcher) {

            check(
                _state.value is
                    InferenceEngine.State.ModelReady
            ) {
                "Model must be ready before resetting conversation"
            }

            try {

                Log.i(
                    TAG,
                    "Resetting native conversation context..."
                )

                /*
                 * Stop any possible generation.
                 */
                _cancelGeneration = true

                /*
                 * Clear llama.cpp conversation/KV state.
                 *
                 * IMPORTANT:
                 *
                 * The model itself is NOT unloaded.
                 */
                nativeResetConversation()

                /*
                 * The native engine is now empty and can
                 * receive a new system prompt.
                 */
                _readyForSystemPrompt = true

                _cancelGeneration = false

                _state.value =
                    InferenceEngine.State.ModelReady

                Log.i(
                    TAG,
                    "Conversation context reset successfully."
                )

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Failed to reset conversation",
                    e
                )

                _state.value =
                    InferenceEngine.State.Error(e)

                throw e
            }
        }
    }

    // -------------------------------------------------------------------------
    // SEND USER PROMPT
    // -------------------------------------------------------------------------

    override fun sendUserPrompt(
        message: String,
        predictLength: Int
    ): Flow<String> = flow {

        require(message.isNotEmpty()) {
            "User prompt cannot be empty"
        }

        check(
            _state.value is
                InferenceEngine.State.ModelReady
        ) {
            "Model is not ready"
        }

        try {

            Log.i(
                TAG,
                "Sending user prompt..."
            )

            /*
             * Reset cancellation for this generation.
             */
            _cancelGeneration = false

            /*
             * We already have a system prompt.
             * The next operation is the user prompt.
             */
            _readyForSystemPrompt = false

            _state.value =
                InferenceEngine.State.ProcessingUserPrompt

            processUserPrompt(
                message,
                predictLength
            ).let { result ->

                if (result != 0) {

                    Log.e(
                        TAG,
                        "Failed to process user prompt: $result"
                    )

                    return@flow
                }
            }

            Log.i(
                TAG,
                "User prompt processed. " +
                    "Generating assistant prompt..."
            )

            _state.value =
                InferenceEngine.State.Generating

            while (!_cancelGeneration) {

                generateNextToken()?.let { utf8token ->

                    if (utf8token.isNotEmpty()) {
                        emit(utf8token)
                    }

                } ?: break
            }

            if (_cancelGeneration) {

                Log.i(
                    TAG,
                    "Assistant generation aborted."
                )

            } else {

                Log.i(
                    TAG,
                    "Assistant generation complete."
                )
            }

            _state.value =
                InferenceEngine.State.ModelReady

        } catch (e: CancellationException) {

            Log.i(
                TAG,
                "Assistant generation flow collection cancelled."
            )

            _state.value =
                InferenceEngine.State.ModelReady

            throw e

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Error during generation!",
                e
            )

            _state.value =
                InferenceEngine.State.Error(e)

            throw e
        }

    }.flowOn(llamaDispatcher)

    // -------------------------------------------------------------------------
    // STOP GENERATION
    // -------------------------------------------------------------------------

    override fun stopGeneration() {

        if (
            _state.value is
                InferenceEngine.State.Generating ||
            _state.value is
                InferenceEngine.State.ProcessingUserPrompt
        ) {

            Log.i(
                TAG,
                "Stopping generation..."
            )

            _cancelGeneration = true
        }
    }

    // -------------------------------------------------------------------------
    // BENCHMARK
    // -------------------------------------------------------------------------

    override suspend fun bench(
        pp: Int,
        tg: Int,
        pl: Int,
        nr: Int
    ): String =
        withContext(llamaDispatcher) {

            check(
                _state.value is
                    InferenceEngine.State.ModelReady
            ) {
                "Benchmark request discarded due to: $state"
            }

            Log.i(
                TAG,
                "Start benchmark " +
                    "(pp: $pp, tg: $tg, pl: $pl, nr: $nr)"
            )

            /*
             * Benchmarking should not allow a new system prompt.
             */
            _readyForSystemPrompt = false

            _state.value =
                InferenceEngine.State.Benchmarking

            benchModel(
                pp,
                tg,
                pl,
                nr
            ).also {

                _state.value =
                    InferenceEngine.State.ModelReady
            }
        }

    // -------------------------------------------------------------------------
    // CLEANUP
    // -------------------------------------------------------------------------

    /**
     * Unload the model and free resources.
     */
    override fun cleanUp() {

        _cancelGeneration = true

        runBlocking(llamaDispatcher) {

            when (val currentState = _state.value) {

                is InferenceEngine.State.ModelReady -> {

                    Log.i(
                        TAG,
                        "Unloading model and freeing resources..."
                    )

                    _readyForSystemPrompt = false

                    _state.value =
                        InferenceEngine.State.UnloadingModel

                    unload()

                    _state.value =
                        InferenceEngine.State.Initialized

                    Log.i(
                        TAG,
                        "Model unloaded!"
                    )
                }

                is InferenceEngine.State.Error -> {

                    Log.i(
                        TAG,
                        "Resetting error states..."
                    )

                    _state.value =
                        InferenceEngine.State.Initialized

                    Log.i(
                        TAG,
                        "States reset!"
                    )
                }

                else -> {

                    throw IllegalStateException(
                        "Cannot unload model in " +
                            "${currentState.javaClass.simpleName}"
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // DESTROY
    // -------------------------------------------------------------------------

    /**
     * Completely destroy the native engine.
     */
    override fun destroy() {

        _cancelGeneration = true

        runBlocking(llamaDispatcher) {

            _readyForSystemPrompt = false

            when (_state.value) {

                is InferenceEngine.State.Uninitialized -> {
                    // Nothing to do.
                }

                is InferenceEngine.State.Initialized -> {
                    shutdown()
                }

                else -> {
                    unload()
                    shutdown()
                }
            }
        }

        llamaScope.cancel()
    }
}
