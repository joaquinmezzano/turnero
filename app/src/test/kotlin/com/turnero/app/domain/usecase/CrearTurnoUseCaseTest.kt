package com.turnero.app.domain.usecase

import com.turnero.app.domain.model.ErrorTurno
import com.turnero.app.domain.model.EstadoTurno
import com.turnero.app.domain.model.VENTANA_POR_DEFECTO
import com.turnero.app.testing.FECHA_BASE
import com.turnero.app.testing.FakeClienteRepository
import com.turnero.app.testing.FakeClockProvider
import com.turnero.app.testing.FakeServicioRepository
import com.turnero.app.testing.FakeTurnoRepository
import com.turnero.app.testing.FakeZonaHorariaProvider
import com.turnero.app.testing.ZONA_BASE
import com.turnero.app.testing.cliente
import com.turnero.app.testing.instanteDeTurno
import com.turnero.app.testing.servicio
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * Alta de turnos.
 *
 * El reloj y la zona estan fijados: `ahora` son las 10:00 hora Argentina del dia base, asi
 * que agendar a las 11:00 es valido y a las 09:00 esta en el pasado, sin depender de la
 * maquina que corre el test.
 */
class CrearTurnoUseCaseTest {

    private val turnos = FakeTurnoRepository()
    private val clientes = FakeClienteRepository()
    private val servicios = FakeServicioRepository()
    private val reloj = FakeClockProvider(instanteDeTurno(hora = 10))
    private val zona = FakeZonaHorariaProvider()
    private val useCase = CrearTurnoUseCase(turnos, clientes, servicios, reloj, zona, VENTANA_POR_DEFECTO)

    private val ana = cliente(nombre = "Pérez, Ana")
    private val corte = servicio(nombre = "Corte", duracionMin = 30)

    private fun conClienteYServicio() {
        clientes.guardar(ana)
        servicios.guardar(corte)
    }

    // ------------------------------------------------------------------ snapshot de duracion

    @Test
    fun `la duracion del turno es el snapshot de la del servicio`() = runTest {
        conClienteYServicio()

        useCase(ana.id, corte.id, FECHA_BASE, LocalTime.of(11, 0), notas = null)

        assertEquals(30, turnos.vivos().single().duracionMin)
    }

    @Test
    fun `cambiar la duracion del servicio no mueve el turno ya agendado`() = runTest {
        conClienteYServicio()
        useCase(ana.id, corte.id, FECHA_BASE, LocalTime.of(11, 0), notas = null)
        val idDelTurno = turnos.vivos().single().id

        // El profesional sube el corte de 30 a 45 minutos en el catalogo. El turno ya
        // agendado tiene que seguir durando 30: es un compromiso, no una proyeccion.
        servicios.guardar(corte.copy(duracionMin = 45))

        assertEquals(30, turnos.almacenado(idDelTurno).duracionMin)
    }

    @Test
    fun `un servicio de mas de una hora guarda su duracion entera`() = runTest {
        clientes.guardar(ana)
        servicios.guardar(corte.copy(nombre = "Coloración", duracionMin = 90))

        useCase(ana.id, corte.id, FECHA_BASE, LocalTime.of(11, 0), notas = null)

        assertEquals(90, turnos.vivos().single().duracionMin)
    }

    // ------------------------------------------------------------------ modelo resultante

    @Test
    fun `el turno nascent arranca PENDIENTE y sin deletedAt`() = runTest {
        conClienteYServicio()

        useCase(ana.id, corte.id, FECHA_BASE, LocalTime.of(11, 0), notas = null)

        val guardado = turnos.vivos().single()
        assertEquals(EstadoTurno.PENDIENTE, guardado.estado)
        assertNull(guardado.deletedAt)
    }

    @Test
    fun `el alta sella createdAt y updatedAt con el instante del reloj`() = runTest {
        conClienteYServicio()

        useCase(ana.id, corte.id, FECHA_BASE, LocalTime.of(11, 0), notas = null)

        val guardado = turnos.vivos().single()
        assertEquals(reloj.instante, guardado.createdAt)
        assertEquals(reloj.instante, guardado.updatedAt)
    }

