package com.turnero.app.ui.screens.clientes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.turnero.app.R
import com.turnero.app.domain.model.Cliente
import com.turnero.app.ui.theme.TurneroTheme
import java.time.Instant
import java.util.UUID

/**
 * Alta / edición de un cliente.
 *
 * El estado de los campos vive acá con `remember` y no en el ViewModel a propósito: es
 * estado efímero de un formulario abierto. La búsqueda, en cambio, sí vive en el
 * `UiState` (ver `ClientesUiState`), porque decide qué se ve en la lista y tiene que
 * sobrevivir a un cambio de configuración.
 *
 * Las claves `remember(cliente?.id)` reinician el formulario si se abre el diálogo con
 * otro cliente. La clave es el `id` y no el `Cliente` entero porque `remember` compara con
 * `equals` estructural, y `Cliente` incluye `updatedAt` e `Instant`: con la clave sobre el
 * objeto, guardar reemitiría el flujo con un `updatedAt` nuevo y el formulario se
 * resetearía solo, borrando lo que el usuario escribió.
 *
 * **La validación es espejo de la del use case** para dar feedback inmediato sin round
 * trip; la autoritativa sigue siendo `domain`, y si esa falla el error vuelve por
 * snackbar. Los patrones son los mismos de `ValidacionCliente.kt`, a propósito: si
 * divergen, el formulario acepta algo que la base va a rechazar y el usuario ve un error
 * después de escribir.
 */
@Composable
fun ClienteEditorDialog(
    cliente: Cliente?,
    onConfirmar: (nombre: String, telefono: String?, email: String?, notas: String?) -> Unit,
    onDescartar: () -> Unit,
) {
    // `rememberSaveable` y no `remember`: son cuatro `String`, y con `remember` una
    // rotación, un cambio de tamaño de la barra de tareas o el gesto de atrás del teclado
    // tiraban el formulario a medio completar. Que `Cliente` no sea `Parcelable` no
    // importa acá, porque no se guarda el objeto sino los textos que el usuario escribió;
    // el flag `dialogo` de `ClientesRoute` sí sigue en `remember` por lo mismo.
    var nombre by rememberSaveable(cliente?.id) { mutableStateOf(cliente?.nombre.orEmpty()) }
    var telefono by rememberSaveable(cliente?.id) { mutableStateOf(cliente?.telefono.orEmpty()) }
    var email by rememberSaveable(cliente?.id) { mutableStateOf(cliente?.email.orEmpty()) }
    var notas by rememberSaveable(cliente?.id) { mutableStateOf(cliente?.notas.orEmpty()) }
    var intentarGuardar by rememberSaveable(cliente?.id) { mutableStateOf(false) }

    val nombreValido = nombre.isNotBlank()
    val telefonoValido = telefono.isBlank() || FORMATO_TELEFONO.matches(telefono)
    val emailValido = email.isBlank() || FORMATO_EMAIL.matches(email.trim())

    AlertDialog(
        onDismissRequest = onDescartar,
        title = {
            Text(
                stringResource(
                    if (cliente == null) R.string.cliente_dialogo_nuevo
                    else R.string.cliente_dialogo_editar,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text(stringResource(R.string.servicio_nombre_label)) },
                    isError = intentarGuardar && !nombreValido,
                    supportingText = {
                        if (intentarGuardar && !nombreValido) {
                            Text(stringResource(R.string.servicio_nombre_error))
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = telefono,
                    onValueChange = { telefono = it },
                    label = { Text(stringResource(R.string.cliente_telefono_label)) },
                    isError = intentarGuardar && !telefonoValido,
                    supportingText = {
                        Text(
                            if (intentarGuardar && !telefonoValido) {
                                stringResource(R.string.cliente_telefono_error)
                            } else {
                                stringResource(R.string.cliente_telefono_ayuda)
                            },
                        )
                    },
                    // `Phone` y no `Number`: el teclado numérico de Android no trae `+`,
                    // `(` ni `)` en muchos idiomas, y el usuario los necesita para escribir
                    // el prefijo internacional.
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.cliente_email_label)) },
                    isError = intentarGuardar && !emailValido,
                    supportingText = {
                        Text(
                            if (intentarGuardar && !emailValido) {
                                stringResource(R.string.cliente_email_error)
                            } else {
                                stringResource(R.string.cliente_email_ayuda)
                            },
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notas,
                    onValueChange = { notas = it },
                    label = { Text(stringResource(R.string.cliente_notas_label)) },
                    supportingText = { Text(stringResource(R.string.cliente_notas_ayuda)) },
                    // Las notas son el único campo de texto largo de la app hasta ahora.
                    // Sin alto mínimo el `AlertDialog` lo aplana a una línea y el usuario
                    // escribe a ciegas.
                    minLines = MULTILINEA_MINIMO,
                    maxLines = MULTILINEA_MAXIMO,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    intentarGuardar = true
                    if (nombreValido && telefonoValido && emailValido) {
                        onConfirmar(
                            nombre.trim(),
                            telefono,
                            email,
                            notas,
                        )
                    }
                },
            ) {
                Text(stringResource(R.string.servicio_guardar))
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
 * Espejo de las reglas de `ValidacionCliente.kt` en `domain`, para poder dar feedback sin
 * round trip. Se **repiten** y no se comparten: las dos capas compilan por separado y
 * `domain` no puede depender de Compose. El riesgo de que diverjan es real y por eso los
 * patrones son los mismos; un test de `ValidacionCliente` es el que avisa si alguien
 * cambia uno solo.
 */
private val FORMATO_EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

private val FORMATO_TELEFONO = Regex("^[0-9+()\\-\\s]+$")

private const val MULTILINEA_MINIMO = 3
private const val MULTILINEA_MAXIMO = 8

@Preview(showBackground = true)
@Composable
private fun ClienteEditorDialogPreview() {
    TurneroTheme(dynamicColor = false) {
        ClienteEditorDialog(
            cliente = Cliente(
                id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                nombre = "Juan Pérez",
                telefono = "+54 9 11 4321-1234",
                email = "juan.perez@mail.com",
                notas = "Prefiere las tardes.",
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
                deletedAt = null,
            ),
            onConfirmar = { _, _, _, _ -> },
            onDescartar = {},
        )
    }
}
