package com.turnero.app.domain.repository

import com.turnero.app.domain.model.Cliente
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

/**
 * Contrato de persistencia de clientes. Lo declara `domain`, lo implementa `data`.
 *
 * Todas las operaciones de lectura excluyen los registros con `deletedAt` informado: el
 * borrado es lógico (soft delete) y la UI nunca debe ver filas eliminadas.
 */
interface ClienteRepository {

    /**
     * Stream de clientes no eliminados, ordenados alfabéticamente.
     *
     * Devuelve `Flow` y no `Result` a propósito: es un stream, no un valor puntual, y un
     * `Flow<Result<List<...>>>` obliga a desempaquetar en cada elemento. Los errores de
     * lectura viajan por el canal de excepciones del `Flow` y se capturan con `catch { }`
     * aguas arriba.
     *
     * **No lleva el filtro de búsqueda.** Buscar por nombre es regla de negocio, y las
     * reglas de negocio viven en los use cases: el repositorio no sabe qué es una
     * consulta ni cómo se normaliza el texto. Ver `ObtenerClientesUseCase`.
     */
    fun observarTodos(): Flow<List<Cliente>>

    /** Cliente no eliminado con ese id, o `null` si no existe / está eliminado. */
    suspend fun obtenerPorId(id: UUID): Result<Cliente?>

    /** Inserta el cliente y devuelve el id persistido. */
    suspend fun crear(cliente: Cliente): Result<UUID>

    /** Reemplaza el cliente existente (match por PK). */
    suspend fun actualizar(cliente: Cliente): Result<Unit>

    /** Soft delete: marca `deletedAt`, no borra la fila. */
    suspend fun eliminar(id: UUID, momento: Instant): Result<Unit>
}
