package com.turnero.app.ui.navigation

import kotlinx.serialization.Serializable
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
 * Listado de clientes. Es el destino de arranque desde el slice 2, porque es el que
 * existe de punta a punta sin depender de turnos.
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

/** Catálogo de servicios. */
@Serializable
data object Servicios : Screen
