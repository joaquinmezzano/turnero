package com.turnero.app.domain.usecase

import com.turnero.app.domain.model.TurnoConServicio
import com.turnero.app.domain.repository.TurnoRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

/**
 * Historial de turnos de un cliente, del mas reciente al mas antiguo.
 *
 * Es el bloque que le faltaba a la ficha del cliente desde el Slice 2, y el lugar donde la
 * maquina de estados se vuelve accionable sobre turnos viejos: es aca donde un `CANCELADO`
 * de hace tres meses se vuelve reversible si todavia no paso el horario.
 */
class ObtenerTurnosDeClienteUseCase @Inject constructor(
    private val turnos: TurnoRepository,
) {
    operator fun invoke(clienteId: UUID): Flow<List<TurnoConServicio>> = turnos.observarDeCliente(clienteId)
}