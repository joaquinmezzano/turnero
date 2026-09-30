package com.turnero.app.domain.usecase

import com.turnero.app.domain.model.Cliente
import com.turnero.app.domain.repository.ClienteRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Lee un cliente puntual para la ficha.
 *
 * `Result<Cliente?>` y no un fallo cuando no está: para la ficha, "no existe" y "el
 * cliente fue eliminado" son **la misma situación** — el DAO filtra `deletedAt IS NULL`,
 * así que las dos llegan como `null` — y la pantalla tiene que mostrar exactamente lo
 * mismo en las dos. Un `Result.failure` obligaría a desarmar el error para terminar
 * llegando al mismo mensaje.
 *
 * Por eso `ErrorCliente.NoExiste` lo decide la capa de `ui` al ver el `null`
 * (`ClienteDetalleViewModel`), y no este caso de uso: el `?` del tipo de retorno es el
 * que dice "puede no haber cliente", y llenarlo de errores lo volvería un caso especial.
 */
class ObtenerClientePorIdUseCase @Inject constructor(
    private val repository: ClienteRepository,
) {

    suspend operator fun invoke(id: UUID): Result<Cliente?> = repository.obtenerPorId(id)
}
