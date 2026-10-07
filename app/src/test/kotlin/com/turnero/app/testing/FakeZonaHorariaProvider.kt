package com.turnero.app.testing

import com.turnero.app.core.datetime.ZonaHorariaProvider
import java.time.ZoneId

/**
 * `ZonaHorariaProvider` con la zona bajo control del test.
 *
 * Es la razon de que la zona sea una interface inyectada: sin esto, un test que agienda un
 * turno a las 10:00 pasaria unicamente en la zona de quien lo corre, y el `Instant` que
 * espera no coincidiria nunca con el que guarda el codigo.
 *
 * Se fija una zona concreta y no la del sistema porque la conversion fecha + hora -> `Instant`
 * es justo lo que hay que verificar.
 */
class FakeZonaHorariaProvider(
    var zona: ZoneId = ZONA_BASE,
) : ZonaHorariaProvider {

    override fun zona(): ZoneId = zona
}

/** GMT-3, la zona del caso de uso que motivo la app. Sin offset de verano. */
val ZONA_BASE: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")