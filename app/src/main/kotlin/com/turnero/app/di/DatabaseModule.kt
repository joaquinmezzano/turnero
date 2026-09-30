package com.turnero.app.di

import android.content.Context
import androidx.room.Room
import com.turnero.app.data.local.TurneroDatabase
import com.turnero.app.data.local.dao.ClienteDao
import com.turnero.app.data.local.dao.ServicioDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Sin `fallbackToDestructiveMigration()`, a propósito, y la versión 2 lo confirma.
     *
     * Con la opción, Room borra la base entera y la vuelve a crear cuando no encuentra
     * una `Migration` que sepa llevar de la versión instalada a la del código. Para una
     * app cuya promesa es *offline-first*, eso es perder la agenda del usuario sin
     * avisarle: el `IllegalStateException` que Room lanzaría sin la opción es un crash en
     * desarrollo, y un crash se arregla; una base vacía se reporta como "la app me borró
     * todo".
     *
     * La versión 1 → 2 no lleva `Migration` a propósito, y acá está el porqué: la v1 nunca
     * salió a un dispositivo (ver el KDoc de `TurneroDatabase`). El día que se publique la
     * primera versión real, este mismo punto es el que tiene que recibir la `Migration`
     * correspondiente, escrita contra el `2.json` de `app/schemas/`.
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TurneroDatabase =
        Room.databaseBuilder(context, TurneroDatabase::class.java, "turnero.db").build()

    @Provides
    fun provideClienteDao(database: TurneroDatabase): ClienteDao = database.clienteDao()

    @Provides
    fun provideServicioDao(database: TurneroDatabase): ServicioDao = database.servicioDao()
}
