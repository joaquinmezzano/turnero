package com.turnero.app.ui.screens.turnos

import androidx.annotation.StringRes
import com.turnero.app.domain.model.Turno
import java.time.LocalDate
import java.time.LocalTime

/**
 * Turno ya enriquecido para la grilla: fila en la que arranca, celdas que ocupa y los
 * datos del cliente/servicio que la celda tiene que pintar.
 *
 * `horaInicio` y `horaFin` se calculan acá con la zona inyectada y no en la pantalla:
 * la grilla muestra la hora real de fin (`inicio + duracionMin`), que puede ser distinta
 * de la hora de la fila siguiente cuando el turno ocupa varias celdas o termina después
 * de las 22:00 (ver spec §7).
 */
data class TurnoConFila(
    val turno: Turno,
    val filaInicio: Int,
    val filasOcupadas: Int,
    val clienteNombre: String,
    val servicioNombre: String,
    /** ARGB del servicio, para pintar el chip con su color (spec §7). `null` si el servicio ya no existe. */
    val servicioColor: Int?,
    val horaInicio: LocalTime,
    val horaFin: LocalTime,
)

data class TurnosUiState(
    val fecha: LocalDate,
    val turnos: List<TurnoConFila> = emptyList(),
    val cargando: Boolean = true,
    /** Error transitorio de escritura; lo consume y lo borra el snackbar. */
    @StringRes val errorRes: Int? = null,
    /**
     * Error **persistente** de lectura, separado de [errorRes] por el mismo motivo que en
     * `ClientesUiState`: el `catch` que lo llena es terminal y deja `turnos` vacio, asi que
     * si viviera en [errorRes] el snackbar lo borraria y la grilla mentiria 15 horas
     * libres. Lo limpia `TurnosViewModel.reintentar()`.
     */
    @StringRes val errorCargaRes: Int? = null,
)