package com.turnero.app.ui.screens.turnos

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.turnero.app.R
import com.turnero.app.domain.model.AccionTurno
import com.turnero.app.domain.model.EstadoTurno
import com.turnero.app.domain.model.Turno
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
fun TurnoDetalleRoute(
    onVolver: () -> Unit,
    onEditar: (UUID) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TurnoDetalleViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // Sin ficha no hay snackbar: el error ya se pinta a pantalla completa (turno liberado
    // o inexistente), y un snackbar encima sería el mismo mensaje dicho dos veces.
    val mensajeError = uiState.errorRes
        ?.takeIf { uiState.turno != null }
        ?.let { stringResource(it) }
    LaunchedEffect(mensajeError) {
        if (mensajeError != null) {
            snackbarHostState.showSnackbar(mensajeError)
            viewModel.onErrorMostrado()
        }
    }
    TurnoDetalleScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onVolver = onVolver,
        onEditar = onEditar,
        onEjecutarAccion = { viewModel.ejecutarAccion(it) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TurnoDetalleScreen(
    uiState: TurnoDetalleUiState,
    snackbarHostState: SnackbarHostState,
    onVolver: () -> Unit,
    onEditar: (UUID) -> Unit,
    onEjecutarAccion: (AccionTurno) -> Unit,
    modifier: Modifier = Modifier,
) {
    val turno = uiState.turno
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.clienteNombre.ifEmpty { stringResource(R.string.turnos_detalle_titulo) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.turnos_detalle_volver_desc),
                        )
                    }
                },
                actions = {
                    if (turno != null) {
                        IconButton(onClick = { onEditar(turno.id) }) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.turnos_detalle_editar_desc),
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
            uiState.cargando -> EstadoCargando(padding)
            // `turno == null` tras cargar: o fue liberado, o no existe. Antes esto dejaba
            // el cuerpo vacío con el error solo en un snackbar que se auto-descarta.
            turno == null -> EstadoNoExiste(
                mensaje = uiState.errorRes ?: R.string.error_turno_no_existe,
                contentPadding = padding,
            )

            else -> CuerpoDetalle(
                uiState = uiState,
                turno = turno,
                onEjecutarAccion = onEjecutarAccion,
                contentPadding = padding,
            )
        }
    }
}

@Composable
private fun CuerpoDetalle(
    uiState: TurnoDetalleUiState,
    turno: Turno,
    onEjecutarAccion: (AccionTurno) -> Unit,
    contentPadding: PaddingValues,
) {
    val local = turno.inicio.atZone(ZoneId.systemDefault())
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
            // Con `fontScale` alto las notas y las acciones desbordan la altura de la
            // pantalla; sin scroll quedaban inalcanzables.
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = stringResource(R.string.turnos_detalle_estado, stringResource(turno.estado.aNombreEstado())))
        Text(text = stringResource(R.string.turnos_detalle_servicio, uiState.servicioNombre))
        Text(text = stringResource(R.string.turnos_detalle_fecha, local.toLocalDate().format(FORMATO_FECHA)))
        Text(text = stringResource(R.string.turnos_detalle_hora, local.toLocalTime().format(FORMATO_HORA)))
        Text(text = stringResource(R.string.turnos_detalle_duracion, turno.duracionMin))
        if (turno.notas.isNullOrBlank()) {
            Text(text = stringResource(R.string.turnos_detalle_sin_notas))
        } else {
            Text(text = stringResource(R.string.turnos_detalle_notas, turno.notas))
        }
        uiState.acciones.forEach { accion ->
            Button(onClick = { onEjecutarAccion(accion) }, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(accion.aStringRes()))
            }
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
private fun EstadoNoExiste(@StringRes mensaje: Int, contentPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(mensaje),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@StringRes
private fun AccionTurno.aStringRes(): Int = when (this) {
    AccionTurno.CONFIRMAR -> R.string.turnos_accion_confirmar
    AccionTurno.MARCAR_ATENDIDO -> R.string.turnos_accion_atendido
    AccionTurno.MARCAR_AUSENTE -> R.string.turnos_accion_ausente
    AccionTurno.CANCELAR -> R.string.turnos_accion_cancelar
    AccionTurno.VOLVER_A_PENDIENTE -> R.string.turnos_accion_volver_pendiente
    AccionTurno.LIBERAR -> R.string.turnos_accion_liberar
}

@StringRes
private fun EstadoTurno.aNombreEstado(): Int = when (this) {
    EstadoTurno.PENDIENTE -> R.string.estado_turno_pendiente
    EstadoTurno.CONFIRMADO -> R.string.estado_turno_confirmado
    EstadoTurno.ATENDIDO -> R.string.estado_turno_atendido
    EstadoTurno.AUSENTE -> R.string.estado_turno_ausente
    EstadoTurno.CANCELADO -> R.string.estado_turno_cancelado
}

private val FORMATO_FECHA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val FORMATO_HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")