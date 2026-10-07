package com.turnero.app.domain.repository

import com.turnero.app.domain.model.EstadoTurno
import com.turnero.app.domain.model.EstadisticasCliente
import com.turnero.app.domain.model.Turno
import com.turnero.app.domain.model.TurnoConServicio
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

/**
 * Contrato de persistencia de turnos. Lo declara `domain`, lo implementa `data`.
 *
 * Todas las lecturas excluyen los registros con `deletedAt` informado: el borrado es logico
 * y la UI nunca debe ver filas liberadas.
 *
 * **No declara el chequeo de solapamiento como metodo separado.** El chequeo y la escritura
 * tienen que ser atomicos para que un doble toque no produzca una doble reserva, asi que
 * van juntos dentro de [crear] y [actualizar]. Un `existeSolapamiento(...)` publicable
 * obligaria a la UI a hacer la carrera en dos pasos, que es exactamente el bug.
 */
interface TurnoRepository {

    /**
     * Turnos vivos cuyo `inicio` cae en `[desde, hasta)`, ordenados por hora, **con el
     * nombre y el color de su servicio**.
     *
     * Devuelve [TurnoConServicio] y no [Turno] porque la grilla pinta el nombre y el color,
     * y esos salen de un `JOIN` que **no filtra `servicios.deletedAt`**: el servicio de un
     * turno historico es un hecho, no un dato vigente (ver `TurnoConServicio`).
     *
     * El rango llega en `Instant` y no en `LocalDate` a proposito: el repositorio no sabe
     * que dia es "hoy" ni que zona usa el telefono. Quien convierte una fecha local en un
     * rango es el use case, con la zona inyectada.
     */
    fun observarDelDia(desde: Instant, hasta: Instant): Flow<List<TurnoConServicio>>

    /**
     * Turnos vivos del cliente, del mas reciente al mas antiguo, **con el nombre y el color
     * de su servicio**.
     *
     * Mismo motivo que [observarDelDia] para devolver [TurnoConServicio]: el historial
     * tiene que seguir mostrando "Corte de pelo" aunque el servicio ya no este en el
     * catalogo.
     */
    fun observarDeCliente(clienteId: UUID): Flow<List<TurnoConServicio>>

    /**
     * Estadisticas del cliente, recalculadas en cada cambio de la tabla.
     *
     * Sale de un `GROUP BY estado` mas el turno mas reciente. Los cinco estados estan
     * siempre presentes, en cero cuando no hay filas (ver `EstadisticasCliente.desde`).
     */
    fun observarEstadisticasDeCliente(clienteId: UUID): Flow<EstadisticasCliente>

    /** Turno no liberado con ese id, o `null` si no existe / ya fue liberado. */
    suspend fun obtenerPorId(id: UUID): Result<Turno?>

    /**
     * Turno no liberado con ese id mas el nombre y el color de su servicio, o `null` si no
     * existe / ya fue liberado.
     *
     * La ficha del turno lo usa para mostrar el servicio aunque haya sido dado de baja:
     * mismo motivo que [observarDelDia].
     */
    suspend fun obtenerPorIdConServicio(id: UUID): Result<TurnoConServicio?>

    /**
     * Inserta el turno **si el horario esta libre**.
     *
     * El chequeo de solapamiento y la insercion van en una misma transaccion: es el unico
     * punto del slice donde el doble toque es un escenario realista, y sin transaccion dos
     * toques rapidos guardarian dos turnos en la misma hora.
     *
     * Falla con `ErrorTurno.Solapamiento` si el horario esta ocupado por un turno en un
     * estado que lo bloquea.
     */
    suspend fun crear(turno: Turno): Result<UUID>

    /**
     * Reemplaza el turno existente, aplicando el mismo chequeo de solapamiento que [crear]
     * pero **excluyendo al propio turno**: si no, un turno se solaparia consigo mismo y no
     * se podrian editar sus notas.
     */
    suspend fun actualizar(turno: Turno): Result<Unit>

    /** Cambia solo el estado y refresca `updatedAt`. */
    suspend fun cambiarEstado(id: UUID, estado: EstadoTurno, momento: Instant): Result<Unit>

    /** Soft delete (`AccionTurno.LIBERAR`): marca `deletedAt`, no borra la fila. */
    suspend fun eliminar(id: UUID, momento: Instant): Result<Unit>
}