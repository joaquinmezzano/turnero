package com.turnero.app.ui.screens.turnos

import com.turnero.app.domain.model.Turno
import com.turnero.app.testing.ZONA_BASE
import com.turnero.app.testing.instanteDeTurno
import com.turnero.app.testing.turno
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalTime
import java.util.UUID

/**
 * Grilla de 15 horas agendables (08:00–22:00 en punto).
 *
 * La regla de oro: la grilla **completa siempre 15 celdas de alto**, y una hora que un
 * turno ocupa (aunque sea su primera hora) **jamás se ofrece como libre**. Un turno que
 * empieza en su hora y dura más de 60 minutos no crea celdas fantasmas debajo: la fila
 * siguiente simplemente no existe en la lista.
 *
 * El solapamiento no se prueba acá: lo impide la transacción del repositorio de Room. Acá
 * se prueba el merge de celdas contra listas ya válidas — y contra una inválida (bloque que
 * cubre la fila donde arranca otro turno) para asegurar que no crashea ni inventa celdas.
 *
 * `construirGrilla` recibe `TurnoConFila`, que es lo que el ViewModel ya calculó (anclaje a
 * la hora, celdas redondeadas hacia arriba): el merge no vuelve a derivar esas dos cosas.
 * El helper [aConFila] replica esa derivación para que el test hable el mismo idioma.
 */
class GrillaTurnosTest {

    private fun aConFila(turno: Turno): TurnoConFila {
        val hora = turno.inicio.atZone(ZONA_BASE).toLocalTime()
        return TurnoConFila(
            turno = turno,
            filaInicio = hora.hour - 8,
            filasOcupadas = (turno.duracionMin + 59) / 60,
            clienteNombre = "Pérez, Ana",
            servicioNombre = "Corte",
            servicioColor = null,
            horaInicio = hora,
            horaFin = turno.inicio.plusSeconds(turno.duracionMin * 60L).atZone(ZONA_BASE).toLocalTime(),
        )
    }

    private fun grillaDe(vararg turnos: Turno): List<CeldaGrilla> =
        construirGrilla(turnos.map(::aConFila))

    private fun filasOcupadasTotales(celdas: List<CeldaGrilla>): Int =
        celdas.sumOf { celda -> if (celda.libre) 1 else celda.filasOcupadas }

    private fun turnosDeLaCelda(celda: CeldaGrilla): List<Turno> = celda.turnos.map { it.turno }

    @Test
    fun `la grilla vacia son quince celdas libres`() {
        val celdas = grillaDe()

        assertEquals(15, celdas.size)
        assertTrue(celdas.all { it.libre })
        assertEquals(15, filasOcupadasTotales(celdas))
        assertEquals(LocalTime.of(8, 0), celdas.first().hora)
        assertEquals(LocalTime.of(22, 0), celdas.last().hora)
    }

    @Test
    fun `un turno de 90 minutos no deja la hora del medio como libre`() {
        val turno90 = turno(
            id = UUID.randomUUID(),
            inicio = instanteDeTurno(hora = 10),
            duracionMin = 90,
        )

        val celdas = grillaDe(turno90)

        // 10:00 (fila 2) ocupa dos filas: la celda cubre 10:00 y 11:00, y la fila 11:00 no
        // existe en la lista. Es el bug del bloqueante B1: ofrecer 11:00 como libre cuando
        // un turno de 10:00 a 11:30 la tiene ocupada.
        assertFalse(celdas.any { it.libre && it.hora == LocalTime.of(11, 0) })
        val celdaDelTurno = celdas.first { it.fila == 2 }
        assertFalse(celdaDelTurno.libre)
        assertEquals(2, celdaDelTurno.filasOcupadas)
        assertEquals(listOf(turno90), turnosDeLaCelda(celdaDelTurno))
        // El alto total de la grilla no cambia: se perdió una celda, no una fila.
        assertEquals(14, celdas.size)
        assertEquals(15, filasOcupadasTotales(celdas))
    }

    @Test
    fun `la hora de contacto exacto entre dos turnos no se ofrece como libre`() {
        val turno10 = turno(
            id = UUID.randomUUID(),
            inicio = instanteDeTurno(hora = 10),
            duracionMin = 60,
        )
        val turno11 = turno(
            id = UUID.randomUUID(),
            inicio = instanteDeTurno(hora = 11),
            duracionMin = 60,
        )

        val celdas = grillaDe(turno10, turno11)

        // 10:00→11:00 y 11:00→12:00 calzan exactos: 11:00 está ocupada por el turno de
        // la hora siguiente y no puede pintarse "Crear turno a las 11:00".
        assertFalse(celdas.any { it.libre && it.hora == LocalTime.of(11, 0) })
        assertEquals(listOf(turno11), turnosDeLaCelda(celdas.first { it.fila == 3 }))
        assertEquals(15, celdas.size)
        assertEquals(15, filasOcupadasTotales(celdas))
    }

