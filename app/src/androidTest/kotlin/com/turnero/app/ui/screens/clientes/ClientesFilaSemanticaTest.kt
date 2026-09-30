package com.turnero.app.ui.screens.clientes

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.turnero.app.R
import com.turnero.app.domain.model.Cliente
import com.turnero.app.ui.theme.TurneroTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

/**
 * Regresión de accesibilidad de la fila de `ClientesScreen`.
 *
 * **Qué se estaba rompiendo.** La `Card` de cada fila llevaba `Modifier.clickable`, y
 * `clickable` fusiona la semántica de los descendientes: los dos `IconButton` de editar y
 * eliminar quedaban **dentro de un único nodo**, junto con el texto y con una sola acción
 * —la de abrir la ficha—. TalkBack anunciaba un nodo por fila, "Eliminar" era
 * inalcanzable, y `onNodeWithContentDescription("Eliminar Ana").performClick()` disparaba
 * la navegación a la ficha. El arreglo fue dejar el `clickable` en la columna del texto, que
 * es **hermana** de los botones y no su ancestro, así que la fila expone tres nodos con
 * tres acciones.
 *
 * Estos tests miran el **árbol de semántica fusionado**, que es el que ve TalkBack, y no
 * una lista de nodos cualquiera. Por eso lo que se afirma no es "existe un nodo con esta
 * descripción" sino el tamaño de la lista de `ContentDescription` del nodo encontrado: si
 * las tres acciones volvieran a estar fusionadas, el nodo que matchea "Eliminar Ana" llevaría
 * las tres descripciones y una sola acción.
 *
 * `createComposeRule()` y no `createAndroidComposeRule<MainActivity>()`: esto no prueba
 * navegación ni Hilt, prueba un composable sin estado con su `UiState` a mano.
 */
@RunWith(AndroidJUnit4::class)
class ClientesFilaSemanticaTest {

    @get:Rule
    val compose = createComposeRule()

    private val id = UUID.fromString("c3e5f7a9-4b6c-4d8e-9fa0-1b2c3d4e5f03")
    private val nombre = "Pérez, Ana"

    private val fichasAbiertas = mutableListOf<UUID>()
    private val editados = mutableListOf<Cliente>()
    private val eliminados = mutableListOf<Cliente>()

    private val cliente = Cliente(
        id = id,
        nombre = nombre,
        telefono = "+54 11 5555-0100",
        email = "ana@example.com",
        notas = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        deletedAt = null,
    )

    // Las descripciones salen de los strings reales, no de literales: si el `contentDescription`
    // cambia de texto, el test tiene que seguir siendo el que encuentra el botón.
    private val descripcionFicha: String
        get() = texto(R.string.clientes_ver_ficha_desc, nombre)

    private val descripcionEditar: String
        get() = texto(R.string.clientes_editar_desc, nombre)

    private val descripcionEliminar: String
        get() = texto(R.string.clientes_eliminar_desc, nombre)

    // ------------------------------------------------------------------ un nodo por acción

    @Test
    fun cadaAccionDeLaFilaEsUnNodoSemanticoPropio() {
        mostrarFila()

        listOf(descripcionFicha, descripcionEditar, descripcionEliminar).forEach { descripcion ->
            val nodos = compose.onAllNodesWithContentDescription(descripcion)
                .fetchSemanticsNodes()
            assertEquals(
                "La descripción \"$descripcion\" tendría que estar en exactamente un nodo",
                1,
                nodos.size,
            )
        }
    }

    @Test
    fun elNodoDeEliminarNoArrastraLasDescripcionesDeLosHermanos() {
        mostrarFila()

        val nodo = compose.onNodeWithContentDescription(descripcionEliminar).fetchSemanticsNode()

        // El corazón de la regresión. Con la `Card` clickeable, este mismo nodo matchea
        // "Eliminar Ana" y es el de la fila entera: lleva las tres descripciones y la
        // única acción, la de abrir la ficha. Con el arreglo, el nodo del botón eliminar
        // no sabe nada de sus hermanos.
        assertEquals(
            listOf(descripcionEliminar),
            descripcionesDel(nodo),
        )
    }

