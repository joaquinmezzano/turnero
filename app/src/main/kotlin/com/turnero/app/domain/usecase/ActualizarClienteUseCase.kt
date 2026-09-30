package com.turnero.app.domain.usecase

import com.turnero.app.core.datetime.ClockProvider
import com.turnero.app.domain.model.ErrorCliente
import com.turnero.app.domain.repository.ClienteRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Edición de un cliente.
 *
 * Rescata el registro existente para preservar `createdAt` (un `@Update` de Room
 * sobreescribe todas las columnas) y para responder `NoExiste` si el cliente ya fue
 * eliminado: sin esa comprobación se podrían "resucitar" filas con soft delete.
 *
 * Mismo patrón exacto que `ActualizarServicioUseCase`, incluida la ventana TOCTOU entre
 * la lectura y la escritura que ya está anotada en `config/TECH_DEBT.md`.
 */
class ActualizarClienteUseCase @Inject constructor(
    private val repository: ClienteRepository,
    private val clock: ClockProvider,
) {
    suspend operator fun invoke(
        id: UUID,
        nombre: String,
        telefono: String?,
        email: String?,
        notas: String?,
    ): Result<Unit> {
        val telefonoNormalizado = normalizarTextoOpcional(telefono)
        val emailNormalizado = normalizarTextoOpcional(email)
        validarCliente(nombre, emailNormalizado, telefonoNormalizado)?.let {
            return Result.failure(it)
        }

        val existente = repository.obtenerPorId(id).getOrElse { return Result.failure(it) }
            ?: return Result.failure(ErrorCliente.NoExiste)

        return repository.actualizar(
            existente.copy(
                nombre = nombre.trim(),
                telefono = telefonoNormalizado,
                email = emailNormalizado,
                notas = normalizarTextoOpcional(notas),
                updatedAt = clock.now(),
            ),
        )
    }
}
