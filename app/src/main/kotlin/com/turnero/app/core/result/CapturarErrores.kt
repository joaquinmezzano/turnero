package com.turnero.app.core.result

import kotlin.coroutines.cancellation.CancellationException

/**
 * Equivalente a `runCatching` que deja pasar la cancelacion.
 *
 * `kotlin.runCatching` captura `Throwable`, y `CancellationException` es un `Throwable`.
 * En una funcion `suspend`, eso convierte la cancelacion en un `Result.failure` y el
 * calling coroutine no relanza: la structured concurrency queda rota y el resto del
 * bloque sigue ejecutandose con el scope ya cancelado.
 *
 * `CancellationException` no es un error de negocio: signify que el ViewModel se esta
 * destruyendo o que el usuario navego. Por eso hay que re-lanzarla siempre.
 *
 * La captura de `Exception` generica es la razon de ser de la funcion, y por eso se
 * suprime la regla en vez de desactivarla en detekt.yml: este helper tiene que atrapar
 * lo que sea que Room lance. Un `SQLiteException` puntual dejaria pasar cualquier otro
 * fallo y lo convertiria en una excepcion cruzando la frontera de dominio.
 */
@Suppress("TooGenericExceptionCaught")
inline fun <T> capturandoErrores(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancelacion: CancellationException) {
        throw cancelacion
    } catch (error: Exception) {
        Result.failure(error)
    }
