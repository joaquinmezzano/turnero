package com.turnero.app.ui.screens.turnos

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.turnero.app.R
import com.turnero.app.domain.model.EstadoTurno
import com.turnero.app.domain.model.VENTANA_POR_DEFECTO
import com.turnero.app.domain.usecase.ActualizarTurnoUseCase
import com.turnero.app.domain.usecase.CrearTurnoUseCase
import com.turnero.app.domain.usecase.ObtenerClientesUseCase
import com.turnero.app.domain.usecase.ObtenerServiciosUseCase
import com.turnero.app.domain.usecase.ObtenerTurnoPorIdUseCase
import com.turnero.app.testing.FakeClienteRepository
import com.turnero.app.testing.FakeClockProvider
import com.turnero.app.testing.FakeServicioRepository
import com.turnero.app.testing.FakeTurnoRepository
import com.turnero.app.testing.FakeZonaHorariaProvider
import com.turnero.app.testing.MainDispatcherRule
import com.turnero.app.testing.cliente
import com.turnero.app.testing.instanteDeTurno
import com.turnero.app.testing.servicio
import com.turnero.app.testing.turno
import com.turnero.app.ui.navigation.TurnoEditor
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
 * Editor de turno (alta y edición comparten pantalla).
 *
 * La hora **no** es un slider libre: el `UiState` expone las 15 horas agendables
 * (08:00–22:00 en punto) y el ViewModel cambia a una de esas. El anclaje a la hora exacta
 * lo hace el use case (ver `CrearTurnoUseCase`), no la pantalla.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TurnoEditorViewModelTest {

    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var turnos: FakeTurnoRepository
    private lateinit var clientes: FakeClienteRepository
    private lateinit var servicios: FakeServicioRepository
    private lateinit var reloj: FakeClockProvider
    private lateinit var zona: FakeZonaHorariaProvider

    @BeforeEach
    fun crearFakes() {
        turnos = FakeTurnoRepository()
        clientes = FakeClienteRepository()
        servicios = FakeServicioRepository()
        reloj = FakeClockProvider()
        zona = FakeZonaHorariaProvider()
    }

    private fun estadoGuardado(
        fecha: String? = null,
        hora: String? = null,
        clienteId: String? = null,
        turnoId: String? = null,
    ): SavedStateHandle {
        val descriptor = TurnoEditor.serializer().descriptor
        return SavedStateHandle(
            mapOf(
                descriptor.getElementName(0) to fecha,
                descriptor.getElementName(1) to hora,
                descriptor.getElementName(2) to clienteId,
                descriptor.getElementName(3) to turnoId,
            ),
        )
    }

    private fun editorDe(ruta: SavedStateHandle): TurnoEditorViewModel = TurnoEditorViewModel(
        obtenerClientes = ObtenerClientesUseCase(clientes),
        obtenerServicios = ObtenerServiciosUseCase(servicios),
        obtenerTurnoPorId = ObtenerTurnoPorIdUseCase(turnos),
        crearTurno = CrearTurnoUseCase(turnos, clientes, servicios, reloj, zona, VENTANA_POR_DEFECTO),
        actualizarTurno = ActualizarTurnoUseCase(turnos, clientes, servicios, reloj, zona, VENTANA_POR_DEFECTO),
        clock = reloj,
        zonaHoraria = zona,
        estadoGuardado = ruta,
    )

    @Test
    fun `expone las 15 horas agendables en el estado`() {
        val vm = editorDe(estadoGuardado())

        assertEquals(15, vm.uiState.value.horasDisponibles.size)
        assertEquals(LocalTime.of(8, 0), vm.uiState.value.horasDisponibles.first())
        assertEquals(LocalTime.of(22, 0), vm.uiState.value.horasDisponibles.last())
    }

    @Test
    fun `la fecha inicial sale del reloj y no de LocalDate now`() {
        // S-V2: el default se sacó del `UiState`; la única fuente de la fecha es el
        // `ClockProvider` inyectado (o el argumento de la ruta). Un `LocalDate.now()` en
        // el estado no se puede testear y hace que la pantalla muestre "hoy" aunque el
        // reloj del test diga otra cosa.
        reloj.instante = instanteDeTurno(hora = 8)

        val vm = editorDe(estadoGuardado())

        assertEquals(reloj.instante.atZone(zona.zona()).toLocalDate(), vm.uiState.value.fecha)
    }

    @Test
    fun `cambiar la hora solo acepta horas ancladas`() {
        val vm = editorDe(estadoGuardado())

        vm.cambiarHora(LocalTime.of(10, 0))
        assertEquals(LocalTime.of(10, 0), vm.uiState.value.hora)
        // 10:37 no existe en la grilla: el editor no tiene camino para escribirlo.
        assertEquals(15, vm.uiState.value.horasDisponibles.size)
    }

    @Test
    fun `alta desde la grilla deja fijadas la fecha y la hora del tap`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fecha = "2026-03-16"
            val hora = "10:00"
            val vm = editorDe(estadoGuardado(fecha = fecha, hora = hora))
            advanceUntilIdle()

            assertEquals("2026-03-16", vm.uiState.value.fecha.toString())
            assertEquals(LocalTime.of(10, 0), vm.uiState.value.hora)
        }

    @Test
    fun `alta desde la ficha del cliente deja fijado el cliente`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val anaId = UUID.randomUUID()
            clientes.guardar(cliente(id = anaId, nombre = "Pérez, Ana"))
            servicios.guardar(servicio(nombre = "Corte", duracionMin = 30))
            val vm = editorDe(estadoGuardado(clienteId = anaId.toString()))
            advanceUntilIdle()

            assertEquals(anaId, vm.uiState.value.clienteId)
        }

    @Test
    fun `guardar un alta crea el turno y avisa por el canal`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val anaId = UUID.randomUUID()
            val corteId = UUID.randomUUID()
            clientes.guardar(cliente(id = anaId))
            servicios.guardar(servicio(id = corteId, nombre = "Corte", duracionMin = 30))
            val vm = editorDe(estadoGuardado())
            advanceUntilIdle()
            vm.actualizarCampos(clienteId = anaId, servicioId = corteId)
            vm.cambiarFecha(java.time.LocalDate.of(2026, 3, 16))
            vm.cambiarHora(LocalTime.of(10, 0))

            vm.guardadoFlow.test {
                vm.guardar()
                advanceUntilIdle()

                awaitItem()
                val guardado = turnos.vivos().single()
                assertEquals(anaId, guardado.clienteId)
                assertEquals(corteId, guardado.servicioId)
                assertEquals(EstadoTurno.PENDIENTE, guardado.estado)
                assertEquals(instanteDeTurno(hora = 10), guardado.inicio)
                assertEquals(30, guardado.duracionMin) // snapshot del servicio
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `editar precarga los datos del turno`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val anaId = UUID.randomUUID()
            val corteId = UUID.randomUUID()
            clientes.guardar(cliente(id = anaId, nombre = "Pérez, Ana"))
            servicios.guardar(servicio(id = corteId, nombre = "Corte"))
            val idTurno = UUID.randomUUID()
            turnos.guardar(
                turno(
                    id = idTurno,
                    clienteId = anaId,
                    servicioId = corteId,
                    inicio = instanteDeTurno(hora = 11),
                    duracionMin = 30,
                    notas = "Notas viejas",
                ),
            )

            val vm = editorDe(estadoGuardado(turnoId = idTurno.toString()))
            advanceUntilIdle()

            assertEquals(idTurno, vm.uiState.value.turnoId)
            assertEquals(anaId, vm.uiState.value.clienteId)
            assertEquals(corteId, vm.uiState.value.servicioId)
            assertEquals("Notas viejas", vm.uiState.value.notas)
            assertEquals(LocalTime.of(11, 0), vm.uiState.value.hora)
        }

    @Test
    fun `guardar una edicion actualiza y avisa por el canal`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val anaId = UUID.randomUUID()
            val corteId = UUID.randomUUID()
            clientes.guardar(cliente(id = anaId))
            servicios.guardar(servicio(id = corteId, nombre = "Corte"))
            val idTurno = UUID.randomUUID()
            turnos.guardar(
                turno(
                    id = idTurno,
                    clienteId = anaId,
                    servicioId = corteId,
                    inicio = instanteDeTurno(hora = 10),
                ),
            )
            val vm = editorDe(estadoGuardado(turnoId = idTurno.toString()))
            advanceUntilIdle()

            vm.guardadoFlow.test {
                vm.guardar()
                advanceUntilIdle()

                awaitItem()
                assertEquals(1, turnos.vivos().size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `guardar con solapamiento deja el error y no avisa`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val anaId = UUID.randomUUID()
            val corteId = UUID.randomUUID()
            clientes.guardar(cliente(id = anaId))
            servicios.guardar(servicio(id = corteId, nombre = "Corte"))
            turnos.falloCrear = com.turnero.app.domain.model.ErrorTurno.Solapamiento
            val vm = editorDe(estadoGuardado())
            advanceUntilIdle()
            vm.actualizarCampos(clienteId = anaId, servicioId = corteId)
            vm.cambiarFecha(java.time.LocalDate.of(2026, 3, 16))
            vm.cambiarHora(LocalTime.of(10, 0))

            vm.guardadoFlow.test {
                vm.guardar()
                advanceUntilIdle()

                expectNoEvents()
                assertEquals(R.string.error_turno_solapamiento, vm.uiState.value.errorRes)
                assertTrue(turnos.vivos().isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `sin cliente ni servicio el guardar no llama al use case`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val vm = editorDe(estadoGuardado())
            advanceUntilIdle()

            vm.guardar()
            advanceUntilIdle()

            assertTrue(turnos.vivos().isEmpty())
            assertEquals(R.string.error_generico, vm.uiState.value.errorRes)
        }
}