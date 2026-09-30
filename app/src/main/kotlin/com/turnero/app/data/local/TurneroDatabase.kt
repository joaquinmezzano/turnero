package com.turnero.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.turnero.app.data.local.dao.ServicioDao
import com.turnero.app.data.local.entity.ServicioEntity

/**
 * Base de datos de la app, version 1.
 *
 * `exportSchema = true` vuelca el esquema a `app/schemas/` en cada build: sin esos
 * JSON no se pueden testear migraciones, asi que van versionados en git.
 */
@Database(
    entities = [ServicioEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TurneroDatabase : RoomDatabase() {

    abstract fun servicioDao(): ServicioDao
}
