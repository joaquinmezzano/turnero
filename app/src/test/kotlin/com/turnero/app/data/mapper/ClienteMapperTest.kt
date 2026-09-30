package com.turnero.app.data.mapper

import com.turnero.app.data.local.entity.ClienteEntity
import com.turnero.app.testing.INSTANTE_BASE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * El mapper de cliente es 1:1 hoy, así que un round trip tiene que devolver la misma
 * fila. Estos tests existen para que el día que aparezca una divergencia (una columna
 * derivada, un `nombre` normalizado para ordenar) el cambio no se cuele en silencio.
 *
 * Los `null` de `telefono`, `email` y `notas` son el caso interesante: `Cliente` los
 * declara `String?` y la entidad también, pero un mapper que los rellenara con `""` sería
 * invisible en la UI y rompería la lectura de contactos.
 */
class ClienteMapperTest {

    private val id = UUID.fromString("d4f6a8b0-5c7d-4e9f-afa1-2c3d4e5f6a04")

    private fun entidad(
        nombre: String = "Pérez, Ana",
        telefono: String? = "+54 11 5555-0100",
        email: String? = "ana@example.com",
        notas: String? = null,
        createdAt: Instant = INSTANTE_BASE,
        updatedAt: Instant = INSTANTE_BASE,
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

    @Test
    fun `entidad a dominio copia los ocho campos`() {
        val fila = entidad(notas = "Viene los sábados")

        val dominio = fila.toDomain()

        assertEquals(fila.id, dominio.id)
        assertEquals(fila.nombre, dominio.nombre)
        assertEquals(fila.telefono, dominio.telefono)
        assertEquals(fila.email, dominio.email)
        assertEquals(fila.notas, dominio.notas)
        assertEquals(fila.createdAt, dominio.createdAt)
        assertEquals(fila.updatedAt, dominio.updatedAt)
        assertEquals(fila.deletedAt, dominio.deletedAt)
    }

    @Test
    fun `dominio a entidad copia los ocho campos`() {
        val dominio = entidad(notas = "Viene los sábados").toDomain()

        val fila = dominio.toEntity()

        assertEquals(dominio.id, fila.id)
        assertEquals(dominio.nombre, fila.nombre)
        assertEquals(dominio.telefono, fila.telefono)
        assertEquals(dominio.email, fila.email)
        assertEquals(dominio.notas, fila.notas)
        assertEquals(dominio.createdAt, fila.createdAt)
        assertEquals(dominio.updatedAt, fila.updatedAt)
        assertEquals(dominio.deletedAt, fila.deletedAt)
    }

    @Test
    fun `un round trip por dominio y entidad devuelve la misma fila`() {
        val fila = entidad(notas = "Viene los sábados")

        assertEquals(fila, fila.toDomain().toEntity())
    }

    @Test
    fun `un contacto null sobrevive el round trip`() {
        val fila = entidad(telefono = null, email = null, notas = null)

        val idaYVuelta = fila.toDomain().toEntity()

        assertNull(idaYVuelta.telefono)
        assertNull(idaYVuelta.email)
        assertNull(idaYVuelta.notas)
    }

    @Test
    fun `un deletedAt informado sobrevive el round trip`() {
        val fila = entidad(deletedAt = INSTANTE_BASE.plusSeconds(120))

        assertEquals(INSTANTE_BASE.plusSeconds(120), fila.toDomain().toEntity().deletedAt)
    }

    @Test
    fun `los acentos y la capitalizacion del nombre no se tocan`() {
        // El nombre va **tal cual** al dominio: normalizarlo acá serviría para ordenar, pero
        // el listado tiene que mostrar "Pérez, Ana", no "perez, ana". La normalización
        // ocurre solo en el filtro de búsqueda, sobre una copia.
        val fila = entidad(nombre = "Pérez, Ana María")

        assertEquals("Pérez, Ana María", fila.toDomain().toEntity().nombre)
    }
}