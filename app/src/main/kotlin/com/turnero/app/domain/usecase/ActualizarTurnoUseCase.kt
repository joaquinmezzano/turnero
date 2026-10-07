package com.turnero.app.domain.usecase

import com.turnero.app.core.datetime.ClockProvider
import com.turnero.app.core.datetime.ZonaHorariaProvider
import com.turnero.app.domain.model.ErrorTurno
import com.turnero.app.domain.model.Turno
import com.turnero.app.domain.model.VentanaAtencion
import com.turnero.app.domain.repository.ClienteRepository
import com.turnero.app.domain.repository.ServicioRepository
import com.turnero.app.domain.repository.TurnoRepository
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

/**
 * Edicion de un turno: se le cambia el cliente, el servicio, la hora o las notas.
 *
 * El estado **no** se edita desde aca: los cambios de estado pasan por
 * `CambiarEstadoTurnoUseCase`, que es el que consulta la maquina de estados. Editar y cambiar
 * de estado son dos caminos distintos a proposito, y dejar el estado fuera de la pantalla de
 * edicion evita que se pueda escribir un estado invalido.
 *
 * Rescata el turno existente para preservar `createdAt`, `estado` y el `id`, y para
 * responder `NoExiste` si ya fue liberado.
 */
class ActualizarTurnoUseCase @Inject constructor(
    private val turnos: TurnoRepository,
    private val clientes: ClienteRepository,
    private val servicios: ServicioRepository,
    private val clock: ClockProvider,
    private val zonaHoraria: ZonaHorariaProvider,
    private val ventana: VentanaAtencion,
) {
    suspend operator fun invoke(
        id: UUID,
        clienteId: UUID,
        servicioId: UUID,
        fecha: LocalDate,
        hora: LocalTime,
        notas: String?,
    ): Result<Unit> {
        val existente = turnos.obtenerPorId(id).getOrElse { return Result.failure(it) }
            ?: return Result.failure(ErrorTurno.NoExiste)

        clientes.obtenerPorId(clienteId).getOrElse { return Result.failure(it) }
            ?: return Result.failure(ErrorTurno.SinCliente)

        val servicio = servicios.obtenerPorId(servicioId).getOrElse { return Result.failure(it) }
            ?: return Result.failure(ErrorTurno.SinServicio)

        val ahora = clock.now()
        val zona = zonaHoraria.zona()
        validarInicioTurno(fecha, hora, ahora, zona, ventana)?.let { return Result.failure(it) }

        // La duracion se re-ANCLA solo si el usuario eligio otro servicio. Si se edita la
        // hora o las notas y el servicio es el mismo, se conserva el snapshot existente:
        // si no, cambiar la duracion de un servicio en el catalogo moveria los turnos ya
        // agendados por el solo hecho de editarles una nota.
        val duracionMin = if (servicioId == existente.servicioId) {
            existente.duracionMin
        } else {
            servicio.duracionMin
        }

        return turnos.actualizar(
            existente.copy(
                clienteId = clienteId,
                servicioId = servicioId,
                inicio = anclarInicio(fecha, hora, zona),
                duracionMin = duracionMin,
                notas = normalizarTextoOpcional(notas),
                updatedAt = ahora,
            ),
        )
    }
}