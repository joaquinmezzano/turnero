package com.turnero.app.domain.usecase

import com.turnero.app.domain.model.Turno
import com.turnero.app.domain.repository.TurnoRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Un turno puntual, para la pantalla de detalle.
 *
 * `Result<Turno?>` y no `Result<Turno>` a proposito: "no existe" y "fallo al leer" son
 * cosas distintas y el `Result` no las confunde. El `null` lo traduce la UI a
 * `ErrorTurno.NoExiste` si necesita mostrarlo.
 */
class ObtenerTurnoPorIdUseCase @Inject constructor(
    private val turnos: TurnoRepository,
) {
    suspend operator fun invoke(id: UUID): Result<Turno?> = turnos.obtenerPorId(id)
}