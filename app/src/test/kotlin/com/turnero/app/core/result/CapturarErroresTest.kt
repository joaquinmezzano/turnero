package com.turnero.app.core.result

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.coroutines.cancellation.CancellationException

/**
 * `capturandoErrores` existe por una sola razon: que `runCatching` no se coma la
 * cancelacion de coroutines. Estos tests fijan esa razon.
 *
 * Si alguien reemplaza el helper por `runCatching` (que es exactamente el cambio
 * tentador, porque `runCatching` hace lo mismo en una linea), el primer test falla.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CapturarErroresTest {

    @Test
    fun `una cancelacion se propaga en vez de convertirse en Result failure`() {
        assertThrows(CancellationException::class.java) {
            capturandoErrores<Unit> { throw CancellationException("cancelado") }
        }
    }

    @Test
    fun `una cancelacion dentro de una coroutine no deja un Result failure colgado`() = runTest {
        val capturados = mutableListOf<Result<Unit>>()

        val trabajo = launch {
            capturados += capturandoErrores { awaitCancellation() }
        }
        trabajo.cancel()
        trabajo.join()

        assertTrue(trabajo.isCancelled, "La coroutine deberia quedar cancelada")
        assertTrue(capturados.isEmpty(), "La cancelacion se capturo como: $capturados")
    }

    @Test
    fun `una excepcion normal se convierte en Result failure`() {
        val resultado = capturandoErrores<Int> { error("fallo de sqlite") }

        assertInstanceOf(IllegalStateException::class.java, resultado.exceptionOrNull())
    }

    @Test
    fun `el error de la excepcion se conserva para poder diagnosticarlo`() {
        val resultado = capturandoErrores<Unit> { throw IllegalStateException("Room no pudo insertar") }

        assertEquals("Room no pudo insertar", resultado.exceptionOrNull()?.message)
    }

    @Test
    fun `un bloque que no falla devuelve Result success`() {
        assertEquals(42, capturandoErrores { 42 }.getOrNull())
    }

    @Test
    fun `un bloque que devuelve null devuelve Result success con null`() {
        val resultado = capturandoErrores<String?> { null }

        assertNull(resultado.exceptionOrNull())
        assertNull(resultado.getOrNull())
    }
}