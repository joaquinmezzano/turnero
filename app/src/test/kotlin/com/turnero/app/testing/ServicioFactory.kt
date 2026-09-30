package com.turnero.app.testing

import com.turnero.app.domain.model.Servicio
import java.time.Instant
import java.util.UUID

/**
 * Datos de prueba compartidos para todos los tests de `Servicio`.
 *
 * `INSTANTE_BASE` es un instante fijo y reconocible (2026-03-14 10:15:30 UTC) para que
 * un fallo muestre una fecha con la que se puede rastrear, en vez del reloj de la
 * maquina. Todos los defaults son validos para un servicio guardado.
 */
val INSTANTE_BASE: Instant = Instant.parse("2026-03-14T10:15:30Z")

/** ARGB empaquetado, igual que el que llega desde la UI. */
const val COLOR_BASE: Int = 0xFF6200EE.toInt()

/**
 * Constructor de `Servicio` con defaults validos.
 *
 * Vive en `testing/` y no duplicado por test: los casos de uso, el mapper y el
 * ViewModel necesitan la misma forma de dato y tres copias divergen.
 */
fun servicio(
    id: UUID = UUID.randomUUID(),
    nombre: String = "Corte",
    duracionMin: Int = 30,
    precioCentavos: Long? = 5_000L,
    color: Int = COLOR_BASE,
    createdAt: Instant = INSTANTE_BASE,
    updatedAt: Instant = createdAt,
    deletedAt: Instant? = null,
): Servicio = Servicio(
    id = id,
    nombre = nombre,
    duracionMin = duracionMin,
    precioCentavos = precioCentavos,
    color = color,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)