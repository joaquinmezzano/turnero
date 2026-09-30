package com.turnero.app.ui.screens.clientes

import androidx.annotation.StringRes
import com.turnero.app.R
import com.turnero.app.domain.model.ErrorCliente

/**
 * Traduce el error que viene dentro del `Result` a un `@StringRes`.
 *
 * Vive en `ui/` y no en `domain/` a propósito: `ErrorCliente` no conoce `R`, y el
 * ViewModel tampoco debería saber de textos, solo de identificadores.
 *
 * La rama `else` cubre los fallos de IO de Room, que no son `ErrorCliente` y llegan como
 * `SQLiteException` envueltos por `capturandoErrores` en el repositorio.
 */
@StringRes
fun Throwable.aErrorRes(): Int = when (this) {
    is ErrorCliente.NombreVacio -> R.string.error_cliente_nombre_vacio
    is ErrorCliente.EmailInvalido -> R.string.error_email_invalido
    is ErrorCliente.TelefonoInvalido -> R.string.error_telefono_invalido
    is ErrorCliente.NoExiste -> R.string.error_cliente_no_existe
    else -> R.string.error_generico
}
