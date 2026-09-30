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
     *
     * Es de un solo uso: lo consume un `LaunchedEffect` que lo muestra en el snackbar y lo
     * borra. El error de carga va aparte, en [errorCargaRes], porque si compartieran
     * campo el borrado posterior al snackbar borraria tambien el error de carga.
     */
    @StringRes val errorRes: Int? = null,
    /**
     * Error **persistente** de lectura: reemplaza la pantalla entera y **no se borra solo**.
     *
     * Lo limpia `reintentar()`. Vive aparte porque el `catch` que lo produce es terminal.
     */
    @StringRes val errorCargaRes: Int? = null,
)
