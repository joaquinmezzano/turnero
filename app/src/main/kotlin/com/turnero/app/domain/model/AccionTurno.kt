package com.turnero.app.domain.model

/**
 * Accion concreta que la UI puede ofrecer sobre un turno y que un use case puede ejecutar.
 *
 * Es la unica traduccion entre "que se puede hacer con este turno" y "que boton se pinta":
 * la pantalla dibuja lo que devuelve [accionesDisponibles] y no decide por su cuenta, y el
 * use case vuelve a evaluar la misma funcion antes de escribir. Con una sola fuente de
 * verdad, un import o un sync futuros no pueden colar una transicion que la UI no mostro.
 *
 * `LIBERAR` no cambia el enum `EstadoTurno`: es un **soft delete** del turno, el mecanismo
 * que ya usa el resto de la app para sacar una fila de circulacion sin perderla.
 */
enum class AccionTurno {
    CONFIRMAR,
    MARCAR_ATENDIDO,
    MARCAR_AUSENTE,
    CANCELAR,
    VOLVER_A_PENDIENTE,
    LIBERAR,
}
