package com.turnero.app.ui.screens.clientes

import app.cash.turbine.test
import com.turnero.app.R
import com.turnero.app.domain.usecase.ActualizarClienteUseCase
import com.turnero.app.domain.usecase.CrearClienteUseCase
import com.turnero.app.domain.usecase.EliminarClienteUseCase
import com.turnero.app.domain.usecase.ObtenerClientesUseCase
import com.turnero.app.testing.FakeClockProvider
import com.turnero.app.testing.FakeClienteRepository
import com.turnero.app.testing.INSTANTE_BASE
import com.turnero.app.testing.MainDispatcherRule
import com.turnero.app.testing.cliente
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.UUID

/**
 * Tests del ViewModel con los use cases reales y el repositorio falso.
 *
 * Los use cases son clases concretas (no interfaces), así que no se pueden mockear sin
 * `mockk`. Montarlos de verdad con el fake cuesta tres líneas y cubre el cableado
 * completo ViewModel -> use case -> repositorio, que es justo lo que este slice prueba.
 *
 * El ViewModel se arma en `@BeforeEach` y no como propiedad: `MainDispatcherRule` corre en
 * `BeforeEachCallback`, que Jupiter ejecuta **antes** que los métodos `@BeforeEach`.
 * Construirlo en el inicializador de propiedades sería hacerlo con el `Main` real y
 * `viewModelScope` caería al dispatcher del sistema.
 *
 * **Los dos errores son campos distintos, y casi todos los tests de acá los usan.** `errorRes`
 * es el snackbar de escritura, de un solo uso: lo muestra el `LaunchedEffect` del Route y
 * lo borra `onErrorMostrado()`. `errorCargaRes` es el estado de pantalla del `catch` de
 * lectura, y no se borra solo. Cuando compartían campo, el borrado posterior al snackbar
 * se llevaba también el error de carga y el listado caía en el estado vacío diciendo
 * "todavía no cargaste clientes", que es una afirmación falsa. Por eso, salvo que el test
 * esté probando justamente la separación, se afirma en los **dos** campos: afirmar en uno
 * solo deja pasar el bug entero.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClientesViewModelTest {

    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    private val id = UUID.fromString("c3e5f7a9-4b6c-4d8e-9fa0-1b2c3d4e5f03")

    private lateinit var repositorio: FakeClienteRepository
    private lateinit var reloj: FakeClockProvider
    private lateinit var viewModel: ClientesViewModel

    @BeforeEach
    fun crearViewModel() {
        repositorio = FakeClienteRepository()
        reloj = FakeClockProvider()
        viewModel = ClientesViewModel(
            obtenerClientes = ObtenerClientesUseCase(repositorio),
            crearCliente = CrearClienteUseCase(repositorio, reloj),
            actualizarCliente = ActualizarClienteUseCase(repositorio, reloj),
            eliminarCliente = EliminarClienteUseCase(repositorio, reloj),
        )
    }

    @Test
    fun `el estado inicial viene cargando y sin clientes`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.uiState.test {
                val inicial = awaitItem()
                assertTrue(inicial.cargando)
                assertTrue(inicial.clientes.isEmpty())
                assertEquals("", inicial.consulta)
                assertNull(inicial.errorRes)
                assertNull(inicial.errorCargaRes)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `una emision del repositorio deja de cargar y muestra los clientes`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val ana = cliente(id = id, nombre = "Pérez, Ana")

            viewModel.uiState.test {
                assertTrue(awaitItem().cargando)

                repositorio.emitir(listOf(ana))
                val emitido = awaitItem()

                assertEquals(listOf(ana), emitido.clientes)
                assertFalse(emitido.cargando)
                assertNull(emitido.errorCargaRes)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `la consulta escribe en el estado sin volver a poner cargando`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // El filtro corre en memoria sobre la lista ya cargada: volver a `cargando =
            // true` haría parpadear el spinner en cada tecla, que es lo que el usuario
            // percibe como "la app se lags".
            repositorio.emitir(listOf(cliente(nombre = "Pérez, Ana")))
            advanceUntilIdle()

            viewModel.onConsultaCambiada("pe")
            advanceUntilIdle()

            assertEquals("pe", viewModel.uiState.value.consulta)
            assertFalse(viewModel.uiState.value.cargando)
        }

    @Test
    fun `escribir la consulta refiltra la lista ya cargada`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val ana = cliente(nombre = "Pérez, Ana")
            val tomas = cliente(nombre = "Tomás")
            repositorio.emitir(listOf(ana, tomas))
            advanceUntilIdle()
            assertEquals(2, viewModel.uiState.value.clientes.size)

            viewModel.onConsultaCambiada("toma")
            // El `flatMapLatest` corre en el dispatcher de test: sin este `advanceUntilIdle`
            // el colector viejo sigue siendo el que escucha y se lleva el `emitir` de
            // abajo, y el colector nuevo nunca ve nada.
            advanceUntilIdle()

            repositorio.emitir(listOf(ana, tomas))
            advanceUntilIdle()

            // Se afirma sobre `value` y no con `Turbine` a propósito: `onConsultaCambiada`
            // emite un estado intermedio (la consulta nueva con la lista todavía sin
            // filtrar) antes de que el `flatMapLatest` re-suscriba y llegue el filtrado.
            // Con `awaitItem()` se leería ese intermedio y el test fallaría sin que haya
            // ningún bug; `advanceUntilIdle()` deja que se asiente.
            //
            // La segunda `emitir` no es un rodeo: el `flatMapLatest` abre una suscripción
            // nueva al `Flow` del repositorio, y `FakeClienteRepository` es un `Channel`,
            // o sea frío. Room en cambio reemite el snapshot actual a cada nuevo
            // colector; el fake no, y por eso hay que volver a empujar la lista.
            assertEquals("toma", viewModel.uiState.value.consulta)
            assertEquals(listOf(tomas), viewModel.uiState.value.clientes)
        }

    // ------------------------------------------------------- error de LECTURA

    @Test
    fun `un error de lectura llena el error de carga y no el del snackbar`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.uiState.test {
                assertTrue(awaitItem().cargando)

                repositorio.fallarLectura(IllegalStateException("fallo de sqlite"))
                val conError = awaitItem()

                // `errorCargaRes` y no `errorRes`: el Route borra `errorRes` apenas mostró
                // el snackbar, y un error de carga no puede ser transitorio.
                assertEquals(R.string.error_generico, conError.errorCargaRes)
                // Y el snackbar tiene que seguir vacío: si este fallo landingara en
                // `errorRes`, el `LaunchedEffect` lo mostraría y lo borraría, la
                // recomposición caería en la lista vacía y el usuario leería "todavía no
                // cargaste clientes" después de un fallo de la base.
                assertNull(conError.errorRes)
                assertFalse(conError.cargando)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onErrorMostrado no se lleva el error de carga`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // El corazón de la regresión. Con un solo campo de error, este `onErrorMostrado`
            // —que el Route llama apenas mostró el snackbar— borraba también el error de
            // carga, y la pantalla se caía al estado vacío mintiendo que no había nada.
            repositorio.fallarLectura(IllegalStateException("fallo de sqlite"))
            advanceUntilIdle()
            assertEquals(R.string.error_generico, viewModel.uiState.value.errorCargaRes)

            viewModel.onErrorMostrado()

            // Sigue ahí: el error de carga no es de un solo uso, y su única salida es
            // `reintentar()`.
            assertEquals(R.string.error_generico, viewModel.uiState.value.errorCargaRes)
        }

    @Test
    fun `un error de lectura no deja el scope del ViewModel cancelado`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repositorio.fallarLectura(IllegalStateException("fallo de sqlite"))
            advanceUntilIdle()
            assertEquals(R.string.error_generico, viewModel.uiState.value.errorCargaRes)

            // Si el `catch` hubiera cancelado el scope, este launch no se ejecutaría y el
            // error de validación nunca llegaría al estado.
            viewModel.crear(nombre = "  ", telefono = null, email = null, notas = null)
            advanceUntilIdle()

            assertEquals(R.string.error_cliente_nombre_vacio, viewModel.uiState.value.errorRes)
        }

    @Test
    fun `un error de lectura y uno de escritura conviven en campos distintos`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // El `catch` de lectura es terminal pero no tapa lo que venga después: el
            // snackbar de una escritura fallida se muestra igual, sobre la pantalla de
            // error de carga.
            repositorio.fallarLectura(IllegalStateException("fallo de sqlite"))
            advanceUntilIdle()
            viewModel.crear(nombre = "   ", telefono = null, email = null, notas = null)
            advanceUntilIdle()

            val estado = viewModel.uiState.value
            assertEquals(R.string.error_generico, estado.errorCargaRes)
            assertEquals(R.string.error_cliente_nombre_vacio, estado.errorRes)
        }

    // ------------------------------------------------------- reintentar

    @Test
    fun `reintentar limpia el error de carga y vuelve a leer la lista`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val ana = cliente(id = id, nombre = "Pérez, Ana")

            viewModel.uiState.test {
                assertTrue(awaitItem().cargando)

                repositorio.fallarLectura(IllegalStateException("fallo de sqlite"))
                assertEquals(R.string.error_generico, awaitItem().errorCargaRes)

                viewModel.reintentar()
                val reintentando = awaitItem()
                assertTrue(reintentando.cargando)
                assertNull(reintentando.errorCargaRes)

                // La base volvió a andar: el listado se repuebla y el error desaparece, sin
                // que nadie haya recreado la pantalla.
                repositorio.emitir(listOf(ana))
                val recuperado = awaitItem()
                assertEquals(listOf(ana), recuperado.clientes)
                assertFalse(recuperado.cargando)
                assertNull(recuperado.errorCargaRes)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `reintentar cancela la coleccion anterior y no deja dos colectores vivos`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repositorio.emitir(listOf(cliente(id = id)))
            advanceUntilIdle()
            assertEquals(1, repositorio.coleccionesActivas)

            repositorio.fallarLectura(IllegalStateException("fallo de sqlite"))
            advanceUntilIdle()
            // El `catch` es terminal, así que la colección terminó: quedó en cero y no
            // colgada leyendo un canal que ya no va a emitir más.
            assertEquals(0, repositorio.coleccionesActivas)

            viewModel.reintentar()
            advanceUntilIdle()

            // 1 y no 2, y dos colecciones iniciadas en total. `observar()` cancela
            // `trabajoLectura` antes de armar la nueva: es lo que impide que el
            // ViewModel vaya acumulando suscriptores al mismo stream, cada uno capaz de
            // escribir su propia `catch` sobre el estado.
            assertEquals(1, repositorio.coleccionesActivas)
            assertEquals(2, repositorio.coleccionesIniciadas)
        }

    @Test
    fun `reintentar dos veces no acumula colecciones vivas`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // El caso donde la cancelación se nota de verdad. Después de un `catch` la
            // colección anterior ya está muerta, así que un `reintentar()` sin `cancel()`
            // no se notaría; la acumulación aparece cuando la anterior **sigue viva**, y
            // con el botón "Reintentar" al alcance es fácil: dos toques, dos colecciones.
            repositorio.emitir(listOf(cliente(id = id)))
            advanceUntilIdle()
            repositorio.fallarLectura(IllegalStateException("fallo de sqlite"))
            advanceUntilIdle()

            viewModel.reintentar()
            advanceUntilIdle()
            viewModel.reintentar()
            advanceUntilIdle()

            assertEquals(1, repositorio.coleccionesActivas)
            assertEquals(3, repositorio.coleccionesIniciadas)
        }

    @Test
    fun `reintentar no se lleva el error de escritura pendiente del snackbar`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // Los dos campos son independientes: reintentar es la salida del error de
            // carga, no la del snackbar. Si se llevara `errorRes`, el Route no llegaría a
            // mostrar el error de escritura que ya estaba en el estado.
            repositorio.fallarLectura(IllegalStateException("fallo de sqlite"))
            advanceUntilIdle()
            viewModel.crear(nombre = "   ", telefono = null, email = null, notas = null)
            advanceUntilIdle()

            viewModel.reintentar()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.errorCargaRes)
            assertEquals(R.string.error_cliente_nombre_vacio, viewModel.uiState.value.errorRes)
        }

    // ------------------------------------------------------- error de ESCRITURA

    @Test
    fun `un error de escritura llena el error del snackbar y no el de carga`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // Todo lo contrario del `catch` de lectura: acá el error es transitorio y lo
            // muestra un snackbar. Si fuera a `errorCargaRes` reemplazaría la pantalla
            // entera por un alta que falló, cuando la lista que ya estaba cargada sigue
            // siendo válida.
            viewModel.crear("Ana", null, "esto-no-es-un-email", null)
            advanceUntilIdle()

            val estado = viewModel.uiState.value
            assertEquals(R.string.error_email_invalido, estado.errorRes)
            assertNull(estado.errorCargaRes)
        }

    @Test
    fun `un fallo del repositorio al eliminar llena el error del snackbar y no el de carga`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repositorio.guardar(cliente(id = id))
            repositorio.falloObtenerPorId = IllegalStateException("Room no pudo leer")

            viewModel.eliminar(id)
            advanceUntilIdle()

            val estado = viewModel.uiState.value
            assertEquals(R.string.error_generico, estado.errorRes)
            assertNull(estado.errorCargaRes)
            // Y la fila sigue viva: un fallo de lectura en la baja no da de baja al cliente.
            assertNull(repositorio.almacenado(id).deletedAt)
        }

    @Test
    fun `un fallo del repositorio al editar llena el error del snackbar y no el de carga`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repositorio.guardar(cliente(id = id))
            repositorio.falloActualizar = IllegalStateException("Room no pudo actualizar")

            viewModel.actualizar(id, "Pérez, Ana María", null, null, null)
            advanceUntilIdle()

            val estado = viewModel.uiState.value
            assertEquals(R.string.error_generico, estado.errorRes)
            assertNull(estado.errorCargaRes)
            assertEquals("Pérez, Ana", repositorio.almacenado(id).nombre)
        }

    @Test
    fun `onErrorMostrado limpia el error del estado`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.crear(nombre = "  ", telefono = null, email = null, notas = null)
            advanceUntilIdle()
            assertEquals(R.string.error_cliente_nombre_vacio, viewModel.uiState.value.errorRes)

            // Sin esto el `LaunchedEffect` del Route volvería a mostrar el mismo snackbar
            // en cada recomposición, porque el `errorRes` no cambiaría de valor.
            viewModel.onErrorMostrado()

            assertNull(viewModel.uiState.value.errorRes)
        }

    // ------------------------------------------------------- escrituras sanas

    @Test
    fun `un alta valida no deja error en el estado`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.crear("Pérez, Ana", "+54 11 5555-0100", "ana@example.com", null)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.errorRes)
            assertNull(viewModel.uiState.value.errorCargaRes)
            assertEquals(listOf("Pérez, Ana"), repositorio.vivos().map { it.nombre })
        }

    @Test
    fun `un alta con nombre en blanco setea el error de nombre vacio`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.crear("   ", null, null, null)
            advanceUntilIdle()

            assertEquals(R.string.error_cliente_nombre_vacio, viewModel.uiState.value.errorRes)
            assertNull(viewModel.uiState.value.errorCargaRes)
            assertTrue(repositorio.vivos().isEmpty())
        }

    @Test
    fun `un alta con email invalido setea el error de email`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.crear("Ana", null, "esto-no-es-un-email", null)
            advanceUntilIdle()

            assertEquals(R.string.error_email_invalido, viewModel.uiState.value.errorRes)
        }

    @Test
    fun `un alta con telefono invalido setea el error de telefono`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.crear("Ana", "llamar 5555", null, null)
            advanceUntilIdle()

            assertEquals(R.string.error_telefono_invalido, viewModel.uiState.value.errorRes)
        }

    @Test
    fun `un fallo del repositorio al dar de alta setea el error generico`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repositorio.falloCrear = IllegalStateException("Room no pudo insertar")

            viewModel.crear("Ana", null, null, null)
            advanceUntilIdle()

            assertEquals(R.string.error_generico, viewModel.uiState.value.errorRes)
        }

    @Test
    fun `editar un cliente inexistente setea el error de cliente inexistente`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.actualizar(id, "Ana", null, null, null)
            advanceUntilIdle()

            assertEquals(R.string.error_cliente_no_existe, viewModel.uiState.value.errorRes)
        }

    @Test
    fun `editar un cliente existente no deja error en el estado`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repositorio.guardar(cliente(id = id))
            reloj.instante = INSTANTE_BASE.plusSeconds(60)

            viewModel.actualizar(id, "Pérez, Ana María", null, null, null)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.errorRes)
            assertEquals("Pérez, Ana María", repositorio.vivos().single().nombre)
        }

    @Test
    fun `eliminar un cliente existente no deja error en el estado`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repositorio.guardar(cliente(id = id))

            viewModel.eliminar(id)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.errorRes)
            assertTrue(repositorio.vivos().isEmpty())
            assertEquals(INSTANTE_BASE, repositorio.almacenado(id).deletedAt)
        }

    @Test
    fun `eliminar un cliente inexistente setea el error de cliente inexistente`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.eliminar(id)
            advanceUntilIdle()

            assertEquals(R.string.error_cliente_no_existe, viewModel.uiState.value.errorRes)
        }

    // ------------------------------------------------------- ciclo de vida

    @Test
    fun `una escritura valida no pisa un error de carga que sigue en pie`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // El `collect` es lo único que limpia `errorCargaRes`, y con el stream roto no
            // hay emissions. Si un alta OK lo borrara, un `reintentar()` posterior creería
            // estar limpio cuando en realidad nadie volvió a leer.
            repositorio.fallarLectura(IllegalStateException("fallo de sqlite"))
            advanceUntilIdle()

            viewModel.crear("Pérez, Ana", null, null, null)
            advanceUntilIdle()

            assertNotNull(viewModel.uiState.value.errorCargaRes)
            assertNull(viewModel.uiState.value.errorRes)
        }
}
