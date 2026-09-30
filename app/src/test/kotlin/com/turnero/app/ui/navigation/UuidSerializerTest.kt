package com.turnero.app.ui.navigation

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Contrato de [UuidSerializer], la pieza de la que depende que la ficha de cliente sea
 * alcanzable.
 *
 * Son dos tests chicos y son exactamente esos dos a propósito. El nombre del descriptor no
 * es decorativo: `androidx.navigation.serialization` deriva el `NavType` de un
 * `SerialDescriptor` con un `when` sobre **`serialName`**, no sobre `kind`, así que un
 * `PrimitiveSerialDescriptor("java.util.UUID", PrimitiveKind.STRING)` declara el kind
 * correcto y aun así cae en la rama desconocida: `composable<ClienteDetalle>`,
 * `navigate(ClienteDetalle(id))` y la lectura del `SavedStateHandle` piden el mismo
 * `NavType`, los tres fallan y la ficha no se abre en toda la app. Es un bug que no aparece
 * compilando ni leyendo el ViewModel, y por eso vale la pena un test que lo ataje.
 *
 * Los dos corren en JVM a propósito: no tocan `android.os.Bundle`, así que no dependen de
 * un emulador y esta red corre en cada PR.
 */
class UuidSerializerTest {

    @Test
    fun `el descriptor declara el serial name de String`() {
        // El nombre canónico, no uno escrito a mano: `String.serializer()` es el dueño de
        // `"kotlin.String"`, y `PrimitiveSerialDescriptor("kotlin.String", ...)` no es una
        // alternativa porque kotlinx.serialization rechaza el nombre duplicado.
        assertEquals(String.serializer().descriptor.serialName, UuidSerializer.descriptor.serialName)

        // Y el valor concreto de ese nombre, que es el literal contra el que compara el
        // `when` de `NavTypeConverter`.
        assertEquals("kotlin.String", UuidSerializer.descriptor.serialName)
    }

    @Test
    fun `serializar y deserializar un UUID conserva el valor`() {
        val original = UUID.fromString("c3e5f7a9-4b6c-4d8e-9fa0-1b2c3d4e5f03")

        val texto = Json.encodeToString(UuidSerializer, original)
        val recuperado = Json.decodeFromString(UuidSerializer, texto)

        // El texto intermedio también se afirma: sin esto, un `serialize` que escribiera
        // cualquier cosa y un `deserialize` que la desarmara bien pasarían el round trip.
        // Ese texto es lo que la navegación guarda en el `Bundle` del argumento.
        assertEquals("\"$original\"", texto)
        assertEquals(original, recuperado)
    }
}
