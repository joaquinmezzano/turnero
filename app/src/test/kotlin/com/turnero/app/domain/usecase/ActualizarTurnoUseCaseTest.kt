package com.turnero.app.domain.usecase

import com.turnero.app.domain.model.ErrorTurno
import com.turnero.app.domain.model.EstadoTurno
import com.turnero.app.domain.model.VENTANA_POR_DEFECTO
import com.turnero.app.testing.FECHA_BASE
import com.turnero.app.testing.FakeClockProvider
import com.turnero.app.testing.FakeClienteRepository
import com.turnero.app.testing.FakeServicioRepository
import com.turnero.app.testing.FakeTurnoRepository
import com.turnero.app.testing.FakeZonaHorariaProvider
import com.turnero.app.testing.cliente
import com.turnero.app.testing.instanteDeTurno
import com.turnero.app.testing.servicio
import com.turnero.app.testing.turno
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalTime
import java.util.UUID

/**
 * Edicion de un turno.
 *
 * Lo que mas importa aca es el snapshot de `duracionMin`: editar un turno **no** puede
 * re-anclar la duracion contra el catalogo salvo que el usuario haya cambiado de servicio.
 */
class ActualizarTurnoUseCaseTest {

    private val turnos = FakeTurnoRepository()
    private val clientes = FakeClienteRepository()
    private val servicios = FakeServicioRepository()
    private val reloj = FakeClockProvider(instanteDeTurno(hora = 10))
    private val zona = FakeZonaHorariaProvider()
    private val useCase = ActualizarTurnoUseCase(turnos, clientes, servicios, reloj, zona, VENTANA_POR_DEFECTO)

    private val ana = cliente(nombre = "Pérez, Ana")
    private val juan = cliente(nombre = "Pérez, Juan")
    private val corte = servicio(nombre = "Corte", duracionMin = 30)

    /** Turno de mañana a las 10:00, de 30 minutos. */
    private val existente = turno(
        clienteId = ana.id,
        servicioId = corte.id,
        inicio = instanteDeTurno(hora = 10, dia = 17),
        duracionMin = 30,
        notas = "sin notas",
    )

    private fun prepare() {
        clientes.guardar(ana)
        clientes.guardar(juan)
        servicios.guardar(corte)
        turnos.guardar(existente)
    }

    // ------------------------------------------------------------------ snapshot de duracion

    @Test
    fun `editar sin cambiar de servicio conserva el snapshot de duracion`() = runTest {
        prepare()
        // El catalogo paso a 45 minutos, pero el turno se creo con 30 y editarle las notas
        // no puede moverlo: solo se re-ancla si el usuario elige otro servicio.
        servicios.guardar(corte.copy(duracionMin = 45))

        useCase(existente.id, ana.id, corte.id, FECHA_BASE.withDayOfMonth(17), LocalTime.of(11, 0), notas = "nuevas")

        assertEquals(30, turnos.almacenado(existente.id).duracionMin)
    }

    @Test
    fun `cambiar de servicio si re-ancla la duracion al nuevo`() = runTest {
        prepare()
        servicios.guardar(corte.copy(id = UUID.randomUUID(), nombre = "Coloración", duracionMin = 90))
        val coloracion = servicios.vivos().first { it.nombre == "Coloración" }

        useCase(existente.id, ana.id, coloracion.id, FECHA_BASE.withDayOfMonth(17), LocalTime.of(11, 0), notas = null)

        val guardado = turnos.almacenado(existente.id)
        assertEquals(coloracion.id, guardado.servicioId)
        assertEquals(90, guardado.duracionMin)
    }

    // ------------------------------------------------------------------ modelo resultante

    @Test
    fun `la edicion mueve el inicio al nuevo anclaje`() = runTest {
        prepare()

        useCase(existente.id, ana.id, corte.id, FECHA_BASE.withDayOfMonth(17), LocalTime.of(11, 37), notas = null)

        assertEquals(instanteDeTurno(hora = 11, dia = 17), turnos.almacenado(existente.id).inicio)
    }

    @Test
    fun `la edicion refresca updatedAt y preserva createdAt`() = runTest {
        prepare()
        reloj.instante = instanteDeTurno(hora = 10, dia = 17).plusSeconds(3_600)

        useCase(existente.id, ana.id, corte.id, FECHA_BASE.withDayOfMonth(17), LocalTime.of(11, 0), notas = null)

        val guardado = turnos.almacenado(existente.id)
        assertEquals(reloj.instante, guardado.updatedAt)
        assertEquals(existente.createdAt, guardado.createdAt)
    }

