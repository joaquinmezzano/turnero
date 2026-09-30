package com.turnero.app.data.local

import androidx.room.TypeConverter
import java.time.Instant

/**
 * Convertidores de tipo de Room.
 *
 * Solo `Instant`, que Room no soporta. `UUID` se persiste nativamente (BLOB de 16
 * bytes) y los enums tambien, asi que no se tocan: un converter de mas es una
 * superficie de bug mas. Agregar uno ademas cambiaria la afinidad de la columna, el
 * `identityHash` del schema y volveria obligatoria una migracion sobre una base ya
 * distribuida.
 *
 * La firma acepta y devuelve nullables porque `deletedAt` es nullable. Si las firmas
 * fueran no-null, Room no encontraria converter para las columnas anulables y el
 * `ksp` falla en tiempo de compilacion.
 */
class Converters {

    @TypeConverter
    fun instantALong(valor: Instant?): Long? = valor?.toEpochMilli()

    @TypeConverter
    fun longAInstant(valor: Long?): Instant? = valor?.let(Instant::ofEpochMilli)
}
