package com.turnero.app.ui.screens.servicios

import app.cash.turbine.test
import com.turnero.app.R
import com.turnero.app.domain.usecase.ActualizarServicioUseCase
import com.turnero.app.domain.usecase.CrearServicioUseCase
import com.turnero.app.domain.usecase.EliminarServicioUseCase
import com.turnero.app.domain.usecase.ObtenerServiciosUseCase
import com.turnero.app.testing.COLOR_BASE
import com.turnero.app.testing.FakeClockProvider
import com.turnero.app.testing.FakeServicioRepository
import com.turnero.app.testing.INSTANTE_BASE
import com.turnero.app.testing.MainDispatcherRule
import com.turnero.app.testing.servicio
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.UUID

/**
 * Tests del ViewModel con los use cases reales y el repositorio falso.
 *
 * Los use cases son clases concretas (no interfaces), asi que no se pueden mockear sin
 * `mockk`. Montarlos de verdad con el fake cuesta tres lineas y cubre el cableado
 * completo ViewModel -> use case -> repositorio, que es justo lo que este slice prueba.
 *
 * El ViewModel se arma en `@BeforeEach` y no como propiedad: `MainDispatcherRule` corre
 * en `BeforeEachCallback`, que Jupiter ejecuta **antes** que los metodos `@BeforeEach`.
 * Construirlo en el inicializador de propiedades seria hacerlo con el `Main` real y
 * `viewModelScope` caeria al dispatcher del sistema.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ServiciosViewModelTest {

    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    private val id = UUID.fromString("c3e5f7a9-4b6c-4d8e-9fa0-1b2c3d4e5f03")

    private lateinit var repositorio: FakeServicioRepository
    private lateinit var reloj: FakeClockProvider
    private lateinit var viewModel: ServiciosViewModel

    @BeforeEach
    fun crearViewModel() {
        repositorio = FakeServicioRepository()
        reloj = FakeClockProvider()
        viewModel = ServiciosViewModel(
            obtenerServicios = ObtenerServiciosUseCase(repositorio),
            crearServicio = CrearServicioUseCase(repositorio, reloj),
            actualizarServicio = ActualizarServicioUseCase(repositorio, reloj),
            eliminarServicio = EliminarServicioUseCase(repositorio, reloj),
        )
    }

    @Test
    fun `el estado inicial viene cargando y sin servicios`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.uiState.test {
            val inicial = awaitItem()
            assertTrue(inicial.cargando)
            assertTrue(inicial.servicios.isEmpty())
            assertNull(inicial.errorRes)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `una emision del repositorio deja de cargar y muestra los servicios`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val corte = servicio(id = id, nombre = "Corte")

            viewModel.uiState.test {
                assertTrue(awaitItem().cargando)

                repositorio.emitir(listOf(corte))
                val emitido = awaitItem()

                assertEquals(listOf(corte), emitido.servicios)
                assertFalse(emitido.cargando)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `una segunda emision reemplaza la lista sin dejar la anterior`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val corte = servicio(nombre = "Corte")
            val coloracion = servicio(nombre = "Coloracion")

            viewModel.uiState.test {
                assertTrue(awaitItem().cargando)

                repositorio.emitir(listOf(corte))
                assertEquals(1, awaitItem().servicios.size)

                repositorio.emitir(listOf(corte, coloracion))
                assertEquals(listOf("Corte", "Coloracion"), awaitItem().servicios.map { it.nombre })

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `un error de lectura se traduce al string de error generico y deja de cargar`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.uiState.test {
                assertTrue(awaitItem().cargando)

                repositorio.fallarLectura(IllegalStateException("fallo de sqlite"))
                val conError = awaitItem()

                assertEquals(R.string.error_generico, conError.errorRes)
                assertFalse(conError.cargando)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `un error de lectura no deja el scope del ViewModel cancelado`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repositorio.fallarLectura(IllegalStateException("fallo de sqlite"))
            advanceUntilIdle()
            assertEquals(R.string.error_generico, viewModel.uiState.value.errorRes)

            // Si el `catch` hubiera cancelado el scope, este launch no se ejecutaria y el
            // error de validacion nunca llegaria al estado.
            viewModel.crear(nombre = "  ", duracionMin = 30, precioCentavos = null, color = COLOR_BASE)
            advanceUntilIdle()

            assertEquals(R.string.error_nombre_vacio, viewModel.uiState.value.errorRes)
        }

    @Test
    fun `un alta valida no deja error en el estado`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.crear(nombre = "Corte", duracionMin = 30, precioCentavos = 5_000L, color = COLOR_BASE)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.errorRes)
        assertEquals(listOf("Corte"), repositorio.vivos().map { it.nombre })
    }

    @Test
    fun `un alta con nombre en blanco setea el error de nombre vacio`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.crear(nombre = "   ", duracionMin = 30, precioCentavos = null, color = COLOR_BASE)
            advanceUntilIdle()

            assertEquals(R.string.error_nombre_vacio, viewModel.uiState.value.errorRes)
        }

    @Test
    fun `un alta con precio negativo setea el error de precio invalido`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.crear(nombre = "Corte", duracionMin = 30, precioCentavos = -1L, color = COLOR_BASE)
            advanceUntilIdle()

            assertEquals(R.string.error_precio_invalido, viewModel.uiState.value.errorRes)
        }

    @Test
    fun `un fallo del repositorio al dar de alta setea el error generico`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repositorio.falloCrear = IllegalStateException("Room no pudo insertar")

            viewModel.crear(nombre = "Corte", duracionMin = 30, precioCentavos = null, color = COLOR_BASE)
            advanceUntilIdle()

            assertEquals(R.string.error_generico, viewModel.uiState.value.errorRes)
        }

    @Test
    fun `editar un servicio inexistente setea el error de servicio inexistente`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.actualizar(id, nombre = "Corte", duracionMin = 30, precioCentavos = null, color = COLOR_BASE)
            advanceUntilIdle()

            assertEquals(R.string.error_servicio_no_existe, viewModel.uiState.value.errorRes)
        }

    @Test
    fun `editar un servicio existente no deja error en el estado`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repositorio.guardar(servicio(id = id))
            reloj.instante = INSTANTE_BASE.plusSeconds(60)

            viewModel.actualizar(id, nombre = "Corte corto", duracionMin = 20, precioCentavos = null, color = COLOR_BASE)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.errorRes)
            assertEquals("Corte corto", repositorio.vivos().single().nombre)
        }

    @Test
    fun `eliminar un servicio existente lo marca como borrado logico`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repositorio.guardar(servicio(id = id))

            viewModel.eliminar(id)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.errorRes)
            assertEquals(reloj.instante, repositorio.almacenado(id).deletedAt)
        }

    @Test
    fun `eliminar un servicio ya eliminado setea el error de servicio inexistente`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repositorio.guardar(servicio(id = id, deletedAt = INSTANTE_BASE))

            viewModel.eliminar(id)
            advanceUntilIdle()

            assertEquals(R.string.error_servicio_no_existe, viewModel.uiState.value.errorRes)
        }

    @Test
    fun `onErrorMostrado limpia el error del estado`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.crear(nombre = "", duracionMin = 30, precioCentavos = null, color = COLOR_BASE)
        advanceUntilIdle()
        assertEquals(R.string.error_nombre_vacio, viewModel.uiState.value.errorRes)

        viewModel.onErrorMostrado()

        assertNull(viewModel.uiState.value.errorRes)
    }
}