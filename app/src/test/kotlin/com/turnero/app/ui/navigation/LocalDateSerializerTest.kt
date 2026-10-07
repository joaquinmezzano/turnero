package com.turnero.app.ui.navigation

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Contrato de [LocalDateSerializer].
 *
 * Espejo del de `UuidSerializer` y del de `LocalTime`: el `NavType` se deriva del
 * **`serialName`** del descriptor, así que el objeto tiene que declarar el de `String`
 * para que `composable<TurnoEditor>` y el `SavedStateHandle` hablen el mismo tipo.
 *
 * La fecha viaja en ISO (`"2026-03-16"`): es lo que `LocalDate.parse` acepta y lo que la
 * navegación guarda en el `Bundle`.
 */
class LocalDateSerializerTest {

    @Test
    fun `el descriptor declara el serial name de String`() {
        assertEquals(String.serializer().descriptor.serialName, LocalDateSerializer.descriptor.serialName)
        assertEquals("kotlin.String", LocalDateSerializer.descriptor.serialName)
    }

    @Test
    fun `serializar y deserializar una fecha conserva el valor`() {
        val original = LocalDate.of(2026, 3, 16)

        val texto = Json.encodeToString(LocalDateSerializer, original)
        val recuperado = Json.decodeFromString(LocalDateSerializer, texto)

        assertEquals("\"2026-03-16\"", texto)
        assertEquals(original, recuperado)
    }
}