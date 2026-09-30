package com.turnero.app.ui.screens.clientes

import androidx.annotation.StringRes
import com.turnero.app.domain.model.Cliente

/**
 * Estado de la ficha de un cliente.
 *
 * `cargando` y `cliente` son separados a propósito: "todavía no lo leí" y "lo leí y no
 * está" son pantallas distintas. Con un solo `cliente: Cliente?` no se podría distinguir
 * el instante de carga del cliente ya eliminado, y mostraría el mensaje equivocado.
 */
data class ClienteDetalleUiState(
    val cliente: Cliente? = null,
    val cargando: Boolean = true,
    @StringRes val errorRes: Int? = null,
)
