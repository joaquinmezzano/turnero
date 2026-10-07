package com.turnero.app.domain.model

/**
 * Casos de error de negocio de `Turno`.
 *
 * Misma forma que [ErrorCliente] y [ErrorServicio]: extiende `Exception` porque es lo que
 * `Result.failure` acepta, asi que el error se devuelve **dentro** de un `Result` y nunca
 * se lanza. Ninguna excepcion cruza la frontera de dominio.
 *
 * No lleva texto asociado a proposito: la traduccion de cada caso a un `@StringRes` vive en
 * la capa `ui`. Si un error de dominio apareciera con mensaje propio, el mismo fallo
 * tendria dos textos que se pueden desincronizar.
 */
sealed class ErrorTurno : Exception() {

    /** El `servicioId` no existe o ya fue eliminado. Sin el, no hay `duracionMin` que copiar. */
    data object SinServicio : ErrorTurno()

    /** El `clienteId` no existe o ya fue eliminado. */
    data object SinCliente : ErrorTurno()

    /** Hay otro turno vivo que ocupa el mismo horario en un estado que lo bloquea. */
    data object Solapamiento : ErrorTurno()

    /** El inicio ya paso: no se agenda en el pasado. */
    data object EnElPasado : ErrorTurno()

    /** El inicio cae fuera de la [VentanaAtencion] inyectada. */
    data object FueraDeVentana : ErrorTurno()

    /** La transicion de estado no esta entre las acciones disponibles para ese turno. */
    data object TransicionInvalida : ErrorTurno()

    /** Se intento editar, cambiar de estado o liberar un turno que no existe o ya fue liberado. */
    data object NoExiste : ErrorTurno()
}