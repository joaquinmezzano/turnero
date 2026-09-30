package com.turnero.app.core.text

import java.text.Collator
import java.util.Locale

/**
 * Orden alfabetico segun el idioma del telefono.
 *
 * Existe porque `ORDER BY nombre COLLATE NOCASE` de SQLite **no** ordena en español:
 * `NOCASE` solo pliega A-Z ASCII, y los bytes UTF-8 de una vocal acentuada empiezan en
 * `0xC3`, mayor que cualquier letra ASCII. El resultado es que "Optica", "Ambar" y
 * "Nandu" caen al final de la lista, detras de "zebra". Verificado con sqlite3.
 *
 * `Collator` con locale `es` si las coloca donde las pondria un diccionario:
 * Ambar, Analisis, gato, Nandu, Optica, Perro, zebra.
 *
 * El `Collator` se crea por llamada y no como campo estatico: no es thread-safe, y los
 * repositorios escriben desde el executor de Room, no desde el hilo principal.
 *
 * La query de Room mantiene su `ORDER BY` como desempate estable. Este sort es el que
 * manda; el de SQL solo fija un orden reproducible para los empates exactos.
 */
fun <T> List<T>.ordenandoPor(clave: (T) -> String): List<T> {
    val collator = Collator.getInstance(Locale.getDefault())
    collator.strength = Collator.PRIMARY
    return sortedWith { primero, segundo -> collator.compare(clave(primero), clave(segundo)) }
}
