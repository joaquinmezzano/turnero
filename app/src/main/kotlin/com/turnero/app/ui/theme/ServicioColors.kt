package com.turnero.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Paleta de tonos ofrecidos por el selector de color de un servicio.
 *
 * Este archivo, junto a `Color.kt` y `Theme.kt`, es el UNICO lugar del proyecto donde
 * se permiten colores literales (ver AGENTS.md). Las screens nunca escriben un
 * `0xFF...`: toman de aca o de `MaterialTheme.colorScheme`.
 *
 * Los valores ARGB se guardan en la base (columna `servicios.color`) y vuelven como
 * `Int`; la UI los reconstruye con `Color(servicio.color)`, que es dato, no literal.
 *
 * Los tonos se eligieron con luminosidad intermedia para que funcionen tanto en tema
 * claro como en oscuro sobre las superficies de Material 3. Medidos contra la superficie
 * oscura, los tonos 6, 7 y 2 quedan entre 2.28:1 y 2.88:1: por eso el swatch lleva
 * `contentDescription` y el color nunca es el unico portador de informacion.
 */
val ServicioColors: List<Color> = listOf(
    Color(0xFF2E7D6F),
    Color(0xFF3F6DB5),
    Color(0xFF7B4FA8),
    Color(0xFFB0446B),
    Color(0xFFB5651D),
    Color(0xFF5C7A29),
    Color(0xFF4A5568),
    Color(0xFF9A3B3B),
)

/**
 * Tinta legible sobre un tono de [ServicioColors] guardado en `servicios.color`.
 *
 * La grilla de turnos pinta el chip con el color del servicio (spec del Slice 3), y un
 * `Color.Unspecified` o un `onSurface` a medias dejarían el texto sin contraste: en tema
 * claro `onSurface` es casi negro y sobre un tono medio no llega a 4.5:1, y al revés en
 * tema oscuro.
 *
 * Elegir negro o blanco por la luminancia relativa es el mismo cálculo que hace WCAG:
 * [UMBRAL_LUMINANCIA] es el punto donde el contraste de las dos tintas se cruza, así que
 * cualquiera de los lados queda en ~4.5:1 o mejor para los ocho tonos de la paleta. Es
 * dato derivado del color del usuario, no una decisión de tema, por eso vive acá y no en
 * una screen.
 */
fun contenidoSobreServicio(colorArgb: Int): Color =
    if (Color(colorArgb).luminance() > UMBRAL_LUMINANCIA) Color.Black else Color.White

/**
 * Luminancia donde el contraste del negro y el del blanco se igualan
 * (`(L + 0.05) / 0.05 == 1.05 / (L + 0.05)` ⇒ `L ≈ 0.179`).
 */
private const val UMBRAL_LUMINANCIA = 0.179f
