package com.turnero.app.domain.usecase

import com.turnero.app.domain.model.EstadisticasCliente
import com.turnero.app.domain.repository.TurnoRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

/**
 * Bloque de estadisticas de la ficha del cliente.
 *
 * Devuelve un `Flow` y no un `Result` porque es una proyeccion viva: el conteo tiene que
 * cambiar solo en cuanto el profesional marca un turno como atendido, sin que la pantalla
 * vuelva a pedirlo. La cuenta se hace en SQL en cada cambio (ver
 * `TurnoRepository.observarEstadisticasDeCliente`), sin contadores materializados.
 */
class ObtenerEstadisticasClienteUseCase @Inject constructor(
    private val turnos: TurnoRepository,
) {
    operator fun invoke(clienteId: UUID): Flow<EstadisticasCliente> =
        turnos.observarEstadisticasDeCliente(clienteId)
}