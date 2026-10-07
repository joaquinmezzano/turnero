package com.turnero.app.domain.usecase

import com.turnero.app.core.datetime.ClockProvider
import com.turnero.app.domain.model.AccionTurno
import com.turnero.app.domain.model.ErrorTurno
import com.turnero.app.domain.model.EstadoTurno
import com.turnero.app.domain.model.accionPermitida
import com.turnero.app.domain.repository.TurnoRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Ejecuta una [AccionTurno] sobre un turno.
 *
 * **Vuelve a consultar la maquina de estados antes de escribir.** La UI ya dibujo solo las
 * acciones disponibles, pero entre el toque y el guardado el turno puede haber cambiado: el
 * doble toque en "Confirmar" no tiene que dejar el turno en un estado raro, y un import o
 * un sync que escriban directo en la base no pueden saltar la validacion. Es la misma
 * funcion pura que la pantalla pinto (ver `accionesDisponibles`).
 *
 * `LIBERAR` no cambia el enum del estado: es un soft delete, el mecanismo que ya usa el
 * resto de la app.
 */
class CambiarEstadoTurnoUseCase @Inject constructor(
    private val turnos: TurnoRepository,
    private val clock: ClockProvider,
) {
    suspend operator fun invoke(id: UUID, accion: AccionTurno): Result<Unit> {
        val turno = turnos.obtenerPorId(id).getOrElse { return Result.failure(it) }
            ?: return Result.failure(ErrorTurno.NoExiste)

        val ahora = clock.now()
        if (!accionPermitida(turno, accion, ahora)) {
            return Result.failure(ErrorTurno.TransicionInvalida)
        }

        return when (accion) {
            AccionTurno.LIBERAR -> turnos.eliminar(turno.id, ahora)
            AccionTurno.CONFIRMAR -> turnos.cambiarEstado(turno.id, EstadoTurno.CONFIRMADO, ahora)
            AccionTurno.MARCAR_ATENDIDO -> turnos.cambiarEstado(turno.id, EstadoTurno.ATENDIDO, ahora)
            AccionTurno.MARCAR_AUSENTE -> turnos.cambiarEstado(turno.id, EstadoTurno.AUSENTE, ahora)
            AccionTurno.CANCELAR -> turnos.cambiarEstado(turno.id, EstadoTurno.CANCELADO, ahora)
            AccionTurno.VOLVER_A_PENDIENTE -> turnos.cambiarEstado(turno.id, EstadoTurno.PENDIENTE, ahora)
        }
    }
}