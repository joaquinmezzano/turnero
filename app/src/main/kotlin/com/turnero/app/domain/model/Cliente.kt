package com.turnero.app.domain.model

import java.time.Instant
import java.util.UUID

/**
 * Cliente del profesional: la persona a la que se le agenda un turno.
 *
 * `telefono`, `email` y `notas` son `null` y no vacíos a propósito. "No tengo teléfono"
 * es un estado normal de la ficha, no un dato faltante: guardarlo como `""` obligaría a
 * cada pantalla a preguntar por emptiness y a mostrar un campo vacío que parece un
 * error de carga. Los use cases normalizan texto en blanco a `null` antes de guardar
 * (ver `ValidacionCliente.kt`).
 *
 * `notas` son **privadas del profesional**: el cliente nunca las ve. Es el mismo
 * criterio que usa el resto de la app para no exponer datos, y de momento no hay
 * pantalla pública que las muestre (ver README → *Contacto*).
 *
 * No hay `direccion`, `foto` ni `color`: no están en el modelo del README. Si algún día
 * aparecen, entran acá y no como un parche en la entidad de Room.
 */
data class Cliente(
    val id: UUID,
    val nombre: String,
    val telefono: String?,
    val email: String?,
    val notas: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
)
