package com.turnero.app.ui.screens.servicios

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.turnero.app.R
import com.turnero.app.domain.model.Servicio
import com.turnero.app.ui.theme.ServicioColors
import com.turnero.app.ui.theme.TurneroTheme
import java.time.Instant
import java.util.UUID
import kotlin.math.absoluteValue
import kotlin.math.roundToLong

/**
 * Alta / edicion de un servicio.
 *
 * El estado de los campos vive aca con `remember` y no en el ViewModel a proposito: es
 * estado efimero de un formulario abierto. AGENTS.md distingue el estado que viene de
 * la base (que va en el `UiState`) del estado de UI, y este es el segundo. Las claves
 * `remember(servicio?.id)` reinician el formulario si se abre el dialogo con otro
 * servicio.
 *
 * La clave es el `id` y no el `Servicio` entero: `remember` compara con `equals`
 * estructural, y `Servicio` incluye `updatedAt` e `Instant`. Con la clave sobre el
 * objeto, guardar reemitiria el Flow con un `updatedAt` nuevo y el formulario se
 * resetearia solo, borrando lo que el usuario escribio.
 *
 * La validacion es espejo de la del use case para dar feedback inmediato; la
 * autoritativa sigue siendo `domain`, y si esa falla el error vuelve por snackbar.
 */
