package com.turnero.app.ui.screens.turnos

import app.cash.turbine.test
import com.turnero.app.R
import com.turnero.app.domain.usecase.ObtenerAgendaDiaUseCase
import com.turnero.app.domain.usecase.ObtenerClientesUseCase
import com.turnero.app.testing.COLOR_BASE
import com.turnero.app.testing.FakeClienteRepository
import com.turnero.app.testing.FakeClockProvider
import com.turnero.app.testing.FakeTurnoRepository
import com.turnero.app.testing.FakeZonaHorariaProvider
import com.turnero.app.testing.MainDispatcherRule
import com.turnero.app.testing.cliente
import com.turnero.app.testing.instanteDeTurno
import com.turnero.app.testing.turno
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalTime
import java.util.UUID

/**
 * Hub de turnos.
 *
 * El ViewModel arma la grilla: cada `TurnoConFila` sabe en qué fila arranca (anclaje a la
 * hora exacta) y cuántas celdas ocupa (servicios de más de 60 minutos). El solapamiento no
 * se prueba acá: vive en el repositorio de Room (ver `CrearTurnoUseCaseTest`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TurnosViewModelTest {

    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var turnos: FakeTurnoRepository
    private lateinit var clientes: FakeClienteRepository
    private lateinit var reloj: FakeClockProvider
    private lateinit var zona: FakeZonaHorariaProvider

    @BeforeEach
    fun crearFakes() {
        turnos = FakeTurnoRepository()
        clientes = FakeClienteRepository()
        reloj = FakeClockProvider()
        zona = FakeZonaHorariaProvider()
    }

    private fun viewModel(): TurnosViewModel = TurnosViewModel(
        obtenerAgendaDia = ObtenerAgendaDiaUseCase(turnos, zona),
        obtenerClientes = ObtenerClientesUseCase(clientes),
        zonaHoraria = zona,
        clock = reloj,
    )

    @Test
    fun `el estado inicial es hoy y con la grilla cargando`() {
        val vm = viewModel()

        assertTrue(vm.uiState.value.cargando)
        assertEquals(
            reloj.instante.atZone(zona.zona()).toLocalDate(),
            vm.uiState.value.fecha,
        )
    }

    @Test
    fun `carga los turnos del dia con su fila y celdas ocupadas`() =
        runTest(mainDispatcherRule.testDispatcher) {
            reloj.instante = instanteDeTurno(hora = 8)
            val anaId = UUID.randomUUID()
            val corteId = UUID.randomUUID()
            clientes.guardar(cliente(id = anaId, nombre = "Pérez, Ana"))
            // El nombre y el color vienen del JOIN del repositorio, no de un catálogo que el
            // ViewModel consulte: el falso responde el nombre/color proyectado.
            turnos.proyectarServicio(servicioId = corteId, nombre = "Corte", color = COLOR_BASE)
            clientes.emitir(listOf(cliente(id = anaId, nombre = "Pérez, Ana")))
            // 10:00, duración 60 → celda 10:00 (fila 2), 1 celda ocupada.
            val turno10 = turno(
                clienteId = anaId,
                servicioId = corteId,
                inicio = instanteDeTurno(hora = 10),
                duracionMin = 60,
            )
            turnos.guardar(turno10)
            turnos.emitirDelDia(listOf(turno10))

            val vm = viewModel()
            advanceUntilIdle()

            vm.uiState.test {
                val cargado = awaitItem()
                assertEquals(1, cargado.turnos.size)
                val conFila = cargado.turnos.single()
                assertEquals(2, conFila.filaInicio) // 10:00 - 08:00 = 2
                assertEquals(1, conFila.filasOcupadas)
                assertEquals("Pérez, Ana", conFila.clienteNombre)
                assertEquals("Corte", conFila.servicioNombre)
                // B3: el chip se pinta con el color que trae la proyección del servicio
                // (null si el test no registró proyección, que es el caso defensivo).
                assertEquals(COLOR_BASE, conFila.servicioColor)
                // B4: la hora real de inicio y la hora real de fin (10:00 + 60 min).
                assertEquals(LocalTime.of(10, 0), conFila.horaInicio)
                assertEquals(LocalTime.of(11, 0), conFila.horaFin)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `un servicio de mas de una hora ocupa varias celdas`() =
        runTest(mainDispatcherRule.testDispatcher) {
            reloj.instante = instanteDeTurno(hora = 8)
            val anaId = UUID.randomUUID()
            val colorId = UUID.randomUUID()
            clientes.guardar(cliente(id = anaId))
            turnos.proyectarServicio(servicioId = colorId, nombre = "Coloración")
            clientes.emitir(listOf(cliente(id = anaId)))
            val turno90 = turno(
                clienteId = anaId,
                servicioId = colorId,
                inicio = instanteDeTurno(hora = 10),
                duracionMin = 90,
            )
            turnos.guardar(turno90)
            turnos.emitirDelDia(listOf(turno90))

            val vm = viewModel()
            advanceUntilIdle()

            assertEquals(2, vm.uiState.value.turnos.single().filasOcupadas)
        }

    @Test
    fun `ir al dia siguiente recarga la grilla para esa fecha`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            val fechaOriginal = vm.uiState.value.fecha

            vm.irDiaSiguiente()
            advanceUntilIdle()

            assertEquals(fechaOriginal.plusDays(1), vm.uiState.value.fecha)
            assertTrue(vm.uiState.value.turnos.isEmpty())
        }

    @Test
    fun `cambiar de dia cancela la coleccion del dia anterior`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            assertEquals(1, turnos.coleccionesActivas)

            vm.irDiaSiguiente()
            advanceUntilIdle()

            // Sin el `cargaJob?.cancel()` del ViewModel, el combine del día anterior
            // seguiría vivo y el contador daría 2.
            assertEquals(1, turnos.coleccionesActivas)
        }

    @Test
    fun `la hora de fin refleja la duracion real del turno`() =
        runTest(mainDispatcherRule.testDispatcher) {
            reloj.instante = instanteDeTurno(hora = 8)
            val anaId = UUID.randomUUID()
            val corteId = UUID.randomUUID()
            clientes.guardar(cliente(id = anaId))
            turnos.proyectarServicio(servicioId = corteId, nombre = "Corte")
            clientes.emitir(listOf(cliente(id = anaId)))
            val turno30 = turno(
                clienteId = anaId,
                servicioId = corteId,
                inicio = instanteDeTurno(hora = 10),
                duracionMin = 30,
            )
            turnos.guardar(turno30)
            turnos.emitirDelDia(listOf(turno30))

            val vm = viewModel()
            advanceUntilIdle()

            // Un turno corto de 10:00 termina a las 10:30, no a las 11:00: la ficha y el
            // chip muestran la hora real de fin (B4).
            assertEquals(LocalTime.of(10, 0), vm.uiState.value.turnos.single().horaInicio)
            assertEquals(LocalTime.of(10, 30), vm.uiState.value.turnos.single().horaFin)
        }

    @Test
    fun `un error de carga deja la grilla vacia y el cartel no lo borra el snackbar`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val vm = viewModel()
            advanceUntilIdle()

            // El `catch` del ViewModel es terminal: deja `turnos` vacío para que la grilla
            // no pinte los turnos de ayer bajo la fecha de hoy, y el error vive en
            // `errorCargaRes`, que el snackbar no consume.
            turnos.fallarLectura(RuntimeException("sqlite"))
            advanceUntilIdle()

            assertTrue(vm.uiState.value.turnos.isEmpty())
            assertEquals(R.string.error_generico, vm.uiState.value.errorCargaRes)

            // `onErrorMostrado` (el snackbar) solo borra `errorRes`, nunca `errorCargaRes`.
            vm.onErrorMostrado()
            assertEquals(R.string.error_generico, vm.uiState.value.errorCargaRes)
            assertTrue(vm.uiState.value.turnos.isEmpty())
        }

    @Test
    fun `reintentar vuelve a leer la agenda y limpia el error de carga`() =
        runTest(mainDispatcherRule.testDispatcher) {
            reloj.instante = instanteDeTurno(hora = 8)
            turnos.fallarLectura(RuntimeException("sqlite"))
            val vm = viewModel()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.errorCargaRes != null)

            // Mientras el cartel estaba en pantalla algo cambió; la reintentada lee los
            // turnos nuevos y sale del estado de error. El canal del falso es BUFFERED, así
            // que emitir antes de recollect alcanza.
            val anaId = UUID.randomUUID()
            val corteId = UUID.randomUUID()
            turnos.proyectarServicio(servicioId = corteId, nombre = "Corte")
            val turno10 = turno(
                clienteId = anaId,
                servicioId = corteId,
                inicio = instanteDeTurno(hora = 10),
                duracionMin = 60,
            )
            turnos.guardar(turno10)
            // El `combine` exige que ambos flujos emitan para aparear: sin el catálogo la
            // reintentada no tendría qué pintar aunque la agenda sí tuviera turnos.
            clientes.emitir(listOf(cliente(id = anaId, nombre = "Pérez, Ana")))
            turnos.emitirDelDia(listOf(turno10))

            vm.reintentar()
            advanceUntilIdle()

            assertEquals(null, vm.uiState.value.errorCargaRes)
            assertEquals(1, vm.uiState.value.turnos.size)
            assertEquals("Corte", vm.uiState.value.turnos.single().servicioNombre)
        }
}