package com.turnero.app.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.turnero.app.ui.screens.clientes.ClienteDetalleRoute
import com.turnero.app.ui.screens.clientes.ClientesRoute
import com.turnero.app.ui.screens.servicios.ServiciosRoute
import com.turnero.app.ui.screens.turnos.TurnosRoute

/**
 * Grafo de navegación de la app.
 *
 * Se declara en `MainActivity`, que ya no renderiza nada por su cuenta: la única
 * excepción temporal a "cero strings hardcodeados" quedó cerrada en el slice 1.
 *
 * **El `Scaffold` vive acá y no en cada pantalla** a partir del slice 2, que es cuando
 * aparece la barra inferior. El padding que produce se aplica al `NavHost`, y por eso
 * todas las pantallas declaran `contentWindowInsets = WindowInsets(0, 0, 0, 0)`: si además
 * tomaran los insets del sistema, cada uno se contaría dos veces y el contenido quedaría
 * debajo de la barra de estado. Ver el KDoc de `ClientesScreen`.
 */
@Composable
fun TurneroNavGraph(navController: NavHostController = rememberNavController()) {
    val entradaActual by navController.currentBackStackEntryAsState()
    val destinoActual = entradaActual?.destination
    val seleccionado = remember(destinoActual) {
        DESTINOS_PRINCIPALES.firstOrNull { principal -> destinoActual.tieneRuta(principal) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        // La barra solo existe en los destinos de primer nivel, así que su presencia y
        // el resaltado son la misma pregunta y salen del mismo `seleccionado`.
        bottomBar = {
            if (seleccionado != null) {
                BarraInferior(
                    destinos = DESTINOS_PRINCIPALES,
                    seleccionado = seleccionado,
                    onDestinoSeleccionado = { principal -> navController.irADestino(principal) },
                )
            }
        },
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = Turnos,
            modifier = Modifier.padding(contentPadding),
        ) {
        composable<Turnos> {
            TurnosRoute(
                onAbrirServicios = { navController.navigate(Servicios) },
                onAbrirTurnoDetalle = { turnoId -> navController.navigate(TurnoDetalle(turnoId)) },
                onCrearTurno = { fecha, hora -> navController.navigate(TurnoEditor(fecha, hora)) },
            )
        }
        composable<Clientes> {
            ClientesRoute(
                onAbrirFicha = { clienteId -> navController.navigate(ClienteDetalle(clienteId)) },
            )
        }
        composable<ClienteDetalle> {
            // El id viaja en el argumento de la ruta y lo lee el ViewModel del
            // `SavedStateHandle`, que Hilt arma desde el `arguments` del
            // `NavBackStackEntry`. Pasarlo además por parámetro sería la misma fuente
            // de verdad leída de dos formas, con dos chances de que se desincronicen.
            ClienteDetalleRoute(
                onVolver = { navController.popBackStack() },
                onCreateTurno = { clienteId ->
                    navController.navigate(TurnoEditor(clienteId = clienteId))
                },
                onAbrirTurno = { turnoId ->
                    navController.navigate(TurnoDetalle(turnoId))
                },
            )
        }
        composable<Servicios> {
            ServiciosRoute()
        }
        composable<TurnoDetalle> {
            com.turnero.app.ui.screens.turnos.TurnoDetalleRoute(
                onVolver = { navController.popBackStack() },
                onEditar = { turnoId ->
                    navController.navigate(TurnoEditor(turnoId = turnoId))
                },
            )
        }
        composable<TurnoEditor> {
            com.turnero.app.ui.screens.turnos.TurnoEditorRoute(
                onVolver = { navController.popBackStack() },
                onGuardado = { navController.popBackStack() },
            )
        }
        }
    }
}

/**
 * ¿El destino actual **es** uno de primer nivel?
 *
 * Solo cuenta la coincidencia exacta. La ficha de un cliente es un destino de segundo
 * nivel y no muestra la barra: la Material 3 la reserva para cambiar de sección, y una
 * ficha con sus acciones de editar y borrar no necesita un selector de secciones
 * consumiéndole 80 px de alto. Volver a Clientes desde la barra de estado ya está, y con
 * el ítem resaltado de nuevo.
 *
 * Por eso `seleccionado` no se calcula de otra manera: si además de la ficha contara
 * como "Clientes", la barra aparecería en ella y la pregunta "¿la muestro?" quedaría
 * atada a "¿qué resalto?", que son decisiones distintas.
 */
private fun NavDestination?.tieneRuta(principal: DestinoPrincipal): Boolean {
    val destino = this ?: return false
    // `hasRoute(KClass)` es una extensión **miembro del companion** de `NavDestination`:
    // no se importa como top-level ni se puede llamar con la ruta calificada
    // (`NavDestination.hasRoute(...)`), hay que abrir el companion con `with`. La
    // variante reificada (`destino.hasRoute<Clientes>()`) tampoco sirve acá, porque el
    // tipo viene de un `KClass` guardado en la lista de destinos y no de un tipo escrito
    // en el código.
    return with(NavDestination) { destino.hasRoute(principal.claseRuta) }
}

/**
 * Navegación de la barra inferior: el patrón de Android para pestañas.
 *
 * - `popUpTo` con el inicio del grafo y `saveState` colapsa la pila hasta la raíz y
 *   guarda el estado de la sección que se está dejando. Sin `saveState`, volver a una
 *   pestaña reconstruye la pantalla desde cero y pierde la búsqueda en curso.
 * - `launchSingleTop` evita apilar dos veces la misma pestaña si se toca dos veces rápido.
 * - `restoreState` es el otro lado de `saveState`: recupera la pantalla como estaba.
 */
private fun NavHostController.irADestino(principal: DestinoPrincipal) {
    navigate(principal.destino) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
