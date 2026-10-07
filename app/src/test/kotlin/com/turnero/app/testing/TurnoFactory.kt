package com.turnero.app.testing

import com.turnero.app.domain.model.EstadoTurno
import com.turnero.app.domain.model.Turno
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Datos de prueba compartidos para todos los tests de `Turno`.
 *
 * Reusa el `INSTANTE_BASE` que ya declara `ServicioFactory`, en el mismo paquete.
 *
 * `inicio` por default es un instante fijo reconocible. Los tests de agenda necesitan
 *_compare en una zona concreta_ mas que en un instante: para eso esta
 * [instanteDeTurno], que construye el `Instant` desde fecha y hora local con la zona base,
 * que es como lo construye el codigo real.
 */
val INICIO_BASE: Instant = Instant.parse("2026-03-16T13:00:00Z")

/** 2026-03-16 (lunes) 10:00 hora de Argentina. */
val FECHA_BASE: LocalDate = LocalDate.of(2026, 3, 16)

/**
 * `Instant` de las [hora]:00 de esa fecha en [ZONA_BASE].
 *
 * Es el valor que el anclaje a la hora tiene que producir: el alta no guarda las 10:37 que
 * le pasen, guarda las 10:00 de esa zona.
 */
fun instanteDeTurno(
    hora: Int,
    dia: Int = FECHA_BASE.dayOfMonth,
    mes: Int = FECHA_BASE.monthValue,
): Instant = FECHA_BASE
    .withDayOfMonth(dia)
    .withMonth(mes)
    .atTime(hora, 0)
    .atZone(ZONA_BASE)
    .toInstant()

/**
 * Constructor de `Turno` con defaults validos para un turno guardado.
 *
 * `duracionMin = 30` y `inicio = INICIO_BASE` dejan el fin en las 10:30 hora Argentina,
 * asi que los tests de la maquina de estados pueden moverse alrededor de un corte limpio.
 */
fun turno(
    id: UUID = UUID.randomUUID(),
    clienteId: UUID = UUID.randomUUID(),
    servicioId: UUID = UUID.randomUUID(),
    inicio: Instant = INICIO_BASE,
    duracionMin: Int = 30,
    estado: EstadoTurno = EstadoTurno.PENDIENTE,
    notas: String? = null,
    createdAt: Instant = INSTANTE_BASE,
    updatedAt: Instant = createdAt,
    deletedAt: Instant? = null,
): Turno = Turno(
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