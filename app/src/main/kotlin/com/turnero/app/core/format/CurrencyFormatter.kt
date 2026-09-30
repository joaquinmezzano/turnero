package com.turnero.app.core.format

import java.text.NumberFormat
import java.util.Locale

/**
 * Formatea un precio almacenado como `Long` de centavos a moneda.
 *
 * El modelo guarda `precioCentavos: Long` (ver AGENTS.md: nada de `BigDecimal` ni
 * `Double` en el dominio), asi que en algun momento hay que pasar de entero a fraccion.
 * Ese `Double` existe solo dentro de este formateador y nunca sale de el.
 *
 * El separador y el simbolo los decide el locale por defecto del dispositivo
 * (`$ 4.500,00` en es-AR, `US$ 45.00` en es-US), que es exactamente lo que se quiere:
 * el precio se muestra en la moneda del telefono.
 */
object CurrencyFormatter {

    /**
     * @param centavos precio en centavos. Negativos se forman con el signo del locale.
     */
    fun formatear(centavos: Long, locale: Locale = Locale.getDefault()): String {
        val formatador = NumberFormat.getCurrencyInstance(locale)
        return formatador.format(centavos.toDouble() / 100.0)
    }
}
