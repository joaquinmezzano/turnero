package com.turnero.app.domain.usecase

import com.turnero.app.domain.model.AccionTurno
import com.turnero.app.domain.model.ErrorTurno
import com.turnero.app.domain.model.EstadoTurno
import com.turnero.app.domain.model.fin
import com.turnero.app.testing.FakeClockProvider
import com.turnero.app.testing.FakeTurnoRepository
import com.turnero.app.testing.instanteDeTurno
import com.turnero.app.testing.turno
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Ejecucion de acciones sobre un turno.
 *
 * El turno por defecto arranca a las 10:00 y dura 30 minutos, asi que su fin es 10:30 hora
 * Argentina. Con el reloj en las 10:00 el turno esta en pre-horario; moviendo el reloj a las
 * 10:31, en post-horario. Que transiciones valen en cada caso esta en
 * `MaquinaEstadosTurnoTest`: aca se prueba que el use case **consulta** esa funcion y no la
 * reimplementa.
 */
class CambiarEstadoTurnoUseCaseTest {

    private val turnos = FakeTurnoRepository()
    private val reloj = FakeClockProvider(instanteDeTurno(hora = 10))
    private val useCase = CambiarEstadoTurnoUseCase(turnos, reloj)

    /** Turno de las 10:00 a las 10:30, en el estado que cada test necesita. */
    private fun turnoEnCurso(estado: EstadoTurno = EstadoTurno.PENDIENTE) =
        turno(estado = estado, inicio = instanteDeTurno(hora = 10), duracionMin = 30)

    // ------------------------------------------------------------------ transiciones validas

    @Test
    fun `CONFIRMAR lleva el turno a CONFIRMADO`() = runTest {
        val pendiente = turnoEnCurso()
        turnos.guardar(pendiente)

        useCase(pendiente.id, AccionTurno.CONFIRMAR)

        assertEquals(EstadoTurno.CONFIRMADO, turnos.almacenado(pendiente.id).estado)
    }

    @Test
    fun `MARCAR_ATENDIDO lleva el turno a ATENDIDO`() = runTest {
        val confirmado = turnoEnCurso(EstadoTurno.CONFIRMADO)
        turnos.guardar(confirmado)

        useCase(confirmado.id, AccionTurno.MARCAR_ATENDIDO)

        assertEquals(EstadoTurno.ATENDIDO, turnos.almacenado(confirmado.id).estado)
    }

    @Test
    fun `MARCAR_AUSENTE solo se acepta post-horario`() = runTest {
        val confirmado = turnoEnCurso(EstadoTurno.CONFIRMADO)
        turnos.guardar(confirmado)
        reloj.instante = confirmado.fin().plusSeconds(1)

        useCase(confirmado.id, AccionTurno.MARCAR_AUSENTE)

        assertEquals(EstadoTurno.AUSENTE, turnos.almacenado(confirmado.id).estado)
    }

    @Test
    fun `CANCELAR lleva el turno a CANCELADO`() = runTest {
        // Desde PENDIENTE no existe CANCELAR: la tabla aprobada solo admite CONFIRMAR o LIBRE
        // desde ahi. Para cancelar, un turno tiene que haber sido confirmado antes. Que
        // PENDIENTE no ofrezca CANCELAR tampoco esta cubierto en `MaquinaEstadosTurnoTest`.
        val confirmado = turnoEnCurso(EstadoTurno.CONFIRMADO)
        turnos.guardar(confirmado)

        useCase(confirmado.id, AccionTurno.CANCELAR)

        assertEquals(EstadoTurno.CANCELADO, turnos.almacenado(confirmado.id).estado)
    }

    @Test
    fun `VOLVER_A_PENDIENTE revierte un cancelado en pre-horario`() = runTest {
        val cancelado = turnoEnCurso(EstadoTurno.CANCELADO)
        turnos.guardar(cancelado)

        useCase(cancelado.id, AccionTurno.VOLVER_A_PENDIENTE)

        assertEquals(EstadoTurno.PENDIENTE, turnos.almacenado(cancelado.id).estado)
    }

    @Test
    fun `LIBERAR hace un soft delete y no cambia el estado`() = runTest {
        val pendiente = turnoEnCurso()
        turnos.guardar(pendiente)

        useCase(pendiente.id, AccionTurno.LIBERAR)

        // El estado sigue siendo PENDIENTE: LIBERAR no es un enum, es un `deletedAt`.
        val guardado = turnos.almacenado(pendiente.id)
        assertEquals(EstadoTurno.PENDIENTE, guardado.estado)
        assertEquals(reloj.instante, guardado.deletedAt)
        assertTrue(turnos.vivos().none { it.id == pendiente.id })
    }

    @Test
    fun `cambiar de estado refresca updatedAt con el reloj`() = runTest {
        val pendiente = turnoEnCurso()
        turnos.guardar(pendiente)
        val nuevoAhora = pendiente.fin().plusSeconds(60)
        reloj.instante = nuevoAhora

        useCase(pendiente.id, AccionTurno.CONFIRMAR)

        assertEquals(nuevoAhora, turnos.almacenado(pendiente.id).updatedAt)
    }

