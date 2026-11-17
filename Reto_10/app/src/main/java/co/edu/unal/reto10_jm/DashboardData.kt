package co.edu.unal.reto10_jm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.math.BigDecimal
import java.net.URI

internal enum class EventSeries(
    val field: String,
    val totalField: String,
    val label: String,
    val color: Color
) {
    Created("creados", "sumCreados", "Creados", Color(0xFF387BF4)),
    Sent("enviados", "sumEnviados", "Enviados", Color(0xFFDB4437)),
    Approved("aprobados", "sumAprobados", "Aprobados", Color(0xFFF0AB00)),
    InProgress("ejecucion", "sumEjecucion", "En ejecución", Color(0xFF0C9956)),
    Completed("ejecutado", "sumEjecutado", "Ejecutados", Color(0xFFA044BA)),
    Invoiced("facturado", "sumFacturado", "Facturados", Color(0xFF008CA4)),
    Cancelled("cancelado", "sumCancelado", "Cancelados", Color(0xFFEF6941)),
    CancelledWithCosts("cancelado_costos", "sumCancelado_costos", "Cancelados con costos", Color(0xFF8D8D21)),
    Archived("archivado", "sumArchivado", "Archivados", Color(0xFF5261B9))
}

internal data class DashboardDay(
    val date: String,
    val counts: List<Int>,
    val approvedValue: BigDecimal
) {
    val label: String
        get() = "${date.substring(8, 10)}/${date.substring(5, 7)}"
}

internal data class DashboardData(
    val days: List<DashboardDay>,
    val totals: List<Int>,
    val approvedTotal: BigDecimal
)

internal sealed interface DashboardState {
    data object Loading : DashboardState
    data object Empty : DashboardState
    data class Loaded(val data: DashboardData) : DashboardState
    data class Error(val message: String) : DashboardState
}

internal class DashboardViewModel : ViewModel() {
    var state: DashboardState by mutableStateOf(DashboardState.Loading)
        private set

    init {
        load()
    }

    fun load() {
        state = DashboardState.Loading
        viewModelScope.launch {
            state = try {
                withContext(Dispatchers.IO) {
                    val data = fetchDashboard()
                    if (data == null) DashboardState.Empty else DashboardState.Loaded(data)
                }
            } catch (_: IOException) {
                DashboardState.Error("No se pudo consultar el Dashboard.")
            } catch (_: JSONException) {
                DashboardState.Error("La respuesta del Dashboard tiene un formato inesperado.")
            } catch (_: NumberFormatException) {
                DashboardState.Error("La respuesta contiene un importe inválido.")
            }
        }
    }
}

private fun fetchDashboard(): DashboardData? =
    withPresupuestaConnection(
        URI("https://presupuestalatam.com/api/dashboard"),
        "application/json",
        followRedirects = true
    ) { connection ->
        if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
        connection.inputStream.bufferedReader().use { parseDashboard(JSONObject(it.readText())) }
    }

private fun parseDashboard(response: JSONObject): DashboardData? {
    val matrix = response.getJSONArray("matriz")
    if (matrix.length() == 0) return null
    val totals = response.getJSONObject("totales")
    val days = List(matrix.length()) { index ->
        val row = matrix.getJSONObject(index)
        val date = row.getString("dia").substringBefore('T')
        if (!date.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) throw JSONException("Invalid report date")
        DashboardDay(
            date = date,
            counts = EventSeries.entries.map { row.getInt(it.field) },
            approvedValue = if (row.isNull("valorAprobados")) {
                BigDecimal.ZERO
            } else {
                row.getString("valorAprobados").toBigDecimal()
            }
        )
    }.sortedBy { it.date }
    return DashboardData(
        days = days,
        totals = EventSeries.entries.map { totals.getInt(it.totalField) },
        approvedTotal = totals.getString("totalAprobados").toBigDecimal()
    )
}
