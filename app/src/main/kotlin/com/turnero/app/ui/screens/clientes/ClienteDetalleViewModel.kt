package com.turnero.app.ui.screens.clientes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.turnero.app.R
import com.turnero.app.domain.usecase.ActualizarClienteUseCase
import com.turnero.app.domain.usecase.EliminarClienteUseCase
import com.turnero.app.domain.usecase.ObtenerClientePorIdUseCase
import com.turnero.app.ui.navigation.ClienteDetalle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel de la ficha de cliente.
 *
 * El id **no** es un parámetro del constructor: sale del `SavedStateHandle`, que Hilt arma
 * desde el `arguments` del `NavBackStackEntry`, o sea del argumento de la ruta type-safe.
 * Es el patrón documentado de AndroidX para navegación type-safe y es lo que hace que la
 * ficha sobreviva a un cambio de configuración sin volver a pedir el dato.
 *
 * Del argumento solo se lee `clienteId`, y se lee con [clienteIdDeLaRuta] y no con
 * `savedStateHandle["clienteId"]` a mano. Ver el KDoc de esas dos funciones: de dónde sale
 * la clave y por qué no se deserializa la ruta entera con `toRoute<ClienteDetalle>()`.
 */
@HiltViewModel
class ClienteDetalleViewModel @Inject constructor(
    private val obtenerClientePorId: ObtenerClientePorIdUseCase,
    private val actualizarCliente: ActualizarClienteUseCase,
    private val eliminarCliente: EliminarClienteUseCase,
    estadoGuardado: SavedStateHandle,
) : ViewModel() {

    private val clienteId: UUID = estadoGuardado.clienteIdDeLaRuta()

    private val _uiState = MutableStateFlow(ClienteDetalleUiState())
    val uiState: StateFlow<ClienteDetalleUiState> = _uiState.asStateFlow()

    /**
     * Avisa que la baja terminó, para que la pantalla vuelva al listado.
     *
     * Va por un `Channel` y no por un campo del `UiState` porque **no es estado de la
     * ficha**: si fuera un `Boolean` del estado, un cambio de configuración volvería a
     * disparar el "volver" y sacaría al usuario de la ficha recién borrada. El `Channel`
     * entrega el evento una sola vez y, como va bufferizado, tampoco se pierde si la
     * recomposición todavía no empezó a escucharlo.
     */
    private val eliminado = Channel<Unit>(Channel.BUFFERED)
    val eliminadoFlow: Flow<Unit> = eliminado.receiveAsFlow()

    init {
        cargar()
    }

    /**
     * Relee el cliente.
     *
     * Se vuelve a llamar después de editar: el `Flow` del listado no alcanza porque esta
     * pantalla no está suscrita a la tabla, y sin esto la ficha seguiría mostrando los
     * datos viejos al volver del diálogo.
     */
    fun cargar() {
        viewModelScope.launch {
            obtenerClientePorId(clienteId)
                .onSuccess { cliente ->
                    _uiState.update { estado ->
                        when {
                            // "No existe" y "estaba eliminado" llegan los dos como `null`
                            // (el DAO filtra `deletedAt IS NULL`) y la pantalla tiene que
                            // decir lo mismo: ese cliente ya no existe.
                            cliente == null -> estado.copy(
                                cliente = null,
                                cargando = false,
                                errorRes = R.string.error_cliente_no_existe,
                            )

                            else -> estado.copy(cliente = cliente, cargando = false, errorRes = null)
                        }
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(cargando = false, errorRes = throwable.aErrorRes()) }
                }
        }
    }

    fun actualizar(nombre: String, telefono: String?, email: String?, notas: String?) {
        viewModelScope.launch {
            actualizarCliente(clienteId, nombre, telefono, email, notas)
                .onSuccess { cargar() }
                .onFailure { mostrarError(it) }
        }
    }

    fun eliminar() {
        viewModelScope.launch {
            eliminarCliente(clienteId)
                .onSuccess { eliminado.send(Unit) }
                .onFailure { mostrarError(it) }
        }
    }

    /** Limpia el error una vez que la pantalla ya mostró el snackbar. */
    fun onErrorMostrado() {
        _uiState.update { it.copy(errorRes = null) }
    }

    private fun mostrarError(throwable: Throwable) {
        _uiState.update { it.copy(errorRes = throwable.aErrorRes()) }
    }
}

/**
 * Clave con la que la navegación deja el argumento de [ClienteDetalle] en el
 * `SavedStateHandle`.
 *
 * **Sale del descriptor de la ruta y no de un string escrito acá.** `navigation` numera los
 * argumentos de una ruta type-safe con `descriptor.getElementName(i)`: es de ahí salen la
 * clave con la que `RouteEncoder` arma el `Bundle` de argumentos y la que después lee
 * `RouteDecoder`. Renombrar el campo `clienteId` de [ClienteDetalle] renombra la clave, y
 * esta línea la sigue sin que nadie tenga que acordarse de actualizarla.
 */
private val claveArgumentoClienteId: String =
    ClienteDetalle.serializer().descriptor.getElementName(0)

/**
 * El `clienteId` del argumento de la ruta, reconstruido desde el texto canónico.
 *
 * **Llega como `String` y no como `UUID`.** `UuidSerializer` declara el descriptor de
 * `String`, así que la navegación lo serializa con `NavType.StringType` —que es un
 * `bundle.putString`— y lo deja en el `handle` como texto. `UUID.fromString` lo vuelve a
 * armar sin pérdida porque el formato canónico es reversible.
 *
 * **Por qué no `toRoute<ClienteDetalle>()`, que es lo idiomático.** Leería el mismo `String`
 * de la misma clave, pero `RouteDecoder` reconstruye un `android.os.Bundle` real con
 * `bundleOf(...)` para volver a sacar de ahí el valor que ya tenía a mano. En un test de
 * JVM ese `Bundle` no existe y el constructor moría con `Method putCharSequence in
 * android.os.Bundle not mocked`, dejando el ViewModel entero —el `Channel` de borrado y la
 * carrera post-edición incluidos— sin poder cubrir. Leyendo el argumento directo, el
 * ViewModel se construye en cualquier JVM.
 *
 * El argumento ausente se sigue tratando como error, como lo era con `toRoute` (que
 * tiraba `MissingFieldException`): es preferible romper en el constructor que seguir
 * adelante con un id vacío y buscar un cliente que no es el que se pidió.
 */
private fun SavedStateHandle.clienteIdDeLaRuta(): UUID {
    val argumento = get<String>(claveArgumentoClienteId)
    requireNotNull(argumento) {
        "El SavedStateHandle de ClienteDetalle no trae el argumento \"$claveArgumentoClienteId\""
    }
    return UUID.fromString(argumento)
}
