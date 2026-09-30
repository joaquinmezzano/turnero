package com.turnero.app.ui.screens.clientes

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
import com.turnero.app.domain.model.Cliente
import java.util.UUID

/**
 * Conecta [ClientesViewModel] con [ClientesScreen].
 *
 * Vive separado del screen para que el screen sea un pintor sin estado: acá se decide qué
 * diálogo está abierto y se traducen los ids de recurso a texto.
 *
 * Se replica el patrón de `ServiciosRoute` archivo por archivo, incluidos los diálogos
 * fuera del `Scaffold`. No se extrae un composable compartido entre las dos pantallas:
 * son dos usos, y `config/TECH_DEBT.md` ya anota que el `SnackbarHost` del `Scaffold`
 * queda detrás de los diálogos. Esa deuda se arregla una sola vez y bien, cuando
 * aparezca el primer flujo que deje un diálogo abierto durante una escritura (el slice 3),
 * no con un `Box` raíz que se haga cargo de tres pantallas a la vez.
 */
@Composable
fun ClientesRoute(
    onAbrirFicha: (UUID) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClientesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var dialogo by remember { mutableStateOf<DialogoClientes>(DialogoClientes.Ninguno) }

    val snackbarHostState = remember { SnackbarHostState() }
    // Solo `errorRes`, nunca `errorCargaRes`: este efecto *borra* lo que muestra, y el
    // error de carga no puede ser transitorio (ver `ClientesUiState`).
    val mensajeError = uiState.errorRes?.let { stringResource(it) }
    LaunchedEffect(mensajeError) {
        if (mensajeError != null) {
            snackbarHostState.showSnackbar(mensajeError)
            viewModel.onErrorMostrado()
        }
    }

    ClientesScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onConsultaCambiada = viewModel::onConsultaCambiada,
        onAbrirFicha = onAbrirFicha,
        onAgregar = { dialogo = DialogoClientes.Editor(cliente = null) },
        onEditar = { dialogo = DialogoClientes.Editor(cliente = it) },
        onEliminar = { dialogo = DialogoClientes.Borrado(cliente = it) },
        onReintentar = viewModel::reintentar,
        modifier = modifier,
    )

    // `when (val actual = dialogo)` y no `when (dialogo)`: `dialogo` es una propiedad
    // delegada por `remember` y Kotlin no puede hacer smart cast sobre ella.
    when (val actual = dialogo) {
        DialogoClientes.Ninguno -> Unit

        is DialogoClientes.Editor -> ClienteEditorDialog(
            cliente = actual.cliente,
            onConfirmar = { nombre, telefono, email, notas ->
                val original = actual.cliente
                if (original == null) {
                    viewModel.crear(nombre, telefono, email, notas)
                } else {
                    viewModel.actualizar(original.id, nombre, telefono, email, notas)
                }
                dialogo = DialogoClientes.Ninguno
            },
            onDescartar = { dialogo = DialogoClientes.Ninguno },
        )

        is DialogoClientes.Borrado -> DialogoConfirmarBorrado(
            cliente = actual.cliente,
            onConfirmar = {
                viewModel.eliminar(actual.cliente.id)
                dialogo = DialogoClientes.Ninguno
            },
            onDescartar = { dialogo = DialogoClientes.Ninguno },
        )
    }
}

@Composable
private fun DialogoConfirmarBorrado(
    cliente: Cliente,
    onConfirmar: () -> Unit,
    onDescartar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDescartar,
        title = { Text(stringResource(R.string.clientes_borrar_titulo)) },
        text = {
            Text(stringResource(R.string.clientes_borrar_cuerpo, cliente.nombre))
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
 * Diálogo abierto, como estado cerrado en vez de tres `Boolean` sueltos. Sin
 * `rememberSaveable`: `Cliente` no es `Parcelable` y el formulario no vale la pena
 * persistirlo entre rotaciones.
 */
private sealed interface DialogoClientes {
    data object Ninguno : DialogoClientes
    data class Editor(val cliente: Cliente?) : DialogoClientes
    data class Borrado(val cliente: Cliente) : DialogoClientes
}
