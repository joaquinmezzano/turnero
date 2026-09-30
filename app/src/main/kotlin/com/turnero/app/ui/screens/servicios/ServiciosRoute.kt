package com.turnero.app.ui.screens.servicios

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.turnero.app.R
import com.turnero.app.domain.model.Servicio

/**
 * Conecta [ServiciosViewModel] con [ServiciosScreen].
 *
 * Vive separado del screen para que el screen sea un pintor sin estado: aca se decide
 * que dialogo esta abierto y se traducen los ids de recurso a texto.
 */
@Composable
fun ServiciosRoute(
    modifier: Modifier = Modifier,
    viewModel: ServiciosViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var dialogo by remember { mutableStateOf<DialogoServicios>(DialogoServicios.Ninguno) }

    val snackbarHostState = remember { SnackbarHostState() }
    val mensajeError = uiState.errorRes?.let { stringResource(it) }
    LaunchedEffect(mensajeError) {
        if (mensajeError != null) {
            snackbarHostState.showSnackbar(mensajeError)
            viewModel.onErrorMostrado()
        }
    }

    ServiciosScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onReintentar = viewModel::reintentar,
        onAgregar = { dialogo = DialogoServicios.Editor(servicio = null) },
        onEditar = { dialogo = DialogoServicios.Editor(servicio = it) },
        onEliminar = { dialogo = DialogoServicios.Borrado(servicio = it) },
        modifier = modifier,
    )

    // `when (val actual = dialogo)` y no `when (dialogo)`: `dialogo` es una propiedad
    // delegada por `remember` y Kotlin no puede hacer smart cast sobre ella.
    when (val actual = dialogo) {
        DialogoServicios.Ninguno -> Unit

        is DialogoServicios.Editor -> ServicioEditorDialog(
            servicio = actual.servicio,
            onConfirmar = { nombre, duracionMin, precioCentavos, color ->
                val original = actual.servicio
                if (original == null) {
                    viewModel.crear(nombre, duracionMin, precioCentavos, color)
                } else {
                    viewModel.actualizar(original.id, nombre, duracionMin, precioCentavos, color)
                }
                dialogo = DialogoServicios.Ninguno
            },
            onDescartar = { dialogo = DialogoServicios.Ninguno },
        )

        is DialogoServicios.Borrado -> DialogoConfirmarBorrado(
            servicio = actual.servicio,
            onConfirmar = {
                viewModel.eliminar(actual.servicio.id)
                dialogo = DialogoServicios.Ninguno
            },
            onDescartar = { dialogo = DialogoServicios.Ninguno },
        )
    }
}

@Composable
private fun DialogoConfirmarBorrado(
    servicio: Servicio,
    onConfirmar: () -> Unit,
    onDescartar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDescartar,
        title = { Text(stringResource(R.string.servicios_borrar_titulo)) },
        text = {
            Text(stringResource(R.string.servicios_borrar_cuerpo, servicio.nombre))
        },
        confirmButton = {
            TextButton(onClick = onConfirmar) {
                Text(stringResource(R.string.servicio_eliminar))
            }
        },
        dismissButton = {
            TextButton(onClick = onDescartar) {
                Text(stringResource(R.string.servicio_cancelar))
            }
        },
    )
}

/**
 * Dialogo abierto, como estado cerrado en vez de tres `Boolean` sueltos. Sin
 * `rememberSaveable`: `Servicio` no es Parcelable y el formulario no vale la pena
 * persistirlo entre rotaciones.
 */
private sealed interface DialogoServicios {
    data object Ninguno : DialogoServicios
    data class Editor(val servicio: Servicio?) : DialogoServicios
    data class Borrado(val servicio: Servicio) : DialogoServicios
}
