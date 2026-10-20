package co.edu.unal.appempresa

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

val categorias = listOf("Consultoría", "Desarrollo a la medida", "Fábrica de software")
data class Empresa(val id: Long = 0, val nombre: String, val web: String, val telefono: String, val email: String, val productos: String, val tipos: Set<Int>)
class EmpresaDb(context: Context, nombreDb: String = "empresas.db") : SQLiteOpenHelper(context, nombreDb, null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE empresas (id INTEGER PRIMARY KEY, nombre TEXT NOT NULL, url_web TEXT NOT NULL, telefono TEXT NOT NULL, email TEXT NOT NULL, productos_servicios TEXT NOT NULL, consultoria INTEGER NOT NULL, desarrollo_medida INTEGER NOT NULL, fabrica_software INTEGER NOT NULL)""")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { error("Migración pendiente: $oldVersion a $newVersion") }
    fun listar(): List<Empresa> = buildList {
        readableDatabase.query("empresas", null, null, null, null, null, "nombre COLLATE NOCASE, id").use { c ->
            fun texto(nombre: String) = c.getString(c.getColumnIndexOrThrow(nombre))
            while(c.moveToNext()) add(Empresa(c.getLong(0), texto("nombre"), texto("url_web"), texto("telefono"), texto("email"), texto("productos_servicios"), (0..2).filter { c.getInt(6 + it) == 1 }.toSet()))
        }
    }
    fun guardar(e: Empresa) {
        val valores = ContentValues().apply {
            put("nombre", e.nombre); put("url_web", e.web); put("telefono", e.telefono); put("email", e.email); put("productos_servicios", e.productos)
            listOf("consultoria", "desarrollo_medida", "fabrica_software").forEachIndexed { i, campo -> put(campo, if(i in e.tipos) 1 else 0) }
        }
        if(e.id == 0L) writableDatabase.insertOrThrow("empresas", null, valores)
        else check(writableDatabase.update("empresas", valores, "id = ?", arrayOf(e.id.toString())) == 1)
    }
    fun eliminar(id: Long) { check(writableDatabase.delete("empresas", "id = ?", arrayOf(id.toString())) == 1) }
}

