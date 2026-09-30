package com.turnero.app.ui.screens.servicios

import androidx.annotation.StringRes
import com.turnero.app.R
import com.turnero.app.domain.model.ErrorServicio

/**
 * Traduce el error que viene dentro del `Result` a un `@StringRes`.
 *
 * Vive en `ui/` y no en `domain/` a proposito: `ErrorServicio` no conoce `R`, y el
 * ViewModel tampoco deberia saber de textos, solo de identificadores.
 *
 * La rama `else` cubre los fallos de IO de Room, que no son `ErrorServicio` y llegan
 * envueltos por `capturandoErrores` en el repositorio.
 */
@StringRes
fun Throwable.aErrorRes(): Int = when (this) {
    is ErrorServicio.NombreVacio -> R.string.error_nombre_vacio
    is ErrorServicio.DuracionInvalida -> R.string.error_duracion_invalida
    is ErrorServicio.PrecioInvalido -> R.string.error_precio_invalido
    is ErrorServicio.NoExiste -> R.string.error_servicio_no_existe
    else -> R.string.error_generico
}
