package com.turnero.app.domain.repository

import com.turnero.app.domain.model.Servicio
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

/**
 * Contrato de persistencia de servicios. Lo declara `domain`, lo implementa `data`.
 *
 * Todas las operaciones de lectura excluyen los registros con `deletedAt` informado:
 * el borrado es logico (soft delete) y la UI nunca debe ver filas eliminadas.
 */
interface ServicioRepository {

    /**
     * Stream de servicios no eliminados, ordenados alfabeticamente.
     *
     * Devuelve `Flow` y no `Result` a proposito: es un stream, no un valor puntual, y
     * un `Flow<Result<List<...>>>` obliga a desempaquetar en cada elemento. Los errores
     * de lectura viajan por el canal de excepciones del `Flow` y se capturan con
     * `catch { }` aguas arriba.
     */
    fun observarTodos(): Flow<List<Servicio>>

    /** Servicio no eliminado con ese id, o `null` si no existe / esta eliminado. */
    suspend fun obtenerPorId(id: UUID): Result<Servicio?>

    /** Inserta el servicio y devuelve el id persistido. */
    suspend fun crear(servicio: Servicio): Result<UUID>

    /** Reemplaza el servicio existente (match por PK). */
    suspend fun actualizar(servicio: Servicio): Result<Unit>

    /** Soft delete: marca `deletedAt`, no borra la fila. */
    suspend fun eliminar(id: UUID, momento: Instant): Result<Unit>
}
