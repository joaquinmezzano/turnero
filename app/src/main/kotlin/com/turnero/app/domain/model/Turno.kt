package com.turnero.app.domain.model

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Turno agendado: la unidad de trabajo de la app.
 *
 * `inicio` es un `Instant` (se guarda en UTC) y representa el arranque del turno, siempre
 * anclado a la hora en punto de la zona del dispositivo. La duracion no se deriva del
 * servicio a proposito.
 *
 * **`duracionMin` es un snapshot copiado del `Servicio` al crear el turno**, nunca un JOIN
 * ni un valor recalculado. Si la duracion del servicio cambia, los turnos ya agendados no
 * se mueven: un turno es un compromiso con el cliente, no una proyeccion del catalogo. Es
 * la razon por la que el campo existe en lugar de derivarse.
 *
 * **No hay `servicioNombre`.** El nombre se resuelve al leer, con un JOIN que *no* filtra
 * `servicios.deletedAt`: "este turno fue un Corte de pelo" es un hecho historico y tiene
 * que sobrevivir a la baja del servicio.
 */
data class Turno(
    val id: UUID,
    val clienteId: UUID,
    val servicioId: UUID,
    val inicio: Instant,
    val duracionMin: Int,
    val estado: EstadoTurno,
    val notas: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
)

/**
 * Momento en que termina el turno: `inicio + duracionMin`.
 *
 * El intervalo de un turno es **semiabierto** `[inicio, fin)`: un turno que termina 10:00 y
 * otro que arranca 10:00 no se solapan (ver [seSolapan]). Por eso el fin no se guarda:
 * derivarlo evita que quede desincronizado con `inicio` o `duracionMin`.
 */
fun Turno.fin(): Instant = inicio.plus(duracionMin.toLong(), ChronoUnit.MINUTES)
