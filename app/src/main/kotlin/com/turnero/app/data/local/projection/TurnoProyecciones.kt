package com.turnero.app.data.local.projection

import com.turnero.app.domain.model.EstadoTurno
import java.time.Instant
import java.util.UUID

/**
 * Fila de `GROUP BY estado` para las estadisticas del cliente.
 *
 * Es una proyeccion de solo lectura, no una entidad: no tiene tabla, PK ni fechas. El mapeo
 * a `EstadisticasCliente` es el que rellena en cero los estados que `GROUP BY` omite.
 */
data class ConteoEstado(
    val estado: EstadoTurno,
    val cantidad: Int,
)

/**
 * Fila de "el turno mas reciente del cliente", con el nombre del servicio resuelto por JOIN.
 *
 * Es una proyeccion y no un `Turno` porque el nombre del servicio **no** se guarda en
 * `turnos` (ver `Turno`): se resuelve en el momento de leer, y por eso necesita el JOIN.
 */
data class UltimaVisitaFila(
    val inicio: Instant,
    val estado: EstadoTurno,
    val servicioNombre: String,
)

/**
 * Fila de turno con el nombre y el color de su servicio, resueltos por `JOIN`.
 *
 * Refleja **todas** las columnas de `turnos` mas las dos del servicio. Es una proyeccion y
 * no un `TurnoEntity` porque el nombre y el color no viven en `turnos`: se resuelven al
 * leer (ver el KDoc de `TurnoConServicio`), asi que necesita el `JOIN`.
 *
 * El `JOIN` que la alimenta **no filtra `servicios.deletedAt`** por el motivo explicado en
 * el KDoc de cada query. `deletedAt` se mapea igual para que la proyeccion siga siendo la
 * fila completa.
 */
data class TurnoConServicioFila(
    val id: UUID,
    val clienteId: UUID,
    val servicioId: UUID,
    val inicio: Instant,
    val duracionMin: Int,
    val estado: EstadoTurno,
    val notas: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
    val servicioNombre: String,
    val servicioColor: Int?,
)