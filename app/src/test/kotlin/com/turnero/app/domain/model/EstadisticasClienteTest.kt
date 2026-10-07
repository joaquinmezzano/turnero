package com.turnero.app.domain.model

import com.turnero.app.testing.INSTANTE_BASE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Construccion de las estadisticas del cliente.
 *
 * La trampa que cubre todo el archivo es la de los ceros: `GROUP BY estado` **omite** los
 * estados sin filas, asi que un cliente con 3 atendidos y 0 cancelados no devuelve fila
 * `CANCELADO`. Sin el relleno, la ficha muestra un hueco donde deberia decir `0`.
 */
class EstadisticasClienteTest {

    @Test
    fun `un cliente sin turnos tiene los cinco estados en cero`() {
        val estadisticas = EstadisticasCliente.desde(conteos = emptyMap(), ultimaVisita = null)

        assertEquals(0, estadisticas.pendientes)
        assertEquals(0, estadisticas.confirmados)
        assertEquals(0, estadisticas.atendidos)
        assertEquals(0, estadisticas.ausentes)
        assertEquals(0, estadisticas.cancelados)
        assertNull(estadisticas.ultimaVisita)
    }

    @Test
    fun `los estados que no aparecen en el GROUP BY se rellenan en cero`() {
        val estadisticas = EstadisticasCliente.desde(
            conteos = mapOf(EstadoTurno.ATENDIDO to 3),
            ultimaVisita = null,
        )

        assertEquals(3, estadisticas.atendidos)
        assertEquals(0, estadisticas.pendientes)
        assertEquals(0, estadisticas.confirmados)
        assertEquals(0, estadisticas.ausentes)
        assertEquals(0, estadisticas.cancelados)
    }

    @Test
    fun `los cinco estados con filas se copian tal cual`() {
        val estadisticas = EstadisticasCliente.desde(
            conteos = mapOf(
                EstadoTurno.PENDIENTE to 1,
                EstadoTurno.CONFIRMADO to 2,
                EstadoTurno.ATENDIDO to 3,
                EstadoTurno.AUSENTE to 4,
                EstadoTurno.CANCELADO to 5,
            ),
            ultimaVisita = null,
        )

        assertEquals(1, estadisticas.pendientes)
        assertEquals(2, estadisticas.confirmados)
        assertEquals(3, estadisticas.atendidos)
        assertEquals(4, estadisticas.ausentes)
        assertEquals(5, estadisticas.cancelados)
    }

    @Test
    fun `la ultima visita viaja con inicio servicio y estado`() {
        val visita = EstadisticasCliente.UltimaVisita(
            inicio = INSTANTE_BASE,
            servicioNombre = "Corte de pelo",
            estado = EstadoTurno.ATENDIDO,
        )

        val estadisticas = EstadisticasCliente.desde(emptyMap(), visita)

        assertEquals(visita, estadisticas.ultimaVisita)
    }

    @Test
    fun `la ultima visita puede ser de un turno cancelado`() {
        // El mas reciente es de **cualquier** estado: un CANCELADO reciente es informacion
        // de contacto y el usuario lo quiere ver. El estado se filtra al contar, no al
        // elegir.
        val visita = EstadisticasCliente.UltimaVisita(
            inicio = INSTANTE_BASE,
            servicioNombre = "Coloración",
            estado = EstadoTurno.CANCELADO,
        )

        assertEquals(EstadoTurno.CANCELADO, EstadisticasCliente.desde(emptyMap(), visita).ultimaVisita?.estado)
    }
}