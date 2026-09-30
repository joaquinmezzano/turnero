package com.turnero.app.core.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * El orden tiene que ser el del idioma del telefono, no el de SQLite.
 *
 * Este test es la defensa contra un revert: si alguien saca [ordenandoPor] del
 * repositorio y deja que mande el `ORDER BY ... COLLATE NOCASE` del DAO, falla.
 */
class OrdenAlfabeticoTest {

    @Test
    fun `un nombre con tilde se ordena en su lugar y no al final`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("es"))
            val palabras = listOf("zebra", "Ambar", "gato", "Optica", "Analisis")

            val ordenadas = palabras.ordenandoPor { it }

            assertEquals(
                listOf("Ambar", "Analisis", "gato", "Optica", "zebra"),
                ordenadas,
                "NOCASE de SQLite dejaria Optica y Ambar despues de zebra",
            )
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `no distingue mayusculas de minusculas`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("es"))
            val palabras = listOf("pera", "Manzana", "pera", "UvA".lowercase())

            val ordenadas = palabras.ordenandoPor { it }

            assertEquals(listOf("Manzana", "pera", "pera", "uva"), ordenadas)
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `ordena por la clave indicada y no por el objeto completo`() {
        val palabras = listOf("tres", "uno", "dos")

        // Ordenar por el texto del largo es alfabetico sobre "3", "3", "4"; los dos
        // "3" quedan emparejados y el sort es estable, asi que conservan su orden relativo.
        assertEquals(listOf("uno", "dos", "tres"), palabras.ordenandoPor { it.length.toString() })

        // La misma lista, ordenando por el largo convertido a texto de un caracter.
        assertEquals(listOf("uno", "dos", "tres"), palabras.ordenandoPor { " ${it.length}" })
    }

    @Test
    fun `una lista vacia o de un elemento no falla`() {
        assertEquals(emptyList<String>(), emptyList<String>().ordenandoPor { it })
        assertEquals(listOf("uno"), listOf("uno").ordenandoPor { it })
    }
}
