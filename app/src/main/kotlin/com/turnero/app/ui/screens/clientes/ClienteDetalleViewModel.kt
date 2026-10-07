package com.turnero.app.ui.screens.clientes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.turnero.app.R
import com.turnero.app.domain.usecase.ActualizarClienteUseCase
import com.turnero.app.domain.usecase.EliminarClienteUseCase
import com.turnero.app.domain.usecase.ObtenerClientePorIdUseCase
import com.turnero.app.domain.usecase.ObtenerEstadisticasClienteUseCase
import com.turnero.app.domain.usecase.ObtenerTurnosDeClienteUseCase
import com.turnero.app.ui.navigation.ClienteDetalle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ClienteDetalleViewModel @Inject constructor(
    private val obtenerClientePorId: ObtenerClientePorIdUseCase,
    private val actualizarCliente: ActualizarClienteUseCase,
    private val eliminarCliente: EliminarClienteUseCase,
    private val obtenerTurnosDeCliente: ObtenerTurnosDeClienteUseCase,
    private val obtenerEstadisticasCliente: ObtenerEstadisticasClienteUseCase,
    estadoGuardado: SavedStateHandle,
) : ViewModel() {

    private val clienteId: UUID = estadoGuardado.clienteIdDeLaRuta()

    private val _uiState = MutableStateFlow(ClienteDetalleUiState())
    val uiState: StateFlow<ClienteDetalleUiState> = _uiState.asStateFlow()

    private val eliminado = Channel<Unit>(Channel.BUFFERED)
    val eliminadoFlow: Flow<Unit> = eliminado.receiveAsFlow()

    init {
        cargar()
    }

    fun cargar() {
        viewModelScope.launch {
            obtenerClientePorId(clienteId)
                .onSuccess { cliente ->
                    _uiState.update { estado ->
                        when {
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
        viewModelScope.launch {
            combine(
                obtenerTurnosDeCliente(clienteId),
                obtenerEstadisticasCliente(clienteId),
            ) { turnos, estadisticas ->
                turnos to estadisticas
            }.collect { (turnos, estadisticas) ->
                _uiState.update { it.copy(turnos = turnos, estadisticas = estadisticas) }
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

    fun onErrorMostrado() {
        _uiState.update { it.copy(errorRes = null) }
    }

    private fun mostrarError(throwable: Throwable) {
        _uiState.update { it.copy(errorRes = throwable.aErrorRes()) }
    }
}

private val claveArgumentoClienteId: String =
    ClienteDetalle.serializer().descriptor.getElementName(0)

private fun SavedStateHandle.clienteIdDeLaRuta(): UUID {
    val argumento = get<String>(claveArgumentoClienteId)
    requireNotNull(argumento) {
        "El SavedStateHandle de ClienteDetalle no trae el argumento \"$claveArgumentoClienteId\""
    }
    return UUID.fromString(argumento)
}
