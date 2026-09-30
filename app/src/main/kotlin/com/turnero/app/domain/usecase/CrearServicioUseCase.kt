package com.turnero.app.domain.usecase

import com.turnero.app.core.datetime.ClockProvider
import com.turnero.app.domain.model.Servicio
import com.turnero.app.domain.repository.ServicioRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Alta de un servicio.
 *
 * El UUID y los timestamps los genera aca, no la UI: la pantalla no deberia poder
 * construir un `Servicio` con `createdAt` inventado. "Ahora" sale del `ClockProvider`,
 * nunca de `Instant.now()`, para que el test sea determinista.
 */
class CrearServicioUseCase @Inject constructor(
    private val repository: ServicioRepository,
    private val clock: ClockProvider,
) {
    suspend operator fun invoke(
        nombre: String,
        duracionMin: Int,
        precioCentavos: Long?,
        color: Int,
    ): Result<UUID> {
        validarServicio(nombre, duracionMin, precioCentavos)?.let { return Result.failure(it) }

        val ahora = clock.now()
        val servicio = Servicio(
            id = UUID.randomUUID(),
            nombre = nombre.trim(),
            duracionMin = duracionMin,
            precioCentavos = precioCentavos,
            color = color,
            createdAt = ahora,
            updatedAt = ahora,
            deletedAt = null,
        )
        return repository.crear(servicio)
    }
}
