package com.turnero.app.ui.screens.clientes

import androidx.annotation.StringRes
import com.turnero.app.domain.model.Cliente

/**
 * Estado de la pantalla de clientes. Vive en el ViewModel; el composable solo lo pinta.
 */
data class ClientesUiState(
    val clientes: List<Cliente> = emptyList(),
    /**
     * Texto de la búsqueda, **en el estado y no en un `remember` del screen**.
     *
     * No es estado efímero de un formulario como el del diálogo de edición: es lo que
     * decide qué se ve en pantalla, así que si viviera en el composable habría que
     * devolverlo al ViewModel para que el flujo filtrado pudiera rearmarse después de un
     * cambio de configuración, y el `UiState` no describiría la pantalla.
     */
    val consulta: String = "",
    val cargando: Boolean = true,
    /**
     * Error **transitorio** de escritura, como **id de recurso** y no como texto ya
     * formateado: el texto lo resuelve la capa `ui` con `stringResource`, y `domain` no
     * puede depender de `R` (ver AGENTS.md).
     *
     * Lo consume un `LaunchedEffect` que lo muestra en el snackbar y después lo borra con
     * `onErrorMostrado()`. Es de un solo uso: si el mismo campo hiciera de estado de
     * pantalla, el borrado posterior al snackbar borraría también el error de carga y la
     * pantalla caería en el estado vacío mintiendo que "todavía no cargaste clientes".
     * Por eso el error de carga vive en [errorCargaRes].
     */
    @StringRes val errorRes: Int? = null,
    /**
     * Error **persistente** de lectura: reemplaza la pantalla entera y **no se borra solo**.
     *
     * Es lo único que sobrevive al `catch` del flujo, que es terminal: si se perdiera,
     * no quedaría forma de volver a leer sin recrear la pantalla. Lo limpia [reintentar].
     */
    @StringRes val errorCargaRes: Int? = null,
)
