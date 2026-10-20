package co.edu.unal.appempresa

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class FlujoEmpresaTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val nombre = "ZZ Prueba automatizada 84219"
    private val editado = "$nombre editada"
    private fun texto(t: String) = ui.onNodeWithText(t)
    private fun pulsar(t: String) { texto(t).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() } }
    private fun esperar(t: String) { ui.waitUntil(10000) { runCatching { ui.onAllNodesWithText(t).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) } }
    @Test fun flujoCompleto() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = EmpresaDb(context)
        val originales = db.listar().filterNot { it.nombre.startsWith(nombre) }
        try {
            ui.activityRule.scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            ui.waitForIdle()
            esperar("Directorio de empresas")
            texto("+").performClick()
            pulsar("Guardar empresa")
            texto("Completa todos los campos.").assertExists()
            texto("Nombre de la empresa").performScrollTo().performTextInput(nombre)
            texto("Página web (https://...)").performScrollTo().performTextInput("https://example.com")
            texto("Teléfono de contacto").performScrollTo().performTextInput("+57 300 1234567")
            texto("Correo electrónico").performScrollTo().performTextInput("incorrecto")
            texto("Productos y servicios").performScrollTo().performTextInput("Aplicaciones móviles de prueba")
            pulsar("Guardar empresa")
            texto("Ingresa un correo válido.").assertExists()
            texto("Correo electrónico").performScrollTo().performTextReplacement("prueba@example.com")
            pulsar("Guardar empresa")
            texto("Selecciona al menos una clasificación.").assertExists()
            pulsar("Consultoría")
            pulsar("Fábrica de software")
            pulsar("Guardar empresa")
            esperar("Directorio de empresas")
            texto("Buscar por nombre").performTextReplacement(nombre)
            esperar(nombre)
            texto("Desarrollo a la medida").performClick()
            texto("No hay coincidencias con los filtros.").assertExists()
            texto("Consultoría").performClick()
            esperar(nombre)
            ui.onNode(hasText(nombre) and !hasSetTextAction()).performScrollTo().performClick()
            texto("prueba@example.com").assertExists()
            pulsar("Editar empresa")
            texto("Nombre de la empresa").performScrollTo().performTextReplacement(editado)
            pulsar("Guardar empresa")
            esperar("Directorio de empresas")
            esperar(editado)
            ui.activityRule.scenario.recreate()
            esperar(editado)
            ui.onNode(hasText(editado) and !hasSetTextAction()).performScrollTo().performClick()
            pulsar("Eliminar empresa")
            texto("Cancelar").performClick()
            texto(editado).assertExists()
            pulsar("Eliminar empresa")
            texto("Eliminar").performClick()
            esperar("Directorio de empresas")
            ui.waitUntil(10000) { db.listar().none { it.nombre == editado } }
            assertEquals(originales, db.listar())
            texto("Buscar por nombre").performTextClearance()
            texto("Todas").performClick()
        } catch (fallo: Throwable) {
            android.util.Log.e("FlujoReto", runCatching { ui.onRoot().printToString() }.getOrDefault("Sin árbol Compose"), fallo)
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap -> java.io.File(context.getExternalFilesDir(null), "fallo.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
            throw fallo
        } finally {
            db.listar().filter { it.nombre.startsWith(nombre) }.forEach { db.eliminar(it.id) }
            db.close()
        }
    }
}





