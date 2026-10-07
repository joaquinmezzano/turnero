package com.turnero.app.ui.screens.turnos

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.turnero.app.R
import com.turnero.app.domain.model.AccionTurno
import com.turnero.app.domain.model.EstadoTurno
import com.turnero.app.domain.usecase.CambiarEstadoTurnoUseCase
import com.turnero.app.domain.usecase.ObtenerClientesUseCase
import com.turnero.app.domain.usecase.ObtenerTurnoConServicioPorIdUseCase
import com.turnero.app.testing.FakeClienteRepository
import com.turnero.app.testing.FakeClockProvider
import com.turnero.app.testing.FakeTurnoRepository
import com.turnero.app.testing.FakeZonaHorariaProvider
import com.turnero.app.testing.MainDispatcherRule
import com.turnero.app.testing.cliente
import com.turnero.app.testing.instanteDeTurno
import com.turnero.app.testing.turno
import com.turnero.app.ui.navigation.TurnoDetalle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.UUID

/**
 * Ficha de un turno.
 *
 * La regla dura del slice: la UI **no decide** qué acciones muestra, pinta las que devuelve
 * `accionesDisponibles`. Acá se verifica que el ViewModel exponga exactamente ese conjunto,
 * y que `LIBERAR` de un `CONFIRMADO` no exista (cancelar y liberar son dos pasos).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TurnoDetalleViewModelTest {

    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    private val turnoId = UUID.fromString("b0b4e9c2-1f3d-4a5b-8c6d-9e0f1a2b3c4d")

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

    private fun detalleDe(): TurnoDetalleViewModel {
        val descriptor = TurnoDetalle.serializer().descriptor
        return TurnoDetalleViewModel(
            obtenerTurnoConServicioPorId = ObtenerTurnoConServicioPorIdUseCase(turnos),
            obtenerClientes = ObtenerClientesUseCase(clientes),
            cambiarEstado = CambiarEstadoTurnoUseCase(turnos, reloj),
            clock = reloj,
            estadoGuardado = SavedStateHandle(
                mapOf(descriptor.getElementName(0) to turnoId.toString()),
            ),
        )
    }

    private suspend fun conClienteYServicio(clienteId: UUID, servicioId: UUID) {
        // El nombre del servicio sale de la proyección con JOIN del repositorio, no de un
        // catálogo que el ViewModel consulte.
        turnos.proyectarServicio(servicioId = servicioId, nombre = "Corte")
        clientes.guardar(cliente(id = clienteId, nombre = "Pérez, Ana"))
        clientes.emitir(listOf(cliente(id = clienteId, nombre = "Pérez, Ana")))
    }

    // ------------------------------------------------------------ carga

    @Test
    fun `carga el turno con su cliente servicio y acciones disponibles`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val anaId = UUID.randomUUID()
            val corteId = UUID.randomUUID()
            conClienteYServicio(anaId, corteId)
            turnos.guardar(
                turno(
                    id = turnoId,
                    clienteId = anaId,
                    servicioId = corteId,
                    inicio = instanteDeTurno(hora = 10),
                    estado = EstadoTurno.PENDIENTE,
                ),
            )

            val vm = detalleDe()
            advanceUntilIdle()

            vm.uiState.test {
                val cargado = awaitItem()
                assertEquals("Pérez, Ana", cargado.clienteNombre)
                assertEquals("Corte", cargado.servicioNombre)
                assertEquals(setOf(AccionTurno.CONFIRMAR, AccionTurno.LIBERAR), cargado.acciones)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `el nombre del servicio sigue visible aunque este dado de baja`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val anaId = UUID.randomUUID()
            val corteId = UUID.randomUUID()
            clientes.guardar(cliente(id = anaId, nombre = "Pérez, Ana"))
            clientes.emitir(listOf(cliente(id = anaId, nombre = "Pérez, Ana")))
            // El falso no modela la baja del servicio: responde la proyección del JOIN tal
            // cual el repositorio real (que no filtra servicios.deletedAt). Acá se prueba
            // que el ViewModel la respeta y no la cruza contra un catálogo vivo; el JOIN de
            // verdad se prueba contra Room en `TurnoDaoTest`.
            turnos.proyectarServicio(servicioId = corteId, nombre = "Corte")
            turnos.guardar(
                turno(
                    id = turnoId,
                    clienteId = anaId,
                    servicioId = corteId,
                    inicio = instanteDeTurno(hora = 10),
                ),
            )

            val vm = detalleDe()
            advanceUntilIdle()

            assertEquals("Corte", vm.uiState.value.servicioNombre)
        }

    @Test
    fun `un turno liberado se muestra como inexistente`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val anaId = UUID.randomUUID()
            val corteId = UUID.randomUUID()
            conClienteYServicio(anaId, corteId)
            turnos.guardar(
                turno(
                    id = turnoId,
                    clienteId = anaId,
                    servicioId = corteId,
                    deletedAt = reloj.instante,
                ),
            )

            val vm = detalleDe()
            advanceUntilIdle()

            assertNull(vm.uiState.value.turno)
            assertEquals(R.string.error_turno_no_existe, vm.uiState.value.errorRes)
        }

    // ------------------------------------------------------------ acciones

    @Test
    fun `un confirmado no ofrece LIBERAR en el detalle`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val anaId = UUID.randomUUID()
            val corteId = UUID.randomUUID()
            conClienteYServicio(anaId, corteId)
            turnos.guardar(
                turno(
                    id = turnoId,
                    clienteId = anaId,
                    servicioId = corteId,
                    inicio = instanteDeTurno(hora = 10),
                    estado = EstadoTurno.CONFIRMADO,
                ),
            )

            val vm = detalleDe()
            advanceUntilIdle()

            // Cancelar y liberar son dos pasos: de un confirmado no se borra.
            assertTrue(AccionTurno.LIBERAR !in vm.uiState.value.acciones)
            assertEquals(
                setOf(AccionTurno.MARCAR_ATENDIDO, AccionTurno.CANCELAR),
                vm.uiState.value.acciones,
            )
        }

    @Test
    fun `ejecutar LIBERAR hace el soft delete del turno`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val anaId = UUID.randomUUID()
            val corteId = UUID.randomUUID()
            conClienteYServicio(anaId, corteId)
            turnos.guardar(
                turno(
                    id = turnoId,
                    clienteId = anaId,
                    servicioId = corteId,
                    estado = EstadoTurno.PENDIENTE,
                ),
            )

            val vm = detalleDe()
            advanceUntilIdle()
            vm.ejecutarAccion(AccionTurno.LIBERAR)
            advanceUntilIdle()

            assertEquals(reloj.instante, turnos.almacenado(turnoId).deletedAt)
        }

    @Test
    fun `una transicion invalida deja el error en el estado`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val anaId = UUID.randomUUID()
            val corteId = UUID.randomUUID()
            conClienteYServicio(anaId, corteId)
            // Estado final: ATENDIDO no ofrece ninguna salida.
            turnos.guardar(
                turno(
                    id = turnoId,
                    clienteId = anaId,
                    servicioId = corteId,
                    estado = EstadoTurno.ATENDIDO,
                ),
            )

            val vm = detalleDe()
            advanceUntilIdle()
            vm.ejecutarAccion(AccionTurno.CONFIRMAR)
            advanceUntilIdle()

            assertEquals(R.string.error_turno_transicion_invalida, vm.uiState.value.errorRes)
        }

    // ------------------------------------------------------------ S-V1

    @Test
    fun `una accion no reverdece cuando los catalogos re-emiten`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val anaId = UUID.randomUUID()
            val corteId = UUID.randomUUID()
            conClienteYServicio(anaId, corteId)
            turnos.guardar(
                turno(
                    id = turnoId,
                    clienteId = anaId,
                    servicioId = corteId,
                    inicio = instanteDeTurno(hora = 10),
                    estado = EstadoTurno.PENDIENTE,
                ),
            )

            val vm = detalleDe()
            advanceUntilIdle()
            vm.ejecutarAccion(AccionTurno.CONFIRMAR)
            // El falso no tiene replay, como el falso de los otros slices: la recarga que
            // dispara la acción vuelve a hacer `first()` sobre el catálogo del cliente y
            // necesita una emisión nueva para completar (Room sí re-emite el snapshot al
            // suscribir). El servicio no re-emite: sale de la proyección de un solo valor.
            clientes.emitir(listOf(cliente(id = anaId, nombre = "Pérez, Ana")))
            advanceUntilIdle()
            assertEquals(EstadoTurno.CONFIRMADO, vm.uiState.value.turno?.estado)
            assertEquals(
                setOf(AccionTurno.MARCAR_ATENDIDO, AccionTurno.CANCELAR),
                vm.uiState.value.acciones,
            )

            // El catálogo del cliente es un flujo vivo de Room: una re-emisión posterior
            // (nuevo cliente) empujaría el snapshot viejo del turno a la ficha si quedara
            // un collector vivo por cada `cargar()`. Con `first()` esto no pasa: la ficha
            // no se entera de la re-emisión.
            clientes.emitir(listOf(cliente(id = anaId, nombre = "Pérez, Ana")))
            advanceUntilIdle()

            assertEquals(EstadoTurno.CONFIRMADO, vm.uiState.value.turno?.estado)
            assertEquals(
                setOf(AccionTurno.MARCAR_ATENDIDO, AccionTurno.CANCELAR),
                vm.uiState.value.acciones,
            )
            // Y el fix no filtra colectores: los cierra. Antes el `combine(...).collect`
            // de cada `cargar()` dejaba dos streams abiertos de por vida. El servicio no
            // cuenta: ya no hay catálogo de servicios en el ViewModel.
            assertEquals(0, clientes.coleccionesActivas)
        }
}