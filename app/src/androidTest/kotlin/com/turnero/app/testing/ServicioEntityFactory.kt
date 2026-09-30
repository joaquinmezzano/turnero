package com.turnero.app.testing

import com.turnero.app.data.local.entity.ServicioEntity
import java.time.Instant
import java.util.UUID

/** Instante fijo y reconocible para poder rastrear un fallo en la base. */
val INSTANTE_BASE: Instant = Instant.parse("2026-03-14T10:15:30Z")

/** ARGB empaquetado, identico al que llega desde la UI. */
const val COLOR_BASE: Int = 0xFF6200EE.toInt()

/**
 * Constructor de `ServicioEntity` con defaults validos.
 *
 * Va duplicado del de `src/test` a proposito: `androidTest` no ve el source set `test`
 * (no es un source set compartido, y agregar `test` como `dependsOn` de `androidTest`
 * solo para esto arrastraria Turbine y los fakes de dominio a un modulo que no los
 * necesita). Los dosxygen lo que cambia es el tipo: aqui se construye la entidad.
 */
fun entidadServicio(
    id: UUID = UUID.randomUUID(),
    nombre: String = "Corte",
    duracionMin: Int = 30,
    precioCentavos: Long? = 5_000L,
    color: Int = COLOR_BASE,
    createdAt: Instant = INSTANTE_BASE,
    updatedAt: Instant = createdAt,
    deletedAt: Instant? = null,
): ServicioEntity = ServicioEntity(
    id = id,
    nombre = nombre,
    duracionMin = duracionMin,
    precioCentavos = precioCentavos,
    color = color,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)