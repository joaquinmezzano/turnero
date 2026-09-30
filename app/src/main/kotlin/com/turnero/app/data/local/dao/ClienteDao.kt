package com.turnero.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.turnero.app.data.local.entity.ClienteEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

@Dao
interface ClienteDao {

    /**
     * Toda `@Query` filtra `deletedAt IS NULL`: es la única barrera contra que la UI
     * muestre filas borradas (ver AGENTS.md).
     *
     * **Acá no hay filtro de búsqueda por nombre, a propósito.** Un `LIKE` de SQLite solo
     * es case-insensitive para ASCII, así que `LIKE '%optica%'` no encuentra `"Óptica"`
     * ni `LIKE '%óptica%'` encuentra `"Optica"`. Buscar en memoria sobre unos cientos de
     * filas es instantáneo y funciona en las dos direcciones. Ver
     * `ObtenerClientesUseCase`.
     *
     * El `ORDER BY` NO es el orden que ve el usuario. `COLLATE NOCASE` solo pliega A-Z
     * ASCII, así que en español "Óptica", "Ámbar" y "Ñandú" caen detrás de "zebra". El
     * orden correcto lo aplica `ClienteRepositoryImpl` con un `Collator` de locale; este
     * `ORDER BY` queda como desempate estable para que la consulta sea reproducible.
     * Ver `core/text/OrdenAlfabetico.kt`.
     */
    @Query("SELECT * FROM clientes WHERE deletedAt IS NULL ORDER BY nombre COLLATE NOCASE ASC")
    fun observarTodos(): Flow<List<ClienteEntity>>

    @Query("SELECT * FROM clientes WHERE id = :id AND deletedAt IS NULL")
    suspend fun obtenerPorId(id: UUID): ClienteEntity?

    /** `@Insert` devuelve el rowid de SQLite, no el UUID. El id lo conoce el modelo. */
    @Insert
    suspend fun insertar(entity: ClienteEntity): Long

    /** Resuelve por PK. El repositorio verifica que se haya afectado una fila. */
    @Update
    suspend fun actualizar(entity: ClienteEntity): Int

    /**
     * Soft delete. El `AND deletedAt IS NULL` hace la operación idempotente: una segunda
     * baja del mismo id devuelve 0 y no pisa la fecha original de eliminación.
     */
    @Query(
        "UPDATE clientes SET deletedAt = :momento, updatedAt = :momento " +
            "WHERE id = :id AND deletedAt IS NULL",
    )
    suspend fun marcarEliminado(id: UUID, momento: Instant): Int
}
