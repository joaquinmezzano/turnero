package com.turnero.app.testing

import com.turnero.app.data.local.entity.ClienteEntity
import java.time.Instant
import java.util.UUID

/**
 * Constructor de `ClienteEntity` con defaults válidos.
 *
 * Va duplicado del de `src/test` a propósito: `androidTest` no ve el source set `test`
 * (no es un source set compartido, y agregar `test` como `dependsOn` de `androidTest` solo
 * para esto arrastraría Turbine y los fakes de dominio a un módulo que no los necesita).
 * Lo único que cambia es el tipo: acá se construye la entidad. Reusa el `INSTANTE_BASE`
 * que ya declara `ServicioEntityFactory`, en el mismo paquete.
 */
fun entidadCliente(
    id: UUID = UUID.randomUUID(),
    nombre: String = "Pérez, Ana",
    telefono: String? = "+54 11 5555-0100",
    email: String? = "ana@example.com",
    notas: String? = null,
    createdAt: Instant = INSTANTE_BASE,
    updatedAt: Instant = createdAt,
    deletedAt: Instant? = null,
): ClienteEntity = ClienteEntity(
    id = id,
    nombre = nombre,
    telefono = telefono,
    email = email,
    notas = notas,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)