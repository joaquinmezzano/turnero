package com.turnero.app.domain.model

/**
 * Casos de error de negocio de `Servicio`.
 *
 * Extiende `Exception` porque es lo que `Result.failure` acepta, y por eso se devuelve
 * **dentro** de un `Result`, nunca lanzado: ninguna excepcion cruza la frontera de
 * dominio (ver AGENTS.md).
 *
 * No lleva texto asociado a proposito. El usuario ve strings de `res/values/strings.xml`
 * y la traduccion de cada caso a un `@StringRes` vive en la capa `ui/`
 * (`ui/screens/servicios/ErrorRes.kt`). Si un error de dominio apareciera con mensaje
 * propio, el mismo fallo tendria dos textos que se pueden desincronizar.
 */
sealed class ErrorServicio : Exception() {

    /** `nombre` en blanco o vacio. */
    data object NombreVacio : ErrorServicio()

    /** `duracionMin` <= 0. Un turno de duracion cero no tiene sentido. */
    data object DuracionInvalida : ErrorServicio()

    /** `precioCentavos` negativo. 0 si es "sin costo" es valido. */
    data object PrecioInvalido : ErrorServicio()

    /** Se intento editar o borrar un servicio que no existe o ya fue eliminado. */
    data object NoExiste : ErrorServicio()
}
