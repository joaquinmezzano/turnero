package com.turnero.app.domain.model

import com.turnero.app.testing.turno
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Matriz de transiciones **exhaustiva**: 5 estados x 2 condiciones temporales.
 *
 * Es el test de mayor valor del slice. La tabla es finita y se puede verificar completa, y
 * un error aca se traduce directo en historial falso: un `ATENDIDO` que todavia se puede
 * volver a confirmar, o un `PENDIENTE` post-horario al que se le marca ausente, son datos
 * que el profesional ve y no puede corregir.
 *
 * La matriz se escribe como tabla y se recorre entera, en vez de un test por celda: asi un
 * estado nuevo compilationa con la matriz incompleta y el test falla en vez de pasar en
 * silencio. Cada fila dice exactamente que se espera.
 */
class MaquinaEstadosTurnoTest {

    /** Fin del turno por defecto del factory: 10:00 + 30 min = 10:30 hora Argentina. */
    private val finDelTurno: Instant = turno().fin()

    /** Pre-horario por definicion: `ahora <= fin`. El borde exacto tambien es pre. */
    private val preHorario: Instant = finDelTurno.minusSeconds(60)

    /** Post-horario: `ahora > fin`, un segundo despues. */
    private val postHorario: Instant = finDelTurno.plusSeconds(1)

    /** Estado x condicion temporal -> conjunto exacto de acciones. */
    private val matriz: Map<Pair<EstadoTurno, String>, Set<AccionTurno>> = mapOf(
        // PENDIENTE no tiene ninguna transicion condicional: confirmar y liberar valen
        // siempre, tambien post-horario. No existe PENDIENTE -> ATENDIDO ni
        // PENDIENTE -> AUSENTE, y eso es decision, no olvido.
        EstadoTurno.PENDIENTE to "pre" to setOf(AccionTurno.CONFIRMAR, AccionTurno.LIBERAR),
        EstadoTurno.PENDIENTE to "post" to setOf(AccionTurno.CONFIRMAR, AccionTurno.LIBERAR),

        // Pre-horario no se puede marcar ausente: el turno todavia no termino, y declararlo
        // ausente afirmaria algo que todavia no se.
        EstadoTurno.CONFIRMADO to "pre" to setOf(AccionTurno.MARCAR_ATENDIDO, AccionTurno.CANCELAR),
        EstadoTurno.CONFIRMADO to "post" to setOf(
            AccionTurno.MARCAR_ATENDIDO,
            AccionTurno.MARCAR_AUSENTE,
            AccionTurno.CANCELAR,
        ),

        // Estados finales sin salida, en pre y en post.
        EstadoTurno.ATENDIDO to "pre" to emptySet(),
        EstadoTurno.ATENDIDO to "post" to emptySet(),
        EstadoTurno.AUSENTE to "pre" to emptySet(),
        EstadoTurno.AUSENTE to "post" to emptySet(),

        // CANCELADO es reversible solo antes de que terminara el turno.
        EstadoTurno.CANCELADO to "pre" to setOf(
            AccionTurno.LIBERAR,
            AccionTurno.VOLVER_A_PENDIENTE,
            AccionTurno.CONFIRMAR,
        ),
        EstadoTurno.CANCELADO to "post" to emptySet(),
    )

    @Test
    fun `la matriz recorre los cinco estados en las dos condiciones temporales`() {
        for (estado in EstadoTurno.entries) {
            for (condicion in listOf("pre", "post")) {
                val esperado = matriz[estado to condicion]
                    ?: error("Falta el caso $estado / $condicion en la matriz del test")
                val ahora = if (condicion == "pre") preHorario else postHorario

                assertEquals(
                    esperado,
                    accionesDisponibles(turno(estado = estado), ahora),
                    "Acciones para $estado en horario $condicion",
                )
            }
        }
    }

    @Test
    fun `la matriz no deja ningun estado sin fila`() {
        // Si alguien agrega un estado nuevo, el test anterior ya falla; este dice por que.
        assertEquals(EstadoTurno.entries.size * 2, matriz.size)
    }

    // ------------------------------------------------------------------ borde temporal

    @Test
    fun `el instante exacto de finTodavia cuenta como pre-horario`() {
        // El corte es `ahora <= fin`, no `ahora < fin`: un profesional que marca atendido
        // justo cuando termino el turno tiene que poder hacerlo.
        val disponible = accionesDisponibles(turno(estado = EstadoTurno.CONFIRMADO), finDelTurno)

        assertFalse(AccionTurno.MARCAR_AUSENTE in disponible)
    }

    @Test
    fun `un segundo despues del fin ya es post-horario`() {
        val disponible = accionesDisponibles(turno(estado = EstadoTurno.CONFIRMADO), finDelTurno.plusSeconds(1))

        assertTrue(AccionTurno.MARCAR_AUSENTE in disponible)
    }

