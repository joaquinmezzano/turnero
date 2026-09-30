package com.turnero.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.turnero.app.data.local.dao.ClienteDao
import com.turnero.app.data.local.dao.ServicioDao
import com.turnero.app.data.local.entity.ClienteEntity
import com.turnero.app.data.local.entity.ServicioEntity

/**
 * Base de datos de la app, versión 2.
 *
 * **Por qué sube a 2 sin `Migration`.** La versión 1 solo tenía `servicios` y nunca llegó
 * a un dispositivo real: el slice 1 se cerró sin publicar nada. No hay usuarios a los que
 * migrarles la agenda, y escribir una `Migration(1, 2)` igual sería una mentira
 * verosímil: parecería que el esquema v1 está en producción y alguien la probaría contra
 * una base creada con el esquema v1 real, que no es el mismo que el que el test cree.
 * Por eso se borró `1.json` y se exporta solo `2.json`: el historial de esquemas tiene que
 * contar la historia real del proyecto, no una inventada.
 *
 * **Por qué no `fallbackToDestructiveMigration()`.** Ver el KDoc de `DatabaseModule.kt`:
 * la opción convertiría un fallo ruidoso de esquema en la pérdida silenciosa de la agenda
 * del usuario. La primera versión que salga a un dispositivo real es la que tiene que
 * respaldar este esquema con una `Migration` de verdad, y eso es trabajo del slice que
 * la publique, no de este.
 *
 * `exportSchema = true` vuelca el esquema a `app/schemas/` en cada build: sin esos JSON
 * no se pueden testear migraciones, así que van versionados en git.
 */
@Database(
    entities = [ClienteEntity::class, ServicioEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TurneroDatabase : RoomDatabase() {

    abstract fun clienteDao(): ClienteDao

    abstract fun servicioDao(): ServicioDao
}
