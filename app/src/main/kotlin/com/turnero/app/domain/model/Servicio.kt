package com.turnero.app.domain.model

import java.time.Instant
import java.util.UUID

/**
 * Servicio que el profesional ofrece. Es la Dimension 3 del modelo de dominio.
 *
 * `precioCentavos` es `null` cuando el servicio no tiene precio ("a consultar"), que
 * es un caso real y no un dato faltante: por eso es nullable y no 0.
 *
 * `color` es un ARGB empaquetado en `Int` para diferenciar el servicio de un vistazo
 * en la agenda. Viene del dato, no de un literal de la UI.
 */
data class Servicio(
    val id: UUID,
    val nombre: String,
    val duracionMin: Int,
    val precioCentavos: Long?,
    val color: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
)
