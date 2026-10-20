package co.edu.unal.appempresa

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class EmpresaDbTest {
    @Test
    fun crudConservaIdentificadoresYPersistencia() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("empresas_test.db")
        var db = EmpresaDb(context, "empresas_test.db")
        try {
            val empresa = Empresa(nombre = "Empresa de prueba", web = "https://example.com", telefono = "+57 300 1234567", email = "contacto@example.com", productos = "Aplicaciones móviles", tipos = setOf(0, 2))
            db.guardar(empresa)
            db.guardar(empresa.copy(nombre = "Segunda empresa", tipos = setOf(1)))
            val primera = db.listar().first { it.nombre == empresa.nombre }
            val segunda = db.listar().first { it.nombre == "Segunda empresa" }
            db.close()
            db = EmpresaDb(context, "empresas_test.db")
            assertEquals(2, db.listar().size)
            assertEquals(setOf(0, 2), db.listar().first { it.id == primera.id }.tipos)
            db.guardar(primera.copy(nombre = "Empresa editada", tipos = setOf(1, 2)))
            assertEquals(setOf(1, 2), db.listar().first { it.id == primera.id }.tipos)
            db.eliminar(primera.id)
            assertEquals(segunda, db.listar().single())
        } finally {
            db.close()
            context.deleteDatabase("empresas_test.db")
        }
    }
}

