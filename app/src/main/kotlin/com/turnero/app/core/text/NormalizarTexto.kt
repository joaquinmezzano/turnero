package com.turnero.app.core.text

import java.text.Normalizer
import java.util.Locale

/**
 * Marcas diacríticas Unicode, o sea la categoría `Mn` (nonspacing mark): el acento de
 * la `ó`, la ñ tildada que descompone `Ñ` en `N` + `U+0303`, etc.
 *
 * Se compila una vez y se reutiliza: `Pattern` es inmutable y thread-safe, al revés que
 * el `Collator` de [ordenandoPor], que por eso se crea por llamada.
 */
private val MARCAS_DIACRITICAS = Regex("\\p{InCombiningDiacriticalMarks}+")

/**
 * Deja un texto comparable con otro que el usuario escribe a mano.
 *
 * **Por qué existe y por qué no alcanza con `LIKE` de SQLite.** El `LIKE` de SQLite solo
 * es case-insensitive para ASCII, así que `LIKE '%optica%'` NO encuentra `"Óptica"` y
 * `LIKE '%óptica%'` NO encuentra `"Optica"`. Para un usuario rioplatense que escribe
 * como ve, eso falla en las dos direcciones. Verificado con `sqlite3`.
 *
 * **Qué hace.** Descompone en NFD (separando la letra base de su acento), borra las
 * marcas combinantes y pasa a minúsculas. Así `"Óptica"`, `"óptica"`, `"OPTICA"` y
 * `"optica"` dan exactamente la misma cadena y la búsqueda funciona en ambos sentidos.
 *
 * **La `ñ` se reduce a `n`, y eso es a propósito.** Con "nino" uno encuentra "Niño",
 * que es el caso que importa. Lo que NO ocurre es que "ninu" encuentre "Niño", y es lo
 * correcto: la tilde de la `ñ` no es un acento sobre una `n`, es otra letra del
 * alfabeto. Aceptar "ninu" como "niño" significaría que la `u` matchea la `ñ` en
 * cualquier posición, que es una regla sin ningún fundamento detrás. El costo del
 * otro lado es un falso positivo ("Nino" y "Niño" se mezclan), que en un buscador de
 * clientes es inocuo. Si algún día molesta, el arreglo es un `ñ` explícito antes de
 * descomponer, y es una línea.
 *
 * **`Locale.ROOT` y no el default.** Esto es normalización para comparar, no
 * presentación. En turco, la `I` mayúscula se pasa a `ı` (punto polla) con el locale por
 * defecto, así que el mismo texto normalizaría distinto según el teléfono del usuario y
 * la búsqueda dependería del aparato. `ROOT` da el mismo resultado en cualquier
 * dispositivo. Presentar según el idioma es otra cosa, y eso lo resuelve
 * `CurrencyFormatter`.
 *
 * @param texto el texto a normalizar, del usuario o del dato guardado.
 * @return la forma comparable, **sin recortar**: quien llame decide si el `trim` tiene
 *   sentido. Recortar acá sería una sorpresa para un texto que no es un campo de
 *   formulario.
 */
fun normalizarParaBuscar(texto: String): String =
    MARCAS_DIACRITICAS.replace(
        Normalizer.normalize(texto, Normalizer.Form.NFD),
        "",
    ).lowercase(Locale.ROOT)
