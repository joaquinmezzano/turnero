package com.turnero.app.domain.model

import java.time.Instant

/**
 * Maquina de estados del turno, como funcion pura.
 *
 * **Es la unica fuente de verdad.** La UI no decide: dibuja lo que devuelve
 * [accionesDisponibles]. Los use cases vuelven a llamar la misma funcion antes de escribir,
 * asi que un import, un sync o una llamada directa al repositorio no pueden colar una
 * transicion que la pantalla nunca mostro.
 *
 * El corte temporal usa el **fin** del turno, no el inicio: `pre-horario` es
 * `ahora <= fin` y `post-horario` es `ahora > fin`. Un turno que ya empezo pero todavia no
 * termino sigue siendo reversible, que es lo que espera el profesional que esta por atender.
 *
 * Decisiones que no se "completan" porque no estan en la tabla de transiciones:
 *
 * - **`PENDIENTE` post-horario no tiene salida hacia `ATENDIDO` ni `AUSENTE`.** Se congela:
 *   el horario ya paso, y dar por atendido un turno que nadie confirmo escribiria una
 *   verdad que el profesional nunca afirmo. Solo se puede confirmar o liberar.
 * - **`ATENDIDO` y `AUSENTE` son finales sin ninguna salida**, en pre o en post-horario.
 * - **`CANCELADO` post-horario tambien es final.** Cancelar un turno que ya termino es un
 *   hecho consumado; deshacerlo escribiria que el turno nunca existio. En pre-horario, en
 *   cambio, es reversible.
 */
fun accionesDisponibles(turno: Turno, ahora: Instant): Set<AccionTurno> {
    val postHorario = ahora.isAfter(turno.fin())
    return when (turno.estado) {
        EstadoTurno.PENDIENTE -> setOf(
            AccionTurno.CONFIRMAR,
            AccionTurno.LIBERAR,
        )

        EstadoTurno.CONFIRMADO -> buildSet {
            add(AccionTurno.MARCAR_ATENDIDO)
            add(AccionTurno.CANCELAR)
            if (postHorario) add(AccionTurno.MARCAR_AUSENTE)
        }

        EstadoTurno.ATENDIDO -> emptySet()

        EstadoTurno.AUSENTE -> emptySet()

        EstadoTurno.CANCELADO ->
            if (postHorario) {
                emptySet()
            } else {
                setOf(
                    AccionTurno.LIBERAR,
                    AccionTurno.VOLVER_A_PENDIENTE,
                    AccionTurno.CONFIRMAR,
                )
            }
    }
}

/**
 * Pregunta "¿esta transicion se puede ejecutar ahora?", que es la consulta que usan los use
 * cases antes de escribir.
 *
 * Se implementa preguntando si la accion pertenece a [accionesDisponibles] y no con una
 * segunda tabla: si las dos reglas se escribieran aparte, divergirian en el primer cambio y
 * la UI ofreceria una accion que el dominio rechaza.
 */
fun accionPermitida(turno: Turno, accion: AccionTurno, ahora: Instant): Boolean =
    accion in accionesDisponibles(turno, ahora)