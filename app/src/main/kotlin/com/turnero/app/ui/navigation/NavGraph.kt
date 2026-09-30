package com.turnero.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.turnero.app.ui.screens.servicios.ServiciosRoute

/**
 * Grafo de navegacion de la app.
 *
 * Se declara en `MainActivity`, que ya no renderiza nada por su cuenta: la unica
 * excepcion temporal a "cero strings hardcodeados" quedo cerrada en el slice 1.
 */
@Composable
fun TurneroNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Servicios,
    ) {
        composable<Servicios> {
            ServiciosRoute()
        }
    }
}
