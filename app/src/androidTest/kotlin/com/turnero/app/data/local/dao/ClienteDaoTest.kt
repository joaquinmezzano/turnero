package com.turnero.app.data.local.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.turnero.app.testing.INSTANTE_BASE
import com.turnero.app.testing.InMemoryTurneroDatabaseRule
import com.turnero.app.testing.entidadCliente
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
import java.util.UUID

/**
 * Tests del DAO contra Room de verdad. JUnit 4 a propósito: `androidx.test` está
 * construido sobre JUnit 4 y no sobre Jupiter (ver AGENTS.md).
 *
 * El foco es el soft delete y el orden. La regla de oro del proyecto es que **toda**
 * `@Query` filtre `deletedAt IS NULL`: el bug más fácil de introducir al agregar una
 * consulta y el más difícil de ver en la UI, porque la fila borrada aparece sin ningún
 * síntoma raro. Por eso los tests de `observarTodos` y `obtenerPorId` cubren el caso
 * borrado, no solo el vivo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ClienteDaoTest {

    @get:Rule
    val baseDeDatos = InMemoryTurneroDatabaseRule()

    private val dao: ClienteDao get() = baseDeDatos.clienteDao

    // ---------------------------------------------------------------- soft delete

    @Test
    fun unClienteMarcadoComoEliminadoDesapareceDeObservarTodos() = runTest {
        val ana = entidadCliente(nombre = "Pérez, Ana")
        dao.insertar(ana)

        dao.marcarEliminado(ana.id, INSTANTE_BASE.plusSeconds(60))

        val vivos = dao.observarTodos().first()
        assertTrue("Todavía devuelve el cliente eliminado", vivos.none { it.id == ana.id })
    }

    @Test
    fun unClienteEliminadoSigueEnLaTablaConDeletedAtInformado() = runTest {
        val ana = entidadCliente(nombre = "Pérez, Ana")
        val momento = INSTANTE_BASE.plusSeconds(60)
        dao.insertar(ana)

        dao.marcarEliminado(ana.id, momento)

        // La fila **no** se borra: es lo que habilita el sync futuro. Se lee por SQL crudo
        // porque el DAO, justamente, esconde las filas borradas.
        assertEquals(momento.toEpochMilli(), leerColumna("deletedAt"))
    }

    @Test
    fun unClienteEliminadoNoApareceEnObtenerPorId() = runTest {
        val ana = entidadCliente(nombre = "Pérez, Ana")
        dao.insertar(ana)

        dao.marcarEliminado(ana.id, INSTANTE_BASE)

        assertNull(dao.obtenerPorId(ana.id))
    }

    @Test
    fun marcarComoEliminadoDevuelveCuantasFilasAfecto() = runTest {
        val ana = entidadCliente(nombre = "Pérez, Ana")
        dao.insertar(ana)

        val primera = dao.marcarEliminado(ana.id, INSTANTE_BASE)
        val segunda = dao.marcarEliminado(ana.id, INSTANTE_BASE.plusSeconds(60))

        // La segunda tiene que decir 0: el `WHERE deletedAt IS NULL` impide reescribir la
        // marca de una baja ya hecha. Sin eso, `updatedAt` y `deletedAt` mentirían.
        assertEquals(1, primera)
        assertEquals(0, segunda)
    }

    @Test
    fun marcarComoEliminadoUnIdInexistenteDevuelveCero() = runTest {
        val resultado = dao.marcarEliminado(UUID.randomUUID(), INSTANTE_BASE)

        assertEquals(0, resultado)
    }

    // ---------------------------------------------------------------- lectura

    @Test
    fun unClienteQueExisteSeDevuelveCompleto() = runTest {
        val ana = entidadCliente(nombre = "Pérez, Ana", notas = "Viene los sábados")
        dao.insertar(ana)

        val leido = dao.obtenerPorId(ana.id)

        assertEquals(ana, leido)
    }

    @Test
    fun unIdQueNoExisteDevuelveNull() = runTest {
        assertNull(dao.obtenerPorId(UUID.randomUUID()))
    }

    @Test
    fun observarTodosEmiteEnOrdenAlfabetico() = runTest {
        // El `ORDER BY nombre COLLATE NOCASE` está en el DAO, no en el use case: el listado
        // tiene que salir ordenado aunque el flujo se consuma desde otro lado.
        dao.insertar(entidadCliente(nombre = "Zeta"))
        dao.insertar(entidadCliente(nombre = "Álvarez"))
        dao.insertar(entidadCliente(nombre = "Mikes"))

        val nombres = dao.observarTodos().first().map { it.nombre }

        // `COLLATE NOCASE` solo mira ASCII, así que "Álvarez" se ordena por la A acentuada
        // (fuera de A-Z) y queda al final. No es el orden de diccionario español: por eso
        // el filtro del use case reordena con `ordenandoPor` cuando hay criterio.
        assertEquals(listOf("Mikes", "Zeta", "Álvarez"), nombres)
    }

    @Test
    fun observarTodosEmiteDeNuevoCuandoCambiaLaTabla() = runTest {
        dao.insertar(entidadCliente(nombre = "Ana"))

        val primera = dao.observarTodos().first()

        dao.insertar(entidadCliente(nombre = "Tomás"))

        val segunda = dao.observarTodos().first()
        assertNotEquals(primera, segunda)
        assertEquals(2, segunda.size)
    }

    // ---------------------------------------------------------------- escritura

    @Test
    fun actualizarCambiaLosCamposEditables() = runTest {
        val ana = entidadCliente(nombre = "Pérez, Ana")
        dao.insertar(ana)
        val editada = ana.copy(nombre = "Pérez, Ana María", telefono = null)

        assertEquals(1, dao.actualizar(editada))

        val leida = dao.obtenerPorId(ana.id)
        assertEquals("Pérez, Ana María", leida?.nombre)
        assertNull(leida?.telefono)
    }

    @Test
    fun actualizarNoDevuelveFilaSiElIdNoExiste() = runTest {
        val fantasma = entidadCliente(nombre = "Fantasma")

        // 0 y no excepción: el repositorio decide qué hacer con eso, y el ViewModel ya
        // verificó que el cliente existe antes de editar.
        assertEquals(0, dao.actualizar(fantasma))
    }

    @Test
    fun unClienteNuevoNoTieneDeletedAtInformado() = runTest {
        dao.insertar(entidadCliente(nombre = "Ana"))

        assertNull(dao.observarTodos().first().single().deletedAt)
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Lee una columna de la fila de `clientes` pasando por SQL crudo.
     *
     * Necesario porque el DAO filtra `deletedAt IS NULL` y justamente lo que hay que
     * verificar es la fila que el DAO esconde. La tabla tiene una sola fila en estos tests,
     * así que no hace falta un `WHERE`.
     */
    private fun leerColumna(columna: String): Long? =
        baseDeDatos.db.openHelper.readableDatabase
            .query("SELECT $columna FROM clientes")
            .use { cursor ->
                assertEquals("Se esperaba exactamente una fila", 1, cursor.count)
                assertTrue(cursor.moveToFirst())
                if (cursor.isNull(0)) null else cursor.getLong(0)
            }
}