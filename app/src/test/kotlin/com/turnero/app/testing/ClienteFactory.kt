package com.turnero.app.testing

import com.turnero.app.domain.model.Cliente
import java.time.Instant
import java.util.UUID

/**
 * Constructor de `Cliente` con defaults válidos.
 *
 * Vive en `testing/` y no duplicado por test: los casos de uso, el mapper y el ViewModel
 * necesitan la misma forma de dato y tres copias divergen. Reusa el `INSTANTE_BASE` que
 * ya declara `ServicioFactory`, en el mismo paquete.
 *
 * El nombre por default **lleva tilde y mayúscula a propósito**: `"Pérez, Ana"` es el
 * caso donde un filtro que no normaliza falla, así que un test de búsqueda que lo usa sin
 * tocarlo ya está probando que la normalización existe. Con un `"Juan Perez"` neutro
 * pasaría igual un filtro roto.
 */
fun cliente(
    id: UUID = UUID.randomUUID(),
    nombre: String = "Pérez, Ana",
    telefono: String? = "+54 11 5555-0100",
    email: String? = "ana@example.com",
    notas: String? = null,
    createdAt: Instant = INSTANTE_BASE,
    updatedAt: Instant = createdAt,
    deletedAt: Instant? = null,
): Cliente = Cliente(
    id = id,
    nombre = nombre,
    telefono = telefono,
    email = email,
    notas = notas,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)