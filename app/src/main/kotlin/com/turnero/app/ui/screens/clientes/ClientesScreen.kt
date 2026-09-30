package com.turnero.app.ui.screens.clientes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.turnero.app.R
import com.turnero.app.domain.model.Cliente
import com.turnero.app.ui.theme.TurneroTheme
import java.time.Instant
import java.util.UUID

/**
 * Pintor de la pantalla de clientes. Sin estado propio: recibe el `ClientesUiState` del
 * ViewModel y callbacks. [ClientesRoute] es quien los conecta.
 *
 * `contentWindowInsets = WindowInsets(0, 0, 0, 0)` porque los insets ya los consumió el
 * `Scaffold` de `TurneroNavGraph`, que además es el que pinta la barra inferior. Si esta
 * pantalla los pidiera otra vez, la barra de estado se contaría dos veces y el contenido
 * quedaría debajo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientesScreen(
    uiState: ClientesUiState,
    snackbarHostState: SnackbarHostState,
    onConsultaCambiada: (String) -> Unit,
    onAbrirFicha: (UUID) -> Unit,
    onAgregar: () -> Unit,
    onEditar: (Cliente) -> Unit,
    onEliminar: (Cliente) -> Unit,
    modifier: Modifier = Modifier,
    onReintentar: () -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.clientes_title)) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAgregar) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.clientes_agregar_desc),
                )
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding()),
        ) {
            // El buscador se oculta cuando no hay nada que buscar: en la lista vacía de
            // entrada un campo de búsqueda es ruido, y en la de "sin resultados" tiene que
            // quedar a la vista porque es lo que hay que corregir.
            if (uiState.clientes.isNotEmpty() || uiState.consulta.isNotBlank()) {
                CampoBusqueda(
                    consulta = uiState.consulta,
                    onConsultaCambiada = onConsultaCambiada,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            when {
                uiState.cargando && uiState.clientes.isEmpty() -> EstadoCargando()
                // Un error de lectura con la lista vacía NO es "no tenés clientes":
                // decirlo sería una afirmación falsa. Va antes que la lista vacía y que
                // "sin resultados", y consulta `errorCargaRes`, que no lo borra el
                // snackbar: si consultara `errorRes` se caería acá solo y mentiría.
                uiState.errorCargaRes != null && uiState.clientes.isEmpty() ->
                    EstadoError(onReintentar)
                // "Sin clientes" y "la búsqueda no dio nada" son dos mensajes distintos:
                // el primero se resuelve dando de alta, el segundo escribiendo otra cosa.
                uiState.clientes.isEmpty() && uiState.consulta.isNotBlank() ->
                    EstadoSinResultados(consulta = uiState.consulta)

                uiState.clientes.isEmpty() -> EstadoVacio(onAgregar)
                else -> ListaClientes(
                    clientes = uiState.clientes,
                    bottomPadding = contentPadding.calculateBottomPadding(),
                    onAbrirFicha = onAbrirFicha,
                    onEditar = onEditar,
                    onEliminar = onEliminar,
                )
            }
        }
    }
}

/**
 * Buscador.
 *
 * Un `OutlinedTextField` y no un `SearchBar` de M3 a propósito: el `SearchBar` de Material 3
 * trae su propia capa de estado, con campo expandido y overlay, y para un filtro de lista
 * ya montado en la pantalla es un `TextField` con un ícono de lupa. Cuando la pantalla
 * crezca y la búsqueda se vuelva una función en sí misma, el `SearchBar` tiene sentido y
 * el cambio es local a este composable.
 */
@Composable
private fun CampoBusqueda(
    consulta: String,
    onConsultaCambiada: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = consulta,
        onValueChange = onConsultaCambiada,
        label = { Text(stringResource(R.string.clientes_buscar_label)) },
        leadingIcon = {
            // Decorativo: el campo ya se anuncia con su etiqueta ("Buscar por nombre") y
            // un ícono sin nombre sólo suma ruido al principio del anuncio.
            Icon(imageVector = Icons.Filled.Search, contentDescription = null)
        },
        trailingIcon = {
            if (consulta.isNotEmpty()) {
                IconButton(onClick = { onConsultaCambiada("") }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.clientes_buscar_limpiar_desc),
                    )
                }
            }
        },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Pantalla de error de carga, con salida.
 *
 * El botón no es decorativo: el `catch` que llena este estado es terminal, así que sin
 * [onReintentar] el error no tiene más salida que matar la pantalla y esperar que el
 * usuario la reconstruya a mano.
 */
