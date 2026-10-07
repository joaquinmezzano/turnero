package com.turnero.app.ui.screens.turnos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.turnero.app.R
import com.turnero.app.domain.model.EstadoTurno
import com.turnero.app.ui.theme.contenidoSobreServicio
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
fun TurnosRoute(
    onAbrirServicios: () -> Unit,
    onAbrirTurnoDetalle: (UUID) -> Unit,
    onCrearTurno: (LocalDate, LocalTime) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TurnosViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val mensajeError = uiState.errorRes?.let { stringResource(it) }
    LaunchedEffect(mensajeError) {
        if (mensajeError != null) {
            snackbarHostState.showSnackbar(mensajeError)
            viewModel.onErrorMostrado()
        }
    }
    TurnosScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onDiaAnterior = { viewModel.irDiaAnterior() },
        onDiaSiguiente = { viewModel.irDiaSiguiente() },
        onAbrirServicios = onAbrirServicios,
        onAbrirTurnoDetalle = onAbrirTurnoDetalle,
        onCrearTurno = onCrearTurno,
        onReintentar = { viewModel.reintentar() },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TurnosScreen(
    uiState: TurnosUiState,
    snackbarHostState: SnackbarHostState,
    onDiaAnterior: () -> Unit,
    onDiaSiguiente: () -> Unit,
    onAbrirServicios: () -> Unit,
    onAbrirTurnoDetalle: (UUID) -> Unit,
    onCrearTurno: (LocalDate, LocalTime) -> Unit,
    onReintentar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuAbierto by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = uiState.fecha.format(FORMATO_FECHA)) },
                navigationIcon = {
                    IconButton(onClick = onDiaAnterior) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.turnos_anterior_desc),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onDiaSiguiente) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = stringResource(R.string.turnos_siguiente_desc),
                        )
                    }
                    Box {
                        IconButton(onClick = { menuAbierto = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.turnos_menu_desc),
                            )
                        }
                        DropdownMenu(expanded = menuAbierto, onDismissRequest = { menuAbierto = false }) {
                            DropdownMenuItem(
                                text = { Text(text = stringResource(R.string.turnos_menu_servicios)) },
                                onClick = {
                                    menuAbierto = false
                                    onAbrirServicios()
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // Los insets ya los consumio el `Scaffold` de `TurneroNavGraph` (ver su KDoc): este
        // Scaffold interno no los vuelve a pedir.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        when {
            // Mientras carga el día nuevo no se pinta la grilla del anterior con la fecha
            // nueva: el `cargando` se respeta y se muestra un indicador.
            uiState.cargando -> EstadoCargando(padding)

            // Error de lectura sin turnos: la grilla no puede caer en "15 horas libres",
            // que seria una afirmacion falsa. Va antes que la grilla y consulta
            // `errorCargaRes`, que el snackbar no borra, para que quede el cartel con
            // "Reintentar".
            uiState.errorCargaRes != null && uiState.turnos.isEmpty() ->
                EstadoError(padding, onReintentar)

            else -> GrillaTurnos(
                uiState = uiState,
                contentPadding = padding,
                onAbrirTurnoDetalle = onAbrirTurnoDetalle,
                onCrearTurno = onCrearTurno,
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

/**
 * Estado de error de carga con salida.
 *
 * El botón no es decorativo: el `catch` que llena este estado es terminal, así que sin
 * [onReintentar] la pantalla no se recupera salvo recreándola a mano. Se pinta en lugar de
 * la grilla porque una grilla vacía afirmaría "no hay turnos" y eso sería mentira.
 */
@Composable
private fun EstadoError(contentPadding: PaddingValues, onReintentar: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.turnos_error_carga),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onReintentar) {
                Text(stringResource(R.string.reintentar))
            }
        }
    }
}

