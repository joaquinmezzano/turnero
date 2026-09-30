package com.turnero.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Fila de la tabla `clientes`.
 *
 * `id` es un `UUID` generado en Kotlin (obligatorio para sync futuro) y no un
 * autoincremental: Room lo persiste nativamente como BLOB de 16 bytes, sin converter.
 * Agregar un converter de `UUID` "por las dudas" cambiaría la afinidad de la columna y
 * el `identityHash` del esquema; ver `Converters.kt`.
 *
 * `createdAt` / `updatedAt` / `deletedAt` están desde el día uno para que el borrado
 * lógico y la auditoría no necesiten una migración más adelante.
 *
 * Los tres campos de contacto van en el mismo orden que en `Cliente` y no se les pone
 * índice: el catálogo de clientes son cientos de filas y el slice 2 busca en memoria
 * (ver `ObtenerClientesUseCase`). La deuda del índice está en `config/TECH_DEBT.md`.
 */
@Entity(tableName = "clientes")
data class ClienteEntity(
    @PrimaryKey val id: UUID,
    val nombre: String,
    val telefono: String?,
    val email: String?,
    val notas: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
)
