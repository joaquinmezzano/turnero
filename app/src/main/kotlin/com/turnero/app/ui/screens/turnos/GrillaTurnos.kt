package com.turnero.app.ui.screens.turnos

import java.time.LocalTime

/**
 * Celda ya resuelta de la grilla de horas agendables.
 *
 * Una celda `libre` ocupa una sola fila; una `ocupada` puede abarcar varias (un turno de
 * 90 minutos cubre dos celdas consecutivas) y puede contener **más de un turno**: los
 * `AUSENTE`/`CANCELADO` liberan el horario pero su tarjeta sigue visible, así que dos
 * turnos pueden compartir fila y la grilla los pinta juntos.
 */
data class CeldaGrilla(
    val fila: Int,
    val hora: LocalTime,
    val turnos: List<TurnoConFila>,
    val filasOcupadas: Int,
) {
    val libre: Boolean get() = turnos.isEmpty()
}

/**
 * Fusiona la lista de turnos del día en las celdas de la grilla, **sin inventar filas**.
 *
 * El bug que esto reemplaza pintaba una celda por índice y buscaba el turno que arranca en
 * cada uno: un turno de varias celdas dejaba huérfanas las filas que cubría, y cada una
 * salía como "libre" (tappable, con la hora ya ocupada). Acá se avanza por filas de verdad:
 * cuando una celda tiene turno, se salta lo que su altura cubre, y las filas cubiertas no
 * existen en la lista — no hay forma de que se ofrezcan como libres.
 *
 * La altura se acota por el próximo turno que arranca más adelante y por el fin de la
 * grilla: si la fila que cubriría ya tiene un turno propio (posible sólo cuando el que
 * cubre está `AUSENTE`/`CANCELADO`), la tarjeta se encoge a su fila de inicio en vez de
 * pisar a la otra, y el total de filas sigue siendo exactamente [totalFilas].
 *
 * Pura y sin Android a propósito: es la parte de la grilla que se testea en JVM.
 */
fun construirGrilla(
    turnos: List<TurnoConFila>,
    totalFilas: Int = 15,
    horaInicio: LocalTime = LocalTime.of(8, 0),
): List<CeldaGrilla> {
    val porFila = turnos
        .groupBy { it.filaInicio.coerceIn(0, totalFilas - 1) }
        .mapValues { (_, enFila) -> enFila.sortedWith(COMPARADOR_TURNO) }
    val filasConInicio = porFila.keys

    val celdas = mutableListOf<CeldaGrilla>()
    var fila = 0
    while (fila < totalFilas) {
        val hora = horaInicio.plusHours(fila.toLong())
        val enFila = porFila[fila].orEmpty()
        if (enFila.isEmpty()) {
            celdas += CeldaGrilla(fila, hora, emptyList(), filasOcupadas = 1)
            fila += 1
            continue
        }
        val siguienteInicio = filasConInicio.filter { it > fila }.minOrNull() ?: totalFilas
        val alturaDeseada = enFila.maxOf { it.filasOcupadas.coerceAtLeast(1) }
        val altura = alturaDeseada
            .coerceAtMost(siguienteInicio - fila)
            .coerceAtMost(totalFilas - fila)
        celdas += CeldaGrilla(fila, hora, enFila, filasOcupadas = altura)
        fila += altura
    }
    return celdas
}

/** Orden estable dentro de una celda compartida: por hora de inicio y, al empatar, por id. */
private val COMPARADOR_TURNO =
    compareBy<TurnoConFila>({ it.turno.inicio }, { it.turno.id })