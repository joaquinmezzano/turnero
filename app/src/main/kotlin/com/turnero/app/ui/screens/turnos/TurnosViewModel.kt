package com.turnero.app.ui.screens.turnos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.turnero.app.core.datetime.ClockProvider
import com.turnero.app.core.datetime.ZonaHorariaProvider
import com.turnero.app.R
import com.turnero.app.domain.usecase.ObtenerAgendaDiaUseCase
import com.turnero.app.domain.usecase.ObtenerClientesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

/**
 * Hub de turnos: la grilla de 15 horas agendables.
 *
 * `TurnoConFila` enriquece el `Turno` de dominio con la fila de la grilla en la que
 * arranca, cuántas celdas ocupa y las horas reales de inicio y fin (la de fin puede ser
 * distinta de la hora de la fila siguiente). Un turno ancla a la hora exacta y un servicio
 * de más de 60 minutos ocupa varias celdas apiladas.
 */
@HiltViewModel
class TurnosViewModel @Inject constructor(
    private val obtenerAgendaDia: ObtenerAgendaDiaUseCase,
    private val obtenerClientes: ObtenerClientesUseCase,
    private val zonaHoraria: ZonaHorariaProvider,
    clock: ClockProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        TurnosUiState(
            fecha = clock.now().atZone(zonaHoraria.zona()).toLocalDate(),
        ),
    )
    val uiState: StateFlow<TurnosUiState> = _uiState.asStateFlow()

    /** La carga de un día se cancela antes de abrir la del siguiente. */
    private var cargaJob: Job? = null

    init {
        cargar()
    }

    fun irDiaSiguiente() {
        _uiState.update { it.copy(fecha = it.fecha.plusDays(1), cargando = true) }
        cargar()
    }

    fun irDiaAnterior() {
        _uiState.update { it.copy(fecha = it.fecha.minusDays(1), cargando = true) }
        cargar()
    }

    /**
     * Suscribe la agenda del día (ya con el nombre y el color del servicio resueltos en la
     * lectura) y el catálogo de clientes, y arma las celdas.
     *
     * El servicio **no** se cruza contra el catálogo vivo: lo trae la proyección del
     * repositorio, que no filtra `servicios.deletedAt` (ver `TurnoConServicio`). El nombre
     * del cliente sí sale del catálogo vivo, que es lo que el usuario pidió.
     *
     * La colección anterior se cancela: sin la cancelación, cada cambio de día dejaría un
     * `combine` vivo empujando los turnos del día viejo cuando cualquiera de los catálogos
     * re-emitiera, y la grilla mostraría datos de ayer bajo la fecha de hoy.
     */
    private fun cargar() {
        cargaJob?.cancel()
        cargaJob = viewModelScope.launch {
            val fecha = _uiState.value.fecha
            val zona = zonaHoraria.zona()
            combine(
                obtenerAgendaDia(fecha),
                obtenerClientes(),
            ) { turnos, clientes ->
                val clientesMap = clientes.associateBy { it.id }
                turnos.map { conServicio ->
                    val turno = conServicio.turno
                    val inicioLocal = turno.inicio.atZone(zona).toLocalTime()
                    val finLocal = turno.inicio
                        .plusSeconds(turno.duracionMin * 60L)
                        .atZone(zona)
                        .toLocalTime()
                    TurnoConFila(
                        turno = turno,
                        filaInicio = (inicioLocal.hour - HORA_INICIO.hour).coerceAtLeast(0),
                        filasOcupadas = (turno.duracionMin + 59) / 60,
                        clienteNombre = clientesMap[turno.clienteId]?.nombre.orEmpty(),
                        servicioNombre = conServicio.servicioNombre,
                        servicioColor = conServicio.servicioColor,
                        horaInicio = inicioLocal,
                        horaFin = finLocal,
                    )
                }
            }.catch {
                // `catch` es terminal: si no se limpiara `turnos`, la grilla pintaria los
                // turnos del dia anterior bajo la fecha nueva. Ademas el error va a
                // `errorCargaRes`, que no lo borra el snackbar, para que quede el cartel
                // con "Reintentar" en vez de 15 horas falsamente libres.
                _uiState.update {
                    it.copy(
                        cargando = false,
                        turnos = emptyList(),
                        errorCargaRes = R.string.error_generico,
                    )
                }
            }.collect { lista ->
                _uiState.update {
                    it.copy(
                        turnos = lista.sortedBy { tf -> tf.turno.inicio },
                        cargando = false,
                        errorRes = null,
                        errorCargaRes = null,
                    )
                }
            }
        }
    }

    /**
     * Vuelve a leer la agenda tras un error de carga.
     *
     * Único camino de salida de [TurnosUiState.errorCargaRes]: el `catch` que lo llena es
     * terminal, así que sin esto la pantalla no se recupera salvo recreándola.
     */
    fun reintentar() {
        _uiState.update { it.copy(cargando = true, errorCargaRes = null) }
        cargar()
    }

    fun onErrorMostrado() {
        _uiState.update { it.copy(errorRes = null) }
    }

    companion object {
        private val HORA_INICIO = LocalTime.of(8, 0)
    }
}