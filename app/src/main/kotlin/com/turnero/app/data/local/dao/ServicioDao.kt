package com.turnero.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.turnero.app.data.local.entity.ServicioEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

@Dao
interface ServicioDao {

    /**
     * Toda `@Query` filtra `deletedAt IS NULL`: es la unica barrera contra que la UI
     * muestre filas borradas (ver AGENTS.md).
     *
     * El `ORDER BY` NO es el orden que ve el usuario. `COLLATE NOCASE` solo pliega A-Z
     * ASCII, asi que en espanol "Optica", "Ambar" y "Nandu" caen detras de "zebra". El
     * orden correcto lo aplica `ServicioRepositoryImpl` con un `Collator` de locale; este
     * `ORDER BY` queda como desempate estable para que la consulta sea reproducible.
     * Ver `core/text/OrdenAlfabetico.kt`.
     */
    @Query("SELECT * FROM servicios WHERE deletedAt IS NULL ORDER BY nombre COLLATE NOCASE ASC")
    fun observarTodos(): Flow<List<ServicioEntity>>

    @Query("SELECT * FROM servicios WHERE id = :id AND deletedAt IS NULL")
    suspend fun obtenerPorId(id: UUID): ServicioEntity?

    /** `@Insert` devuelve el rowid de SQLite, no el UUID. El id lo conoce el modelo. */
    @Insert
    suspend fun insertar(entity: ServicioEntity): Long

    /** Resuelve por PK. El repositorio verifica que se haya afectado una fila. */
    @Update
    suspend fun actualizar(entity: ServicioEntity): Int

    /**
     * Soft delete. El `AND deletedAt IS NULL` hace la operacion idempotente: una segunda
     * baja del mismo id devuelve 0 y no pisa la fecha original de eliminacion.
     */
    @Query(
        "UPDATE servicios SET deletedAt = :momento, updatedAt = :momento " +
            "WHERE id = :id AND deletedAt IS NULL",
    )
    suspend fun marcarEliminado(id: UUID, momento: Instant): Int
}
