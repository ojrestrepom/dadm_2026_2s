package co.edu.unal.reto11_llm

import android.app.Application
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class ChatMessage(val text: String, val fromUser: Boolean)

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val modelReady: Boolean = false,
    val status: String? = null,
    val error: String? = null,
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val executor = Executors.newSingleThreadExecutor()
    private val inferenceDispatcher = executor.asCoroutineDispatcher()
    private val modelFile = File(application.filesDir, "chat-model.litertlm")
    private val metricsFile = File(application.cacheDir, "gemma-metrics.txt")
    private val mutableState = MutableStateFlow(ChatState())
    val state = mutableState.asStateFlow()

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    init {
        if (modelFile.isFile) runOperation("Cargando Gemma…", ::loadModel)
    }

    fun importModel(uri: Uri) {
        runOperation("Importando y cargando el modelo…") {
            val tempFile = File(modelFile.parentFile, "chat-model.part")
            try {
                val resolver = getApplication<Application>().contentResolver
                checkNotNull(resolver.openInputStream(uri)) { "No se pudo abrir el archivo." }
                    .use { input -> tempFile.outputStream().use { input.copyTo(it) } }
                check(tempFile.length() > 0 && tempFile.renameTo(modelFile)) {
                    "No se pudo guardar el modelo."
                }
                try {
                    loadModel()
                } catch (error: Exception) {
                    modelFile.delete()
                    throw error
                }
            } finally {
                tempFile.delete()
            }
        }
    }

    fun send(prompt: String) {
        val text = prompt.trim()
        if (text.isEmpty() || !state.value.modelReady || state.value.status != null) return

        mutableState.update {
            it.copy(messages = it.messages + ChatMessage(text, true) + ChatMessage("", false))
        }
        runOperation("Generando respuesta…") {
            val start = SystemClock.elapsedRealtime()
            var firstText = 0L
            val response = StringBuilder()
            val currentConversation = conversation ?: createConversation().also { conversation = it }
            try {
                suspendCancellableCoroutine<Unit> { completion ->
                    currentConversation.sendMessageAsync(text, object : MessageCallback {
                        override fun onMessage(message: Message) {
                            if (!completion.isActive) return
                            val part = message.toString()
                            if (part.isEmpty()) return

                            if (firstText == 0L) firstText = SystemClock.elapsedRealtime() - start
                            response.append(part)
                            mutableState.update {
                                it.copy(messages = it.messages.dropLast(1) + ChatMessage(response.toString(), false))
                            }
                        }

                        override fun onDone() = completion.resume(Unit)

                        override fun onError(throwable: Throwable) {
                            completion.resumeWithException(throwable)
                        }
                    })
                    completion.invokeOnCancellation { currentConversation.cancelProcess() }
                }
                check(response.isNotBlank()) { "Gemma devolvió una respuesta vacía." }
                recordMetric("generation first_text_ms=$firstText total_ms=${SystemClock.elapsedRealtime() - start} chars=${response.length}")
            } catch (error: Exception) {
                if (response.isEmpty()) {
                    mutableState.update { it.copy(messages = it.messages.dropLast(1)) }
                }
                throw error
            }
        }
    }

    fun newChat() {
        if (!state.value.modelReady) return
        runOperation("Iniciando chat…") {
            conversation?.close()
            conversation = null
            mutableState.update { it.copy(messages = emptyList()) }
        }
    }

    private fun loadModel() {
        val start = SystemClock.elapsedRealtime()
        val app = getApplication<Application>()
        val candidate = Engine(
            EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
                maxNumTokens = 2048,
                cacheDir = app.cacheDir.absolutePath,
            ),
        )
        try {
            candidate.initialize()
            engine = candidate
            conversation = createConversation()
            mutableState.update { it.copy(modelReady = true) }
            recordMetric("model_load_ms=${SystemClock.elapsedRealtime() - start}", reset = true)
        } catch (error: Throwable) {
            engine = null
            runCatching { candidate.close() }
            throw error
        }
    }

    private fun createConversation(): Conversation =
        checkNotNull(engine).createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(
                    "Eres un asistente general. Responde en el idioma del usuario con claridad y brevedad.",
                ),
            ),
        )

    private fun runOperation(status: String, operation: suspend () -> Unit) {
        if (state.value.status != null) return
        mutableState.update { it.copy(status = status, error = null) }
        viewModelScope.launch {
            try {
                withContext(inferenceDispatcher) { operation() }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                reportError(error)
            } catch (error: LinkageError) {
                reportError(error)
            } finally {
                mutableState.update { it.copy(status = null) }
            }
        }
    }

    private fun recordMetric(metric: String, reset: Boolean = false) {
        Log.i(TAG, metric)
        val appInfo = getApplication<Application>().applicationInfo
        if (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return

        // Some devices suppress application info logs. Keep timings available to ADB in debug builds.
        runCatching {
            if (reset) metricsFile.writeText("$metric\n") else metricsFile.appendText("$metric\n")
        }
    }

    private fun reportError(error: Throwable) {
        Log.e(TAG, "LiteRT-LM operation failed", error)
        val message = when (error) {
            is LinkageError -> "LiteRT-LM no está disponible para este dispositivo."
            else -> error.message ?: "Ocurrió un error al ejecutar Gemma."
        }
        mutableState.update { it.copy(error = message) }
    }

    override fun onCleared() {
        executor.execute {
            try {
                conversation?.close()
            } finally {
                try {
                    engine?.close()
                } finally {
                    inferenceDispatcher.close()
                }
            }
        }
        super.onCleared()
    }

    private companion object {
        const val TAG = "Reto11Gemma"
    }
}


