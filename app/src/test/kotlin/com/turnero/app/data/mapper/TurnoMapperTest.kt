package com.turnero.app.data.mapper

import com.turnero.app.data.local.entity.TurnoEntity
import com.turnero.app.data.local.projection.TurnoConServicioFila
import com.turnero.app.data.local.projection.UltimaVisitaFila
import com.turnero.app.domain.model.EstadoTurno
import com.turnero.app.domain.model.EstadisticasCliente
import com.turnero.app.testing.COLOR_BASE
import com.turnero.app.testing.INSTANTE_BASE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * El mapper de turnos es 1:1, asi que un round trip tiene que devolver la misma fila.
 *
 * Estos tests existen para que el dia que aparezca una divergencia (un enum persistido como
 * entero, un estado normalizado) el cambio no se cuele en silencio.
 */
class TurnoMapperTest {

    private val id = UUID.fromString("9b1d4f6a-2c3e-4a5b-8d7e-6f5a4b3c2d1e")

    private fun entidad(
        id: UUID = this.id,
        clienteId: UUID = UUID.randomUUID(),
        servicioId: UUID = UUID.randomUUID(),
        inicio: Instant = INSTANTE_BASE,
        duracionMin: Int = 30,
        estado: EstadoTurno = EstadoTurno.PENDIENTE,
        notas: String? = "traer toalla",
        createdAt: Instant = INSTANTE_BASE,
        updatedAt: Instant = createdAt,
        deletedAt: Instant? = null,
    ): TurnoEntity = TurnoEntity(
        id = id,
        clienteId = clienteId,
        servicioId = servicioId,
        inicio = inicio,
        duracionMin = duracionMin,
        estado = estado,
        notas = notas,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

    @Test
    fun `entidad a dominio copia los diez campos`() {
        val fila = entidad()

        val dominio = fila.toDomain()

        assertEquals(fila.id, dominio.id)
        assertEquals(fila.clienteId, dominio.clienteId)
        assertEquals(fila.servicioId, dominio.servicioId)
        assertEquals(fila.inicio, dominio.inicio)
        assertEquals(fila.duracionMin, dominio.duracionMin)
        assertEquals(fila.estado, dominio.estado)
        assertEquals(fila.notas, dominio.notas)
        assertEquals(fila.createdAt, dominio.createdAt)
        assertEquals(fila.updatedAt, dominio.updatedAt)
        assertEquals(fila.deletedAt, dominio.deletedAt)
    }

    @Test
    fun `dominio a entidad copia los diez campos`() {
        val dominio = entidad().toDomain()

        val fila = dominio.toEntity()

        assertEquals(dominio.id, fila.id)
        assertEquals(dominio.clienteId, fila.clienteId)
        assertEquals(dominio.servicioId, fila.servicioId)
        assertEquals(dominio.inicio, fila.inicio)
        assertEquals(dominio.duracionMin, fila.duracionMin)
        assertEquals(dominio.estado, fila.estado)
        assertEquals(dominio.notas, fila.notas)
        assertEquals(dominio.createdAt, fila.createdAt)
        assertEquals(dominio.updatedAt, fila.updatedAt)
        assertEquals(dominio.deletedAt, fila.deletedAt)
    }

    @Test
    fun `un round trip por dominio y entidad devuelve la misma fila`() {
        val fila = entidad(estado = EstadoTurno.ATENDIDO, duracionMin = 90)

        assertEquals(fila, fila.toDomain().toEntity())
    }

    @Test
    fun `cada estado del enum sobrevive el round trip`() {
        for (estado in EstadoTurno.entries) {
            assertEquals(estado, entidad(estado = estado).toDomain().toEntity().estado)
        }
    }

    @Test
    fun `unas notas null sobreviven el round trip`() {
        assertNull(entidad(notas = null).toDomain().toEntity().notas)
    }

    @Test
    fun `un deletedAt informado sobrevive el round trip`() {
        val momento = INSTANTE_BASE.plusSeconds(120)

        assertEquals(momento, entidad(deletedAt = momento).toDomain().toEntity().deletedAt)
    }

    @Test
    fun `la fila de la ultima visita se mapea al modelo de dominio`() {
        val fila = UltimaVisitaFila(
            inicio = INSTANTE_BASE,
            estado = EstadoTurno.CANCELADO,
            servicioNombre = "Corte de pelo",
        )

        val visita: EstadisticasCliente.UltimaVisita = fila.toDomain()

        assertEquals(INSTANTE_BASE, visita.inicio)
        assertEquals("Corte de pelo", visita.servicioNombre)
        assertEquals(EstadoTurno.CANCELADO, visita.estado)
    }

    @Test
    fun `la proyeccion con servicio se mapea al turno y su servicio`() {
        val fila = TurnoConServicioFila(
            id = id,
            clienteId = UUID.randomUUID(),
            servicioId = UUID.randomUUID(),
            inicio = INSTANTE_BASE,
            duracionMin = 60,
            estado = EstadoTurno.CONFIRMADO,
            notas = null,
            createdAt = INSTANTE_BASE,
            updatedAt = INSTANTE_BASE,
            deletedAt = null,
            servicioNombre = "Corte de pelo",
            servicioColor = COLOR_BASE,
        )

        val conServicio = fila.toDomain()

        assertEquals(fila.id, conServicio.turno.id)
        assertEquals(fila.clienteId, conServicio.turno.clienteId)
        assertEquals(fila.servicioId, conServicio.turno.servicioId)
        assertEquals(fila.inicio, conServicio.turno.inicio)
        assertEquals(fila.duracionMin, conServicio.turno.duracionMin)
        assertEquals(fila.estado, conServicio.turno.estado)
        assertEquals(fila.notas, conServicio.turno.notas)
        assertEquals(fila.createdAt, conServicio.turno.createdAt)
        assertEquals(fila.updatedAt, conServicio.turno.updatedAt)
        assertEquals(fila.deletedAt, conServicio.turno.deletedAt)
        assertEquals("Corte de pelo", conServicio.servicioNombre)
        assertEquals(COLOR_BASE, conServicio.servicioColor)
    }

    @Test
    fun `un color de servicio null llega como null al dominio`() {
        val fila = TurnoConServicioFila(
            id = id,
            clienteId = UUID.randomUUID(),
            servicioId = UUID.randomUUID(),
            inicio = INSTANTE_BASE,
            duracionMin = 30,
            estado = EstadoTurno.PENDIENTE,
            notas = null,
            createdAt = INSTANTE_BASE,
            updatedAt = INSTANTE_BASE,
            deletedAt = null,
            servicioNombre = "Corte",
            servicioColor = null,
        )

        assertNull(fila.toDomain().servicioColor)
    }
}