package com.turnero.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.turnero.app.domain.model.ErrorTurno
import com.turnero.app.domain.model.EstadoTurno
import com.turnero.app.testing.INSTANTE_BASE
import com.turnero.app.testing.InMemoryTurneroDatabaseRule
import com.turnero.app.testing.entidadServicio
import com.turnero.app.testing.finDelDia
import com.turnero.app.testing.inicioDelDia
import com.turnero.app.testing.instanteDeTurno
import com.turnero.app.testing.turno
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `TurnoRepositoryImpl` contra Room de verdad. JUnit 4 a proposito: `androidx.test` esta
 * construido sobre JUnit 4 y no sobre Jupiter (ver AGENTS.md).
 *
 * Nombres de tests en camelCase y no con backticks de la suite de `src/test`: `androidTest`
 * pasa por el dexer, y D8 rechaza los nombres de clase con espacios (ver
 * `TurnoDaoTest`).
 *
 * **Por que existe este archivo.** `FakeTurnoRepository` no implementa el solapamiento y
 * lo dice en su KDoc: un falso que lo reprodujera estaria probando el falso y no el
 * codigo. La regla pura vive en `seSolapan` (testeada en `src/test`); lo que solo se
 * puede verificar contra SQLite es que el repositorio la aplique **y que el chequeo y la
 * escritura corran en la misma transaccion de Room**.
 *
 * Esa atomicidad es lo que estos tests custodian: el codigo historicalmente correcto tenia
 * el chequeo adentro de `withTransaction` y el `insertar` afuera, que es exactamente la
 * doble reserva que la transaccion existe para cerrar. Un `crear` que falla pero igual
 * deja una fila, o dos toques simultaneos que dejan dos filas, son regresiones que ningun
 * test de `src/test` puede ver porque el fake ni siquiera pasa por esa ruta.
 *
 * Ningun test toca la maquina de estados: las transiciones se escriben directamente desde
 * aca porque lo que se prueba es el chequeo de solapamiento de `cambiarEstado`, no la
 * matriz de transiciones (esa esta testeada en `src/test`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TurnoRepositoryImplTest {

    @get:Rule
    val baseDeDatos = InMemoryTurneroDatabaseRule()

    // `lazy` y no inicializacion directa: la regla abre la base en `starting`, que corre
    // despues de construir la instancia del test.
    private val repositorio: TurnoRepositoryImpl by lazy {
        TurnoRepositoryImpl(baseDeDatos.turnoDao, baseDeDatos.db)
    }

    // ---------------------------------------------------------------- crear

    @Test
    fun crearConUnaHoraLibreInsertaElTurno() = runTest {
        val nuevo = conServicio(turno(inicio = instanteDeTurno(10), duracionMin = 30))

        val resultado = repositorio.crear(nuevo)

        assertEquals(nuevo.id, resultado.getOrNull())
        assertEquals(1, contarFilas())
        // La agenda del dia sale del JOIN con `servicios` (que no filtra deletedAt): sin la
        // fila de servicio el turno no apareceria, aunque este insertado.
        val delDia = repositorio.observarDelDia(inicioDelDia(), finDelDia()).first()
        assertEquals(listOf(nuevo.id), delDia.map { it.turno.id })
        assertEquals("Corte", delDia.single().servicioNombre)
    }

    @Test
    fun crearFallaConSolapamientoSiLaHoraYaEstaOcupada() = runTest {
        val primero = turno(inicio = instanteDeTurno(10), duracionMin = 30)
        repositorio.crear(primero).getOrThrow()

        val segundo = turno(inicio = instanteDeTurno(10), duracionMin = 30)
        val resultado = repositorio.crear(segundo)

        assertEquals(ErrorTurno.Solapamiento, resultado.exceptionOrNull())
        assertEquals("El rechazo no puede dejar una fila a medias", 1, contarFilas())
    }

    @Test
    fun dosCrearConsecutivosSobreLaMismaHoraNoDejanDosFilas() = runTest {
        val primero = turno(inicio = instanteDeTurno(10), duracionMin = 30)
        val segundo = turno(inicio = instanteDeTurno(10), duracionMin = 30)

        val primerResultado = repositorio.crear(primero)
        val segundoResultado = repositorio.crear(segundo)

        assertTrue(primerResultado.isSuccess)
        assertEquals(ErrorTurno.Solapamiento, segundoResultado.exceptionOrNull())
        assertEquals(1, contarFilas())
    }

    @Test
    fun dosCrearSimultaneosSobreLaMismaHoraSoloDejanUnaFila() = runTest {
        val primero = turno(inicio = instanteDeTurno(10), duracionMin = 30)
        val segundo = turno(inicio = instanteDeTurno(10), duracionMin = 30)

        // El doble toque real: dos `crear` que no esperan al que viene, en paralelo. Room
        // serializa las transacciones, asi que con el chequeo y el insert en el mismo
        // bloque exactamente uno de los dos ve la hora ocupada. Si el insert corriera
        // afuera de la transaccion, los dos podrian leer "libre" y escribir igual.
        val resultados = listOf(
            async(Dispatchers.Default) { repositorio.crear(primero) },
            async(Dispatchers.Default) { repositorio.crear(segundo) },
        ).awaitAll()

        assertEquals("Tiene que ganar exactamente uno", 1, resultados.count { it.isSuccess })
        val falla = resultados.filter { it.isFailure }.single()
        assertEquals(ErrorTurno.Solapamiento, falla.exceptionOrNull())
        assertEquals(1, contarFilas())
    }

    // ---------------------------------------------------------------- actualizar

    @Test
    fun actualizarPermiteEditarseASiMismoSinChocarConsigoMismo() = runTest {
        val existente = turno(inicio = instanteDeTurno(10), duracionMin = 30)
        repositorio.crear(existente).getOrThrow()

        val editado = existente.copy(
            notas = "Confirmado por WhatsApp",
            updatedAt = INSTANTE_BASE.plusSeconds(60),
        )
        val resultado = repositorio.actualizar(editado)

        // Sin excluir al propio id, el turno se solaparia consigo mismo y no se podria
        // editar ni siquiera las notas sin mover la hora.
        assertTrue(resultado.isSuccess)
        val leido = repositorio.obtenerPorId(existente.id).getOrNull()
        assertEquals("Confirmado por WhatsApp", leido?.notas)
        assertEquals(instanteDeTurno(10), leido?.inicio)
    }

    @Test
    fun actualizarRechazaMoverseAUnaFranjaOcupadaPorOtro() = runTest {
        val primero = turno(inicio = instanteDeTurno(10), duracionMin = 30)
        val segundo = turno(inicio = instanteDeTurno(11), duracionMin = 30)
        repositorio.crear(primero).getOrThrow()
        repositorio.crear(segundo).getOrThrow()

        val resultado = repositorio.actualizar(segundo.copy(inicio = instanteDeTurno(10)))

        assertEquals(ErrorTurno.Solapamiento, resultado.exceptionOrNull())
        // Y no se movio: el fallo revirtio la transaccion entera.
        val leido = repositorio.obtenerPorId(segundo.id).getOrNull()
        assertEquals(instanteDeTurno(11), leido?.inicio)
    }

    // ---------------------------------------------------------------- reabrir un cancelado

    @Test
    fun reabrirUnCanceladoSobreUnaHoraYaOcupadaFallaConSolapamiento() = runTest {
        val anulado = turno(inicio = instanteDeTurno(10), duracionMin = 30)
        repositorio.crear(anulado).getOrThrow()
        repositorio.cambiarEstado(anulado.id, EstadoTurno.CANCELADO, INSTANTE_BASE.plusSeconds(60))
            .getOrThrow()
        // Mientras estuvo CANCELADO la hora quedo libre y otro tomo su lugar: esa es la
        // puerta trasera que el chequeo de `cambiarEstado` tiene que cerrar.
        val ocupante = turno(inicio = instanteDeTurno(10), duracionMin = 30, estado = EstadoTurno.CONFIRMADO)
        repositorio.crear(ocupante).getOrThrow()

        val resultado = repositorio.cambiarEstado(
            anulado.id,
            EstadoTurno.PENDIENTE,
            INSTANTE_BASE.plusSeconds(120),
        )

        assertEquals(ErrorTurno.Solapamiento, resultado.exceptionOrNull())
        assertEquals(
            "El estado no puede haber cambiado si el chequeo fallo",
            EstadoTurno.CANCELADO,
            repositorio.obtenerPorId(anulado.id).getOrNull()?.estado,
        )
        assertEquals(2, contarFilas())
    }

    @Test
    fun reabrirUnCanceladoAConfirmadoSobreUnaHoraOcupadaFallaConSolapamiento() = runTest {
        val anulado = turno(inicio = instanteDeTurno(10), duracionMin = 30)
        repositorio.crear(anulado).getOrThrow()
        repositorio.cambiarEstado(anulado.id, EstadoTurno.CANCELADO, INSTANTE_BASE.plusSeconds(60))
            .getOrThrow()
        val ocupante = turno(inicio = instanteDeTurno(10), duracionMin = 30, estado = EstadoTurno.CONFIRMADO)
        repositorio.crear(ocupante).getOrThrow()

        val resultado = repositorio.cambiarEstado(
            anulado.id,
            EstadoTurno.CONFIRMADO,
            INSTANTE_BASE.plusSeconds(120),
        )

        assertEquals(ErrorTurno.Solapamiento, resultado.exceptionOrNull())
        assertEquals(
            EstadoTurno.CANCELADO,
            repositorio.obtenerPorId(anulado.id).getOrNull()?.estado,
        )
    }

    @Test
    fun reabrirUnCanceladoEnUnaHoraLibreSiFunciona() = runTest {
        // Control de los dos tests anteriores: sin este, un `cambiarEstado` que fallara
        // siempre pasaria la prueba de rechazo.
        val anulado = turno(inicio = instanteDeTurno(10), duracionMin = 30)
        repositorio.crear(anulado).getOrThrow()
        repositorio.cambiarEstado(anulado.id, EstadoTurno.CANCELADO, INSTANTE_BASE.plusSeconds(60))
            .getOrThrow()
        val enOtraHora = turno(inicio = instanteDeTurno(11), duracionMin = 30)
        repositorio.crear(enOtraHora).getOrThrow()

        val resultado = repositorio.cambiarEstado(
            anulado.id,
            EstadoTurno.PENDIENTE,
            INSTANTE_BASE.plusSeconds(120),
        )

        assertTrue(resultado.isSuccess)
        assertEquals(
            EstadoTurno.PENDIENTE,
            repositorio.obtenerPorId(anulado.id).getOrNull()?.estado,
        )
    }

    // ---------------------------------------------------------------- eliminar

    @Test
    fun eliminarEsUnSoftDeleteLaFilaSiguePeroSaleDeLaAgenda() = runTest {
        val existente = conServicio(turno(inicio = instanteDeTurno(10), duracionMin = 30))
        repositorio.crear(existente).getOrThrow()

        val resultado = repositorio.eliminar(existente.id, INSTANTE_BASE.plusSeconds(60))

        assertTrue(resultado.isSuccess)
        assertEquals("La fila no se destruye: habilita el sync futuro", 1, contarFilas())
        assertEquals(1, contarFilasEliminadas())
        assertTrue(repositorio.observarDelDia(inicioDelDia(), finDelDia()).first().isEmpty())
    }

    /**
     * Inserta el servicio al que apunta el turno, porque las lecturas de agenda resuelven
     * el nombre con un `INNER JOIN` a `servicios` (que no filtra `deletedAt`).
     *
     * Es la misma regla que `TurnoDaoTest`: un turno sin servicio es un dato que la agenda
     * no sabe proyectar, y los tests de solapamiento que no leen la agenda no lo necesitan.
     */
    private suspend fun conServicio(turno: com.turnero.app.domain.model.Turno): com.turnero.app.domain.model.Turno {
        baseDeDatos.dao.insertar(
            entidadServicio(id = turno.servicioId, nombre = "Corte"),
        )
        return turno
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Cuenta **todas** las filas de `turnos`, incluidas las borradas: el DAO esconde las
     * borradas, y justamente lo que hay que verificar es que siguen ahi.
     */
    private fun contarFilas(): Int =
        baseDeDatos.db.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM turnos")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getInt(0)
            }

    private fun contarFilasEliminadas(): Int =
        baseDeDatos.db.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM turnos WHERE deletedAt IS NOT NULL")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getInt(0)
            }
}