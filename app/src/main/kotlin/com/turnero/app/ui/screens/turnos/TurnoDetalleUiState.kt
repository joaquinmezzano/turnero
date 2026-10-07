package com.turnero.app.ui.screens.turnos

import com.turnero.app.domain.model.AccionTurno
import com.turnero.app.domain.model.Turno

data class TurnoDetalleUiState(
    val turno: Turno? = null,
    val clienteNombre: String = "",
    val servicioNombre: String = "",
    val acciones: Set<AccionTurno> = emptySet(),
    val cargando: Boolean = true,
    val errorRes: Int? = null,
)
