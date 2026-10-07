package com.turnero.app.ui.screens.turnos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.turnero.app.R
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
fun TurnoEditorRoute(
    onVolver: () -> Unit,
    onGuardado: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TurnoEditorViewModel = hiltViewModel(),
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
    LaunchedEffect(Unit) {
        viewModel.guardadoFlow.collect { onGuardado() }
    }
    TurnoEditorScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onVolver = onVolver,
        onGuardar = { viewModel.guardar() },
        onDiaAnterior = { viewModel.cambiarFecha(uiState.fecha.minusDays(1)) },
        onDiaSiguiente = { viewModel.cambiarFecha(uiState.fecha.plusDays(1)) },
        onCambiarHora = { viewModel.cambiarHora(it) },
        onCambiarCliente = { viewModel.actualizarCampos(clienteId = it) },
        onCambiarServicio = { viewModel.actualizarCampos(servicioId = it) },
        onCambiarNotas = { viewModel.actualizarCampos(notas = it) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TurnoEditorScreen(
    uiState: TurnoEditorUiState,
    snackbarHostState: SnackbarHostState,
    onVolver: () -> Unit,
    onGuardar: () -> Unit,
    onDiaAnterior: () -> Unit,
    onDiaSiguiente: () -> Unit,
    onCambiarHora: (java.time.LocalTime) -> Unit,
    onCambiarCliente: (UUID) -> Unit,
    onCambiarServicio: (UUID) -> Unit,
    onCambiarNotas: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var clienteExpandido by remember { mutableStateOf(false) }
    var servicioExpandido by remember { mutableStateOf(false) }
    var horaExpandida by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(
                        if (uiState.turnoId == null) R.string.turnos_editor_nuevo else R.string.turnos_editor_editar,
                    ))
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.turnos_editor_volver_desc),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // Los insets ya los consumio el `Scaffold` de `TurneroNavGraph` (ver su KDoc): este
        // Scaffold interno no los vuelve a pedir.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Fecha: siempre navegable (‹ ›). La hora queda anclada a la hora exacta.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = onDiaAnterior) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.turnos_anterior_desc),
                    )
                }
                Text(
                    text = uiState.fecha.format(FORMATO_FECHA),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDiaSiguiente) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.turnos_siguiente_desc),
                    )
                }
            }

            // Hora: selector de las 15 horas agendables (08:00 a 22:00, en punto).
            // La hora es la unidad de reserva: nunca se deja escribir 10:37.
            ExposedDropdownMenuBox(
                expanded = horaExpandida,
                onExpandedChange = { horaExpandida = it },
            ) {
                OutlinedTextField(
                    value = uiState.hora.format(FORMATO_HORA),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.turnos_editor_hora)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = horaExpandida) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = horaExpandida,
                    onDismissRequest = { horaExpandida = false },
                ) {
                    uiState.horasDisponibles.forEach { hora ->
                        DropdownMenuItem(
                            text = { Text(hora.format(FORMATO_HORA)) },
                            onClick = {
                                onCambiarHora(hora)
                                horaExpandida = false
                            },
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = clienteExpandido,
                onExpandedChange = { clienteExpandido = it },
            ) {
                OutlinedTextField(
                    value = uiState.clientes.find { it.id == uiState.clienteId }?.nombre.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.turnos_editor_cliente)) },
                    placeholder = { Text(stringResource(R.string.turnos_editor_seleccionar_cliente)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = clienteExpandido) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = clienteExpandido,
                    onDismissRequest = { clienteExpandido = false },
                ) {
                    uiState.clientes.forEach { cliente ->
                        DropdownMenuItem(
                            text = { Text(cliente.nombre) },
                            onClick = {
                                onCambiarCliente(cliente.id)
                                clienteExpandido = false
                            },
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = servicioExpandido,
                onExpandedChange = { servicioExpandido = it },
            ) {
                OutlinedTextField(
                    value = uiState.servicios.find { it.id == uiState.servicioId }?.nombre.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.turnos_editor_servicio)) },
                    placeholder = { Text(stringResource(R.string.turnos_editor_seleccionar_servicio)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = servicioExpandido) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = servicioExpandido,
                    onDismissRequest = { servicioExpandido = false },
                ) {
                    uiState.servicios.forEach { servicio ->
                        DropdownMenuItem(
                            text = { Text(servicio.nombre) },
                            onClick = {
                                onCambiarServicio(servicio.id)
                                servicioExpandido = false
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = uiState.notas,
                onValueChange = onCambiarNotas,
                label = { Text(stringResource(R.string.turnos_editor_notas)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = onGuardar,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.clienteId != null && uiState.servicioId != null,
            ) {
                Text(stringResource(R.string.turnos_editor_guardar))
            }
        }
    }
}

// Constantes de formato, igual que en las otras pantallas: construirlas en cada
// recomposición es trabajo al pedo. La deuda de localización de estos patrones está
// registrada en `config/TECH_DEBT.md` (Slice 3+ — Turnos).
private val FORMATO_FECHA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val FORMATO_HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