@Composable
private fun EstadoError(onReintentar: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.clientes_error_carga),
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
private fun EstadoCargando() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EstadoVacio(onAgregar: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.clientes_vacio_titulo),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.clientes_vacio_cuerpo),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        TextButton(onClick = onAgregar) {
            Text(
                text = stringResource(R.string.clientes_vacio_accion),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun EstadoSinResultados(consulta: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.clientes_sin_resultados_titulo),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.clientes_sin_resultados_cuerpo, consulta),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            // La consulta la escribió el usuario y puede ser larga: sin tope se parte la
            // línea de forma fea con cualquier nombre raro.
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun ListaClientes(
    clientes: List<Cliente>,
    bottomPadding: Dp,
    onAbrirFicha: (UUID) -> Unit,
    onEditar: (Cliente) -> Unit,
    onEliminar: (Cliente) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 8.dp,
            // 88.dp es lo que hay que dejar libre para el FAB, que mide 56.dp y va
            // separado 16.dp del borde.
            bottom = bottomPadding + 88.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // `key` es obligatoria: sin ella Compose reutiliza las celdas por posición y al
        // filtrar la lista —que reordena y cambia el largo— el contenido se mezcla entre
        // items.
        items(clientes, key = { it.id }) { cliente ->
            ClienteCard(
                cliente = cliente,
                onAbrirFicha = { onAbrirFicha(cliente.id) },
                onEditar = { onEditar(cliente) },
                onEliminar = { onEliminar(cliente) },
            )
        }
    }
}

@Composable
private fun ClienteCard(
    cliente: Cliente,
    onAbrirFicha: () -> Unit,
    onEditar: () -> Unit,
    onEliminar: () -> Unit,
) {
    // La Card NO lleva `Modifier.clickable`: fusionaría la semántica de los descendientes y
    // los dos `IconButton` quedarían metidos dentro de un único nodo, con una sola acción.
    // TalkBack anunciaría un nodo por fila y "Eliminar" sería inalcanzable. Por eso el
    // `clickable` va en la columna del texto, que es **hermana** de los botones y no su
    // ancestro: tres nodos talkback por fila, con tres acciones.
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val descripcionFicha = stringResource(R.string.clientes_ver_ficha_desc, cliente.nombre)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = ALTO_MINIMO_TACTIL)
                    // `mergeDescendants = true` con `contentDescription` propio: sin el
                    // merge, TalkBack lee el nombre, después el teléfono y después otra vez
                    // la descripción de la fila. Con el merge, los `Text` se vuelven la
                    // etiqueta del botón y se anuncia una sola vez.
                    .clickable(onClick = onAbrirFicha)
                    .semantics(mergeDescendants = true) { contentDescription = descripcionFicha }
                    .padding(vertical = 4.dp),
            ) {
                Text(
                    text = cliente.nombre,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = contactoLegible(cliente),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onEditar) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    // Con el nombre del cliente, no genérico: si el `contentDescription`
                    // fuera "Editar", las cuarenta filas sonarían idénticas y el usuario
                    // no sabría sobre cuál está parado.
                    contentDescription = stringResource(R.string.clientes_editar_desc, cliente.nombre),
                )
            }
            IconButton(onClick = onEliminar) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(
                        R.string.clientes_eliminar_desc,
                        cliente.nombre,
                    ),
                )
            }
        }
    }
}

/**
 * Una sola línea de contacto en la lista: el teléfono si hay, si no el email, si no un
 * aviso.
 *
 * No muestra los dos a la vez porque con un `fontScale` de 2 la fila se vuelve un bloque
 * de texto de cuatro líneas. Los dos se ven enteros en la ficha.
 */
@Composable
private fun contactoLegible(cliente: Cliente): String = when {
    !cliente.telefono.isNullOrBlank() -> cliente.telefono
    !cliente.email.isNullOrBlank() -> cliente.email
    else -> stringResource(R.string.clientes_sin_contacto)
}

/** Mínimo táctil del área que abre la ficha, así el blanco es fácil de acertar. */
private val ALTO_MINIMO_TACTIL = 48.dp

@Preview(showBackground = true)
@Composable
private fun ClientesScreenPreview() {
    val ahora = Instant.EPOCH
    TurneroTheme(dynamicColor = false) {
        ClientesScreen(
            uiState = ClientesUiState(
                clientes = listOf(
                    Cliente(
                        id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        nombre = "Juan Pérez",
                        telefono = "+54 9 11 4321-1234",
                        email = "juan.perez@mail.com",
                        notas = null,
                        createdAt = ahora,
                        updatedAt = ahora,
                        deletedAt = null,
                    ),
                    Cliente(
                        id = UUID.fromString("00000000-0000-0000-0000-000000000002"),
                        nombre = "Niño con un nombre larguísimo que no entra en la fila",
                        telefono = null,
                        email = null,
                        notas = null,
                        createdAt = ahora,
                        updatedAt = ahora,
                        deletedAt = null,
                    ),
                ),
                cargando = false,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onConsultaCambiada = {},
            onAbrirFicha = {},
            onAgregar = {},
            onReintentar = {},
            onEditar = {},
            onEliminar = {},
        )
    }
}
