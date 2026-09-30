package com.turnero.app.domain.usecase

import app.cash.turbine.test
import com.turnero.app.testing.FakeClienteRepository
import com.turnero.app.testing.cliente
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests de búsqueda y orden de [ObtenerClientesUseCase].
 *
 * El caso interesante es el de los acentos: `"perez"` tiene que encontrar `"Pérez"` y al
 * revés. Un `LIKE` de SQLite no lo resuelve en ninguna de las dos direcciones, que es
 * exactamente lo que hace que el filtro viva en Kotlin.
 */
class ObtenerClientesUseCaseTest {

    private val repositorio = FakeClienteRepository()
    private val useCase = ObtenerClientesUseCase(repositorio)

    @Test
    fun `sin consulta devuelve todos los clientes`() = runTest {
        val ana = cliente(nombre = "Ana")
        val tomas = cliente(nombre = "Tomás")

        useCase().test {
            repositorio.emitir(listOf(ana, tomas))

            assertEquals(listOf(ana, tomas), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `buscar sin acentos encuentra el nombre que los tiene`() = runTest {
        val perez = cliente(nombre = "Pérez, Ana")
        val otro = cliente(nombre = "Gómez, Luis")

        useCase("perez").test {
            repositorio.emitir(listOf(perez, otro))

            assertEquals(listOf(perez), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `buscar con acentos encuentra el nombre que no los tiene`() = runTest {
        // La dirección inversa importa igual: el usuario puede tener copiado el nombre con
        // la tilde desde una agenda y tipear sin ella, o al revés.
        val gomez = cliente(nombre = "Gomez, Luis")

        useCase("gómez").test {
            repositorio.emitir(listOf(gomez))

            assertEquals(listOf(gomez), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `la busqueda ignora mayusculas`() = runTest {
        val ana = cliente(nombre = "Ana Perez")

        useCase("ANA").test {
            repositorio.emitir(listOf(ana))

            assertEquals(listOf(ana), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `la busqueda es por subcadena y no por prefijo`() = runTest {
        val ana = cliente(nombre = "Ana María Pérez")

        useCase("maria").test {
            repositorio.emitir(listOf(ana))

            assertEquals(listOf(ana), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `un criterio con espacios sobrantes busca lo mismo`() = runTest {
        val ana = cliente(nombre = "Optica Roma")

        useCase("   ÓPTICA   ").test {
            repositorio.emitir(listOf(ana))

            assertEquals(listOf(ana), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `una consulta sin resultados devuelve una lista vacia y no un error`() = runTest {
        useCase("zzzz").test {
            repositorio.emitir(listOf(cliente(nombre = "Ana")))

            assertEquals(emptyList<Any>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `el resultado del filtro sale ordenado alfabeticamente`() = runTest {
        val zeta = cliente(nombre = "Zeta")
        val anaMaria = cliente(nombre = "Ana María")
        val alvarez = cliente(nombre = "Álvarez")

        useCase("a").test {
            // Los tres nombres contienen "a", así que el filtro no descarta a ninguno, y
            // la entrada va a propósito sin ordenar: si el filtro no ordenara, el orden de
            // entrada se vería. Con `ordenandoPor` sale "Álvarez" antes que "Ana María"
            // (la `l` va antes que la `n`) y "Zeta" al final.
            repositorio.emitir(listOf(zeta, anaMaria, alvarez))

            assertEquals(listOf("Álvarez", "Ana María", "Zeta"), awaitItem().map { it.nombre })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `el filtro solo deja los nombres que contienen el criterio`() = runTest {
        // El criterio "a" no está en todas partes: "Mikes" no lo tiene y no tiene que salir.
        val mikes = cliente(nombre = "Mikes")
        val alvarez = cliente(nombre = "Álvarez")

        useCase("a").test {
            repositorio.emitir(listOf(mikes, alvarez))

            assertEquals(listOf("Álvarez"), awaitItem().map { it.nombre })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `un criterio vacio no aplica el filtro`() = runTest {
        // El criterio vacío no es un `contains("")` sobre un flujo distinto: es el mismo
        // camino con un `if`, y sin el filtro aparecerían todos.
        val ana = cliente(nombre = "Ana")
        val tomas = cliente(nombre = "Tomás")

        useCase("").test {
            repositorio.emitir(listOf(ana, tomas))

            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `el stream emite de nuevo cuando cambia el repositorio`() = runTest {
        val ana = cliente(nombre = "Ana")

        useCase().test {
            repositorio.emitir(listOf(ana))
            assertEquals(listOf(ana), awaitItem())

            val conTomas = listOf(ana, cliente(nombre = "Tomás"))
            repositorio.emitir(conTomas)
            assertEquals(conTomas, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `el filtro no toca el repositorio`() = runTest {
        useCase("ana").test {
            repositorio.emitir(listOf(cliente(nombre = "Ana")))
            awaitItem()

            // Filtrar es una `map` sobre el `Flow`: si el use case escribiera, el nombre
            // aparecería en `llamadas` y la lectura dejaría de ser de solo lectura.
            assertTrue(repositorio.llamadas.isEmpty(), "Se llamó al repositorio: ${repositorio.llamadas}")
            cancelAndIgnoreRemainingEvents()
        }
    }
}