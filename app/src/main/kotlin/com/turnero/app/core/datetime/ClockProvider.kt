package com.turnero.app.core.datetime

import java.time.Instant
import javax.inject.Inject

/**
 * Fuente unica de "ahora" para toda la app.
 *
 * `Instant.now()` esta prohibido en `domain/` y `data/` (ver AGENTS.md): sin esta
 * indireccion los use cases no se pueden testear de forma determinista, porque el
 * resultado dependeria del reloj de la maquina que corre el test.
 *
 * Esta es la unica clase del proyecto autorizada a llamar a `Instant.now()`, y vive
 * en `core/` justamente para poder ser inyectada desde cualquier capa.
 */
interface ClockProvider {
    fun now(): Instant
}

/** Implementacion real, ligada al reloj del sistema. Se bindea en `di/ClockModule.kt`. */
class SystemClockProvider @Inject constructor() : ClockProvider {
    override fun now(): Instant = Instant.now()
}
