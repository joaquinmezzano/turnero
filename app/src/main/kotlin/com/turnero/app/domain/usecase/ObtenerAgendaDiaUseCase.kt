package com.turnero.app.domain.usecase

import com.turnero.app.core.datetime.ZonaHorariaProvider
import com.turnero.app.domain.model.TurnoConServicio
import com.turnero.app.domain.repository.TurnoRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * Turnos de un dia, ordenados por hora.
 *
 * Recibe un [LocalDate] y no un rango de `Instant` porque "el dia" es un concepto de la
 * agenda, no de la base: convertir la fecha local al intervalo `[00:00, 24:00)` de la zona
 * del telefono es regla de negocio y por eso vive aca, no en la pantalla. La zona se lee del
 * provider inyectado, nunca de `ZoneId.systemDefault()` dentro del use case.
 */
class ObtenerAgendaDiaUseCase @Inject constructor(
    private val turnos: TurnoRepository,
    private val zonaHoraria: ZonaHorariaProvider,
) {
    operator fun invoke(fecha: LocalDate): Flow<List<TurnoConServicio>> {
        val zona = zonaHoraria.zona()
        val desde: Instant = fecha.atStartOfDay(zona).toInstant()
        val hasta: Instant = fecha.plusDays(1).atStartOfDay(zona).toInstant()
        return turnos.observarDelDia(desde, hasta)
    }
}