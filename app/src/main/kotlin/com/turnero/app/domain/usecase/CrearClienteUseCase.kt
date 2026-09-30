package com.turnero.app.domain.usecase

import com.turnero.app.core.datetime.ClockProvider
import com.turnero.app.domain.model.Cliente
import com.turnero.app.domain.repository.ClienteRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Alta de un cliente.
 *
 * El UUID y los timestamps los genera acá, no la UI: la pantalla no debería poder
 * construir un `Cliente` con `createdAt` inventado. "Ahora" sale del `ClockProvider`, nunca
 * de `Instant.now()`, para que el test sea determinista.
 *
 * Teléfono, email y notas se normalizan **antes** de validar, así que se guarda
 * exactamente lo que se validó: un campo que el usuario dejó en blanco queda en `null` y
 * no como una cadena vacía (ver `ValidacionCliente.kt`).
 */
class CrearClienteUseCase @Inject constructor(
    private val repository: ClienteRepository,
    private val clock: ClockProvider,
) {
    suspend operator fun invoke(
        nombre: String,
        telefono: String?,
        email: String?,
        notas: String?,
    ): Result<UUID> {
        val telefonoNormalizado = normalizarTextoOpcional(telefono)
        val emailNormalizado = normalizarTextoOpcional(email)
        validarCliente(nombre, emailNormalizado, telefonoNormalizado)?.let {
            return Result.failure(it)
        }

        val ahora = clock.now()
        val cliente = Cliente(
            id = UUID.randomUUID(),
            nombre = nombre.trim(),
            telefono = telefonoNormalizado,
            email = emailNormalizado,
            notas = normalizarTextoOpcional(notas),
            createdAt = ahora,
            updatedAt = ahora,
            deletedAt = null,
        )
        return repository.crear(cliente)
    }
}
