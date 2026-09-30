package com.turnero.app.domain.usecase

import com.turnero.app.domain.model.ErrorCliente
import com.turnero.app.testing.FakeClockProvider
import com.turnero.app.testing.FakeClienteRepository
import com.turnero.app.testing.INSTANTE_BASE
import com.turnero.app.testing.cliente
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class ActualizarClienteUseCaseTest {

    private val repositorio = FakeClienteRepository()
    private val reloj = FakeClockProvider()
    private val useCase = ActualizarClienteUseCase(repositorio, reloj)

    private val id = UUID.fromString("6f1c9a20-1c2e-4f3b-9d5a-2b7c4e8f1a01")

    @Test
    fun `la edicion preserva el createdAt original`() = runTest {
        repositorio.guardar(cliente(id = id, createdAt = INSTANTE_BASE))
        reloj.instante = INSTANTE_BASE.plusSeconds(3600)

        useCase(id, nombre = "Pérez, Ana María", telefono = null, email = null, notas = null)

        assertEquals(INSTANTE_BASE, repositorio.vivos().single().createdAt)
    }

    @Test
    fun `la edicion refresca updatedAt con el instante del reloj`() = runTest {
        repositorio.guardar(cliente(id = id, updatedAt = INSTANTE_BASE))
        reloj.instante = INSTANTE_BASE.plusSeconds(3600)

        useCase(id, nombre = "Pérez, Ana María", telefono = null, email = null, notas = null)

        assertEquals(INSTANTE_BASE.plusSeconds(3600), repositorio.vivos().single().updatedAt)
    }

    @Test
    fun `la edicion aplica los campos que el usuario toco`() = runTest {
        repositorio.guardar(cliente(id = id))

        useCase(
            id,
            nombre = "Pérez, Ana María",
            telefono = "+54 11 5555-0200",
            email = "ana.maria@example.com",
            notas = "Viene los sábados",
        )

        val guardado = repositorio.vivos().single()
        assertEquals("Pérez, Ana María", guardado.nombre)
        assertEquals("+54 11 5555-0200", guardado.telefono)
        assertEquals("ana.maria@example.com", guardado.email)
        assertEquals("Viene los sábados", guardado.notas)
    }

    @Test
    fun `la edicion puede vaciar un contacto que antes estaba`() = runTest {
        repositorio.guardar(cliente(id = id, telefono = "+54 11 5555-0100"))

        useCase(id, nombre = "Pérez, Ana", telefono = "", email = null, notas = null)

        assertNull(repositorio.vivos().single().telefono)
    }

    @Test
    fun `la edicion guarda el nombre sin los espacios sobrantes`() = runTest {
        repositorio.guardar(cliente(id = id))

        useCase(id, nombre = "  Pérez, Ana  ", telefono = null, email = null, notas = null)

        assertEquals("Pérez, Ana", repositorio.vivos().single().nombre)
    }

    @Test
    fun `editar un id que nunca existio devuelve NoExiste`() = runTest {
        val resultado = useCase(id, nombre = "Ana", telefono = null, email = null, notas = null)

        assertEquals(ErrorCliente.NoExiste, resultado.exceptionOrNull())
    }

    @Test
    fun `editar un cliente ya eliminado devuelve NoExiste para no resucitarlo`() = runTest {
        repositorio.guardar(cliente(id = id, deletedAt = INSTANTE_BASE))

        val resultado = useCase(id, nombre = "Ana", telefono = null, email = null, notas = null)

        assertEquals(ErrorCliente.NoExiste, resultado.exceptionOrNull())
    }

    @Test
    fun `editar un cliente ya eliminado no lo devuelve a la lista de vivos`() = runTest {
        // Si la edición escribiera sobre una fila soft-deleted, el cliente reaparecería
        // solo en el listado. Por eso [FakeClienteRepository.almacenado] y no [vivos]: con
        // `vivos` la aserción pasaría aunque la fila se hubiera reactivado.
        repositorio.guardar(cliente(id = id, deletedAt = INSTANTE_BASE))

        useCase(id, nombre = "Ana", telefono = null, email = null, notas = null)

        assertTrue(repositorio.vivos().isEmpty())
        assertEquals(INSTANTE_BASE, repositorio.almacenado(id).deletedAt)
    }

    @Test
    fun `una validacion fallida no consulta ni escribe en el repositorio`() = runTest {
        repositorio.guardar(cliente(id = id))

        val resultado = useCase(id, nombre = "   ", telefono = null, email = null, notas = null)

        assertEquals(ErrorCliente.NombreVacio, resultado.exceptionOrNull())
        assertTrue(repositorio.llamadas.isEmpty(), "Se llamó al repositorio: ${repositorio.llamadas}")
    }

    @Test
    fun `un fallo al leer el cliente existente no se convierte en NoExiste`() = runTest {
        // Si un error de SQLite se tradujera a NoExiste, la pantalla mostraría "ese cliente
        // no existe" y el usuario buscaría un problema que no tiene: los datos están.
        val fallo = IllegalStateException("fallo de sqlite")
        repositorio.guardar(cliente(id = id))
        repositorio.falloObtenerPorId = fallo

        val resultado = useCase(id, nombre = "Ana", telefono = null, email = null, notas = null)

        assertEquals(fallo, resultado.exceptionOrNull())
        assertTrue(!repositorio.llamadas.contains("actualizar"))
    }

    @Test
    fun `un fallo al escribir la edicion vuelve como Result failure`() = runTest {
        val fallo = IllegalStateException("Room no actualizo ninguna fila")
        repositorio.guardar(cliente(id = id))
        repositorio.falloActualizar = fallo

        val resultado = useCase(id, nombre = "Ana", telefono = null, email = null, notas = null)

        assertEquals(fallo, resultado.exceptionOrNull())
    }

    @Test
    fun `la edicion no cambia el id`() = runTest {
        repositorio.guardar(cliente(id = id))

        useCase(id, nombre = "Ana", telefono = null, email = null, notas = null)

        assertEquals(id, repositorio.vivos().single().id)
    }
}