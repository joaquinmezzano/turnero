package com.turnero.app.domain.usecase

import com.turnero.app.testing.FakeClienteRepository
import com.turnero.app.testing.INSTANTE_BASE
import com.turnero.app.testing.cliente
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class ObtenerClientePorIdUseCaseTest {

    private val repositorio = FakeClienteRepository()
    private val useCase = ObtenerClientePorIdUseCase(repositorio)

    private val id = UUID.fromString("6f1c9a20-1c2e-4f3b-9d5a-2b7c4e8f1a01")

    @Test
    fun `devuelve el cliente que existe`() = runTest {
        val ana = cliente(id = id, nombre = "Pérez, Ana")
        repositorio.guardar(ana)

        val resultado = useCase(id)

        assertEquals(ana, resultado.getOrNull())
    }

    @Test
    fun `un id que no existe devuelve null y no un error`() = runTest {
        val resultado = useCase(id)

        // `null` y no `NoExiste`: el caso "no está" es un resultado válido de una lectura,
        // no una falla. Quien lo necesita como error (editar, borrar) lo traduce; la ficha
        // lo muestra como pantalla de error con su propio mensaje.
        assertNull(resultado.exceptionOrNull())
        assertNull(resultado.getOrNull())
    }

    @Test
    fun `un cliente eliminado devuelve null`() = runTest {
        repositorio.guardar(cliente(id = id, deletedAt = INSTANTE_BASE))

        val resultado = useCase(id)

        assertNull(resultado.getOrNull())
        assertNull(resultado.exceptionOrNull())
    }

    @Test
    fun `un fallo de lectura se propaga como Result failure`() = runTest {
        val fallo = IllegalStateException("fallo de sqlite")
        repositorio.falloObtenerPorId = fallo

        val resultado = useCase(id)

        assertEquals(fallo, resultado.exceptionOrNull())
    }
}