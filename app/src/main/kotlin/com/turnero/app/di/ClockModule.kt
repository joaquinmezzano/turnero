package com.turnero.app.di

import com.turnero.app.core.datetime.ClockProvider
import com.turnero.app.core.datetime.SystemClockProvider
import com.turnero.app.core.datetime.SystemZonaHorariaProvider
import com.turnero.app.core.datetime.ZonaHorariaProvider
import com.turnero.app.domain.model.VENTANA_POR_DEFECTO
import com.turnero.app.domain.model.VentanaAtencion
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * El `ClockProvider` es el unico punto del proyecto donde "ahora" se resuelve contra el
 * reloj real. Los tests de `domain` inyectan un fake y por eso son deterministas.
 *
 * Acá vive tambien la [ZonaHorariaProvider] por el mismo motivo: el turno se agenda a
 * partir de fecha y hora locales, y sin esa indireccion el test pasaria solo en la zona de
 * quien lo corre.
 *
 * Y la [VentanaAtencion], que es un **valor**, no una interfaz: hoy es
 * [VENTANA_POR_DEFECTO] y el Slice 6 la reemplaza por `HorarioAtencion` por dia de semana.
 * Que se provea acá y no se lea como constante dentro de la funcion de validacion es lo que
 * hace que ese cambio no toque un solo call site.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ClockModule {

    @Binds
    @Singleton
    abstract fun bindClockProvider(impl: SystemClockProvider): ClockProvider

    @Binds
    @Singleton
    abstract fun bindZonaHorariaProvider(impl: SystemZonaHorariaProvider): ZonaHorariaProvider

    companion object {

        @Provides
        @Singleton
        fun provideVentanaAtencion(): VentanaAtencion = VENTANA_POR_DEFECTO
    }
}