    @Test
    fun `cambiar de estado no toca createdAt ni deletedAt`() = runTest {
        val pendiente = turnoEnCurso()
        turnos.guardar(pendiente)
        reloj.instante = pendiente.fin().plusSeconds(600)

        useCase(pendiente.id, AccionTurno.CONFIRMAR)

        val guardado = turnos.almacenado(pendiente.id)
        assertEquals(pendiente.createdAt, guardado.createdAt)
        assertNull(guardado.deletedAt)
    }

    // ------------------------------------------------------------------ transiciones invalidas

    @Test
    fun `un turno inexistente devuelve NoExiste`() = runTest {
        val resultado = useCase(UUID.randomUUID(), AccionTurno.CONFIRMAR)

        assertEquals(ErrorTurno.NoExiste, resultado.exceptionOrNull())
    }

    @Test
    fun `un turno ya liberado devuelve NoExiste`() = runTest {
        val pendiente = turnoEnCurso().copy(deletedAt = reloj.instante)
        turnos.guardar(pendiente)

        val resultado = useCase(pendiente.id, AccionTurno.CONFIRMAR)

        assertEquals(ErrorTurno.NoExiste, resultado.exceptionOrNull())
    }

    @Test
    fun `un ATENDIDO rechaza cualquier accion`() = runTest {
        val atendido = turnoEnCurso(EstadoTurno.ATENDIDO)
        turnos.guardar(atendido)

        for (accion in AccionTurno.entries) {
            val resultado = useCase(atendido.id, accion)
            assertEquals(ErrorTurno.TransicionInvalida, resultado.exceptionOrNull(), "accion $accion")
        }
    }

    @Test
    fun `un PENDIENTE post-horario no se puede marcar atendido ni ausente`() = runTest {
        // Decision de producto: si el horario ya paso y nadie confirmo, el turno queda
        // congelado. "Completarlo" escribiria una verdad que el profesional nunca afirmo.
        val pendiente = turnoEnCurso(EstadoTurno.PENDIENTE)
        turnos.guardar(pendiente)
        reloj.instante = pendiente.fin().plusSeconds(1)

        assertEquals(
            ErrorTurno.TransicionInvalida,
            useCase(pendiente.id, AccionTurno.MARCAR_ATENDIDO).exceptionOrNull(),
        )
        assertEquals(
            ErrorTurno.TransicionInvalida,
            useCase(pendiente.id, AccionTurno.MARCAR_AUSENTE).exceptionOrNull(),
        )
    }

    @Test
    fun `un PENDIENTE post-horario todavia se puede confirmar y liberar`() = runTest {
        val pendiente = turnoEnCurso(EstadoTurno.PENDIENTE)
        turnos.guardar(pendiente)
        reloj.instante = pendiente.fin().plusSeconds(1)

        assertNull(useCase(pendiente.id, AccionTurno.CONFIRMAR).exceptionOrNull())
        assertEquals(EstadoTurno.CONFIRMADO, turnos.almacenado(pendiente.id).estado)
    }

    @Test
    fun `de CONFIRMADO no se puede liberar hay que cancelar primero`() = runTest {
        val confirmado = turnoEnCurso(EstadoTurno.CONFIRMADO)
        turnos.guardar(confirmado)

        val resultado = useCase(confirmado.id, AccionTurno.LIBERAR)

        assertEquals(ErrorTurno.TransicionInvalida, resultado.exceptionOrNull())
    }

    @Test
    fun `un CANCELADO post-horario no se puede volver a pendiente`() = runTest {
        val cancelado = turnoEnCurso(EstadoTurno.CANCELADO)
        turnos.guardar(cancelado)
        reloj.instante = cancelado.fin().plusSeconds(1)

        assertEquals(
            ErrorTurno.TransicionInvalida,
            useCase(cancelado.id, AccionTurno.VOLVER_A_PENDIENTE).exceptionOrNull(),
        )
    }

    @Test
    fun `una transicion invalida no escribe nada en el repositorio`() = runTest {
        val atendido = turnoEnCurso(EstadoTurno.ATENDIDO)
        turnos.guardar(atendido)

        useCase(atendido.id, AccionTurno.CONFIRMAR)

        assertEquals(EstadoTurno.ATENDIDO, turnos.almacenado(atendido.id).estado)
        assertTrue("cambiarEstado" !in turnos.llamadas, "Se escribio: ${turnos.llamadas}")
        assertTrue("eliminar" !in turnos.llamadas, "Se libero: ${turnos.llamadas}")
    }

    // ------------------------------------------------------------------ errores ajenos

    @Test
    fun `un fallo al leer el turno vuelve como Result failure`() = runTest {
        val pendiente = turnoEnCurso()
        turnos.guardar(pendiente)
        val fallo = IllegalStateException("fallo de SQLite")
        turnos.falloObtenerPorId = fallo

        val resultado = useCase(pendiente.id, AccionTurno.CONFIRMAR)

        assertEquals(fallo, resultado.exceptionOrNull())
    }

    @Test
    fun `un fallo al escribir el estado vuelve como Result failure`() = runTest {
        val pendiente = turnoEnCurso()
        turnos.guardar(pendiente)
        val fallo = IllegalStateException("Room no actualizo ninguna fila")
        turnos.falloCambiarEstado = fallo

        val resultado = useCase(pendiente.id, AccionTurno.CONFIRMAR)

        assertEquals(fallo, resultado.exceptionOrNull())
    }
}