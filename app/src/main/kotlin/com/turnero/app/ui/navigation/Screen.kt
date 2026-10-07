package com.turnero.app.ui.navigation

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * Destinos de navegación, type-safe.
 *
 * Al ser `@Serializable`, `NavHost` genera la ruta y los argumentos por reflection del
 * serializer: no hay strings de ruta que puedan quedar desincronizados del modelo. El
 * costo es que agregar un campo con valor por default mantiene la compatibilidad con las
 * rutas ya guardadas en el back stack.
 */
@Serializable
sealed interface Screen

/**
 * Hub de turnos. Es el destino de arranque a partir del Slice 3.
 */
@Serializable
data object Turnos : Screen

/**
 * Listado de clientes.
 */
@Serializable
data object Clientes : Screen

/**
 * Ficha de un cliente.
 *
 * **Es una ruta con argumento y no un diálogo, a propósito.** Un `AlertDialog` no tiene
 * back del sistema: el gesto de volver no lo cierra, y no hay deep link ni forma de que
 * otra parte de la app (o un recordatorio del slice 7) abra la ficha de un cliente
 * concreto. Con un argumento en la ruta, la navegación deja el id en el
 * `SavedStateHandle` del `NavBackStackEntry` y el `ClienteDetalleViewModel` lo sobrevive a
 * un cambio de configuración.
 *
 * El `UUID` lleva `@Serializable(with = UuidSerializer::class)` porque el plugin del
 * compilador no encuentra el soporte de `kotlinx-serialization-core` para `UUID` y el
 * argumento, de todos modos, viaja como texto en la ruta. Ver el KDoc de `UuidSerializer`.
 */
@Serializable
data class ClienteDetalle(
    @Serializable(with = UuidSerializer::class)
    val clienteId: UUID,
) : Screen

/** Detalle de un turno. */
@Serializable
data class TurnoDetalle(
    @Serializable(with = UuidSerializer::class)
    val turnoId: UUID,
) : Screen

/**
 * Editor de turno (alta y edición).
 *
 * Entradas según de dónde venga:
 * - Desde la grilla de turnos: `fecha` y `hora` fijadas por el tap; se elige cliente y
 *   servicio.
 * - Desde la ficha del cliente: `clienteId` fijado; se elige día, hora y servicio.
 * - Desde el detalle de un turno: `turnoId`; la pantalla precarga y permite editar los
 *   datos (la hora siempre anclada a la hora exacta, 08:00–22:00).
 */
@Serializable
data class TurnoEditor(
    @Serializable(with = LocalDateSerializer::class)
    val fecha: LocalDate? = null,
    @Serializable(with = LocalTimeSerializer::class)
    val hora: LocalTime? = null,
    @Serializable(with = UuidSerializer::class)
    val clienteId: UUID? = null,
    @Serializable(with = UuidSerializer::class)
    val turnoId: UUID? = null,
) : Screen

/** Catálogo de servicios. Se accede desde el overflow del top bar de Turnos. */
@Serializable
data object Servicios : Screen