    @Test
    fun `el alta devuelve el UUID del turno persistido`() = runTest {
        conClienteYServicio()

        val id = useCase(ana.id, corte.id, FECHA_BASE, LocalTime.of(11, 0), notas = null).getOrThrow()

        assertEquals(id, turnos.vivos().single().id)
    }

    @Test
    fun `el inicio se guarda anclado a la hora y con la zona inyectada`() = runTest {
        conClienteYServicio()

        useCase(ana.id, corte.id, FECHA_BASE, LocalTime.of(11, 37), notas = null)

        val guardado = turnos.vivos().single()
        assertEquals(instanteDeTurno(hora = 11), guardado.inicio)
        assertEquals(0, guardado.inicio.atZone(ZONA_BASE).minute)
    }

    @Test
    fun `el inicio cambia segun la zona del dispositivo`() = runTest {
        // Misma fecha y misma hora, dos zonas: el `Instant` guardado es otro. Es lo que hace
        // que un telefono en otra zona vea el turno a la hora local que le corresponde.
        //
        // El reloj se corre a las 7 porque Madrid esta adelante de Argentina: las 11:00 locales
        // en Madrid son las 10:00 UTC, y con el reloj en las 10:00 de Argentina (13:00 UTC) esa
        // segunda creacion caeria en `EnElPasado` y no escribiria nada.
        reloj.instante = instanteDeTurno(hora = 7)
        conClienteYServicio()
        val madrid = ZoneId.of("Europe/Madrid")

        useCase(ana.id, corte.id, FECHA_BASE, LocalTime.of(11, 0), notas = null)
        val enArgentina = turnos.vivos().single().inicio

        zona.zona = madrid
        useCase(ana.id, corte.id, FECHA_BASE, LocalTime.of(11, 0), notas = null)
        val enMadrid = turnos.vivos().last().inicio

        assertTrue(enArgentina != enMadrid)
        assertEquals(LocalTime.of(11, 0), LocalTime.ofInstant(enArgentina, ZONA_BASE))
        assertEquals(LocalTime.of(11, 0), LocalTime.ofInstant(enMadrid, madrid))
    }

    @Test
    fun `las notas se guardan sin espacios sobrantes`() = runTest {
        conClienteYServicio()

        useCase(ana.id, corte.id, FECHA_BASE, LocalTime.of(11, 0), notas = "  venir con pelo seco ")

        assertEquals("venir con pelo seco", turnos.vivos().single().notas)
    }

    @Test
    fun `unas notas de solo espacios se guardan como null`() = runTest {
        conClienteYServicio()

        useCase(ana.id, corte.id, FECHA_BASE, LocalTime.of(11, 0), notas = "   ")

        assertNull(turnos.vivos().single().notas)
    }

    // ------------------------------------------------------------------ referencias

    @Test
    fun `un cliente inexistente se rechaza con SinCliente`() = runTest {
        servicios.guardar(corte)

        val resultado = useCase(ana.id, corte.id, FECHA_BASE, LocalTime.of(11, 0), notas = null)

        assertEquals(ErrorTurno.SinCliente, resultado.exceptionOrNull())
    }

    @Test
    fun `un cliente eliminado se rechaza con SinCliente`() = runTest {
        clientes.guardar(ana.copy(deletedAt = reloj.instante))
        servicios.guardar(corte)

        val resultado = useCase(ana.id, corte.id, FECHA_BASE, LocalTime.of(11, 0), notas = null)

        assertEquals(ErrorTurno.SinCliente, resultado.exceptionOrNull())
    }

    @Test
    fun `un servicio inexistente se rechaza con SinServicio`() = runTest {
        clientes.guardar(ana)

        val resultado = useCase(ana.id, corte.id, FECHA_BASE, LocalTime.of(11, 0), notas = null)

        assertEquals(ErrorTurno.SinServicio, resultado.exceptionOrNull())
    }

