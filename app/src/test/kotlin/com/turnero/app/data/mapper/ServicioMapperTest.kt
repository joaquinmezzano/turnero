package com.turnero.app.data.mapper

import com.turnero.app.data.local.entity.ServicioEntity
import com.turnero.app.testing.COLOR_BASE
import com.turnero.app.testing.INSTANTE_BASE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * El mapper es 1:1 hoy, asi que un round trip tiene que devolver el mismo objeto.
 * Estos tests existen para que, el dia que aparezca una divergencia (un enum, un
 * `nombre` normalizado para ordenar), el cambio no se cuele en silencio: si el mapeo
 * deja de ser reversible, alguno de estos falla.
 */
class ServicioMapperTest {

    private val id = UUID.fromString("d4f6a8b0-5c7d-4e9f-afa1-2c3d4e5f6a04")

    private fun entidad(
        nombre: String = "Corte",
        duracionMin: Int = 30,
        precioCentavos: Long? = 5_000L,
        color: Int = COLOR_BASE,
        createdAt: Instant = INSTANTE_BASE,
        updatedAt: Instant = INSTANTE_BASE,
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

    @Test
    fun `entidad a dominio copia los ocho campos`() {
        val fila = entidad()

        val dominio = fila.toDomain()

        assertEquals(fila.id, dominio.id)
        assertEquals(fila.nombre, dominio.nombre)
        assertEquals(fila.duracionMin, dominio.duracionMin)
        assertEquals(fila.precioCentavos, dominio.precioCentavos)
        assertEquals(fila.color, dominio.color)
        assertEquals(fila.createdAt, dominio.createdAt)
        assertEquals(fila.updatedAt, dominio.updatedAt)
        assertEquals(fila.deletedAt, dominio.deletedAt)
    }

    @Test
    fun `dominio a entidad copia los ocho campos`() {
        val dominio = entidad().toDomain()

        val fila = dominio.toEntity()

        assertEquals(dominio.id, fila.id)
        assertEquals(dominio.nombre, fila.nombre)
        assertEquals(dominio.duracionMin, fila.duracionMin)
        assertEquals(dominio.precioCentavos, fila.precioCentavos)
        assertEquals(dominio.color, fila.color)
        assertEquals(dominio.createdAt, fila.createdAt)
        assertEquals(dominio.updatedAt, fila.updatedAt)
        assertEquals(dominio.deletedAt, fila.deletedAt)
    }

    @Test
    fun `un round trip por dominio y entidad devuelve la misma fila`() {
        val fila = entidad(nombre = "Coloracion", duracionMin = 90, precioCentavos = 18_500L, color = 0xFF00FF00.toInt())

        assertEquals(fila, fila.toDomain().toEntity())
    }

    @Test
    fun `un deletedAt null sobrevive el round trip`() {
        val fila = entidad(deletedAt = null)

        assertNull(fila.toDomain().toEntity().deletedAt)
    }

    @Test
    fun `un deletedAt informado sobrevive el round trip`() {
        val fila = entidad(deletedAt = INSTANTE_BASE.plusSeconds(120))

        assertEquals(INSTANTE_BASE.plusSeconds(120), fila.toDomain().toEntity().deletedAt)
    }

    @Test
    fun `un precioCentavos null sobrevive el round trip`() {
        val fila = entidad(precioCentavos = null)

        assertNull(fila.toDomain().toEntity().precioCentavos)
    }

    @Test
    fun `un color negativo se copia sin perder el signo de bit alto`() {
        val fila = entidad(color = 0xFF6200EE.toInt())

        assertEquals(0xFF6200EE.toInt(), fila.toDomain().toEntity().color)
    }
}