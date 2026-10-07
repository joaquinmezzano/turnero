package com.turnero.app.data.mapper

import com.turnero.app.data.local.entity.TurnoEntity
import com.turnero.app.data.local.projection.TurnoConServicioFila
import com.turnero.app.data.local.projection.UltimaVisitaFila
import com.turnero.app.domain.model.EstadisticasCliente
import com.turnero.app.domain.model.Turno
import com.turnero.app.domain.model.TurnoConServicio

/**
 * Entity <-> Domain.
 *
 * El mapeo es 1:1 porque los dos modelos tienen la misma forma. El `estado` viaja tal cual:
 * Room lo persiste como TEXT con el nombre de la constante y lo devuelve al enum, asi que no
 * hay nada que traducir. Si alguna vez lo bajara a un entero por tamano, este archivo es el
 * unico lugar donde habria que hacerlo.
 */
fun TurnoEntity.toDomain(): Turno = Turno(
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

fun Turno.toEntity(): TurnoEntity = TurnoEntity(
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

/** Proyeccion de la ultima visita -> modelo de dominio, sin nombre inventado. */
fun UltimaVisitaFila.toDomain(): EstadisticasCliente.UltimaVisita = EstadisticasCliente.UltimaVisita(
    inicio = inicio,
    servicioNombre = servicioNombre,
    estado = estado,
)

/**
 * Proyeccion con servicio -> modelo de dominio.
 *
 * Reusa [toDomain] para la parte de `turnos`: la proyeccion tiene exactamente las mismas
 * columnas de la entidad mas las dos del servicio, asi que no hay que repetir el mapeo de
 * los diez campos del turno.
 */
fun TurnoConServicioFila.toDomain(): TurnoConServicio = TurnoConServicio(
    turno = TurnoEntity(
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
    ).toDomain(),
    servicioNombre = servicioNombre,
    servicioColor = servicioColor,
)