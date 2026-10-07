package com.turnero.app.domain.usecase

import com.turnero.app.domain.model.TurnoConServicio
import com.turnero.app.domain.repository.TurnoRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Un turno puntual con el nombre y el color de su servicio, para la ficha de detalle.
 *
 * Separado de [ObtenerTurnoPorIdUseCase] a proposito: el editor solo necesita los campos
 * editables del turno y no paga el `JOIN`; la ficha si necesita mostrar de que servicio
 * fue, incluso si ese servicio ya fue dado de baja (ver `TurnoConServicio`).
 *
 * `Result<TurnoConServicio?>` y no `Result<TurnoConServicio>` por el mismo motivo que
 * [ObtenerTurnoPorIdUseCase]: "no existe" y "fallo al leer" son cosas distintas.
 */
class ObtenerTurnoConServicioPorIdUseCase @Inject constructor(
    private val turnos: TurnoRepository,
) {
    suspend operator fun invoke(id: UUID): Result<TurnoConServicio?> =
        turnos.obtenerPorIdConServicio(id)
}
