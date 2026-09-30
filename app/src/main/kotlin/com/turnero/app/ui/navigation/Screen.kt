package com.turnero.app.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Destinos de navegacion, type-safe.
 *
 * Al ser `@Serializable`, `NavHost` genera la ruta y los argumentos por reflection del
 * serializer: no hay strings de ruta que puedan quedar desincronizados del modelo. El
 * costo es que agregar un campo con valor por default mantiene la compatibilidad con
 * las rutas ya guardadas en el back stack.
 */
@Serializable
sealed interface Screen

/** Catalogo de servicios. Es el unico destino del slice 1. */
@Serializable
data object Servicios : Screen
