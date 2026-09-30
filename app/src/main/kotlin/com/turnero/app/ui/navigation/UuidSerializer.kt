package com.turnero.app.ui.navigation

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.UUID

/**
 * `UUID` como texto para las rutas type-safe.
 *
 * **Por qué hace falta.** `kotlinx-serialization-core` no tiene un `KSerializer` público
 * para `java.util.UUID`, así que sin esto la propiedad da "Serializer has not been found
 * for type 'UUID'". La vía oficial es `@Serializable(with = ...)` en la propiedad, con un
 * serializer propio.
 *
 * **El descriptor no se declara: se pide prestado a `String`, y esto no es cosmético.**
 * `androidx.navigation.serialization` deriva el `NavType` del `SerialDescriptor` con un
 * `when` sobre **`serialName`**, no sobre `kind`: compara contra los literales
 * `"kotlin.Int"`, `"kotlin.Long"`, `"kotlin.String"`, `"kotlin.Array"` ycompanyía, y
 * `kind` solo se consulta para detectar enums. O sea que un
 * `PrimitiveSerialDescriptor("java.util.UUID", PrimitiveKind.STRING)` declara kind
 * `STRING` — lo correcto — y aun así **cae en la rama desconocida**: `composable<ClienteDetalle>`,
 * `navigate(ClienteDetalle(id))` y la lectura del `SavedStateHandle` piden el mismo
 * `NavType` y los tres fallan, con la ficha de cliente inalcanzable en la app entera.
 *
 * Reutilizar el descriptor de `String.serializer()` hace que el serial name sea
 * `"kotlin.String"`, que es exactamente lo que el `when` busca, así que el valor se
 * guarda y se lee como `String` en el `Bundle`. `PrimitiveSerialDescriptor("kotlin.String", ...)`
 * **no** sirve como sustituto: kotlinx tira por nombre de descriptor duplicado.
 *
 * El formato canónico (`8-4-4-4-12` en hex) es reversible sin información extra, así que
 * no hay pérdida: `UUID.fromString` reconstruye el valor exacto.
 */
object UuidSerializer : KSerializer<UUID> {

    override val descriptor: SerialDescriptor = String.serializer().descriptor

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}
