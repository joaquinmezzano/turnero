package com.turnero.app.core.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests de [normalizarParaBuscar].
 *
 * El foco no es "funciona", es **qué contrato exacto** cumple, porque de eso depende que
 * un usuario que escribe sin acentos encuentre al cliente que sí los tiene.
 */
class NormalizarTextoTest {

    @Test
    fun `quita los acentos de las vocales`() {
        assertEquals("optica", normalizarParaBuscar("óptica"))
        assertEquals("angel", normalizarParaBuscar("Ángel"))
        assertEquals("jose", normalizarParaBuscar("José"))
    }

    @Test
    fun `deja las eñes como n, sin convertirlas en n con tilde`() {
        // La `ñ` es la excepción que rompe el "solo quito marcas": no es una vocal acentuada
        // sino una letra propia del español.
        assertEquals("nino", normalizarParaBuscar("niño"))
        assertEquals("montana", normalizarParaBuscar("montaña"))
    }

    @Test
    fun `baja a minusculas`() {
        assertEquals("ana perez", normalizarParaBuscar("Ana Pérez"))
    }

    @Test
    fun `buscar sin acentos encuentra el nombre que los tiene`() {
        val nombre = "Óptica Roma"

        assertTrue(normalizarParaBuscar(nombre).contains(normalizarParaBuscar("optica")))
        assertTrue(normalizarParaBuscar(nombre).contains(normalizarParaBuscar("ÓPTICA")))
        assertTrue(normalizarParaBuscar(nombre).contains(normalizarParaBuscar("Roma")))
    }

    @Test
    fun `buscar con la enie invertida no encuentra la enie`() {
        // El límite del algoritmo, asumido a propósito: `ñ` → `n`, y no `n` → `ñ`.
        // Colapsar ambas en el mismo símbolo haría que "nino" encontrara "niño" **y**
        // "ninu", que es un prefijo real de "nunca". El costo es que quien escriba
        // "ninu" no encuentra a "Niño"; el beneficio es que "nunca" no.matchea "ñico".
        val nombre = "Niño, Tomás"

        assertTrue(normalizarParaBuscar(nombre).contains(normalizarParaBuscar("nino")))
        assertFalse(normalizarParaBuscar(nombre).contains(normalizarParaBuscar("ninu")))
    }

    @Test
    fun `busca por subcadena y no solo por prefijo`() {
        val nombre = "Ana María Pérez"

        assertTrue(normalizarParaBuscar(nombre).contains(normalizarParaBuscar("perez")))
        assertTrue(normalizarParaBuscar(nombre).contains(normalizarParaBuscar("maria")))
    }

    @Test
    fun `no recorta los espacios de los extremos`() {
        // El `trim` es responsabilidad de quien llama (`ObtenerClientesUseCase` lo hace
        // antes). Si esto recortara por su cuenta, el criterio sería distinto del que el
        // usuario escribió y el contrato quedaría partido entre dos capas.
        assertEquals("  ana perez  ", normalizarParaBuscar("  Ana Pérez  "))
        assertEquals("ana  perez", normalizarParaBuscar("Ana  Pérez"))
    }

    @Test
    fun `un texto vacio o en blanco se normaliza a vacio`() {
        assertEquals("", normalizarParaBuscar(""))
        assertEquals("", normalizarParaBuscar("   ").trim())
    }

    @Test
    fun `deja intactos los simbolos que no son letras`() {
        assertEquals("carlos +54 11 5555-0100", normalizarParaBuscar("Carlos +54 11 5555-0100"))
        assertEquals("maria (c/uru)", normalizarParaBuscar("María (C/Uru)"))
    }

    @Test
    fun `normalizar es idempotente`() {
        // Si no lo fuera, `contains(normalizar(normalizar(x)))` y el filtro andarían con
        // una pasada de más en cada tecla.
        val una = normalizarParaBuscar("  Sofía ÑÁÑEZ  ")

        assertEquals(normalizarParaBuscar(una), una)
    }
}