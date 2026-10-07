package com.turnero.app.ui.screens.clientes

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.turnero.app.R
import com.turnero.app.domain.model.Cliente
import com.turnero.app.domain.model.EstadisticasCliente
import com.turnero.app.domain.model.TurnoConServicio
import com.turnero.app.ui.theme.TurneroTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.util.UUID

/**
 * Ficha del cliente: sus datos y las acciones de editar y dar de baja.
 *
 * `contentWindowInsets = WindowInsets(0, 0, 0, 0)` porque los insets ya los consumió el
 * `Scaffold` de `TurneroNavGraph`, que además es el que decide si esta pantalla tiene
 * barra inferior (no: solo los destinos de primer nivel la muestran).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClienteDetalleScreen(
    uiState: ClienteDetalleUiState,
    snackbarHostState: SnackbarHostState,
    onVolver: () -> Unit,
    onEditar: () -> Unit,
    onEliminar: () -> Unit,
    onCreateTurno: () -> Unit = {},
    onAbrirTurno: (java.util.UUID) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val cliente = uiState.cliente
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = cliente?.nombre.orEmpty(),
                        // Sin tope, un nombre de treinta caracteres empuja el ícono de
                        // volver fuera de la barra.
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(
                                R.string.cliente_detalle_volver_desc,
                            ),
                        )
                    }
                },
                actions = {
                    if (cliente != null) {
                        IconButton(onClick = onEditar) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(
                                    R.string.cliente_detalle_editar_desc,
                                    cliente.nombre,
                                ),
                            )
                        }
                        IconButton(onClick = onEliminar) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(
                                    R.string.cliente_detalle_eliminar_desc,
                                    cliente.nombre,
                                ),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { contentPadding ->
        when {
            uiState.cargando -> EstadoCargando(contentPadding)
            // Cliente `null` y error cargado: o no existe, o no se pudo leer. El mensaje
            // concreto lo trae el `UiState`; el `elvis` es la red por si algún día un
            // camino deja el estado sin error, y antes que una pantalla en blanco.
            cliente == null -> EstadoError(
                mensaje = uiState.errorRes ?: R.string.cliente_detalle_error_carga,
                contentPadding = contentPadding,
            )

            else -> FichaCliente(
                cliente = cliente,
                turnos = uiState.turnos,
                estadisticas = uiState.estadisticas,
                onCreateTurno = onCreateTurno,
                onAbrirTurno = onAbrirTurno,
                contentPadding = contentPadding,
            )
        }
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
private fun EstadoError(@StringRes mensaje: Int, contentPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(mensaje),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
    }
}


@Composable
private fun FichaCliente(
    cliente: Cliente,
    turnos: List<TurnoConServicio>,
    estadisticas: EstadisticasCliente?,
    onCreateTurno: () -> Unit,
    onAbrirTurno: (java.util.UUID) -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (cliente.telefono == null && cliente.email == null) {
                    Text(
                        text = stringResource(R.string.cliente_detalle_sin_contacto),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    cliente.telefono?.let { DatoCliente(R.string.cliente_detalle_telefono, it) }
                    cliente.email?.let { DatoCliente(R.string.cliente_detalle_email, it) }
                }
            }
        }

        Text(
            text = stringResource(R.string.cliente_detalle_notas),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = cliente.notas ?: stringResource(R.string.cliente_detalle_sin_notas),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(onClick = onCreateTurno, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cliente_detalle_crear_turno))
        }

        estadisticas?.let { EstadisticasCard(it) }
        HistorialTurnos(turnos, onAbrirTurno = onAbrirTurno)
    }
}

@Composable
private fun EstadisticasCard(stats: EstadisticasCliente) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.cliente_estadisticas_titulo), style = MaterialTheme.typography.titleMedium)
            Text(text = stringResource(R.string.cliente_estadisticas_pendientes, stats.pendientes))
            Text(text = stringResource(R.string.cliente_estadisticas_confirmados, stats.confirmados))
            Text(text = stringResource(R.string.cliente_estadisticas_atendidos, stats.atendidos))
            Text(text = stringResource(R.string.cliente_estadisticas_ausentes, stats.ausentes))
            Text(text = stringResource(R.string.cliente_estadisticas_cancelados, stats.cancelados))
            stats.ultimaVisita?.let { ultima ->
                val fecha = ultima.inicio.atZone(ZoneId.systemDefault()).toLocalDate()
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                Text(
                    text = stringResource(
                        R.string.cliente_estadisticas_ultima_visita,
                        fecha,
                        ultima.servicioNombre,
                        stringResource(ultima.estado.aNombreEstado()),
                    ),
                )
            } ?: Text(
                text = stringResource(R.string.cliente_estadisticas_sin_visitas),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HistorialTurnos(
    turnos: List<TurnoConServicio>,
    onAbrirTurno: (java.util.UUID) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.cliente_historial_titulo), style = MaterialTheme.typography.titleMedium)
        if (turnos.isEmpty()) {
            Text(
                text = stringResource(R.string.cliente_historial_vacio),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            turnos.forEach { conServicio ->
                val turno = conServicio.turno
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAbrirTurno(turno.id) },
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        val fecha = turno.inicio.atZone(ZoneId.systemDefault())
                        Text(
                            text = stringResource(
                                R.string.cliente_historial_fila,
                                fecha.toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                                fecha.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                                // Sale del JOIN del historial y no del catalogo vivo, para
                                // que siga visible aunque el servicio este dado de baja.
                                conServicio.servicioNombre,
                            ),
                        )
                        Text(text = stringResource(turno.estado.aNombreEstado()))
                    }
                }
            }
        }
    }
}

@androidx.annotation.StringRes
private fun com.turnero.app.domain.model.EstadoTurno.aNombreEstado(): Int = when (this) {
    com.turnero.app.domain.model.EstadoTurno.PENDIENTE -> R.string.estado_turno_pendiente
    com.turnero.app.domain.model.EstadoTurno.CONFIRMADO -> R.string.estado_turno_confirmado
    com.turnero.app.domain.model.EstadoTurno.ATENDIDO -> R.string.estado_turno_atendido
    com.turnero.app.domain.model.EstadoTurno.AUSENTE -> R.string.estado_turno_ausente
    com.turnero.app.domain.model.EstadoTurno.CANCELADO -> R.string.estado_turno_cancelado
}
@Composable
private fun DatoCliente(@StringRes etiqueta: Int, valor: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = stringResource(etiqueta),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = ANCHO_MINIMO_ETIQUETA),
        )
        Text(
            text = valor,
            style = MaterialTheme.typography.bodyLarge,
            // Un teléfono o un email escritos por el usuario pueden ser larguísimos, y con
            // `fontScale` grande se comen el ancho de la fila.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Ancho reservado a la etiqueta, para que los valores queden alineados entre filas. */
private val ANCHO_MINIMO_ETIQUETA = 96.dp

@Preview(showBackground = true)
@Composable
private fun ClienteDetalleScreenPreview() {
    TurneroTheme(dynamicColor = false) {
        ClienteDetalleScreen(
            uiState = ClienteDetalleUiState(
                cliente = Cliente(
                    id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    nombre = "Juan Pérez",
                    telefono = "+54 9 11 4321-1234",
                    email = "juan.perez@mail.com",
                    notas = "Prefiere las tardes. Viene por el control de la ortopedia.",
                    createdAt = Instant.EPOCH,
                    updatedAt = Instant.EPOCH,
                    deletedAt = null,
                ),
                cargando = false,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onVolver = {},
            onEditar = {},
            onEliminar = {},
        )
    }
}