    @Test
    fun `un servicio eliminado se rechaza con SinServicio`() = runTest {
        clientes.guardar(ana)
        servicios.guardar(corte.copy(deletedAt = reloj.instante))

        val resultado = useCase(ana.id, corte.id, FECHA_BASE, LocalTime.of(11, 0), notas = null)

        assertEquals(ErrorTurno.SinServicio, resultado.exceptionOrNull())
    }

    // ------------------------------------------------------------------ validaciones

    @Test
    fun `un inicio en el pasado se rechaza con EnElPasado`() = runTest {
        conClienteYServicio()

        val resultado = useCase(ana.id, corte.id, FECHA_BASE, LocalTime.of(9, 0), notas = null)

        assertEquals(ErrorTurno.EnElPasado, resultado.exceptionOrNull())
    }

    @Test
    fun `un inicio fuera de la ventana se rechaza con FueraDeVentana`() = runTest {
        conClienteYServicio()

        val resultado = useCase(ana.id, corte.id, FECHA_BASE, LocalTime.of(7, 0), notas = null)

        assertEquals(ErrorTurno.FueraDeVentana, resultado.exceptionOrNull())
    }

    @Test
    fun `las veintidos es una hora de inicio valida`() = runTest {
        conClienteYServicio()

        val resultado = useCase(ana.id, corte.id, FECHA_BASE, LocalTime.of(22, 0), notas = null)

        assertNull(resultado.exceptionOrNull())
    }

    @Test
    fun `una validacion fallida no escribe ningun turno`() = runTest {
        conClienteYServicio()

        useCase(ana.id, corte.id, FECHA_BASE, LocalTime.of(9, 0), notas = null)
        useCase(ana.id, corte.id, FECHA_BASE, LocalTime.of(23, 0), notas = null)
        useCase(UUID.randomUUID(), corte.id, FECHA_BASE, LocalTime.of(11, 0), notas = null)

        assertTrue(turnos.vivos().isEmpty(), "Se escribieron turnos: ${turnos.vivos()}")
        assertTrue("crear" !in turnos.llamadas, "Se llamo a crear: ${turnos.llamadas}")
    }

    // ------------------------------------------------------------------ errores ajenos

    @Test
    fun `un fallo al leer el cliente vuelve como Result failure`() = runTest {
        val fallo = IllegalStateException("fallo de SQLite")
        clientes.falloObtenerPorId = fallo

        val resultado = useCase(ana.id, corte.id, FECHA_BASE, LocalTime.of(11, 0), notas = null)

        assertEquals(fallo, resultado.exceptionOrNull())
    }

    @Test
    fun `un solapamiento detectado por el repositorio vuelve como Solapamiento`() = runTest {
        conClienteYServicio()
        // El choque vive en la transaccion de Room, no en el use case: lo que se comprueba
        // aca es que el error del repositorio llegue intacto, sin envolverlo.
        turnos.falloCrear = ErrorTurno.Solapamiento

        val resultado = useCase(ana.id, corte.id, FECHA_BASE, LocalTime.of(11, 0), notas = null)

        assertEquals(ErrorTurno.Solapamiento, resultado.exceptionOrNull())
    }

    @Test
    fun `un fallo del repositorio al insertar vuelve como Result failure`() = runTest {
        conClienteYServicio()
        val fallo = IllegalStateException("Room no pudo insertar")
        turnos.falloCrear = fallo

        val resultado = useCase(ana.id, corte.id, FECHA_BASE, LocalTime.of(11, 0), notas = null)

        assertEquals(fallo, resultado.exceptionOrNull())
    }

    @Test
    fun `dos altas seguidas generan UUID distintos`() = runTest {
        conClienteYServicio()

        val primero = useCase(ana.id, corte.id, FECHA_BASE, LocalTime.of(11, 0), notas = null).getOrThrow()
        val segundo = useCase(ana.id, corte.id, FECHA_BASE, LocalTime.of(12, 0), notas = null).getOrThrow()

        assertTrue(primero != segundo)
    }
}