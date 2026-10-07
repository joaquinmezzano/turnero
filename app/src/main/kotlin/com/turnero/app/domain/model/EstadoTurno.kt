package com.turnero.app.domain.model

/**
 * Estado de un turno en la agenda.
 *
 * **`LIBRE` no es un valor de este enum.** Un horario esta libre cuando *ninguna* fila lo
 * ocupa, y eso se deriva al pintar la grilla. Materializar una fila por hora vacia haria
 * crecer la tabla sin limite guardando filas que no son turnos (ver el diseno del Slice 3).
 *
 * La ausencia de fila se representa con `AccionTurno.LIBERAR`, que es un soft delete: la
 * fila sigue existiendo y visible, deja de bloquear el horario.
 */
enum class EstadoTurno {
    PENDIENTE,
    CONFIRMADO,
    ATENDIDO,
    AUSENTE,
    CANCELADO,
}
