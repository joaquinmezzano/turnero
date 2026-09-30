package com.turnero.app.data.mapper

import com.turnero.app.data.local.entity.ClienteEntity
import com.turnero.app.domain.model.Cliente

/**
 * Entity <-> Domain.
 *
 * El mapeo es 1:1 porque los dos modelos tienen la misma forma. No hay normalización de
 * texto acá a propósito: el nombre se guarda tal como lo escribió el profesional, con sus
 * acentos, y la forma comparable para buscar la calcula `normalizarParaBuscar` en el
 * momento de buscar. Guardar una columna `nombreBusqueda` sería duplicar el dato con el
 * problema de tener que recalcularla en cada edición.
 */
fun ClienteEntity.toDomain(): Cliente = Cliente(
    id = id,
    nombre = nombre,
    telefono = telefono,
    email = email,
    notas = notas,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun Cliente.toEntity(): ClienteEntity = ClienteEntity(
    id = id,
    nombre = nombre,
    telefono = telefono,
    email = email,
    notas = notas,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)
