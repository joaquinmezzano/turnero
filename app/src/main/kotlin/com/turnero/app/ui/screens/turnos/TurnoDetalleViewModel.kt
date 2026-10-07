package com.turnero.app.ui.screens.turnos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.turnero.app.core.datetime.ClockProvider
import com.turnero.app.domain.model.AccionTurno
import com.turnero.app.domain.model.accionesDisponibles
import com.turnero.app.R
import com.turnero.app.domain.usecase.CambiarEstadoTurnoUseCase
import com.turnero.app.domain.usecase.ObtenerClientesUseCase
import com.turnero.app.domain.usecase.ObtenerTurnoConServicioPorIdUseCase
import com.turnero.app.ui.navigation.TurnoDetalle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Ficha de un turno.
 *
 * La UI no decide qué acciones muestra: la máquina de estados (`accionesDisponibles`) es la
 * única fuente de verdad, y la pantalla pinta exactamente lo que devuelve. `LIBERAR` es un
 * soft delete y lo ejecuta `CambiarEstadoTurnoUseCase`, que vuelve a validar la transición
 * antes de escribir.
 */
@HiltViewModel
class TurnoDetalleViewModel @Inject constructor(
    private val obtenerTurnoConServicioPorId: ObtenerTurnoConServicioPorIdUseCase,
    private val obtenerClientes: ObtenerClientesUseCase,
    private val cambiarEstado: CambiarEstadoTurnoUseCase,
    private val clock: ClockProvider,
    estadoGuardado: SavedStateHandle,
) : ViewModel() {

    private val clave = TurnoDetalle.serializer().descriptor.getElementName(0)
    private val turnoId: UUID = UUID.fromString(
        requireNotNull(estadoGuardado.get<String>(clave)) { "Falta turnoId" },
    )

    private val _uiState = MutableStateFlow(TurnoDetalleUiState())
    val uiState: StateFlow<TurnoDetalleUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    /**
     * Lee el turno **con el nombre de su servicio**, el nombre del cliente y la máquina de
     * estados.
     *
     * Se llama al entrar y después de cada acción, porque la ficha no está suscrita a la
     * tabla de turnos y el use case es de un solo valor.
     *
     * El servicio sale del `JOIN` del repositorio, que **no filtra `servicios.deletedAt`**:
     * así la ficha de un turno de un servicio ya dado de baja sigue mostrando de qué
     * servicio fue (ver `TurnoConServicio`). No se cruza contra `ObtenerServiciosUseCase`
     * porque eso resucitaría el bug: un servicio borrado devolvería nombre vacío.
     *
     * El catálogo de clientes sí se lee del catálogo vivo, con `first()` y **no** con
     * `collect`: es un flujo de Room que nunca completa, y un `collect` por llamada dejaría
     * un colector vivo por acción, con el snapshot del turno capturado en su `onSuccess`.
     * Cuando el catálogo re-emitiera, esos colectores viejos empujarían al estado un turno
     * de una versión anterior y la ficha reverdecería con acciones que después fallan con
     * `TransicionInvalida`. Con `first()` la corrutina termina al terminar la carga.
     */
    fun cargar() {
        viewModelScope.launch {
            obtenerTurnoConServicioPorId(turnoId).onSuccess { conServicio ->
                if (conServicio == null) {
                    _uiState.update {
                        it.copy(turno = null, cargando = false, errorRes = R.string.error_turno_no_existe)
                    }
                    return@onSuccess
                }
                val turno = conServicio.turno
                val clientes = obtenerClientes().first()
                val clienteNombre = clientes.find { c -> c.id == turno.clienteId }?.nombre.orEmpty()
                val acciones = accionesDisponibles(turno, clock.now())
                _uiState.update {
                    it.copy(
                        turno = turno,
                        clienteNombre = clienteNombre,
                        servicioNombre = conServicio.servicioNombre,
                        acciones = acciones,
                        cargando = false,
                        errorRes = null,
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(cargando = false, errorRes = R.string.error_generico) }
            }
        }
    }

    fun ejecutarAccion(accion: AccionTurno) {
        viewModelScope.launch {
            cambiarEstado(turnoId, accion)
                .onSuccess { cargar() }
                .onFailure { _uiState.update { s -> s.copy(errorRes = it.aErrorResTurno()) } }
        }
    }

    fun onErrorMostrado() {
        _uiState.update { it.copy(errorRes = null) }
    }
}