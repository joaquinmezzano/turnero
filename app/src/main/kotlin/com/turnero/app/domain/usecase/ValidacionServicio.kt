package com.turnero.app.domain.usecase

import com.turnero.app.domain.model.ErrorServicio

/**
 * Validacion compartida por los tres casos de uso que escriben un `Servicio`.
 *
 * Vive aparte y no como metodo de los use cases a proposito: si `crear`, `actualizar` y
 * `eliminar` tuvieran cada uno su copia, el dia que se agregue una regla nueva
 * (duracion maxima, nombre duplicado) seguro se olvide en uno de los tres.
 *
 * Devuelve el primer error encontrado o `null` si el servicio es valido.
 */
internal fun validarServicio(
    nombre: String,
    duracionMin: Int,
    precioCentavos: Long?,
): ErrorServicio? = when {
    nombre.isBlank() -> ErrorServicio.NombreVacio
    duracionMin <= 0 -> ErrorServicio.DuracionInvalida
    precioCentavos != null && precioCentavos < 0L -> ErrorServicio.PrecioInvalido
    else -> null
}
