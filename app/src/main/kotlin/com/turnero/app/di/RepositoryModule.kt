package com.turnero.app.di

import com.turnero.app.data.repository.ClienteRepositoryImpl
import com.turnero.app.data.repository.ServicioRepositoryImpl
import com.turnero.app.domain.repository.ClienteRepository
import com.turnero.app.domain.repository.ServicioRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * El binding de interfaz de `domain` a implementación de `data` vive acá y en ningún
 * otro lado: `data` no debe importar Hilt para saber cómo se provee.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindClienteRepository(impl: ClienteRepositoryImpl): ClienteRepository

    @Binds
    @Singleton
    abstract fun bindServicioRepository(impl: ServicioRepositoryImpl): ServicioRepository
}
