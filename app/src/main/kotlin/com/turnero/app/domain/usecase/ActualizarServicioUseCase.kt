package com.turnero.app.domain.usecase

import com.turnero.app.core.datetime.ClockProvider
import com.turnero.app.domain.model.ErrorServicio
import com.turnero.app.domain.repository.ServicioRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Edicion de un servicio.
 *
 * Rescata el registro existente para preservar `createdAt` (un `@Update` de Room
 * sobreescribe todas las columnas) y para responder `NoExiste` si el servicio ya fue
 * eliminado: sin esa comprobacion se podrian "resucitar" filas con soft delete.
 */
class ActualizarServicioUseCase @Inject constructor(
    private val repository: ServicioRepository,
    private val clock: ClockProvider,
) {
    suspend operator fun invoke(
        id: UUID,
        nombre: String,
        duracionMin: Int,
        precioCentavos: Long?,
        color: Int,
    ): Result<Unit> {
        validarServicio(nombre, duracionMin, precioCentavos)?.let { return Result.failure(it) }

        val existente = repository.obtenerPorId(id).getOrElse { return Result.failure(it) }
            ?: return Result.failure(ErrorServicio.NoExiste)

        return repository.actualizar(
            existente.copy(
                nombre = nombre.trim(),
                duracionMin = duracionMin,
                precioCentavos = precioCentavos,
                color = color,
                updatedAt = clock.now(),
            ),
        )
    }
}