    @Test
    fun `dos turnos de la misma hora se agrupan en una sola celda`() {
        // Ids fijos: el merge ordena por inicio y después por id, y la comparación entre
        // UUIDs tiene que ser determinista en el test.
        val idA = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val idB = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val idC = UUID.fromString("00000000-0000-0000-0000-000000000003")
        val turnoA = turno(id = idA, inicio = instanteDeTurno(hora = 10), duracionMin = 30)
        val turnoB = turno(id = idB, inicio = instanteDeTurno(hora = 10), duracionMin = 30)
        // Copia idéntica salvo el id: el merge agrupa por hora, no por turno.
        val turnoC = turno(id = idC, inicio = instanteDeTurno(hora = 10), duracionMin = 30)

        // Entran desordenados a propósito: el resultado no puede depender del orden.
        val celdas = grillaDe(turnoA, turnoC, turnoB)

        val celdaDeLasDiez = celdas.first { it.fila == 2 }
        assertEquals(LocalTime.of(10, 0), celdaDeLasDiez.hora)
        assertEquals(listOf(turnoA, turnoB, turnoC), turnosDeLaCelda(celdaDeLasDiez))
        assertEquals(1, celdaDeLasDiez.filasOcupadas)
        // Un turno de 30 minutos ocupa solo su fila: la 11:00 sigue siendo una celda libre
        // aparte, así que la grilla completa no pierde celdas.
        assertEquals(15, celdas.size)
        assertEquals(15, filasOcupadasTotales(celdas))
    }

    @Test
    fun `un turno que termina despues de las 22 no agrega filas a la grilla`() {
        val turno21 = turno(
            id = UUID.randomUUID(),
            inicio = instanteDeTurno(hora = 21),
            duracionMin = 120,
        )

        val celdas = grillaDe(turno21)

        // 21:00 (fila 13) + 2 filas de alto cubre hasta las 23:00, que no existe en la
        // grilla: la fila 14 (22:00) queda cubierta por la celda y desaparece de la lista.
        assertFalse(celdas.any { it.fila == 14 })
        val celdaDeLasNueve = celdas.first { it.fila == 13 }
        assertEquals(2, celdaDeLasNueve.filasOcupadas)
        assertEquals(14, celdas.size)
        assertEquals(15, filasOcupadasTotales(celdas))
    }

    @Test
    fun `un bloque que cubre la fila donde arranca otro turno se recorta sin solaparse`() {
        // Lista inválida para producción (el repositorio la bloquea), pero la grilla no
        // puede crashear ni inventar una celda: el turno de las 11:00 arranca dentro del
        // bloque de las 10:00, así que el bloque se recorta a una fila y ambos se pintan.
        val turno10 = turno(
            id = UUID.randomUUID(),
            inicio = instanteDeTurno(hora = 10),
            duracionMin = 120,
        )
        val turno11 = turno(
            id = UUID.randomUUID(),
            inicio = instanteDeTurno(hora = 11),
            duracionMin = 30,
        )

        val celdas = grillaDe(turno10, turno11)

        assertEquals(1, celdas.first { it.fila == 2 }.filasOcupadas)
        assertEquals(listOf(turno11), turnosDeLaCelda(celdas.first { it.fila == 3 }))
        assertEquals(15, celdas.size)
        assertEquals(15, filasOcupadasTotales(celdas))
    }

    @Test
    fun `las celdas libres crean turnos en su propia hora`() {
        val celdas = grillaDe()

        val libreDeLasDiez = celdas.first { it.hora == LocalTime.of(10, 0) }
        assertTrue(libreDeLasDiez.libre)
        // El `hora` de la celda es el que propaga la pantalla al crear el turno: la hora
        // anclada no puede diferir de la fila que representa.
        assertEquals(libreDeLasDiez.fila, libreDeLasDiez.hora.hour - 8)
    }

    @Test
    fun `mantiene el orden estable de los turnos dentro de la celda`() {
        val idA = UUID.fromString("00000000-0000-0000-0000-00000000000a")
        val idB = UUID.fromString("00000000-0000-0000-0000-00000000000b")
        val turnoA = turno(id = idA, inicio = instanteDeTurno(hora = 10), duracionMin = 30)
        val turnoB = turno(id = idB, inicio = instanteDeTurno(hora = 10), duracionMin = 30)

        val celdas = grillaDe(turnoB, turnoA)

        // Ordenados por inicio y después por id: el merge no depende del orden de entrada.
        val celdaDeLasDiez = celdas.first { it.fila == 2 }
        assertSame(turnoA, turnosDeLaCelda(celdaDeLasDiez)[0])
        assertSame(turnoB, turnosDeLaCelda(celdaDeLasDiez)[1])
    }
}