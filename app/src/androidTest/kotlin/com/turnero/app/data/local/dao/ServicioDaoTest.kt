package com.turnero.app.data.local.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.turnero.app.testing.InMemoryTurneroDatabaseRule
import com.turnero.app.testing.INSTANTE_BASE
import com.turnero.app.testing.entidadServicio
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

/**
 * Tests del DAO contra Room de verdad. JUnit 4 a proposito: `androidx.test` esta
 * construido sobre JUnit 4 y no sobre Jupiter (ver AGENTS.md).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ServicioDaoTest {

    @get:Rule
    val baseDeDatos = InMemoryTurneroDatabaseRule()

    private val dao: ServicioDao get() = baseDeDatos.dao

    // ---------------------------------------------------------------- soft delete

    @Test
    fun unServicioMarcadoComoEliminadoDesapareceDeObservarTodos() = runTest {
        val corte = entidadServicio(nombre = "Corte")
        dao.insertar(corte)

        dao.marcarEliminado(corte.id, INSTANTE_BASE.plusSeconds(60))

        val vivos = dao.observarTodos().first()
        assertTrue("Todavia devuelve el servicio eliminado", vivos.none { it.id == corte.id })
    }

    @Test
    fun unServicioEliminadoSigueEnLaTablaConDeletedAtInformado() = runTest {
        val corte = entidadServicio(nombre = "Corte")
        val momento = INSTANTE_BASE.plusSeconds(60)
        dao.insertar(corte)

        dao.marcarEliminado(corte.id, momento)

        // El borrado es logico: la fila NO se borra, se le pone deletedAt. Es lo que
        // habilita el sync futuro, y por eso `INSERT OR REPLACE` / `DELETE` aqui seria
        // una regresion silenciosa.
        assertEquals(1, contarFilas())
        assertEquals(momento.toEpochMilli(), leerColumna("deletedAt"))
    }

    @Test
    fun marcarEliminadoActualizaTambienUpdatedAt() = runTest {
        val corte = entidadServicio(nombre = "Corte")
        dao.insertar(corte)

        dao.marcarEliminado(corte.id, INSTANTE_BASE.plusSeconds(60))

        assertEquals(INSTANTE_BASE.plusSeconds(60).toEpochMilli(), leerColumna("updatedAt"))
    }

    @Test
    fun observarTodosDevuelveSoloLosServiciosVivos() = runTest {
        val corte = entidadServicio(nombre = "Corte")
        val coloracion = entidadServicio(nombre = "Coloracion")
        val barba = entidadServicio(nombre = "Barba")
        dao.insertar(corte)
        dao.insertar(coloracion)
        dao.insertar(barba)

        dao.marcarEliminado(barba.id, INSTANTE_BASE.plusSeconds(60))

        assertEquals(
            listOf("Coloracion", "Corte"),
            dao.observarTodos().first().map { it.nombre },
        )
    }

    @Test
    fun obtenerPorIdDevuelveNullParaUnServicioEliminado() = runTest {
        val corte = entidadServicio(nombre = "Corte")
        dao.insertar(corte)

        dao.marcarEliminado(corte.id, INSTANTE_BASE.plusSeconds(60))

        assertNull(dao.obtenerPorId(corte.id))
    }

    @Test
    fun marcarEliminadoEsIdempotenteYNoPisaLaFechaOriginal() = runTest {
        val corte = entidadServicio(nombre = "Corte")
        dao.insertar(corte)
        val primerMomento = INSTANTE_BASE.plusSeconds(60)

        val primera = dao.marcarEliminado(corte.id, primerMomento)
        val segunda = dao.marcarEliminado(corte.id, INSTANTE_BASE.plusSeconds(7_200))

        assertEquals("La primera baja tiene que tocar una fila", 1, primera)
        assertEquals("La segunda baja no deberia tocar ninguna", 0, segunda)
        assertEquals(primerMomento.toEpochMilli(), leerColumna("deletedAt"))
    }

    @Test
    fun marcarEliminadoDeUnIdInexistenteNoTocaNingunaFila() = runTest {
        val corte = entidadServicio(nombre = "Corte")
        dao.insertar(corte)

        val afectadas = dao.marcarEliminado(UUID.randomUUID(), INSTANTE_BASE.plusSeconds(60))

        assertEquals(0, afectadas)
        assertNull(leerColumna("deletedAt"))
    }

    // ---------------------------------------------------------------- orden

    @Test
    fun observarTodosOrdenaPorNombreSinDistinguirMayusculas() = runTest {
        // Se insertan fuera de orden y con mayusculas mezcladas a proposito: en orden
        // binario (BINARY, el default de SQLite) el resultado seria el inverso, asi que
        // el test distingue de verdad el `COLLATE NOCASE` de la query.
        dao.insertar(entidadServicio(nombre = "Perro"))
        dao.insertar(entidadServicio(nombre = "gato"))
        dao.insertar(entidadServicio(nombre = "Azul"))

        val nombres = dao.observarTodos().first().map { it.nombre }

        assertEquals(listOf("Azul", "gato", "Perro"), nombres)
    }

    @Test
    fun unNombreConTildeSeOrdenaDespuesDeTodosLosAscii() = runTest {
        // LIMITACION CONOCIDA, no comportamiento deseado: el `COLLATE NOCASE` de SQLite
        // solo pliega A-Z de ASCII, no acentos. Como los bytes UTF-8 de una vocal
        // acentuada son mayores que los de cualquier letra ASCII, "Ambar" con tilde
        // queda al final del catalogo en vez de ser el primero.
        // Si alguien arregla el orden (columna `nombreOrden`, `ICU`, `COLLATE RTRIM`
        // + ICU, etc.) este test es el que hay que actualizar, y hay que leerlo antes
        // de borrarlo.
        dao.insertar(entidadServicio(nombre = "zebra"))
        dao.insertar(entidadServicio(nombre = "\u00c1mbar"))

        val nombres = dao.observarTodos().first().map { it.nombre }

        assertEquals(listOf("zebra", "\u00c1mbar"), nombres)
    }

    // ---------------------------------------------------------------- crud

    @Test
    fun obtenerPorIdDevuelveElServicioConTodosSusCampos() = runTest {
        val original = entidadServicio(
            nombre = "Coloracion",
            duracionMin = 90,
            precioCentavos = 18_500L,
            color = 0xFF00FF00.toInt(),
        )
        dao.insertar(original)

        assertEquals(original, dao.obtenerPorId(original.id))
    }

    @Test
    fun unPrecioCentavosNullSePersisteComoNull() = runTest {
        val sinPrecio = entidadServicio(nombre = "Diagnostico", precioCentavos = null)
        dao.insertar(sinPrecio)

        assertNull(dao.obtenerPorId(sinPrecio.id)?.precioCentavos)
    }

    @Test
    fun insertarDevuelveUnRowidValidoYNoMenosUno() = runTest {
        val corte = entidadServicio(nombre = "Corte")

        val rowid = dao.insertar(corte)

        assertNotEquals("El repositorio trata -1 como error de Room", -1L, rowid)
    }

    @Test
    fun actualizarDevuelveUnoYSobreescribeLasColumnas() = runTest {
        val corte = entidadServicio(nombre = "Corte", duracionMin = 30, precioCentavos = 5_000L)
        dao.insertar(corte)

        val afectadas = dao.actualizar(
            corte.copy(nombre = "Corte corto", duracionMin = 20, precioCentavos = 4_000L, color = 1),
        )

        assertEquals(1, afectadas)
        val guardada = dao.obtenerPorId(corte.id)
        assertEquals("Corte corto", guardada?.nombre)
        assertEquals(20, guardada?.duracionMin)
        assertEquals(4_000L, guardada?.precioCentavos)
        assertEquals(1, guardada?.color)
    }

    @Test
    fun actualizarDevuelveCeroParaUnIdQueNoExiste() = runTest {
        val corte = entidadServicio(nombre = "Corte")

        // El repositorio usa esto para detectar el fallo: un 0 significa que el
        // `@Update` no matcheo ninguna fila.
        assertEquals(0, dao.actualizar(corte))
        assertEquals(0, contarFilas())
    }

    // ---------------------------------------------------------------- converters

    @Test
    fun elConverterDeInstantSobreviveElRoundTripPorLaBase() = runTest {
        val instante = Instant.parse("2026-03-14T10:15:30.123Z")
        val corte = entidadServicio(nombre = "Corte", createdAt = instante, updatedAt = instante)
        dao.insertar(corte)

        assertEquals(instante, dao.obtenerPorId(corte.id)?.createdAt)
    }

    @Test
    fun elConverterDeInstantGuardaPrecisionDeMilisegundos() = runTest {
        // La columna `createdAt` es INTEGER: se persiste `toEpochMilli()`. Los
        // nanosegundos no caben, asi que se truncan. Documentado aqui para que nadie
        // escriba un test que compare `Instant.now()` con nanos y se crea que es un bug
        // del converter.
        val conNanos = Instant.parse("2026-03-14T10:15:30.123456789Z")
        val corte = entidadServicio(nombre = "Corte", createdAt = conNanos)
        dao.insertar(corte)

        assertEquals(
            Instant.parse("2026-03-14T10:15:30.123Z"),
            dao.obtenerPorId(corte.id)?.createdAt,
        )
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Lee una columna de la fila de `servicios` pasando por SQL crudo.
     *
     * Necesario porque el DAO filtra `deletedAt IS NULL` y justamente lo que hay que
     * verificar es la fila que el DAO esconde. La tabla tiene una sola fila en estos
     * tests, asi que no hace falta un `WHERE`.
     */
    private fun leerColumna(columna: String): Long? =
        baseDeDatos.db.openHelper.readableDatabase
            .query("SELECT $columna FROM servicios")
            .use { cursor ->
                assertEquals("Se esperaba exactamente una fila", 1, cursor.count)
                assertTrue(cursor.moveToFirst())
                if (cursor.isNull(0)) null else cursor.getLong(0)
            }

    private fun contarFilas(): Int =
        baseDeDatos.db.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM servicios")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getInt(0)
            }
}