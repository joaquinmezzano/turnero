package com.turnero.app.di

import com.turnero.app.core.datetime.ClockProvider
import com.turnero.app.core.datetime.SystemClockProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * El `ClockProvider` es el unico punto del proyecto donde "ahora" se resuelve contra el
 * reloj real. Los tests de `domain` inyectan un fake y por eso son deterministas.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ClockModule {

    @Binds
    @Singleton
    abstract fun bindClockProvider(impl: SystemClockProvider): ClockProvider
}
