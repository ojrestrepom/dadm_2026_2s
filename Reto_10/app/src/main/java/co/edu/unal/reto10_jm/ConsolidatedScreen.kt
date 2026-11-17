package co.edu.unal.reto10_jm

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale

private enum class DateField(val label: String, val hint: String) {
    Start("Fecha inicial", "Mostrar desde"),
    End("Fecha final", "Mostrar hasta antes de")
}

@Composable
internal fun ConsolidatedScreen(viewModel: ConsolidatedViewModel, modifier: Modifier) {
    var editing by rememberSaveable { mutableStateOf<DateField?>(null) }
    val state = viewModel.state
    LazyColumn(modifier) {
        item(key = "filters") {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Consolidado", style = MaterialTheme.typography.headlineSmall)
                DateControl(DateField.Start, viewModel.startDate) { editing = DateField.Start }
                DateControl(DateField.End, viewModel.endDate) { editing = DateField.End }
                if (!viewModel.isRangeValid) {
                    Text(
                        "La fecha final debe ser posterior a la inicial.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Button(
                    onClick = viewModel::load,
                    enabled = viewModel.isRangeValid && state != ConsolidatedState.Loading
                ) { Text("Mostrar") }
            }
        }

        when (state) {
            ConsolidatedState.Idle -> Unit
            ConsolidatedState.Loading -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is ConsolidatedState.Error -> item(key = "error") {
                Column(Modifier.padding(16.dp)) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::load) { Text("Reintentar") }
                }
            }
            is ConsolidatedState.Loaded -> {
                item(key = "count") {
                    val count = state.events.size
                    val summary = when (count) {
                        0 -> "No hay eventos para este rango."
                        1 -> "1 evento"
                        else -> "$count eventos"
                    }
                    Text(summary, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                itemsIndexed(state.events, key = { _, event -> event.id }) { index, event ->
                    ConsolidatedRow(event, alternate = index % 2 != 0)
                }
            }
        }
    }

    editing?.let { field ->
        ReportDateDialog(
            field = field,
            selected = if (field == DateField.Start) viewModel.startDate else viewModel.endDate,
            onDismiss = { editing = null },
            onConfirm = {
                viewModel.setDate(field == DateField.Start, it)
                editing = null
            }
        )
    }
}

@Composable
private fun DateControl(field: DateField, value: Long, open: () -> Unit) {
    Column {
        Text(field.label, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = open, modifier = Modifier.fillMaxWidth()) {
            Text(formatReportDate(value, "dd/MM/yyyy"))
        }
        Text(field.hint, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportDateDialog(
    field: DateField,
    selected: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val picker = rememberDatePickerState(initialSelectedDateMillis = selected)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { picker.selectedDateMillis?.let(onConfirm) },
                enabled = picker.selectedDateMillis != null
            ) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    ) {
        DatePicker(state = picker, title = { Text(field.label, Modifier.padding(16.dp)) })
    }
}

@Composable
private fun ConsolidatedRow(event: ConsolidatedEvent, alternate: Boolean) {
    val colors = MaterialTheme.colorScheme
    val total = event.totalUsd?.let {
        NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }.format(it)
    } ?: "—"

    Column {
        HorizontalDivider(color = colors.outlineVariant)
        Column(
            Modifier.fillMaxWidth()
                .background(if (alternate) colors.surfaceContainerHigh else colors.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text("Línea de negocio: ${event.line}")
            Text("OP: ${event.opi}")
            Text("Evento: ${event.name}", style = MaterialTheme.typography.titleSmall)
            Text("Cliente: ${event.client}")
            CountryFlag(event.country)
            Text("Ejecutivo Principal: ${event.executive}")
            Text("Estado: ${event.status}")
            Text("Total (USD): $total")
        }
    }
}

private val countryFlags = mapOf(
    "AR" to R.drawable.flag_ar,
    "BO" to R.drawable.flag_bo,
    "BR" to R.drawable.flag_br,
    "CL" to R.drawable.flag_cl,
    "CO" to R.drawable.flag_co,
    "CR" to R.drawable.flag_cr,
    "DE" to R.drawable.flag_de,
    "DO" to R.drawable.flag_do,
    "EC" to R.drawable.flag_ec,
    "GT" to R.drawable.flag_gt,
    "HN" to R.drawable.flag_hn,
    "JM" to R.drawable.flag_jm,
    "MX" to R.drawable.flag_mx,
    "NI" to R.drawable.flag_ni,
    "PA" to R.drawable.flag_pa,
    "PE" to R.drawable.flag_pe,
    "PR" to R.drawable.flag_pr,
    "PY" to R.drawable.flag_py,
    "SV" to R.drawable.flag_sv,
    "TT" to R.drawable.flag_tt,
    "US" to R.drawable.flag_us,
    "UY" to R.drawable.flag_uy,
    "VE" to R.drawable.flag_ve
)

@Composable
private fun CountryFlag(value: String) {
    val code = value.trim().uppercase(Locale.ROOT)
    // The catalog mixes two- and three-letter abbreviations and uses CH for Chile.
    val region = when (code) {
        "BOL" -> "BO"
        "CH" -> "CL"
        "JAM" -> "JM"
        "PRI" -> "PR"
        "PRY" -> "PY"
        "TTO" -> "TT"
        else -> code
    }
    val flag = countryFlags[region]
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("País:")
        if (flag != null) {
            val country = Locale.forLanguageTag("und-$region").getDisplayCountry(Locale.forLanguageTag("es"))
            Image(
                painter = painterResource(flag),
                contentDescription = country,
                modifier = Modifier.size(width = 24.dp, height = 18.dp)
            )
        } else {
            Text(code.ifEmpty { "—" })
        }
    }
}
