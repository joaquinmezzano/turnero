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

/**
 * Conecta [ClienteDetalleViewModel] con [ClienteDetalleScreen].
 *
 * El `clienteId` **no** se pasa por parámetro: viaja en el argumento de la ruta y lo lee
 * el ViewModel del `SavedStateHandle` (ver su KDoc). Acá solo se traduce el evento de
 * "se eliminó" a una vuelta atrás, que es navegación y por lo tanto es del `Route`.
 */
@Composable
fun ClienteDetalleRoute(
    onVolver: () -> Unit,
    onCreateTurno: (java.util.UUID) -> Unit = {},
    onAbrirTurno: (java.util.UUID) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ClienteDetalleViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var dialogo by remember { mutableStateOf<DialogoDetalle>(DialogoDetalle.Ninguno) }

    // Se declara antes del contenido para que el LaunchedEffect del evento de borrado
    // exista ya montado: si el cliente se elimina, el `onVolver` tiene que ejecutarse
    // aunque en ese momento no haya nada que pintar.
    LaunchedEffect(viewModel) {
        viewModel.eliminadoFlow.collect { onVolver() }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    // El error solo va al snackbar si hay ficha en pantalla. Con `cliente == null` el
    // error ya se pinta a pantalla completa, y un snackbar encima sería el mismo mensaje
    // dicho dos veces.
    val mensajeError = uiState.errorRes
        ?.takeIf { uiState.cliente != null }
        ?.let { stringResource(it) }
    LaunchedEffect(mensajeError) {
        if (mensajeError != null) {
            snackbarHostState.showSnackbar(mensajeError)
            viewModel.onErrorMostrado()
        }
    }

    ClienteDetalleScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onVolver = onVolver,
        onEditar = { dialogo = DialogoDetalle.Editor },
        onEliminar = { dialogo = DialogoDetalle.Borrado },
        onCreateTurno = { uiState.cliente?.let { onCreateTurno(it.id) } },
        onAbrirTurno = onAbrirTurno,
        modifier = modifier,
    )

    when (val actual = dialogo) {
        DialogoDetalle.Ninguno -> Unit

        DialogoDetalle.Editor -> uiState.cliente?.let { cliente ->
            ClienteEditorDialog(
                cliente = cliente,
                onConfirmar = { nombre, telefono, email, notas ->
                    viewModel.actualizar(nombre, telefono, email, notas)
                    dialogo = DialogoDetalle.Ninguno
                },
                onDescartar = { dialogo = DialogoDetalle.Ninguno },
            )
        }

        DialogoDetalle.Borrado -> uiState.cliente?.let { cliente ->
            DialogoConfirmarBorrado(
                cliente = cliente,
                onConfirmar = {
                    viewModel.eliminar()
                    dialogo = DialogoDetalle.Ninguno
                },
                onDescartar = { dialogo = DialogoDetalle.Ninguno },
            )
        }
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
 * La ficha tiene un solo cliente y no hay forma de cambiar de cliente sin volver atrás,
 * así que no hace falta guardar el `Cliente` en el estado del diálogo: con dos `data
 * object` alcanza. Igual que en `ClientesRoute`, sin `rememberSaveable`.
 */
private sealed interface DialogoDetalle {
    data object Ninguno : DialogoDetalle
    data object Editor : DialogoDetalle
    data object Borrado : DialogoDetalle
}
