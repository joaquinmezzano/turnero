package com.turnero.app.ui.screens.servicios

import androidx.annotation.StringRes
import com.turnero.app.domain.model.Servicio

/**
 * Estado de la pantalla de servicios. Vive en el ViewModel; el composable solo lo pinta.
 */
data class ServiciosUiState(
    val servicios: List<Servicio> = emptyList(),
    val cargando: Boolean = true,
    /**
     * Error a mostrar, como **id de recurso** y no como texto ya formateado: el texto
     * lo resuelve la capa `ui` con `stringResource`, y `domain` no puede depender de
     * `R` (ver AGENTS.md).
     */
    @StringRes val errorRes: Int? = null,
)
