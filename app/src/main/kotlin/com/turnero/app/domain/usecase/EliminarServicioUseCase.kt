package com.turnero.app.domain.usecase

import com.turnero.app.core.datetime.ClockProvider
import com.turnero.app.domain.model.ErrorServicio
import com.turnero.app.domain.repository.ServicioRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Baja logica de un servicio: marca `deletedAt`, nunca borra la fila (ver AGENTS.md).
 *
 * El instante del borrado sale del `ClockProvider`. Se consulta antes de borrar para
 * poder distinguir "no existe" de "borrado": el DAO filtra `deletedAt IS NULL` y una
 * segunda baja sobre el mismo id no tocaria ninguna fila.
 */
class EliminarServicioUseCase @Inject constructor(
    private val repository: ServicioRepository,
    private val clock: ClockProvider,
) {
    suspend operator fun invoke(id: UUID): Result<Unit> {
        val existente = repository.obtenerPorId(id).getOrElse { return Result.failure(it) }
            ?: return Result.failure(ErrorServicio.NoExiste)

        return repository.eliminar(existente.id, clock.now())
    }
}
