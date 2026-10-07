package com.turnero.app.domain.usecase

import com.turnero.app.core.datetime.ClockProvider
import com.turnero.app.core.datetime.ZonaHorariaProvider
import com.turnero.app.domain.model.ErrorTurno
import com.turnero.app.domain.model.EstadoTurno
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
 * Alta de un turno.
 *
 * **El `Instant` de inicio lo construye este use case**, no la pantalla: la fecha y la hora
 * llegan como `LocalDate`/`LocalTime` en hora local y el dominio las ancla con la zona
 * inyectada. La UI no puede, entonces, guardar un turno en 10:37 ni decidir por su cuenta
 * que zona horaria usar.
 *
 * **`duracionMin` se copia del `Servicio` que se eligio.** Es un snapshot y no un JOIN: si
 * manana el servicio pasa de 30 a 45 minutos, este turno sigue durando 30. Por eso el turno
 * se valida contra el servicio *antes* de armar el modelo, y no despues.
 *
 * El solapamiento no se comprueba aca: vive dentro de `TurnoRepository.crear`, que hace el
 * chequeo y la insercion en la misma transaccion. Un `existeSolapamiento(...)` publico
 * seria una carrera de dos pasos, que es justo la doble reserva que la transaccion evita.
 */
class CrearTurnoUseCase @Inject constructor(
    private val turnos: TurnoRepository,
    private val clientes: ClienteRepository,
    private val servicios: ServicioRepository,
    private val clock: ClockProvider,
    private val zonaHoraria: ZonaHorariaProvider,
    private val ventana: VentanaAtencion,
) {
    suspend operator fun invoke(
        clienteId: UUID,
        servicioId: UUID,
        fecha: LocalDate,
        hora: LocalTime,
        notas: String?,
    ): Result<UUID> {
        // El cliente se busca para confirmar que existe: un turno es un compromiso con una
        // persona concreta y no tiene sentido guardar uno sin cliente.
        val cliente = clientes.obtenerPorId(clienteId).getOrElse { return Result.failure(it) }
            ?: return Result.failure(ErrorTurno.SinCliente)

        val servicio = servicios.obtenerPorId(servicioId).getOrElse { return Result.failure(it) }
            ?: return Result.failure(ErrorTurno.SinServicio)

        val ahora = clock.now()
        val zona = zonaHoraria.zona()
        validarInicioTurno(fecha, hora, ahora, zona, ventana)?.let { return Result.failure(it) }

        val turno = Turno(
            id = UUID.randomUUID(),
            clienteId = cliente.id,
            servicioId = servicio.id,
            inicio = anclarInicio(fecha, hora, zona),
            duracionMin = servicio.duracionMin,
            estado = EstadoTurno.PENDIENTE,
            notas = normalizarTextoOpcional(notas),
            createdAt = ahora,
            updatedAt = ahora,
            deletedAt = null,
        )
        return turnos.crear(turno)
    }
}