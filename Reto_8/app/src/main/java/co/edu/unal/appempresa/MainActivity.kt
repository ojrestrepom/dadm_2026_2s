package co.edu.unal.appempresa

import android.os.Bundle
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.edu.unal.appempresa.ui.theme.AppEmpresaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val db = EmpresaDb(applicationContext)
        setContent { AppEmpresaTheme { Directorio(db) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Directorio(db: EmpresaDb) {
    var empresas by remember { mutableStateOf(emptyList<Empresa>()) }
    var busqueda by rememberSaveable { mutableStateOf("") }
    var filtro by rememberSaveable { mutableStateOf(-1) }
    var pantalla by rememberSaveable { mutableStateOf("lista") }
    var id by rememberSaveable { mutableStateOf(0L) }
    var borrar by remember { mutableStateOf<Empresa?>(null) }
    var ocupado by remember { mutableStateOf(false) }
    val mensajes = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun operar(mensaje: String? = null, accion: () -> Unit = {}) {
        scope.launch {
            ocupado = true
            try {
                empresas = withContext(Dispatchers.IO) { accion(); db.listar() }
                if(mensaje != null) pantalla = "lista"
            } catch (_: Exception) {
                ocupado = false
                mensajes.showSnackbar("No se pudo completar la operación. Intenta nuevamente.")
                return@launch
            }
            ocupado = false
            if(mensaje != null) mensajes.showSnackbar(mensaje)
        }
    }
    LaunchedEffect(Unit) { operar() }
    BackHandler(pantalla != "lista") { pantalla = "lista" }
    val seleccion = empresas.find { it.id == id }
    Scaffold(topBar = { TopAppBar(title = { Text(when(pantalla) { "formulario" -> if(id == 0L) "Nueva empresa" else "Editar empresa"; "detalle" -> "Información de empresa"; else -> "Directorio de empresas" }) }) },
        snackbarHost = { SnackbarHost(mensajes) },
        floatingActionButton = { if(pantalla == "lista") FloatingActionButton(onClick = { id = 0; pantalla = "formulario" }) { Text("+", Modifier.padding(12.dp)) } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 16.dp)) {
            if(ocupado) LinearProgressIndicator(Modifier.fillMaxWidth())
            when(pantalla) {
                "formulario" -> Formulario(seleccion, ocupado, { pantalla = "lista" }) { e -> operar("Empresa guardada") { db.guardar(e) } }
                "detalle" -> seleccion?.let { e ->
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = { pantalla = "lista" }) { Text("Volver al directorio") }
                        Text(e.nombre, style = MaterialTheme.typography.headlineSmall)
                        listOf("Página web" to e.web, "Teléfono" to e.telefono, "Correo electrónico" to e.email, "Productos y servicios" to e.productos, "Clasificación" to e.tipos.joinToString { categorias[it] }).forEach { (titulo, valor) ->
                            Text(titulo, style = MaterialTheme.typography.labelLarge); Text(valor)
                        }
                        Button(onClick = { pantalla = "formulario" }, enabled = !ocupado) { Text("Editar empresa") }
                        OutlinedButton(onClick = { borrar = e }, enabled = !ocupado) { Text("Eliminar empresa") }
                        Spacer(Modifier.height(24.dp))
                    }
                }
                else -> {
                    OutlinedTextField(busqueda, { busqueda = it }, label = { Text("Buscar por nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Clasificación", Modifier.padding(top = 12.dp))
                    FilterChip(selected = filtro == -1, onClick = { filtro = -1 }, label = { Text("Todas") })
                    categorias.forEachIndexed { i, nombre -> FilterChip(selected = filtro == i, onClick = { filtro = if(filtro == i) -1 else i }, label = { Text(nombre) }) }
                    val visibles = empresas.filter { it.nombre.contains(busqueda.trim(), true) && (filtro == -1 || filtro in it.tipos) }
                    Text("${visibles.size} empresas", Modifier.padding(vertical = 8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
                        if(visibles.isEmpty()) item { Text(if(empresas.isEmpty()) "Aún no hay empresas. Pulsa + para registrar la primera." else "No hay coincidencias con los filtros.") }
                        items(visibles, key = { it.id }) { e ->
                            OutlinedCard(onClick = { id = e.id; pantalla = "detalle" }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(e.nombre, style = MaterialTheme.typography.titleMedium)
                                    Text(e.tipos.joinToString { categorias[it] }, style = MaterialTheme.typography.bodySmall)
                                    Text(e.telefono)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    borrar?.let { e -> AlertDialog(onDismissRequest = { borrar = null }, title = { Text("Eliminar empresa") }, text = { Text("¿Deseas eliminar a ${e.nombre}? Esta acción no se puede deshacer.") },
        confirmButton = { TextButton(onClick = { borrar = null; operar("Empresa eliminada") { db.eliminar(e.id) } }) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = { borrar = null }) { Text("Cancelar") } }) }
}

@Composable
private fun Formulario(e: Empresa?, ocupado: Boolean, cancelar: () -> Unit, guardar: (Empresa) -> Unit) {
    var nombre by rememberSaveable(e?.id) { mutableStateOf(e?.nombre ?: "") }
    var web by rememberSaveable(e?.id) { mutableStateOf(e?.web ?: "") }
    var telefono by rememberSaveable(e?.id) { mutableStateOf(e?.telefono ?: "") }
    var email by rememberSaveable(e?.id) { mutableStateOf(e?.email ?: "") }
    var productos by rememberSaveable(e?.id) { mutableStateOf(e?.productos ?: "") }
    var tipos by rememberSaveable(e?.id) { mutableStateOf(e?.tipos?.toList() ?: emptyList<Int>()) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Completa todos los campos y selecciona al menos una clasificación.")
        OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre de la empresa") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(web, { web = it }, label = { Text("Página web (https://...)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(telefono, { telefono = it }, label = { Text("Teléfono de contacto") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it }, label = { Text("Correo electrónico") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(productos, { productos = it }, label = { Text("Productos y servicios") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        Text("Clasificación", style = MaterialTheme.typography.titleMedium)
        categorias.forEachIndexed { i, titulo ->
            Row(Modifier.fillMaxWidth().clickable { tipos = if(i in tipos) tipos - i else tipos + i }, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(i in tipos, onCheckedChange = null); Text(titulo, Modifier.padding(start = 8.dp))
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            error = when {
                listOf(nombre, web, telefono, email, productos).any { it.isBlank() } -> "Completa todos los campos."
                !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Ingresa un correo válido."
                !Patterns.WEB_URL.matcher(web.trim()).matches() || !(web.trim().startsWith("https://") || web.trim().startsWith("http://")) -> "Ingresa una URL válida con https:// o http://."
                tipos.isEmpty() -> "Selecciona al menos una clasificación."
                else -> null
            }
            if(error == null) guardar(Empresa(e?.id ?: 0, nombre.trim(), web.trim(), telefono.trim(), email.trim(), productos.trim(), tipos.toSet()))
        }, enabled = !ocupado, modifier = Modifier.fillMaxWidth()) { Text("Guardar empresa") }
        OutlinedButton(onClick = cancelar, enabled = !ocupado, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
        Spacer(Modifier.height(24.dp))
    }
}

