package com.turnero.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.turnero.app.data.local.dao.ClienteDao
import com.turnero.app.data.local.dao.ServicioDao
import com.turnero.app.data.local.dao.TurnoDao
import com.turnero.app.data.local.entity.ClienteEntity
import com.turnero.app.data.local.entity.ServicioEntity
import com.turnero.app.data.local.entity.TurnoEntity

/**
 * Base de datos de la app, versión 3.
 *
 * **Por qué sube a 3 sin `Migration`.** Ni la versión 1 ni la 2 salieron de desarrollo: el
 * slice 2 se cerró sin publicar nada. No hay usuarios a los que migrarles la agenda, y
 * escribir una `Migration(2, 3)` igual sería una mentira verosímil: parecería que el esquema
 * v2 está en producción y alguien la probaría contra una base creada con el esquema v2
 * real, que no es el mismo que el que el test cree. Por eso se borró `2.json` y se exporta
 * solo `3.json`: el historial de esquemas tiene que contar la historia real del proyecto, no
 * una inventada. Mismo criterio que la v1 → 2.
 *
 * **Por qué no `fallbackToDestructiveMigration()`.** Ver el KDoc de `DatabaseModule.kt`.
 * La primera versión que salga a un dispositivo real es la que tiene que respaldar este
 * esquema con una `Migration` de verdad, y eso es trabajo del slice que la publique, no de
 * este.
 *
 * `exportSchema = true` vuelca el esquema a `app/schemas/` en cada build: sin esos JSON no
 * se pueden testear migraciones, así que van versionados en git.
 */
@Database(
    entities = [ClienteEntity::class, ServicioEntity::class, TurnoEntity::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TurneroDatabase : RoomDatabase() {

    abstract fun clienteDao(): ClienteDao

    abstract fun servicioDao(): ServicioDao

    abstract fun turnoDao(): TurnoDao
}