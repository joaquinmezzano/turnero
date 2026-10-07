package com.turnero.app.ui.screens.clientes

import androidx.annotation.StringRes
import com.turnero.app.domain.model.Cliente
import com.turnero.app.domain.model.EstadisticasCliente
import com.turnero.app.domain.model.TurnoConServicio

data class ClienteDetalleUiState(
    val cliente: Cliente? = null,
    /**
     * Historial con el nombre del servicio resuelto por `JOIN` (ver [TurnoConServicio]): la
     * ficha lo muestra aunque el servicio ya haya sido dado de baja.
     */
    val turnos: List<TurnoConServicio> = emptyList(),
    val estadisticas: EstadisticasCliente? = null,
    val cargando: Boolean = true,
    @StringRes val errorRes: Int? = null,
)
