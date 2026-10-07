package com.turnero.app.domain.model

import java.time.LocalTime

/**
 * Franja horaria en la que se pueden iniciar turnos.
 *
 * Es un valor **inyectado** en los use cases que validan, no una constante leida dentro de
 * la funcion. Hoy es [VENTANA_POR_DEFECTO], pero el Slice 6 mete `HorarioAtencion` por dia
 * de la semana y cambia la implementacion sin tocar un solo call site: los use cases ya
 * reciben la ventana por constructor.
 *
 * Solo importa la **hora de inicio**: un turno puede terminar despues de [hasta]. `hasta`
 * es inclusive como hora de inicio.
 */
data class VentanaAtencion(
    val desde: LocalTime,
    val hasta: LocalTime,
)

/** 08:00 a 22:00, la ventana del MVP. */
val VENTANA_POR_DEFECTO = VentanaAtencion(
    desde = LocalTime.of(8, 0),
    hasta = LocalTime.of(22, 0),
)
