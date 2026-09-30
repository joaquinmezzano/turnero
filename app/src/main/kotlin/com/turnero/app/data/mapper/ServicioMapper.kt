package com.turnero.app.data.mapper

import com.turnero.app.data.local.entity.ServicioEntity
import com.turnero.app.domain.model.Servicio

/**
 * Entity <-> Domain.
 *
 * Hoy el mapeo es 1:1 porque los dos modelos tienen la misma forma. Cuando aparezca
 * una divergencia (por ejemplo `nombre` normalizado a mayuscula para ordenar, o un
 * enum de estado) este es el unico lugar donde se traduce, y las dos funciones se
 * mueven juntas.
 */
fun ServicioEntity.toDomain(): Servicio = Servicio(
    id = id,
    nombre = nombre,
    duracionMin = duracionMin,
    precioCentavos = precioCentavos,
    color = color,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun Servicio.toEntity(): ServicioEntity = ServicioEntity(
    id = id,
    nombre = nombre,
    duracionMin = duracionMin,
    precioCentavos = precioCentavos,
    color = color,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)
