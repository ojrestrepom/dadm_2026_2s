package co.edu.unal.reto10_jm

import android.app.Application
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

private val baseUri = URI("https://presupuestalatam.com/")

internal data class EventDetails(
    val opi: Long,
    val name: String,
    val contact: String,
    val approvedVersion: Int?,
    val documentUrl: URI?
)

internal sealed interface EventState {
    data object Idle : EventState
    data object Loading : EventState
    data object NotFound : EventState
    data class Found(val event: EventDetails) : EventState
    data class Error(val message: String) : EventState
}

internal sealed interface DocumentState {
    val url: URI

    data class Loading(override val url: URI) : DocumentState
    data class Ready(override val url: URI, val file: File, val pageCount: Int) : DocumentState
    data class Error(override val url: URI, val message: String) : DocumentState
}

internal class EventViewModel(application: Application) : AndroidViewModel(application) {
    var input by mutableStateOf("")
        private set
    var validationError by mutableStateOf(false)
        private set
    var state: EventState by mutableStateOf(EventState.Idle)
        private set
    var document: DocumentState? by mutableStateOf(null)
        private set

    private var searchJob: Job? = null
    private var documentJob: Job? = null

    fun editOpi(value: String) {
        input = value
        validationError = false
        searchJob?.cancel()
        state = EventState.Idle
    }

    fun search() {
        val opi = input.trim().toLongOrNull()
        if (opi == null || opi <= 0 || !input.trim().all { it in '0'..'9' }) {
            validationError = true
            return
        }
        searchJob?.cancel()
        state = EventState.Loading
        searchJob = viewModelScope.launch {
            state = try {
                withContext(Dispatchers.IO) { fetchEvent(opi) }
            } catch (_: IOException) {
                ensureActive()
                EventState.Error("No se pudo consultar el evento. Inténtalo de nuevo.")
            } catch (_: JSONException) {
                ensureActive()
                EventState.Error("La respuesta del evento tiene un formato inesperado.")
            }
        }
    }

    fun openDocument(url: URI) {
        closeDocument()
        document = DocumentState.Loading(url)
        val file = File(getApplication<Application>().cacheDir, "approval-${UUID.randomUUID()}.pdf")
        documentJob = viewModelScope.launch {
            var retained = false
            try {
                val pageCount = withContext(Dispatchers.IO) { downloadDocument(url, file) }
                document = DocumentState.Ready(url, file, pageCount)
                retained = true
            } catch (_: IOException) {
                ensureActive()
                document = DocumentState.Error(url, "No se pudo cargar el PDF.")
            } catch (_: SecurityException) {
                ensureActive()
                document = DocumentState.Error(url, "El PDF está protegido o no se puede abrir.")
            } finally {
                if (!retained) {
                    withContext(NonCancellable + Dispatchers.IO) { file.delete() }
                }
            }
        }
    }

    fun closeDocument() {
        documentJob?.cancel()
        (document as? DocumentState.Ready)?.file?.delete()
        document = null
    }

    override fun onCleared() {
        closeDocument()
        super.onCleared()
    }
}

private fun fetchEvent(opi: Long): EventState =
    withPresupuestaConnection(baseUri.resolve("api/eventos/opi/$opi"), "application/json") { connection ->
        if (connection.responseCode == 404) return@withPresupuestaConnection EventState.NotFound
        if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
        val payload = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        val event = payload.get("evento")
        if (event == JSONObject.NULL) return@withPresupuestaConnection EventState.NotFound
        val details = payload.getJSONObject("evento")
        EventState.Found(
            EventDetails(
                opi = details.getLong("opi"),
                name = details.getString("nombre"),
                contact = if (details.isNull("contacto")) "" else details.getString("contacto").trim(),
                approvedVersion = if (details.isNull("version_aprobada")) null else details.getInt("version_aprobada"),
                documentUrl = if (details.isNull("url_aprobacion")) {
                    null
                } else {
                    approvalUri(details.getString("url_aprobacion"))
                }
            )
        )
    }

private fun approvalUri(path: String): URI? {
    if (path.isBlank()) return null
    val url = try {
        baseUri.resolve(URI(path.trim())).normalize()
    } catch (_: URISyntaxException) {
        return null
    }
    return url.takeIf {
        it.scheme.equals("https", ignoreCase = true) &&
            it.host.equals(baseUri.host, ignoreCase = true) &&
            it.userInfo == null && it.port in listOf(-1, 443) &&
            !it.path.isNullOrBlank() && it.path != "/"
    }
}

private suspend fun downloadDocument(url: URI, file: File): Int {
    val context = kotlin.coroutines.coroutineContext
    withPresupuestaConnection(url, "application/pdf") { connection ->
        if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
        connection.inputStream.use { input ->
            file.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    context.ensureActive()
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                }
            }
        }
    }
    return openPdf(file) { renderer ->
        renderer.pageCount.takeIf { it > 0 } ?: throw IOException("Empty PDF")
    }
}

internal fun <T> openPdf(file: File, useRenderer: (PdfRenderer) -> T): T {
    val source = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    val renderer = try {
        PdfRenderer(source)
    } catch (error: Exception) {
        source.close()
        throw error
    }
    return renderer.use(useRenderer)
}

internal fun <T> withPresupuestaConnection(
    url: URI,
    accept: String,
    followRedirects: Boolean = false,
    method: String = "GET",
    read: (HttpsURLConnection) -> T
): T {
    val connection = url.toURL().openConnection() as HttpsURLConnection
    try {
        connection.requestMethod = method
        connection.instanceFollowRedirects = followRedirects
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Accept", accept)
        return read(connection)
    } finally {
        connection.disconnect()
    }
}