@Composable
private fun GrillaTurnos(
    uiState: TurnosUiState,
    contentPadding: PaddingValues,
    onAbrirTurnoDetalle: (UUID) -> Unit,
    onCrearTurno: (LocalDate, LocalTime) -> Unit,
) {
    // `construirGrilla` fusiona las celdas: las filas cubiertas por un turno de varias
    // celdas no existen en la lista, así que nunca pueden ofrecerse como libres.
    val celdas = remember(uiState.turnos) { construirGrilla(uiState.turnos) }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(celdas, key = { it.fila }) { celda ->
            if (celda.libre) {
                CeldaLibre(
                    celda = celda,
                    fecha = uiState.fecha,
                    onCrearTurno = onCrearTurno,
                )
            } else {
                CeldaOcupada(
                    celda = celda,
                    onAbrirTurnoDetalle = onAbrirTurnoDetalle,
                )
            }
        }
    }
}

@Composable
private fun CeldaLibre(
    celda: CeldaGrilla,
    fecha: LocalDate,
    onCrearTurno: (LocalDate, LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val horaTexto = celda.hora.format(FORMATO_HORA)
    // Rótulo de la acción, no del nodo: el texto mergeado ("10:00") ya es la etiqueta, y
    // un `contentDescription` propio en el mismo nodo mergeado haría que TalkBack anuncie
    // los dos (doble lectura). El rótulo de acción reemplaza el genérico "double tap".
    val rotuloAccion = stringResource(R.string.turnos_celda_libre_desc, horaTexto)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ALTO_FILA)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClickLabel = rotuloAccion) { onCrearTurno(fecha, celda.hora) }
            .semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = stringResource(R.string.turnos_hora_libre, horaTexto),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun CeldaOcupada(
    celda: CeldaGrilla,
    onAbrirTurnoDetalle: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Los turnos que comparten fila se pintan juntos, uno al lado del otro. La altura es
    // un mínimo (N * 48dp), no una altura fija: con `fontScale` alto el texto necesita más
    // que esos 48dp y con altura fija la tarjeta lo recortaba. `heightIn(min=...)` deja
    // crecer la celda y arrastrar la grilla, así el contenido nunca se corta.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ALTO_FILA * celda.filasOcupadas),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        celda.turnos.forEach { conFila ->
            TurnoChip(
                conFila = conFila,
                onAbrirTurnoDetalle = onAbrirTurnoDetalle,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = ALTO_FILA),
            )
        }
    }
}

@Composable
private fun TurnoChip(
    conFila: TurnoConFila,
    onAbrirTurnoDetalle: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorFondo = conFila.servicioColor?.let { Color(it) }
        ?: MaterialTheme.colorScheme.surfaceVariant
    val colorContenido = if (conFila.servicioColor != null) {
        contenidoSobreServicio(conFila.servicioColor)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val horaInicio = conFila.horaInicio.format(FORMATO_HORA)
    val horaFin = conFila.horaFin.format(FORMATO_HORA)
    // Rótulo de la acción, no del nodo: el texto mergeado (horas · cliente, servicio ·
    // estado) ya es la etiqueta completa, y un `contentDescription` propio en el mismo
    // nodo mergeado haría que TalkBack anuncie los dos (doble lectura).
    val rotuloAccion = stringResource(R.string.turnos_celda_ocupada_desc, conFila.clienteNombre, horaInicio)
    Card(
        modifier = modifier
            .clickable(onClickLabel = rotuloAccion) { onAbrirTurnoDetalle(conFila.turno.id) }
            .semantics(mergeDescendants = true) {},
        colors = CardDefaults.cardColors(
            containerColor = colorFondo,
            contentColor = colorContenido,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                text = stringResource(
                    R.string.turnos_turno_ocupado,
                    horaInicio,
                    horaFin,
                    conFila.clienteNombre,
                ),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.turnos_turno_servicio_estado,
                    conFila.servicioNombre,
                    stringResource(conFila.turno.estado.aNombreEstado()),
                ),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@androidx.annotation.StringRes
private fun EstadoTurno.aNombreEstado(): Int = when (this) {
    EstadoTurno.PENDIENTE -> R.string.estado_turno_pendiente
    EstadoTurno.CONFIRMADO -> R.string.estado_turno_confirmado
    EstadoTurno.ATENDIDO -> R.string.estado_turno_atendido
    EstadoTurno.AUSENTE -> R.string.estado_turno_ausente
    EstadoTurno.CANCELADO -> R.string.estado_turno_cancelado
}

private val FORMATO_FECHA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val FORMATO_HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val ALTO_FILA = 48.dp