package com.turnero.app.ui.screens.clientes

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.turnero.app.R
import com.turnero.app.domain.usecase.ActualizarClienteUseCase
import com.turnero.app.domain.usecase.EliminarClienteUseCase
import com.turnero.app.domain.usecase.ObtenerClientePorIdUseCase
import com.turnero.app.testing.FakeClockProvider
import com.turnero.app.testing.FakeClienteRepository
import com.turnero.app.testing.INSTANTE_BASE
import com.turnero.app.testing.MainDispatcherRule
import com.turnero.app.testing.cliente
import com.turnero.app.ui.navigation.ClienteDetalle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.UUID

/**
 * Tests de la ficha de cliente.
 *
 * Los use cases son clases concretas, así que no se pueden mockear sin `mockk`. Montarlos de
 * verdad con el fake cubre el cableado ViewModel -> use case -> repositorio, que es lo que
 * importa acá: casi todo lo que hace este ViewModel es decidir **qué pasa con el `Result`**
 * que le devuelven (inexistente / borrado / fallo de la base / validación).
 *
 * **El ViewModel se construye en el cuerpo de cada test, no en `@BeforeEach`.** No es
 * estilo: `runTest` vacía la cola del `TestCoroutineScheduler` antes de la primera línea del
 * cuerpo, así que el `viewModelScope.launch { }` del `init` —encolado durante `@BeforeEach`—
 * ya corrió para cuando empieza el test. Armado en `@BeforeEach`, el ViewModel lee la base
 * **antes** de que el test prepare el fake, y los casos que pasan pasan por casualidad: el
 * `cargar()` inicial encontró el almacén vacío, dejó "no existe" y todas las afirmaciones
 * posteriores dan lo mismo. Con el ViewModel construido después de preparar el fake, cada
 * test lee lo que dice leer.
 *
 * Por la misma razón `el estado inicial viene cargando` no usa `advanceUntilIdle()`: el
 * `cargando = true` es el estado **antes** de que corra el dispatcher, y en cuanto el test
 * avanza el reloj ya no existe. Un `awaitItem().cargando` sobre el `StateFlow` tampoco lo
 * alcanza: la suscripción arranca después de la carga, y lo primero que ve es el estado final.
 *
 * **El `SavedStateHandle` se arma con [estadoGuardadoDe] y no a mano** porque la clave del
 * argumento la decide `navigation`, no la app: sale del nombre del elemento en el descriptor
 * de la ruta. Escribir `"clienteId" = ...` en el test lo ataría a una representación que
 * puede cambiar sin que nadie lo note, y el test pasaría en verde mientras la app no abre la
 * ficha.
 *
 * Ojo con lo que se está probando acá y no: que la navegación escriba ese `String` en el
 * `Bundle` de argumentos es un contrato de la librería que en un test de JVM no se puede
 * verificar. Lo que sí se verifica es el otro lado del cable —que el ViewModel lo lea de la
 * clave correcta y falle de verdad si no está— y `UuidSerializerTest` fija que el
 * serializador que la navegación usa para eso declara el descriptor de `String`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClienteDetalleViewModelTest {

    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    private val id = UUID.fromString("c3e5f7a9-4b6c-4d8e-9fa0-1b2c3d4e5f03")

    private lateinit var repositorio: FakeClienteRepository
    private lateinit var reloj: FakeClockProvider

    @BeforeEach
    fun crearFakes() {
        repositorio = FakeClienteRepository()
        reloj = FakeClockProvider()
    }

    // ------------------------------------------------------------ estado inicial

    @Test
    fun `el estado inicial viene cargando y sin cliente`() {
        // Sin `advanceUntilIdle()` a propósito: la carga inicial quedó encolada en el
        // dispatcher de test y todavía no corrió. Este es el estado que ve la pantalla entre
        // que se abre la ficha y que vuelve la primera lectura, y es el que distingue
        // "todavía no lo leí" de "lo leí y no está": con un solo campo, los dos serían la
        // misma pantalla.
        val viewModel = fichaDe()

        assertTrue(viewModel.uiState.value.cargando)
        assertNull(viewModel.uiState.value.cliente)
        assertNull(viewModel.uiState.value.errorRes)
    }

    // ------------------------------------------------------------ carga

    @Test
    fun `carga el cliente que esta en el argumento de la ruta`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val ana = cliente(id = id, nombre = "Pérez, Ana", telefono = "+54 11 5555-0100")
            repositorio.guardar(ana)
            val viewModel = fichaDe()
            advanceUntilIdle()

            viewModel.uiState.test {
                val cargado = awaitItem()
                assertEquals(ana, cargado.cliente)
                assertFalse(cargado.cargando)
                assertNull(cargado.errorRes)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `un id que no existe deja el estado de error de cliente inexistente`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // El DAO filtra `deletedAt IS NULL`, así que "nunca existió" y "ya estaba
            // eliminado" llegan los dos como `null` y tienen que mostrar lo mismo.
            val viewModel = fichaDe()
            advanceUntilIdle()

            viewModel.uiState.test {
                val conError = awaitItem()
                assertNull(conError.cliente)
                assertFalse(conError.cargando)
                assertEquals(R.string.error_cliente_no_existe, conError.errorRes)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `un cliente ya eliminado se muestra como inexistente`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repositorio.guardar(cliente(id = id, deletedAt = INSTANTE_BASE))
            val viewModel = fichaDe()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.cliente)
            assertEquals(R.string.error_cliente_no_existe, viewModel.uiState.value.errorRes)
        }

    @Test
    fun `un fallo de la base al cargar deja el error generico y no el de inexistente`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // La distinción importa: `null` es "no hay cliente" y un `Result.failure` es
            // "no pude preguntar". Mostrar "no existe" ante un fallo de SQLite le dice al
            // usuario que el registro se perdió.
            repositorio.falloObtenerPorId = IllegalStateException("Room no pudo leer")
            val viewModel = fichaDe()
            advanceUntilIdle()

            assertEquals(R.string.error_generico, viewModel.uiState.value.errorRes)
        }

    @Test
    fun `volver a cargar después de un error deja el estado limpio`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repositorio.falloObtenerPorId = IllegalStateException("Room no pudo leer")
            val viewModel = fichaDe()
            advanceUntilIdle()
            assertEquals(R.string.error_generico, viewModel.uiState.value.errorRes)

            repositorio.guardar(cliente(id = id, nombre = "Pérez, Ana"))
            repositorio.falloObtenerPorId = null
            viewModel.cargar()
            advanceUntilIdle()

            assertEquals("Pérez, Ana", viewModel.uiState.value.cliente?.nombre)
            assertNull(viewModel.uiState.value.errorRes)
            assertFalse(viewModel.uiState.value.cargando)
        }

    // ------------------------------------------------------------ edición

    @Test
    fun `editar recarga la ficha con los datos nuevos`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val ana = cliente(id = id, nombre = "Pérez, Ana")
            repositorio.guardar(ana)
            val viewModel = fichaDe()
            advanceUntilIdle()
            reloj.instante = INSTANTE_BASE.plusSeconds(60)

            viewModel.actualizar("Pérez, Ana María", null, "ana@example.com", "Turno a la tarde")
            advanceUntilIdle()

            val cliente = viewModel.uiState.value.cliente
            assertEquals("Pérez, Ana María", cliente?.nombre)
            // La edición normaliza lo que el usuario dejó en blanco a `null`, y el ViewModel
            // tiene que mostrar eso y no la cadena vacía que tenía el formulario.
            assertNull(cliente?.telefono)
            assertEquals("Turno a la tarde", cliente?.notas)
            // `createdAt` sobrevive: un `@Update` de Room pisa todas las columnas.
            assertEquals(ana.createdAt, cliente?.createdAt)
            assertNull(viewModel.uiState.value.errorRes)
        }

    @Test
    fun `editar con datos invalidos deja el error del snackbar y conserva la ficha cargada`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val ana = cliente(id = id, nombre = "Pérez, Ana")
            repositorio.guardar(ana)
            val viewModel = fichaDe()
            advanceUntilIdle()

            viewModel.actualizar("  ", null, null, null)
            advanceUntilIdle()

            // La ficha que ya estaba cargada sigue siendo válida: una edición inválida no la
            // borra, solo dispara el snackbar.
            assertEquals(R.string.error_cliente_nombre_vacio, viewModel.uiState.value.errorRes)
            assertEquals("Pérez, Ana", viewModel.uiState.value.cliente?.nombre)
            assertEquals(ana.nombre, repositorio.almacenado(id).nombre)
        }

    // ------------------------------------------------------------ baja

    @Test
    fun `eliminar avisa por el canal y marca el cliente como borrado logico`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repositorio.guardar(cliente(id = id, nombre = "Pérez, Ana"))
            val viewModel = fichaDe()
            advanceUntilIdle()

            // El evento va por un `Channel` y no por un campo del estado a propósito: un
            // `Boolean` en el `UiState` volvería a disparar el "volver" en cada cambio de
            // configuración y sacaría al usuario de una ficha que ya no existe.
            viewModel.eliminadoFlow.test {
                viewModel.eliminar()
                advanceUntilIdle()

                awaitItem()
                assertEquals(INSTANTE_BASE, repositorio.almacenado(id).deletedAt)
                assertTrue(repositorio.vivos().isEmpty())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `eliminar entrega el evento una sola vez`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repositorio.guardar(cliente(id = id))
            val viewModel = fichaDe()
            advanceUntilIdle()

            viewModel.eliminadoFlow.test {
                viewModel.eliminar()
                advanceUntilIdle()
                awaitItem()

                // Un `Channel` se consume: si el evento se pudiera leer dos veces, la
                // pantalla intentaría volver dos veces al listado.
                expectNoEvents()

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `eliminar un cliente ya eliminado no avisa que se dio de baja`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // No se manda nada al `Channel`, y el error va al estado: la pantalla se queda
            // donde está mostrando el motivo.
            repositorio.guardar(cliente(id = id, deletedAt = INSTANTE_BASE))
            val viewModel = fichaDe()
            advanceUntilIdle()

            viewModel.eliminadoFlow.test {
                viewModel.eliminar()
                advanceUntilIdle()

                expectNoEvents()
                assertEquals(R.string.error_cliente_no_existe, viewModel.uiState.value.errorRes)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onErrorMostrado limpia el error de la ficha`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repositorio.guardar(cliente(id = id, nombre = "Pérez, Ana"))
            val viewModel = fichaDe()
            advanceUntilIdle()
            viewModel.eliminar()
            advanceUntilIdle()
            viewModel.eliminar()
            advanceUntilIdle()
            assertEquals(R.string.error_cliente_no_existe, viewModel.uiState.value.errorRes)

            viewModel.onErrorMostrado()

            assertNull(viewModel.uiState.value.errorRes)
        }

    // ------------------------------------------------------------ argumento de la ruta

    @Test
    fun `el argumento de la ruta se llama como el campo del modelo`() {
        // La clave no la elige la app: `navigation` la saca de
        // `descriptor.getElementName(i)`, y el ViewModel la lee de ahí. Si alguien renombra
        // `clienteId` en [ClienteDetalle] o le pone un `@SerialName`, esta es la que lo
        // avisa — el ViewModel seguiría funcionando y la ficha, no.
        assertEquals("clienteId", ClienteDetalle.serializer().descriptor.getElementName(0))
    }

    @Test
    fun `un SavedStateHandle sin el argumento rompe en el constructor`() {
        // Leer el argumento a mano puede devolver `null` en silencio, y el peor resultado
        // posible de eso no es una pantalla vacía: es buscar y editar un cliente que no es el
        // que se pidió. Romper en el constructor es la opción buena.
        val error = assertThrows<IllegalArgumentException> {
            ClienteDetalleViewModel(
                obtenerClientePorId = ObtenerClientePorIdUseCase(repositorio),
                actualizarCliente = ActualizarClienteUseCase(repositorio, reloj),
                eliminarCliente = EliminarClienteUseCase(repositorio, reloj),
                estadoGuardado = SavedStateHandle(emptyMap<String, Any?>()),
            )
        }

        assertTrue(
            error.message.orEmpty().contains("clienteId"),
            "El error tiene que decir qué argumento falta, no solo que falta: ${error.message}",
        )
    }

    // ------------------------------------------------------------ helpers

    /**
     * La ficha lista para probar, con el `SavedStateHandle` que arma la navegación.
     *
     * Se construye acá y no como propiedad para que el test pueda dejar el fake en el estado
     * que necesita **antes** de que exista el `cargar()` inicial. Ver el KDoc de la clase.
     */
    private fun fichaDe(clienteId: UUID = id): ClienteDetalleViewModel = ClienteDetalleViewModel(
        obtenerClientePorId = ObtenerClientePorIdUseCase(repositorio),
        actualizarCliente = ActualizarClienteUseCase(repositorio, reloj),
        eliminarCliente = EliminarClienteUseCase(repositorio, reloj),
        estadoGuardado = estadoGuardadoDe(clienteId),
    )

    /**
     * El `SavedStateHandle` tal como lo arma la navegación para [ClienteDetalle].
     *
     * La clave sale del **serializer de la ruta**, no de un string escrito acá: es el
     * `getElementName` del descriptor, que es exactamente de donde `NavHost` saca el nombre
     * del argumento. Si el test escribiera `"clienteId" = ...` a mano, pasaría en verde
     * aunque la navegación mandara otra clave.
     *
     * El valor va como texto canónico porque `UuidSerializer` declara el descriptor de
     * `String`: es lo que `NavType.StringType` escribe en el `Bundle` y lo que
     * `UUID.fromString` vuelve a reconstruir sin pérdida.
     */
    private fun estadoGuardadoDe(clienteId: UUID): SavedStateHandle {
        val descriptor = ClienteDetalle.serializer().descriptor
        return SavedStateHandle(
            mapOf(descriptor.getElementName(0) to clienteId.toString()),
        )
    }
}
