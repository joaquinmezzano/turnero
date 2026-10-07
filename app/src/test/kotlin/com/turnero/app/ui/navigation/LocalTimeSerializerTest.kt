package com.turnero.app.ui.navigation

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalTime

/**
 * Contrato de [LocalTimeSerializer].
 *
 * Espejo del de `UuidSerializer`: el `NavType` de `androidx.navigation.serialization` se
 * deriva del **`serialName`** del descriptor, así que el objeto tiene que declarar el de
 * `String` para que `composable<TurnoEditor>` y el `SavedStateHandle` hablen el mismo tipo.
 *
 * La hora viaja en ISO con segundos (`"10:00:00"`, como lo imprime
 * `DateTimeFormatter.ISO_LOCAL_TIME`): `LocalTime.parse` lo acepta sin formato extra y es
 * lo que la navegación guarda en el `Bundle`.
 */
class LocalTimeSerializerTest {

    @Test
    fun `el descriptor declara el serial name de String`() {
        // El nombre canónico, no uno escrito a mano: `String.serializer()` es el dueño de
        // `"kotlin.String"`, igual que en `UuidSerializer`.
        assertEquals(String.serializer().descriptor.serialName, LocalTimeSerializer.descriptor.serialName)
        assertEquals("kotlin.String", LocalTimeSerializer.descriptor.serialName)
    }

    @Test
    fun `serializar y deserializar una hora conserva el valor`() {
        val original = LocalTime.of(10, 0)

        val texto = Json.encodeToString(LocalTimeSerializer, original)
        val recuperado = Json.decodeFromString(LocalTimeSerializer, texto)

        assertEquals("\"10:00:00\"", texto)
        assertEquals(original, recuperado)
    }
}