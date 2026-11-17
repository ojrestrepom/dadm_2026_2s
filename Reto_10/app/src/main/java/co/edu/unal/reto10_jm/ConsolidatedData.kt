package co.edu.unal.reto10_jm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.math.BigDecimal
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal data class ConsolidatedEvent(
    val id: String,
    val line: String,
    val opi: Long,
    val name: String,
    val client: String,
    val country: String,
    val executive: String,
    val status: String,
    val totalUsd: BigDecimal?
)

internal sealed interface ConsolidatedState {
    data object Idle : ConsolidatedState
    data object Loading : ConsolidatedState
    data class Loaded(val events: List<ConsolidatedEvent>) : ConsolidatedState
    data class Error(val message: String) : ConsolidatedState
}

internal class ConsolidatedViewModel : ViewModel() {
    var endDate by mutableStateOf(todayForDatePicker())
        private set
    var startDate by mutableStateOf(
        Calendar.getInstance(utc).run {
            timeInMillis = endDate
            add(Calendar.DATE, -7)
            timeInMillis
        }
    )
        private set
    var state: ConsolidatedState by mutableStateOf(ConsolidatedState.Idle)
        private set

    val isRangeValid: Boolean get() = startDate < endDate

    private var query: Job? = null

    fun setDate(isStart: Boolean, utcMillis: Long) {
        if (isStart && startDate == utcMillis || !isStart && endDate == utcMillis) return
        query?.cancel()
        if (isStart) startDate = utcMillis else endDate = utcMillis
        state = ConsolidatedState.Idle
    }

    fun load() {
        if (!isRangeValid || state == ConsolidatedState.Loading) return
        val from = formatReportDate(startDate, "yyyy-MM-dd")
        val until = formatReportDate(endDate, "yyyy-MM-dd")
        state = ConsolidatedState.Loading
        query = viewModelScope.launch {
            state = try {
                ConsolidatedState.Loaded(withContext(Dispatchers.IO) { fetchConsolidated(from, until) })
            } catch (_: IOException) {
                ensureActive()
                ConsolidatedState.Error("No se pudo consultar el consolidado.")
            } catch (_: JSONException) {
                ensureActive()
                ConsolidatedState.Error("El consolidado tiene un formato inesperado.")
            } catch (_: NumberFormatException) {
                ensureActive()
                ConsolidatedState.Error("El consolidado contiene un total inválido.")
            }
        }
    }
}

private val utc = TimeZone.getTimeZone("UTC")

private fun todayForDatePicker(): Long {
    val local = Calendar.getInstance()
    return Calendar.getInstance(utc).run {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        timeInMillis
    }
}

internal fun formatReportDate(utcMillis: Long, pattern: String): String =
    SimpleDateFormat(pattern, Locale.ROOT).apply { timeZone = utc }.format(Date(utcMillis))

private fun fetchConsolidated(from: String, until: String): List<ConsolidatedEvent> {
    val payload = JSONObject()
        .put("fechaIni", from)
        .put("fechaFin", until)
        .toString()
        .toByteArray(Charsets.UTF_8)

    return withPresupuestaConnection(
        URI("https://presupuestalatam.com/api/listareventos"),
        "application/json",
        method = "POST"
    ) { connection ->
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setFixedLengthStreamingMode(payload.size)
        connection.outputStream.use { it.write(payload) }
        if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
        val response = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        val events = response.getJSONArray("infoEventos")
        List(events.length()) { index ->
            val event = events.getJSONObject(index)
            ConsolidatedEvent(
                id = event.getString("_id"),
                line = event.getString("linea"),
                opi = event.getLong("opi"),
                name = event.getString("nombre"),
                client = event.getString("cliente"),
                country = if (event.isNull("pais")) "" else event.getString("pais"),
                executive = event.getString("ejecutivo"),
                status = event.getString("estado"),
                totalUsd = if (event.isNull("total")) null else event.getString("total").toBigDecimal()
            )
        }
    }
}
