package com.turnero.app.ui.screens.turnos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.turnero.app.core.datetime.ClockProvider
import com.turnero.app.core.datetime.ZonaHorariaProvider
import com.turnero.app.domain.usecase.ActualizarTurnoUseCase
import com.turnero.app.domain.usecase.CrearTurnoUseCase
import com.turnero.app.domain.usecase.ObtenerClientesUseCase
import com.turnero.app.domain.usecase.ObtenerServiciosUseCase
import com.turnero.app.domain.usecase.ObtenerTurnoPorIdUseCase
import com.turnero.app.R
import com.turnero.app.ui.navigation.TurnoEditor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

/**
 * Editor de turno: alta y edición comparten pantalla.
 *
 * La hora viene **fijada** por la navegación (anclaje a la hora exacta, ver spec): no hay
 * un slider libre, la grilla y la ficha del cliente pasan la hora que ya eligieron.
 */
@HiltViewModel
class TurnoEditorViewModel @Inject constructor(
    private val obtenerClientes: ObtenerClientesUseCase,
    private val obtenerServicios: ObtenerServiciosUseCase,
    private val obtenerTurnoPorId: ObtenerTurnoPorIdUseCase,
    private val crearTurno: CrearTurnoUseCase,
    private val actualizarTurno: ActualizarTurnoUseCase,
    private val clock: ClockProvider,
    private val zonaHoraria: ZonaHorariaProvider,
    estadoGuardado: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        TurnoEditorUiState(
            fecha = clock.now().atZone(zonaHoraria.zona()).toLocalDate(),
        ),
    )
    val uiState: StateFlow<TurnoEditorUiState> = _uiState.asStateFlow()

    private val guardado = Channel<Unit>(Channel.BUFFERED)
    val guardadoFlow: Flow<Unit> = guardado.receiveAsFlow()

    init {
        leerRuta(estadoGuardado)
        cargarCatalogos()
        cargarTurnoSiEdita()
    }

    private fun leerRuta(estadoGuardado: SavedStateHandle) {
        val n = TurnoEditor.serializer().descriptor
        val fecha = estadoGuardado.get<String>(n.getElementName(0))?.let(LocalDate::parse)
        val hora = estadoGuardado.get<String>(n.getElementName(1))?.let(LocalTime::parse)
        val clienteId = estadoGuardado.get<String>(n.getElementName(2))?.let(UUID::fromString)
        val turnoId = estadoGuardado.get<String>(n.getElementName(3))?.let(UUID::fromString)
        _uiState.update {
            it.copy(
                turnoId = turnoId,
                clienteId = clienteId,
                fecha = fecha ?: clock.now().atZone(zonaHoraria.zona()).toLocalDate(),
                hora = hora ?: it.hora,
            )
        }
    }

    private fun cargarCatalogos() {
        viewModelScope.launch {
            obtenerClientes().collect { clientes ->
                _uiState.update { it.copy(clientes = clientes) }
            }
        }
        viewModelScope.launch {
            obtenerServicios().collect { servicios ->
                _uiState.update { it.copy(servicios = servicios) }
            }
        }
    }

    private fun cargarTurnoSiEdita() {
        val id = _uiState.value.turnoId ?: return
        viewModelScope.launch {
            obtenerTurnoPorId(id).onSuccess { turno ->
                turno?.let {
                    val local = it.inicio.atZone(zonaHoraria.zona())
                    _uiState.update { s ->
                        s.copy(
                            fecha = local.toLocalDate(),
                            hora = local.toLocalTime(),
                            clienteId = it.clienteId,
                            servicioId = it.servicioId,
                            notas = it.notas.orEmpty(),
                        )
                    }
                }
            }
        }
    }

    fun actualizarCampos(
        clienteId: UUID? = null,
        servicioId: UUID? = null,
        notas: String? = null,
    ) {
        _uiState.update { s ->
            s.copy(
                clienteId = clienteId ?: s.clienteId,
                servicioId = servicioId ?: s.servicioId,
                notas = notas ?: s.notas,
            )
        }
    }

    fun cambiarFecha(fecha: LocalDate) {
        _uiState.update { it.copy(fecha = fecha) }
    }

    /** Cambia la hora a una de las 15 horas agendables (08:00–22:00). */
    fun cambiarHora(hora: LocalTime) {
        _uiState.update { it.copy(hora = hora) }
    }

    fun guardar() {
        val s = _uiState.value
        val clienteId = s.clienteId
        val servicioId = s.servicioId
        if (clienteId == null || servicioId == null) {
            // El botón de guardar está deshabilitado en este caso; defensivo.
            _uiState.update { it.copy(errorRes = R.string.error_generico) }
            return
        }
        val notas = s.notas.ifBlank { null }
        viewModelScope.launch {
            val resultado = if (s.turnoId == null) {
                crearTurno(clienteId, servicioId, s.fecha, s.hora, notas)
            } else {
                actualizarTurno(requireNotNull(s.turnoId), clienteId, servicioId, s.fecha, s.hora, notas)
            }
            resultado
                .onSuccess { guardado.send(Unit) }
                .onFailure { _uiState.update { estado -> estado.copy(errorRes = it.aErrorResTurno()) } }
        }
    }

    fun onErrorMostrado() {
        _uiState.update { it.copy(errorRes = null) }
    }
}
