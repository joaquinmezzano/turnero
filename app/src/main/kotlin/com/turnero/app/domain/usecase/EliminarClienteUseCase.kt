package com.turnero.app.domain.usecase

import com.turnero.app.core.datetime.ClockProvider
import com.turnero.app.domain.model.ErrorCliente
import com.turnero.app.domain.repository.ClienteRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Baja lógica de un cliente: marca `deletedAt`, nunca borra la fila (ver AGENTS.md).
 *
 * El instante del borrado sale del `ClockProvider`. Se consulta antes de borrar para
 * poder distinguir "no existe" de "borrado": el DAO filtra `deletedAt IS NULL` y una
 * segunda baja sobre el mismo id no tocaría ninguna fila.
 *
 * **El turno no se toca.** Esta baja no propaga a los turnos del cliente: la fila de
 * `turno` conserva su `clienteId` y queda apuntando a un cliente eliminado. Es
 * exactamente el mismo criterio que el soft delete de `Servicio` y la razón por la que
 * el modelo tiene los tres timestamps desde el día uno. Slice 3 define qué muestra la
 * agenda cuando el cliente ya no está; hasta entonces, ningún turno se mueve ni se
 * borra por dar de baja a un cliente.
 */
class EliminarClienteUseCase @Inject constructor(
    private val repository: ClienteRepository,
    private val clock: ClockProvider,
) {
    suspend operator fun invoke(id: UUID): Result<Unit> {
        val existente = repository.obtenerPorId(id).getOrElse { return Result.failure(it) }
            ?: return Result.failure(ErrorCliente.NoExiste)

        return repository.eliminar(existente.id, clock.now())
    }
}
