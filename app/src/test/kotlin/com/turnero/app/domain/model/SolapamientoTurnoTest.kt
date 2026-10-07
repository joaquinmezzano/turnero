package com.turnero.app.domain.model

import com.turnero.app.testing.turno
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Regla de solapamiento en su forma pura.
 *
 * El mismo criterio lo implementa `TurnoDao.obtenerQueSolapan` en SQL, y por eso la tabla
 * se prueba de nuevo contra Room en `androidTest`. Aca se prueba la regla; alla, que la
 * query la implemente.
 */
class SolapamientoTurnoTest {

    /** 10:00 hora Argentina del dia base. Todas las horas de la tabla salen de aca. */
    private val diez: Instant = turno().inicio

    private fun instante(hora: Int, minuto: Int = 0): Instant =
        diez.plusSeconds((hora * 60L + minuto) * 60L)

    // ------------------------------------------------------------------ interseccion

    @Test
    fun `dos intervalos que se pisan se solapan`() {
        assertTrue(
            seSolapan(
                inicioA = instante(10),
                finA = instante(10, 30),
                inicioB = instante(10, 15),
                finB = instante(10, 45),
            ),
        )
    }

    @Test
    fun `el contacto exacto en el borde no es solapamiento`() {
        // El caso mas comun de la app: un turno que termina 10:00 y otro que arranca 10:00.
        // Tratarlo como choque llenaria la agenda de errores.
        assertFalse(
            seSolapan(
                inicioA = instante(9, 30),
                finA = instante(10),
                inicioB = instante(10),
                finB = instante(10, 30),
            ),
        )
    }

    @Test
    fun `el contacto exacto tampoco solapa en el otro sentido`() {
        assertFalse(
            seSolapan(
                inicioA = instante(10),
                finA = instante(10, 30),
                inicioB = instante(9, 30),
                finB = instante(10),
            ),
        )
    }

    @Test
    fun `un servicio de mas de una hora ocupa varias celdas`() {
        // Un turno de 90 minutos arrancando 10:00 ocupa la de 10:00 y la de 11:00: agendar a
        // las 11:00 tiene que chocar aunque las horas no sean iguales.
        val largo = turno(inicio = instante(10), duracionMin = 90)
        val siguiente = turno(inicio = instante(11), duracionMin = 30)

        assertTrue(largo.seSolapaCon(siguiente))
        assertTrue(siguiente.seSolapaCon(largo))
    }

    @Test
    fun `multi-celda que arranca justo cuando termina el largo tampoco choca`() {
        val largo = turno(inicio = instante(10), duracionMin = 90)
        val siguiente = turno(inicio = instante(11, 30), duracionMin = 30)

        assertFalse(largo.seSolapaCon(siguiente))
    }

    @Test
    fun `un intervalo contenido dentro de otro se solapa`() {
        assertTrue(
            turno(inicio = instante(9), duracionMin = 180)
                .seSolapaCon(turno(inicio = instante(10), duracionMin = 30)),
        )
    }

    @Test
    fun `dos intervalos separados no se solapan`() {
        assertFalse(
            turno(inicio = instante(9), duracionMin = 30)
                .seSolapaCon(turno(inicio = instante(14), duracionMin = 30)),
        )
    }

    @Test
    fun `un turno se solapa consigo mismo`() {
        // Por eso `actualizar` excluye al propio id del chequeo: sin eso no se podrian
        // editar ni las notas ni la hora de un turno ya agendado.
        val unTurno = turno(inicio = instante(10), duracionMin = 30)

        assertTrue(unTurno.seSolapaCon(unTurno))
    }

    // ------------------------------------------------------------------ estados bloqueantes

    @Test
    fun `PENDIENTE CONFIRMADO y ATENDIDO bloquean el horario`() {
        assertTrue(bloqueaHorario(EstadoTurno.PENDIENTE))
        assertTrue(bloqueaHorario(EstadoTurno.CONFIRMADO))
        assertTrue(bloqueaHorario(EstadoTurno.ATENDIDO))
    }

    @Test
    fun `AUSENTE y CANCELADO liberan el horario`() {
        // El turno sigue existiendo y se sigue viendo en la grilla, pero no impide agendar.
        // Sin esto, un corte cancelado dejaria el horario tomado para siempre.
        assertFalse(bloqueaHorario(EstadoTurno.AUSENTE))
        assertFalse(bloqueaHorario(EstadoTurno.CANCELADO))
    }

    @Test
    fun `el conjunto de estados bloqueantes tiene exactamente los tres estados que ocupan`() {
        assertEquals(
            setOf(EstadoTurno.PENDIENTE, EstadoTurno.CONFIRMADO, EstadoTurno.ATENDIDO),
            ESTADOS_QUE_BLOQUEAN,
        )
    }
}