package com.turnero.app.domain.model

/**
 * Turno con el nombre y el color de su servicio, resueltos al leer.
 *
 * Existe porque [Turno] **no snapshotea el nombre del servicio**: guarda la duracion (que
 * es un compromiso) pero no el catalogo. La agenda del dia y el historial del cliente
 * necesitan el nombre y el color para pintar la grilla y las fichas, y los resuelven con un
 * `JOIN` que **no filtra `servicios.deletedAt`**.
 *
 * Motivo: "este turno fue un Corte de pelo" es un hecho historico, no un dato vigente. Si
 * el servicio se dio de baja, un `JOIN` filtrado devolveria vacio justo donde el dato
 * existe y el turno quedaria sin nombre ni color. La excepcion esta escrita en el KDoc de
 * cada query que la aplica.
 *
 * `servicioColor` es null solo en el caso defensivo de que no haya fila de servicio; con el
 * `INNER JOIN` de las queries no deberia pasar.
 */
data class TurnoConServicio(
    val turno: Turno,
    val servicioNombre: String,
    val servicioColor: Int?,
)
