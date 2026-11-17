package co.edu.unal.reto10_jm

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun EventScreen(viewModel: EventViewModel, modifier: Modifier) {
    val document = viewModel.document
    if (document != null) {
        BackHandler { viewModel.closeDocument() }
        ApprovalViewer(document, { viewModel.openDocument(document.url) }, viewModel::closeDocument, modifier)
        return
    }

    val focus = LocalFocusManager.current
    val submit = {
        focus.clearFocus()
        viewModel.search()
    }

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Evento", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = viewModel.input,
            onValueChange = viewModel::editOpi,
            label = { Text("Número de OP") },
            singleLine = true,
            isError = viewModel.validationError,
            supportingText = if (viewModel.validationError) {
                { Text("Ingresa un número entero positivo.") }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = submit, enabled = viewModel.state != EventState.Loading) {
            Text("Mostrar")
        }
        when (val state = viewModel.state) {
            EventState.Idle -> Unit
            EventState.Loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            EventState.NotFound -> Text("No se encontró un evento para esa OP.")
            is EventState.Error -> {
                Text(state.message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = submit) { Text("Reintentar") }
            }
            is EventState.Found -> {
                HorizontalDivider()
                EventResult(state.event, viewModel::openDocument)
            }
        }
    }
}

@Composable
private fun EventResult(event: EventDetails, openDocument: (java.net.URI) -> Unit) {
    EventField("OP", event.opi.toString())
    EventField("Nombre del evento", event.name)
    EventField("Contacto", event.contact.ifBlank { "Sin contacto registrado" })
    event.approvedVersion?.let { EventField("Versión aprobada", it.toString()) }
    Text("Documento de aprobación", style = MaterialTheme.typography.labelLarge)
    if (event.documentUrl == null) {
        Text("Sin documento de aprobación")
    } else {
        Button(onClick = { openDocument(event.documentUrl) }) { Text("Ver documento") }
    }
}

@Composable
private fun EventField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