    @Test
    fun `la duracion del turno decide donde cae el corte temporal`() {
        // Dos turnos que arrancan a la misma hora pero duran distinto cortan distinto: el
        // que dura 90 min todavia esta en curso cuando el de 30 ya termino.
        val largo = turno(inicio = turno().inicio, duracionMin = 90, estado = EstadoTurno.CONFIRMADO)
        val corto = turno(inicio = turno().inicio, duracionMin = 30, estado = EstadoTurno.CONFIRMADO)
        val ahora = turno().fin().plusSeconds(1)

        assertFalse(AccionTurno.MARCAR_AUSENTE in accionesDisponibles(largo, ahora))
        assertTrue(AccionTurno.MARCAR_AUSENTE in accionesDisponibles(corto, ahora))
    }

    // ------------------------------------------------------------------ estados finales

    @Test
    fun `ATENDIDO no ofrece ninguna salida en ninguna condicion`() {
        assertEquals(emptySet<AccionTurno>(), accionesDisponibles(turno(estado = EstadoTurno.ATENDIDO), preHorario))
        assertEquals(emptySet<AccionTurno>(), accionesDisponibles(turno(estado = EstadoTurno.ATENDIDO), postHorario))
    }

    @Test
    fun `AUSENTE no ofrece ninguna salida en ninguna condicion`() {
        assertEquals(emptySet<AccionTurno>(), accionesDisponibles(turno(estado = EstadoTurno.AUSENTE), preHorario))
        assertEquals(emptySet<AccionTurno>(), accionesDisponibles(turno(estado = EstadoTurno.AUSENTE), postHorario))
    }

    @Test
    fun `CANCELADO post-horario es final y pre-horario es reversible`() {
        assertEquals(emptySet<AccionTurno>(), accionesDisponibles(turno(estado = EstadoTurno.CANCELADO), postHorario))
        assertTrue(accionesDisponibles(turno(estado = EstadoTurno.CANCELADO), preHorario).isNotEmpty())
    }

    // ------------------------------------------------------------------ PENDIENTE post-horario

    @Test
    fun `un PENDIENTE post-horario solo se puede confirmar o liberar`() {
        // Decision explicita del producto: si el horario ya paso y nadie confirmo, el turno
        // queda congelado. "Completarlo" a ATENDIDO escribiria una verdad que el
        // profesional nunca afirmo.
        val disponible = accionesDisponibles(turno(estado = EstadoTurno.PENDIENTE), postHorario)

        assertEquals(setOf(AccionTurno.CONFIRMAR, AccionTurno.LIBERAR), disponible)
        assertFalse(AccionTurno.MARCAR_ATENDIDO in disponible)
        assertFalse(AccionTurno.MARCAR_AUSENTE in disponible)
    }

    // ------------------------------------------------------------------ LIBERAR

    @Test
    fun `LIBRE no es un estado de turno sino la ausencia de fila`() {
        // El horario libre se deriva al pintar, no se materializa. Si alguna vez apareciera
        // un `LIBRE` en el enum, esta matriz dejaria de cubrir los cinco estados reales.
        assertEquals(
            listOf("PENDIENTE", "CONFIRMADO", "ATENDIDO", "AUSENTE", "CANCELADO"),
            EstadoTurno.entries.map { it.name },
        )
    }

    @Test
    fun `de CONFIRMADO no se puede liberar hay que cancelar primero`() {
        // Consecuencia deliberada de la tabla: el boton de borrar solo aparece cuando
        // LIBERAR esta disponible, y de CONFIRMADO no lo esta.
        val disponible = accionesDisponibles(turno(estado = EstadoTurno.CONFIRMADO), preHorario)

        assertFalse(AccionTurno.LIBERAR in disponible)
        assertTrue(AccionTurno.CANCELAR in disponible)
    }

    // ------------------------------------------------------------------ consulta de validez

    @Test
    fun `accionPermitida coincide con la pertenencia a accionesDisponibles`() {
        val turno = turno(estado = EstadoTurno.CANCELADO)

        for (accion in AccionTurno.entries) {
            assertEquals(
                accion in accionesDisponibles(turno, preHorario),
                accionPermitida(turno, accion, preHorario),
                "accionPermitida disagrees para $accion",
            )
        }
    }

    @Test
    fun `accionPermitida rechaza una transicion que la UI no ofrecio`() {
        val turno = turno(estado = EstadoTurno.ATENDIDO)

        assertFalse(accionPermitida(turno, AccionTurno.CONFIRMAR, postHorario))
        assertFalse(accionPermitida(turno, AccionTurno.LIBERAR, postHorario))
    }
}