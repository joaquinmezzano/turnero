package com.turnero.app.di

import com.turnero.app.data.repository.ServicioRepositoryImpl
import com.turnero.app.domain.repository.ServicioRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * El binding de interfaz de `domain` a implementacion de `data` vive aca y en ningun
 * otro lado: `data` no debe importar Hilt para saber como se provee.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindServicioRepository(impl: ServicioRepositoryImpl): ServicioRepository
}
