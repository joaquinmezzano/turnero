package com.turnero.app.ui.screens.servicios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.turnero.app.R
import com.turnero.app.core.format.CurrencyFormatter
import com.turnero.app.domain.model.Servicio
import com.turnero.app.ui.theme.ServicioColors
import com.turnero.app.ui.theme.TurneroTheme
import java.time.Instant
import java.util.UUID

/**
 * Pintor de la pantalla de servicios. Sin estado propio: recibe el `ServiciosUiState`
 * del ViewModel y callbacks. [ServiciosRoute] es quien los conecta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiciosScreen(
    uiState: ServiciosUiState,
    snackbarHostState: SnackbarHostState,
    onAgregar: () -> Unit,
    onEditar: (Servicio) -> Unit,
    onEliminar: (Servicio) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.servicios_title)) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAgregar) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.servicios_agregar_desc),
                )
            }
        },
    ) { contentPadding ->
        when {
            uiState.cargando && uiState.servicios.isEmpty() -> EstadoCargando(contentPadding)
            // Un error de lectura con la lista vacia NO es "no tenes servicios": decirselo
            // al usuario es una afirmacion falsa. El snackbar explica el detalle y se
            // descarta solo, asi que el estado de pantalla tiene que decirlo tambien.
            uiState.errorRes != null && uiState.servicios.isEmpty() -> EstadoError(contentPadding)
            uiState.servicios.isEmpty() -> EstadoVacio(contentPadding, onAgregar)
            else -> ListaServicios(
                servicios = uiState.servicios,
                contentPadding = contentPadding,
                onEditar = onEditar,
                onEliminar = onEliminar,
            )
        }
    }
}

@Composable
private fun EstadoError(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.servicios_error_carga),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EstadoCargando(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EstadoVacio(contentPadding: PaddingValues, onAgregar: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.servicios_vacio_titulo),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.servicios_vacio_cuerpo),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        TextButton(onClick = onAgregar) {
            Text(
                text = stringResource(R.string.servicios_vacio_accion),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ListaServicios(
    servicios: List<Servicio>,
    contentPadding: PaddingValues,
    onEditar: (Servicio) -> Unit,
    onEliminar: (Servicio) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 88.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // `key` es obligatoria: sin ella Compose reutiliza las celdas por posicion y al
        // borrar o reordenar alfabeticamente el contenido se mezcla entre items.
        items(servicios, key = { it.id }) { servicio ->
            ServicioCard(
                servicio = servicio,
                onEditar = { onEditar(servicio) },
                onEliminar = { onEliminar(servicio) },
            )
        }
    }
}

@Composable
private fun ServicioCard(
    servicio: Servicio,
    onEditar: () -> Unit,
    onEliminar: () -> Unit,
) {
    // La Card NO lleva `Modifier.clickable`: fusionaria la semantica de los descendientes
    // y los dos `IconButton` quedarian metidos dentro de un unico nodo, con una sola
    // accion. TalkBack anunciaria un solo nodo por fila y "Eliminar" seria inalcanzable.
    // Editar ya tiene su propio boton, asi que el click de la fila era redundante.
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MuestraColor(color = servicio.color)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = servicio.nombre,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.servicio_duracion_min, servicio.duracionMin),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = precioLegible(servicio.precioCentavos),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                // Sin tope, un precio de siete cifras mas `fontScale` 2.0 se come el ancho
                // de la fila: los hijos sin `weight` se miden contra el ancho completo y la
                // `Column` de al lado es la que colapsa, no el precio.
                modifier = Modifier.widthIn(max = ANCHO_MAXIMO_PRECIO),
            )
            IconButton(onClick = onEditar) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(
                        R.string.servicios_editar_desc,
                        servicio.nombre,
                    ),
                )
            }
            IconButton(onClick = onEliminar) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(
                        R.string.servicios_eliminar_desc,
                        servicio.nombre,
                    ),
                )
            }
        }
    }
}

/**
 * El color viene del dato (`Color(servicio.color)`), no de un literal en la pantalla:
 * es informacion guardada por el usuario, identica a leer un `id` de la base.
 */
@Composable
private fun MuestraColor(color: Int) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(Color(color)),
    )
}

/** Sin precio muestra "a consultar": un guion lo descarta el lector de pantalla. */
@Composable
private fun precioLegible(precioCentavos: Long?): String =
    precioCentavos?.let { CurrencyFormatter.formatear(it) }
        ?: stringResource(R.string.servicio_precio_sin_definir)

/** Tope del precio en la fila: reserva el ancho para el nombre y los dos botones. */
private val ANCHO_MAXIMO_PRECIO = 120.dp

@Preview(showBackground = true)
@Composable
private fun ServiciosScreenPreview() {
    val ahora = Instant.EPOCH
    TurneroTheme(dynamicColor = false) {
        ServiciosScreen(
            uiState = ServiciosUiState(
                servicios = listOf(
                    Servicio(
                        id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        nombre = "Corte de pelo",
                        duracionMin = 45,
                        precioCentavos = 8_000,
                        color = ServicioColors[0].toArgb(),
                        createdAt = ahora,
                        updatedAt = ahora,
                        deletedAt = null,
                    ),
                    Servicio(
                        id = UUID.fromString("00000000-0000-0000-0000-000000000002"),
                        nombre = "Coloración",
                        duracionMin = 120,
                        precioCentavos = null,
                        color = ServicioColors[2].toArgb(),
                        createdAt = ahora,
                        updatedAt = ahora,
                        deletedAt = null,
                    ),
                ),
                cargando = false,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onAgregar = {},
            onEditar = {},
            onEliminar = {},
        )
    }
}
