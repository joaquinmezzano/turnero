package com.turnero.app.domain.model

import java.time.Instant

/**
 * Que estados ocupan el horario y cuales lo liberan.
 *
 * `PENDIENTE`, `CONFIRMADO` y `ATENDIDO` bloquean. `AUSENTE` y `CANCELADO` no: el turno
 * sigue existiendo y se sigue viendo en la grilla (es informacion real del cliente), pero
 * no impide agendar ahi. Sin esta distincion, un corte de pelo cancelado dejaria el horario
 * ocupado para siempre.
 *
 * Vive en `domain` y no en el `@Query` porque es regla de negocio. El DAO recibe la lista
 * por parametro en vez de tenerla escrita en el SQL.
 */
val ESTADOS_QUE_BLOQUEAN: Set<EstadoTurno> = setOf(
    EstadoTurno.PENDIENTE,
    EstadoTurno.CONFIRMADO,
    EstadoTurno.ATENDIDO,
)

/** `true` si un turno en este estado impide agendar en su horario. */
fun bloqueaHorario(estado: EstadoTurno): Boolean = estado in ESTADOS_QUE_BLOQUEAN

/**
 * Interseccion de dos intervalos **semiabiertos** `[inicio, fin)`.
 *
 * `inicioA < finB && finA > inicioB`. El semiabierto importa: un turno que termina 10:00 y
 * otro que arranca 10:00 **no** se solapan, y tratarlos como choque llenaria la agenda de
 * errores en el caso mas comun.
 *
 * La comparacion es general, no un `==` de horas: aunque los turnos arranquen en punto, un
 * servicio de mas de 60 minutos ocupa varias celdas, asi que el choque se decide por
 * interseccion de intervalos.
 *
 * Esta es la regla del dominio; `TurnoDao.obtenerQueSolapan` la implementa en SQL para no
 * traer filas a Kotlin. Las dos se prueban, cada una donde puede correr.
 */
fun seSolapan(inicioA: Instant, finA: Instant, inicioB: Instant, finB: Instant): Boolean =
    inicioA.isBefore(finB) && finA.isAfter(inicioB)

/** Atajo de [seSolapan] para dos [Turno] completos. */
fun Turno.seSolapaCon(otro: Turno): Boolean = seSolapan(inicio, fin(), otro.inicio, otro.fin())