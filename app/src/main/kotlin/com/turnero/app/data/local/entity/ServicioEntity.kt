package com.turnero.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Fila de la tabla `servicios`.
 *
 * `id` es un `UUID` generado en Kotlin (obligatorio para sync futuro) y no un
 * autoincremental: Room lo persiste nativamente como BLOB de 16 bytes, sin converter.
 *
 * `createdAt` / `updatedAt` / `deletedAt` estan desde el dia uno para que el borrado
 * logico y la auditoria no necesiten una migracion mas adelante.
 */
@Entity(tableName = "servicios")
data class ServicioEntity(
    @PrimaryKey val id: UUID,
    val nombre: String,
    val duracionMin: Int,
    val precioCentavos: Long?,
    val color: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
)
