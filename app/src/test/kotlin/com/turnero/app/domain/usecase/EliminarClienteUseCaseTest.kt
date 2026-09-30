package com.turnero.app.domain.usecase

import com.turnero.app.domain.model.ErrorCliente
import com.turnero.app.testing.FakeClockProvider
import com.turnero.app.testing.FakeClienteRepository
import com.turnero.app.testing.INSTANTE_BASE
import com.turnero.app.testing.cliente
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class EliminarClienteUseCaseTest {

    private val repositorio = FakeClienteRepository()
    private val reloj = FakeClockProvider()
    private val useCase = EliminarClienteUseCase(repositorio, reloj)

    private val id = UUID.fromString("6f1c9a20-1c2e-4f3b-9d5a-2b7c4e8f1a01")

    @Test
    fun `la baja es logica y deja la fila en el almacen`() = runTest {
        // La fila desaparece del listado pero sigue en la base: es lo que permite que un
        // sync futuro la borre de verdad, o que se pueda deshacer.
        repositorio.guardar(cliente(id = id))

        useCase(id)

        assertNotNull(repositorio.almacenado(id).deletedAt)
        assertTrue(repositorio.vivos().isEmpty(), "El cliente sigue vivo")
    }

    @Test
    fun `la baja marca deletedAt con el instante del reloj`() = runTest {
        repositorio.guardar(cliente(id = id))
        reloj.instante = INSTANTE_BASE.plusSeconds(120)

        useCase(id)

        assertEquals(INSTANTE_BASE.plusSeconds(120), repositorio.almacenado(id).deletedAt)
    }

    @Test
    fun `la baja no toca el createdAt original`() = runTest {
        repositorio.guardar(cliente(id = id, createdAt = INSTANTE_BASE))
        reloj.instante = INSTANTE_BASE.plusSeconds(120)

        useCase(id)

        assertEquals(INSTANTE_BASE, repositorio.almacenado(id).createdAt)
    }

    @Test
    fun `borrar un id que nunca existio devuelve NoExiste`() = runTest {
        val resultado = useCase(id)

        assertEquals(ErrorCliente.NoExiste, resultado.exceptionOrNull())
    }

    @Test
    fun `borrar un cliente ya eliminado devuelve NoExiste`() = runTest {
        repositorio.guardar(cliente(id = id, deletedAt = INSTANTE_BASE))

        val resultado = useCase(id)

        assertEquals(ErrorCliente.NoExiste, resultado.exceptionOrNull())
    }

    @Test
    fun `borrar dos veces no cambia la marca de la primera baja`() = runTest {
        repositorio.guardar(cliente(id = id))
        reloj.instante = INSTANTE_BASE.plusSeconds(120)
        useCase(id)
        val primeraMarca = repositorio.almacenado(id).deletedAt

        reloj.instante = INSTANTE_BASE.plusSeconds(999)
        useCase(id)

        // Reescribir `deletedAt` con el segundo instante haría que la baja pareciera
        // reciente y borraría la evidencia de cuándo pasó.
        assertEquals(primeraMarca, repositorio.almacenado(id).deletedAt)
    }

    @Test
    fun `un fallo al leer el cliente no se convierte en NoExiste`() = runTest {
        val fallo = IllegalStateException("fallo de sqlite")
        repositorio.guardar(cliente(id = id))
        repositorio.falloObtenerPorId = fallo

        val resultado = useCase(id)

        assertEquals(fallo, resultado.exceptionOrNull())
    }

    @Test
    fun `un fallo al escribir la baja vuelve como Result failure`() = runTest {
        val fallo = IllegalStateException("Room no actualizo ninguna fila")
        repositorio.guardar(cliente(id = id))
        repositorio.falloEliminar = fallo

        val resultado = useCase(id)

        assertEquals(fallo, resultado.exceptionOrNull())
        assertNull(repositorio.almacenado(id).deletedAt, "La fila quedó marcada igual")
    }

    @Test
    fun `la baja devuelve Unit y no el cliente`() = runTest {
        repositorio.guardar(cliente(id = id))

        val resultado = useCase(id)

        // No hace falta devolver el cliente: la pantalla solo necesita saber que se fue, y
        // devolver el `deletedAt` obligaría al llamador a conocer el instante de la baja.
        assertEquals(Unit, resultado.getOrNull())
    }
}