@Composable
fun ServicioEditorDialog(
    servicio: Servicio?,
    onConfirmar: (nombre: String, duracionMin: Int, precioCentavos: Long?, color: Int) -> Unit,
    onDescartar: () -> Unit,
) {
    var nombre by remember(servicio?.id) { mutableStateOf(servicio?.nombre.orEmpty()) }
    var duracion by remember(servicio?.id) {
        mutableStateOf(servicio?.duracionMin?.toString().orEmpty())
    }
    var precio by remember(servicio?.id) {
        mutableStateOf(servicio?.precioCentavos?.aTextoPrecio().orEmpty())
    }
    var color by remember(servicio?.id) {
        mutableStateOf(servicio?.color ?: ServicioColors.first().toArgb())
    }
    var intentarGuardar by remember(servicio?.id) { mutableStateOf(false) }
    val duracionMin = duracion.toIntOrNull()
    val precioCentavos = precio.aCentavos()
    val precioValido = precio.isBlank() || (precioCentavos != null && precioCentavos >= 0L)
    val nombreValido = nombre.isNotBlank()
    val duracionValida = duracionMin != null && duracionMin > 0

    AlertDialog(
        onDismissRequest = onDescartar,
        title = {
            Text(
                stringResource(
                    if (servicio == null) R.string.servicio_dialogo_nuevo
                    else R.string.servicio_dialogo_editar,
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
                    value = duracion,
                    onValueChange = { duracion = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.servicio_duracion_label)) },
                    isError = intentarGuardar && !duracionValida,
                    supportingText = {
                        if (intentarGuardar && !duracionValida) {
                            Text(stringResource(R.string.servicio_duracion_error))
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = precio,
                    onValueChange = { precio = it },
                    label = { Text(stringResource(R.string.servicio_precio_label)) },
                    isError = intentarGuardar && !precioValido,
                    supportingText = {
                        Text(
                            if (intentarGuardar && !precioValido) {
                                stringResource(R.string.servicio_precio_error)
                            } else {
                                stringResource(R.string.servicio_precio_ayuda)
                            },
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.servicio_color_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                SelectorColor(colorSeleccionado = color, onColorSeleccionado = { color = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    intentarGuardar = true
                    validarFormulario(nombre, duracion, precio, color)?.let { datos ->
                        onConfirmar(datos.nombre, datos.duracionMin, datos.precioCentavos, datos.color)
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
 * La seleccion se marca con el grosor del borde, no con un check superpuesto: el check
 * habria que teñirlo de un color fijo para contrastar contra un tono medio, y cualquier
 * color fijo esta prohibido aca.
 *
 * El borde usa `outline` y `onSurface`, los dos tokens de M3 pensados para contraste
 * contra superficies, NO `surfaceVariant`: ese token es una superficie, y contra el gris
 * azulado de la paleta daba 1.24:1 en tema oscuro, o sea un contorno invisible.
 *
 * Ojo igual: ninguno de los dos garantiza 3:1 contra los ocho tonos a la vez, porque
 * los tonos son intermedios por diseno (sirven para tema claro y oscuro). Por eso el
 * swatch lleva `contentDescription`: el color nunca es el unico portador de informacion.
 *
 * Los swatches miden 48dp (el minimo tactil) y se reparten en filas: ocho en una sola
 * fila no entran en el ancho de un `AlertDialog` sin achicarlos por debajo del minimo.
 */
@Composable
private fun SelectorColor(colorSeleccionado: Int, onColorSeleccionado: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ServicioColors
            .mapIndexed { indice, color -> IndexedValue(indice, color) }
            .chunked(COLUMNAS_POR_FILA)
            .forEach { fila ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    fila.forEach { (indice, color) ->
                        val argb = color.toArgb()
                        val seleccionado = argb == colorSeleccionado
                        val descripcion = stringResource(
                            if (seleccionado) R.string.servicio_color_seleccionado
                            else R.string.servicio_color_opcion,
                            indice + 1,
                        )
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (seleccionado) 3.dp else 1.dp,
                                    color = if (seleccionado) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                    shape = CircleShape,
                                )
                                .clip(CircleShape)
                                .clickable { onColorSeleccionado(argb) }
                                .semantics { contentDescription = descripcion },
                        )
                    }
                }
            }
    }
}

private const val COLUMNAS_POR_FILA = 4

/** Datos del formulario ya validados, listos para mandarse al use case. */
private data class DatosServicio(
    val nombre: String,
    val duracionMin: Int,
    val precioCentavos: Long?,
    val color: Int,
)

/**
 * Espejo de `validarServicio` en `domain`, para poder dar feedback sin round trip.
 * Devuelve `null` si falta completar o corregir algo; el use case sigue siendo la
 * autoridad y de todos modos devuelve `Result`.
 */
private fun validarFormulario(
    nombre: String,
    duracion: String,
    precio: String,
    color: Int,
): DatosServicio? {
    val minutos = duracion.toIntOrNull() ?: return null
    val centavos = precio.aCentavos()
    val precioOk = precio.isBlank() || (centavos != null && centavos >= 0L)
    return if (nombre.isNotBlank() && minutos > 0 && precioOk) {
        DatosServicio(nombre = nombre.trim(), duracionMin = minutos, precioCentavos = centavos, color = color)
    } else {
        null
    }
}

/**
 * Sin precio el campo queda vacio y se interpreta como `null`, no como 0: "a consultar"
 * es un estado valido del modelo (`precioCentavos: Long?`).
 */
private fun String.aCentavos(): Long? {
    if (isBlank()) return null
    val valor = trim().replace(',', '.').toDoubleOrNull() ?: return null
    return (valor * 100).roundToLong()
}

/** Inversa de `aCentavos`, para cargar el campo al editar. */
private fun Long.aTextoPrecio(): String {
    val absoluto = absoluteValue
    val resto = absoluto % 100
    val unidades = absoluto / 100
    return if (resto == 0L) {
        unidades.toString()
    } else {
        "$unidades,${resto.toString().padStart(2, '0')}"
    }
}

@Preview(showBackground = true)
@Composable
private fun ServicioEditorDialogPreview() {
    TurneroTheme(dynamicColor = false) {
        ServicioEditorDialog(
            servicio = Servicio(
                id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                nombre = "Corte de pelo",
                duracionMin = 45,
                precioCentavos = 8_000,
                color = ServicioColors[0].toArgb(),
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
                deletedAt = null,
            ),
            onConfirmar = { _, _, _, _ -> },
            onDescartar = {},
        )
    }
}
