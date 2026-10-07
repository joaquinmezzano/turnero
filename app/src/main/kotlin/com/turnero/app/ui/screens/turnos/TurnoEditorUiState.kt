package com.turnero.app.ui.screens.turnos

import androidx.annotation.StringRes
import com.turnero.app.domain.model.Cliente
import com.turnero.app.domain.model.Servicio
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * Estado del editor de turno (alta y edición comparten pantalla).
 *
 * `turnoId != null` ⇒ modo edición: se precargan los datos del turno.
 * `fecha`/`hora` vienen fijados desde la grilla de turnos, o se navegan desde la ficha
 * del cliente (la hora siempre entre las horas agendables, en punto).
 *
 * `fecha` **no tiene default** a propósito: el único camino a su valor es el
 * `ClockProvider` inyectado (o el argumento de la ruta), nunca `LocalDate.now()`.
 */
data class TurnoEditorUiState(
    val turnoId: UUID? = null,
    val fecha: LocalDate,
    val hora: LocalTime = LocalTime.of(8, 0),
    val clienteId: UUID? = null,
    val servicioId: UUID? = null,
    val notas: String = "",
    val clientes: List<Cliente> = emptyList(),
    val servicios: List<Servicio> = emptyList(),
    val cargando: Boolean = false,
    @StringRes val errorRes: Int? = null,
) {
    /** Las 15 horas agendables (08:00 a 22:00, en punto). La hora es la unidad de reserva. */
    val horasDisponibles: List<LocalTime> =
        (0 until 15).map { LocalTime.of(8, 0).plusHours(it.toLong()) }
}
