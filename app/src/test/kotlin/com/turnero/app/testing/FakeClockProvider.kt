package com.turnero.app.testing

import com.turnero.app.core.datetime.ClockProvider
import java.time.Instant

/**
 * `ClockProvider` con el tiempo bajo control del test.
 *
 * Es la razon de que `ClockProvider` sea una interface inyectada: sin esto, "el
 * `updatedAt` se refresca" solo se puede afirmar con `Thread.sleep` o comparando
 * contra `Instant.now()`, que es un test que corre en verde por suerte.
 *
 * Es mutable a proposito: `clock.instante = masTarde` simula el paso del tiempo sin
 * tocar el reloj real.
 */
class FakeClockProvider(
    var instante: Instant = INSTANTE_BASE,
) : ClockProvider {

    override fun now(): Instant = instante
}