    @Test
    fun elNodoDeEditarNoArrastraLasDescripcionesDeLosHermanos() {
        mostrarFila()

        val nodo = compose.onNodeWithContentDescription(descripcionEditar).fetchSemanticsNode()

        assertEquals(listOf(descripcionEditar), descripcionesDel(nodo))
    }

    @Test
    fun losTresNodosDeLaFilaSonDistintos() {
        mostrarFila()

        val ficha = compose.onNodeWithContentDescription(descripcionFicha).fetchSemanticsNode()
        val editar = compose.onNodeWithContentDescription(descripcionEditar).fetchSemanticsNode()
        val eliminar = compose.onNodeWithContentDescription(descripcionEliminar).fetchSemanticsNode()

        // "Un nodo por acción" en serio y no "un `contentDescription` por acción": si los
        // tres fueran el mismo nodo, las tres búsquedas devolverían el mismo `SemanticsNode`.
        assertNotEquals("Editar y eliminar son el mismo nodo", editar.id, eliminar.id)
        assertNotEquals("La ficha y editar son el mismo nodo", ficha.id, editar.id)
        assertNotEquals("La ficha y eliminar son el mismo nodo", ficha.id, eliminar.id)
    }

    // ------------------------------------------------------------------ una acción por clic

    @Test
    fun tocarEliminarBorraEseClienteYNoAbreLaFicha() {
        mostrarFila()

        compose.onNodeWithContentDescription(descripcionEliminar).performClick()

        assertEquals(listOf(cliente), eliminados)
        assertTrue("Un toque en eliminar no puede abrir la ficha", fichasAbiertas.isEmpty())
        assertTrue(editados.isEmpty())
    }

    @Test
    fun tocarEditarAbreElDialogoDeEseClienteYNoAbreLaFicha() {
        mostrarFila()

        compose.onNodeWithContentDescription(descripcionEditar).performClick()

        assertEquals(listOf(cliente), editados)
        assertTrue("Un toque en editar no puede abrir la ficha", fichasAbiertas.isEmpty())
        assertTrue(eliminados.isEmpty())
    }

    @Test
    fun tocarLaColumnaDelTextoAbreLaFichaYNoBorraNiEdita() {
        mostrarFila()

        compose.onNodeWithContentDescription(descripcionFicha).performClick()

        assertEquals(listOf(id), fichasAbiertas)
        assertTrue(eliminados.isEmpty())
        assertTrue(editados.isEmpty())
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Las `ContentDescription` que el nodo anuncia, o una lista vacía si no tiene ninguna.
     *
     * Se comparan listas completas y no booleanos porque el modo de falla de la regresión
     * es justamente un nodo que tiene *demasiadas*: "este nodo tiene descripción" da
     * verde tanto en la versión buena como en la rota.
     *
     * `SemanticsConfiguration` no expone un `getOrNull` en Compose UI 1.12, así que el
     * `contains` va explícito. Sin él, `get` tira en vez de devolver vacío y un test que
     * falla por una razón termina reportando un `NoSuchElementException` de otra.
     */
    private fun descripcionesDel(nodo: SemanticsNode): List<String> {
        val config: SemanticsConfiguration = nodo.config
        return if (SemanticsProperties.ContentDescription in config) {
            config[SemanticsProperties.ContentDescription]
        } else {
            emptyList()
        }
    }

    /**
     * Monta una `ClientesScreen` con un único cliente.
     *
     * Los tres `contentDescription` llevan el nombre del cliente justamente para que
     * "Editar Ana" y "Editar Tomás" no suenen igual: si la fila no lo llevara, los tests
     * de `onNodeWithContentDescription` pasarían con cualquier texto y no estarían probando
     * que la fila dice sobre quién está parado el usuario.
     */
    private fun mostrarFila() {
        compose.setContent {
            TurneroTheme(dynamicColor = false) {
                ClientesScreen(
                    uiState = ClientesUiState(
                        clientes = listOf(cliente),
                        cargando = false,
                    ),
                    snackbarHostState = remember { SnackbarHostState() },
                    onConsultaCambiada = {},
                    onAbrirFicha = { fichasAbiertas += it },
                    onAgregar = {},
                    onEditar = { editados += it },
                    onEliminar = { eliminados += it },
                    onReintentar = {},
                )
            }
        }
    }

    private fun texto(res: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<Context>().getString(res, *args)
}
