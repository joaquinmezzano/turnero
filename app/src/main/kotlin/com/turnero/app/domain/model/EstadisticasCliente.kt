package com.turnero.app.domain.model

import java.time.Instant

/**
 * Proyeccion de solo lectura con el historial de un cliente.
 *
 * **Sin tabla de contadores y sin cache.** Si los numeros se materializaran habria que
 * invalidarlos en cada cambio de estado, y el primer olvido mostraria cifras falsas en la
 * ficha del cliente. Sale de contar sobre la tabla viva en cada lectura.
 */
data class EstadisticasCliente(
    val pendientes: Int,
    val confirmados: Int,
    val atendidos: Int,
    val ausentes: Int,
    val cancelados: Int,
    val ultimaVisita: UltimaVisita?,
) {

    /**
     * Turno mas reciente del cliente, **de cualquier estado**.
     *
     * No se filtra por `ATENDIDO`: un `CANCELADO` reciente es informacion de contacto y el
     * usuario lo quiere ver. El estado viaja en la propia ultima visita para que la pantalla
     * pueda distinguir "15/03/2026 (Corte de pelo)" de "15/03/2026 (Corte de pelo · Cancelado)".
     */
    data class UltimaVisita(
        val inicio: Instant,
        val servicioNombre: String,
        val estado: EstadoTurno,
    )

    companion object {

        /**
         * Construye las estadisticas rellenando en cero los estados sin filas.
         *
         * Existe porque `GROUP BY estado` **omite** los estados que no tienen filas: un
         * cliente con 3 atendidos y 0 cancelados no devuelve fila `CANCELADO`. Sin este
         * relleno la ficha muestra un hueco donde deberia decir `0`.
         *
         * @param conteos filas de `GROUP BY estado`, ya colapsadas a mapa.
         */
        fun desde(
            conteos: Map<EstadoTurno, Int>,
            ultimaVisita: UltimaVisita?,
        ): EstadisticasCliente = EstadisticasCliente(
            pendientes = conteos[EstadoTurno.PENDIENTE] ?: 0,
            confirmados = conteos[EstadoTurno.CONFIRMADO] ?: 0,
            atendidos = conteos[EstadoTurno.ATENDIDO] ?: 0,
            ausentes = conteos[EstadoTurno.AUSENTE] ?: 0,
            cancelados = conteos[EstadoTurno.CANCELADO] ?: 0,
            ultimaVisita = ultimaVisita,
        )
    }
}