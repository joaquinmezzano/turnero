package com.turnero.app.ui.screens.servicios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.turnero.app.domain.usecase.ActualizarServicioUseCase
import com.turnero.app.domain.usecase.CrearServicioUseCase
import com.turnero.app.domain.usecase.EliminarServicioUseCase
import com.turnero.app.domain.usecase.ObtenerServiciosUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel del catalogo de servicios.
 *
 * Solo invoca use cases, nunca el repositorio (ver AGENTS.md). El estado vive aca; el
 * composable recibe el `ServiciosUiState` y callbacks, y no sabe que existen.
 */
@HiltViewModel
class ServiciosViewModel @Inject constructor(
    private val obtenerServicios: ObtenerServiciosUseCase,
    private val crearServicio: CrearServicioUseCase,
    private val actualizarServicio: ActualizarServicioUseCase,
    private val eliminarServicio: EliminarServicioUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServiciosUiState())
    val uiState: StateFlow<ServiciosUiState> = _uiState.asStateFlow()

    /**
     * Coleccion de lectura viva, para poder cancelarla al reintentar.
     */
    private var trabajoLectura: Job? = null

    init {
        observar()
    }

    /**
     * Levanta la cadena de lectura.
     *
     * Va en una funcion y no en el `init` para que [reintentar] pueda rearmarla: `catch`
     * es **terminal**, asi que un error transitorio dejaba la pantalla muerta.
     */
    private fun observar() {
        trabajoLectura?.cancel()
        trabajoLectura = viewModelScope.launch {
            obtenerServicios()
                // `catch` va antes de `collect`: asi una falla de lectura no cancela el
                // scope del ViewModel, y el error queda en el estado en vez de caer en el
                // CoroutineExceptionHandler. Un try/catch alrededor del collect no
                // serviria porque el error llega por el canal de excepciones del Flow.
                .catch { throwable ->
                    _uiState.update {
                        it.copy(cargando = false, errorCargaRes = throwable.aErrorRes())
                    }
                }
                .collect { servicios ->
                    _uiState.update {
                        it.copy(servicios = servicios, cargando = false, errorCargaRes = null)
                    }
                }
        }
    }

    /** Vuelve a leer desde cero tras un error de carga. Unico camino de salida de `errorCargaRes`. */
    fun reintentar() {
        _uiState.update { it.copy(cargando = true, errorCargaRes = null) }
        observar()
    }

    fun crear(nombre: String, duracionMin: Int, precioCentavos: Long?, color: Int) {
        viewModelScope.launch {
            crearServicio(nombre, duracionMin, precioCentavos, color)
                .onFailure { mostrarError(it) }
        }
    }

    fun actualizar(id: UUID, nombre: String, duracionMin: Int, precioCentavos: Long?, color: Int) {
        viewModelScope.launch {
            actualizarServicio(id, nombre, duracionMin, precioCentavos, color)
                .onFailure { mostrarError(it) }
        }
    }

    fun eliminar(id: UUID) {
        viewModelScope.launch {
            eliminarServicio(id).onFailure { mostrarError(it) }
        }
    }

    /** Limpia el error una vez que la pantalla ya mostro el snackbar. */
    fun onErrorMostrado() {
        _uiState.update { it.copy(errorRes = null) }
    }

    private fun mostrarError(throwable: Throwable) {
        _uiState.update { it.copy(errorRes = throwable.aErrorRes()) }
    }
}
