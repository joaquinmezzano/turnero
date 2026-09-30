package com.turnero.app.di

import android.content.Context
import androidx.room.Room
import com.turnero.app.data.local.TurneroDatabase
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
     * Sin `fallbackToDestructiveMigration()` a proposito.
     *
     * En la version 1 la base es nueva y no hay migracion que ejecutar, asi que la
     * opcion no cambia nada hoy. Cuando aparezca la version 2,Room lanzara
     * `IllegalStateException` en vez de borrar la agenda del usuario en silencio: un
     * fallo ruidoso en desarrollo es infinitamente preferable a perder turnos en
     * produccion. Agregar la migracion real es trabajo del slice que agregue la
     * version 2.
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TurneroDatabase =
        Room.databaseBuilder(context, TurneroDatabase::class.java, "turnero.db").build()

    @Provides
    fun provideServicioDao(database: TurneroDatabase): ServicioDao = database.servicioDao()
}