    @Test
    fun `la edicion no cambia el estado del turno`() = runTest {
        // El estado no se edita desde la pantalla de edicion: pasa por la maquina de
        // estados. Si se pudiera escribir aca, la tabla de transiciones dejaria de ser la
        // unica fuente de verdad.
        prepare()
        val confirmado = existente.copy(estado = EstadoTurno.CONFIRMADO)
        turnos.guardar(confirmado)

        useCase(confirmado.id, ana.id, corte.id, FECHA_BASE.withDayOfMonth(17), LocalTime.of(11, 0), notas = null)

        assertEquals(EstadoTurno.CONFIRMADO, turnos.almacenado(confirmado.id).estado)
    }

    @Test
    fun `las notas de solo espacios se guardan como null al editar`() = runTest {
        prepare()

        useCase(existente.id, ana.id, corte.id, FECHA_BASE.withDayOfMonth(17), LocalTime.of(11, 0), notas = "  ")

        assertNull(turnos.almacenado(existente.id).notas)
    }

    @Test
    fun `la edicion devuelve el id del turno guardado`() = runTest {
        prepare()

        val resultado = useCase(
            existente.id,
            ana.id,
            corte.id,
            FECHA_BASE.withDayOfMonth(17),
            LocalTime.of(11, 0),
            notas = null,
        )

        assertNull(resultado.exceptionOrNull())
        assertEquals(existente.id, turnos.almacenado(existente.id).id)
    }

    // ------------------------------------------------------------------ errores

    @Test
    fun `editar un turno inexistente devuelve NoExiste`() = runTest {
        prepare()

        val resultado = useCase(UUID.randomUUID(), ana.id, corte.id, FECHA_BASE, LocalTime.of(11, 0), notas = null)

        assertEquals(ErrorTurno.NoExiste, resultado.exceptionOrNull())
    }

    @Test
    fun `editar un turno liberado devuelve NoExiste`() = runTest {
        prepare()
        val liberado = existente.copy(deletedAt = reloj.instante)
        turnos.guardar(liberado)

        val resultado = useCase(liberado.id, ana.id, corte.id, FECHA_BASE, LocalTime.of(11, 0), notas = null)

        assertEquals(ErrorTurno.NoExiste, resultado.exceptionOrNull())
    }

    @Test
    fun `editar con un cliente inexistente devuelve SinCliente`() = runTest {
        prepare()

        val resultado = useCase(
            existente.id,
            UUID.randomUUID(),
            corte.id,
            FECHA_BASE.withDayOfMonth(17),
            LocalTime.of(11, 0),
            notas = null,
        )

        assertEquals(ErrorTurno.SinCliente, resultado.exceptionOrNull())
    }

    @Test
    fun `editar con un servicio inexistente devuelve SinServicio`() = runTest {
        prepare()

        val resultado = useCase(
            existente.id,
            ana.id,
            UUID.randomUUID(),
            FECHA_BASE.withDayOfMonth(17),
            LocalTime.of(11, 0),
            notas = null,
        )

        assertEquals(ErrorTurno.SinServicio, resultado.exceptionOrNull())
    }

    @Test
    fun `editar a una hora en el pasado devuelve EnElPasado`() = runTest {
        prepare()

        // El reloj esta en el 16 a las 10:00 y `existente` es del 17: un turno del 17 no puede
        // ser "en el pasado", por eso la referencia es hoy mismo y no el dia siguiente.
        val resultado = useCase(existente.id, ana.id, corte.id, FECHA_BASE, LocalTime.of(9, 0), notas = null)

        assertEquals(ErrorTurno.EnElPasado, resultado.exceptionOrNull())
    }

    @Test
    fun `editar fuera de la ventana devuelve FueraDeVentana`() = runTest {
        prepare()

        val resultado = useCase(existente.id, ana.id, corte.id, FECHA_BASE.withDayOfMonth(17), LocalTime.of(23, 0), notas = null)

        assertEquals(ErrorTurno.FueraDeVentana, resultado.exceptionOrNull())
    }

    @Test
    fun `una validacion fallida no escribe nada`() = runTest {
        prepare()

        // Las dos tienen que fallar en la validacion: si la primera pasara, escribiria y el
        // conteo de llamadas dejaria de ser cero aunque la segunda este bien rechazada.
        useCase(existente.id, ana.id, corte.id, FECHA_BASE, LocalTime.of(9, 0), notas = null)
        useCase(existente.id, ana.id, corte.id, FECHA_BASE.withDayOfMonth(17), LocalTime.of(23, 0), notas = null)

        assertTrue("actualizar" !in turnos.llamadas, "Se escribio: ${turnos.llamadas}")
        assertEquals(existente, turnos.almacenado(existente.id))
    }

    @Test
    fun `un solapamiento detectado por el repositorio vuelve como Solapamiento`() = runTest {
        prepare()
        turnos.falloActualizar = ErrorTurno.Solapamiento

        val resultado = useCase(existente.id, ana.id, corte.id, FECHA_BASE.withDayOfMonth(17), LocalTime.of(11, 0), notas = null)

        assertEquals(ErrorTurno.Solapamiento, resultado.exceptionOrNull())
    }
}