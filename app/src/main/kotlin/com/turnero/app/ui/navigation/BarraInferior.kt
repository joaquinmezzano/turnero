package com.turnero.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.turnero.app.R
import com.turnero.app.ui.theme.TurneroTheme
import kotlin.reflect.KClass

/**
 * Un destino de primer nivel de la app, o sea uno que merece un ícono en la barra
 * inferior.
 *
 * La etiqueta y el ícono van **dentro** del destino y no al revés: la lista es la fuente
 * de verdad, así que un slice futuro agrega una línea y la barra y el resaltado salen
 * solos, sin tocar el `NavigationBar`.
 *
 * `claseRuta` existe aparte de `destino` porque el resaltado se resuelve con
 * `NavDestination.hasRoute(KClass)`: la navegación es type-safe y no expone el tipo del
 * destino actual por reflection, así que hay que guardar el `KClass` al armar la lista en
 * vez de adivinarlo en el momento de pintar.
 */
data class DestinoPrincipal(
    val destino: Screen,
    val claseRuta: KClass<out Screen>,
    @StringRes val etiqueta: Int,
    val icono: ImageVector,
)

/**
 * Los destinos de la barra inferior.
 *
 * **Dos ítems: Turnos y Clientes.** `Servicios` deja de estar en la barra y pasa al
 * overflow del top bar de Turnos.
 */
val DESTINOS_PRINCIPALES: List<DestinoPrincipal> = listOf(
    DestinoPrincipal(Turnos, Turnos::class, R.string.nav_turnos, Icons.Filled.DateRange),
    DestinoPrincipal(Clientes, Clientes::class, R.string.nav_clientes, Icons.Filled.Groups),
)

/**
 * Barra de navegación inferior. Pinta y avisa: no sabe de `NavController`.
 *
 * Recibe el destino ya resuelto en `seleccionado` en lugar del `NavDestination` crudo
 * porque decidir si la ficha de un cliente cuenta como "Clientes" es regla de
 * navegación, y esa regla vive en el `NavGraph` que es quien tiene el controlador.
 */
@Composable
fun BarraInferior(
    destinos: List<DestinoPrincipal>,
    seleccionado: DestinoPrincipal?,
    onDestinoSeleccionado: (DestinoPrincipal) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        destinos.forEach { principal ->
            NavigationBarItem(
                selected = principal == seleccionado,
                onClick = { onDestinoSeleccionado(principal) },
                icon = {
                    Icon(
                        imageVector = principal.icono,
                        // Sin `contentDescription`: la etiqueta de abajo ya nombra el
                        // destino, y duplicarla hace que TalkBack lea lo mismo dos veces.
                        // El nombre del ícono no aporta nada más.
                        contentDescription = null,
                    )
                },
                label = { Text(text = stringResource(principal.etiqueta)) },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BarraInferiorPreview() {
    TurneroTheme(dynamicColor = false) {
        BarraInferior(
            destinos = DESTINOS_PRINCIPALES,
            seleccionado = DESTINOS_PRINCIPALES.first(),
            onDestinoSeleccionado = {},
        )
    }
}
