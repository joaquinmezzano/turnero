package com.turnero.app.core.datetime

import java.time.ZoneId
import javax.inject.Inject

/**
 * Fuente unica de la zona horaria del dispositivo.
 *
 * Existe por el mismo motivo que [ClockProvider]: para que `domain` y `data` no lean el
 * entorno de la maquina. Un turno se guarda como `Instant`, pero se agenda a partir de una
 * fecha y una hora locales; la conversion necesita la zona, y si esa zona se tomara de
 * `ZoneId.systemDefault()` dentro de un use case, el test solo pasaria en la zona de quien
 * lo corre.
 *
 * Esta es la unica clase del proyecto autorizada a llamar a `ZoneId.systemDefault()`, y
 * vive en `core/` justamente para poder ser inyectada desde cualquier capa.
 */
interface ZonaHorariaProvider {
    fun zona(): ZoneId
}

/** Implementacion real, ligada a la zona del telefono. Se bindea en `di/ClockModule.kt`. */
class SystemZonaHorariaProvider @Inject constructor() : ZonaHorariaProvider {
    override fun zona(): ZoneId = ZoneId.systemDefault()
}
