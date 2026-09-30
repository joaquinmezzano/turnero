package com.turnero.app.domain.model

/**
 * Casos de error de negocio de `Cliente`.
 *
 * Misma forma que [ErrorServicio] por una razón concreta: extender `Exception` es lo
 * que `Result.failure` acepta, así que el error se devuelve **dentro** de un `Result` y
 * nunca se lanza. Ninguna excepción cruza la frontera de dominio (ver AGENTS.md).
 *
 * No lleva texto asociado a propósito. El usuario ve strings de
 * `res/values/strings.xml` y la traducción de cada caso a un `@StringRes` vive en la
 * capa `ui` (`ui/screens/clientes/ErrorRes.kt`). Si un error de dominio apareciera con
 * mensaje propio, el mismo fallo tendría dos textos que se pueden desincronizar.
 */
sealed class ErrorCliente : Exception() {

    /** `nombre` en blanco o vacío. Es el único campo obligatorio de la ficha. */
    data object NombreVacio : ErrorCliente()

    /** `email` informado con un formato que no parece una dirección. */
    data object EmailInvalido : ErrorCliente()

    /** `telefono` informado con caracteres que no son de un número telefónico. */
    data object TelefonoInvalido : ErrorCliente()

    /** Se intentó editar o borrar un cliente que no existe o ya fue eliminado. */
    data object NoExiste : ErrorCliente()
}
