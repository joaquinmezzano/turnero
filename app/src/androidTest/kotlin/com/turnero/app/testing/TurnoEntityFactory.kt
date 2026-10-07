package com.turnero.app.testing

import com.turnero.app.data.local.entity.TurnoEntity
import com.turnero.app.domain.model.EstadoTurno
import com.turnero.app.domain.model.Turno
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * 2026-03-16 (lunes): fecha fija de todos los fixtures de turno.
 *
 * Constante a proposito, igual que `INSTANTE_BASE`: un `LocalDate.now()` deja de ser
 * reproducible apenas el test cruza medianoche o corre en otra maquina.
 */
val FECHA_BASE: LocalDate = LocalDate.of(2026, 3, 16)

/** 10:00 UTC del dia base: inicio por defecto de los fixtures. */
val INICIO_BASE: Instant = Instant.parse("2026-03-16T10:00:00Z")

/**
 * `Instant` de las [hora]:00 UTC del [dia] de [FECHA_BASE].
 *
 * **UTC a proposito, y no la zona del dispositivo.** Las queries que prueban estos tests
 * comparan `Instant` contra `Instant` sin mirar el reloj de nadie, y fijar una zona aca
 * solo agregaria un dato del que hay que acordarse. Los tests que si dependen de la zona
 * (anclaje a la hora, ventana de atencion) viven en `src/test`, junto a `ZONA_BASE`.
 */
fun instanteDeTurno(hora: Int, dia: Int = FECHA_BASE.dayOfMonth): Instant =
    FECHA_BASE.withDayOfMonth(dia).atTime(hora, 0).atZone(ZoneOffset.UTC).toInstant()

/** 00:00 UTC del [dia]: limite inferior (incluido) del dia. */
fun inicioDelDia(dia: Int = FECHA_BASE.dayOfMonth): Instant = instanteDeTurno(0, dia)

/** 00:00 UTC del dia siguiente: limite superior (excluido) del dia. */
fun finDelDia(dia: Int = FECHA_BASE.dayOfMonth): Instant = instanteDeTurno(0, dia + 1)

/**
 * Constructor de `TurnoEntity` con defaults validos.
 *
 * Va duplicado del de `src/test` a proposito: `androidTest` no ve el source set `test`
 * (ver el mismo razonamiento en [entidadCliente]). Los unicos campos con valor propio son
 * `inicio` y `duracionMin`: 30 minutos contra `INICIO_BASE` dejan el fin en las 10:30,
 * que es el corte limpio con el que se arma la mayoria de los casos de solapamiento.
 */
fun entidadTurno(
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

/**
 * Contraparte de dominio de [entidadTurno]: la que recibe `TurnoRepositoryImpl.crear`.
 *
 * Duplicado del `turno(...)` de `src/test/TurnoFactory.kt` por el mismo motivo que las
 * entity factories, y **con la misma forma y los mismos defaults** para que un test pueda
 * moverse de un source set al otro sin reescribir las llamadas.
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
