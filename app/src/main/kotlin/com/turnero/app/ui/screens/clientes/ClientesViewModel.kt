package com.turnero.app.ui.screens.clientes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.turnero.app.domain.usecase.ActualizarClienteUseCase
import com.turnero.app.domain.usecase.CrearClienteUseCase
import com.turnero.app.domain.usecase.EliminarClienteUseCase
import com.turnero.app.domain.usecase.ObtenerClientesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel del listado de clientes.
 *
 * Solo invoca use cases, nunca el repositorio (ver AGENTS.md). El estado vive acá; el
 * composable recibe el `ClientesUiState` y callbacks, y no sabe que existen.
 */
@HiltViewModel
class ClientesViewModel @Inject constructor(
    private val obtenerClientes: ObtenerClientesUseCase,
    private val crearCliente: CrearClienteUseCase,
    private val actualizarCliente: ActualizarClienteUseCase,
    private val eliminarCliente: EliminarClienteUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClientesUiState())
    val uiState: StateFlow<ClientesUiState> = _uiState.asStateFlow()

    /**
     * Colección de lectura viva, para poder cancelarla al reintentar.
     *
     * El `Job` de `viewModelScope` alcanza: `viewModelScope` cancela por sí solo cuando
     * el ViewModel se limpia.
     */
    private var trabajoLectura: Job? = null

    init {
        observar()
    }

    /**
     * Levanta la cadena de lectura.
     *
     * Vive en una función y no en el `init` para que [reintentar] pueda volver a
     * armarla: `catch` es **terminal**, así que un error transitorio de la base mataba
     * el `collect` para siempre y dejaba la pantalla muerta sin forma de recuperarse sin
     * recrearla.
     */
    private fun observar() {
        trabajoLectura?.cancel()
        trabajoLectura = viewModelScope.launch {
            _uiState
                // La consulta manda sobre el flujo. `distinctUntilChanged` es lo que
                // evita re-suscribirse al `Flow` de Room cuando la lista se actualiza:
                // cada emisión del `UiState` vuelve a pasar por acá, y sin el filtro el
                // `flatMapLatest` se rearmaría en cada alta, con la lista parpadeando.
                .map { it.consulta }
                .distinctUntilChanged()
                .flatMapLatest { consulta -> obtenerClientes(consulta) }
                // `catch` va antes de `collect`: así una falla de lectura no cancela el
                // scope del ViewModel y el error queda en el estado en vez de caer en el
                // CoroutineExceptionHandler. Un try/catch alrededor del collect no
                // serviría porque el error llega por el canal de excepciones del Flow.
                //
                // El error va a `errorCargaRes` y no a `errorRes`: `errorRes` lo consume
                // un `LaunchedEffect` que lo borra después de mostrar el snackbar, y
                // desde ahí el listado caería en el estado vacío diciendo "todavía no
                // cargaste clientes", que es mentira cuando lo que pasó fue un fallo.
                .catch { throwable ->
                    _uiState.update { it.copy(cargando = false, errorCargaRes = throwable.aErrorRes()) }
                }
                .collect { clientes ->
                    _uiState.update { it.copy(clientes = clientes, cargando = false, errorCargaRes = null) }
                }
        }
    }

    /**
     * Vuelve a leer desde cero tras un error de carga.
     *
     * Único camino de salida de [ClientesUiState.errorCargaRes].
     */
    fun reintentar() {
        _uiState.update { it.copy(cargando = true, errorCargaRes = null) }
        observar()
    }

    /**
     * Actualiza la búsqueda.
     *
     * No toca `cargando`: el filtro corre en memoria sobre la lista ya cargada, así que
     * volver a `true` haría parpadear el spinner en cada tecla.
     */
    fun onConsultaCambiada(texto: String) {
        _uiState.update { it.copy(consulta = texto) }
    }

    fun crear(nombre: String, telefono: String?, email: String?, notas: String?) {
        viewModelScope.launch {
            crearCliente(nombre, telefono, email, notas)
                .onFailure { mostrarError(it) }
        }
    }

    fun actualizar(id: UUID, nombre: String, telefono: String?, email: String?, notas: String?) {
        viewModelScope.launch {
            actualizarCliente(id, nombre, telefono, email, notas)
                .onFailure { mostrarError(it) }
        }
    }

    fun eliminar(id: UUID) {
        viewModelScope.launch {
            eliminarCliente(id).onFailure { mostrarError(it) }
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
