package com.turnero.app.domain.usecase

import com.turnero.app.domain.model.ErrorServicio
import com.turnero.app.testing.FakeClockProvider
import com.turnero.app.testing.FakeServicioRepository
import com.turnero.app.testing.INSTANTE_BASE
import com.turnero.app.testing.servicio
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class EliminarServicioUseCaseTest {

    private val repositorio = FakeServicioRepository()
    private val reloj = FakeClockProvider()
    private val useCase = EliminarServicioUseCase(repositorio, reloj)

    private val id = UUID.fromString("b2d4e6f8-3a5b-4c7d-8e9f-0a1b2c3d4e02")

    @Test
    fun `la baja marca deletedAt con el instante del reloj`() = runTest {
        repositorio.guardar(servicio(id = id))

        useCase(id).getOrThrow()

        assertEquals(reloj.instante, repositorio.almacenado(id).deletedAt)
    }

    @Test
    fun `la baja no borra la fila sino que la deja marcada`() = runTest {
        repositorio.guardar(servicio(id = id, deletedAt = null))

        useCase(id).getOrThrow()

        assertEquals(servicio(id = id, deletedAt = INSTANTE_BASE), repositorio.almacenado(id))
    }

    @Test
    fun `la baja saca el servicio de la lista de vivos`() = runTest {
        repositorio.guardar(servicio(id = id))
        repositorio.guardar(servicio(nombre = "Coloracion"))

        useCase(id).getOrThrow()

        assertEquals(listOf("Coloracion"), repositorio.vivos().map { it.nombre })
    }

    @Test
    fun `borrar un id que nunca existio devuelve NoExiste`() = runTest {
        val resultado = useCase(id)

        assertEquals(ErrorServicio.NoExiste, resultado.exceptionOrNull())
    }

    @Test
    fun `borrar un servicio ya eliminado devuelve NoExiste`() = runTest {
        repositorio.guardar(servicio(id = id, deletedAt = INSTANTE_BASE))

        val resultado = useCase(id)

        assertEquals(ErrorServicio.NoExiste, resultado.exceptionOrNull())
    }

    @Test
    fun `una segunda baja no pisa la fecha original de eliminacion`() = runTest {
        repositorio.guardar(servicio(id = id))
        useCase(id).getOrThrow()
        reloj.instante = INSTANTE_BASE.plusSeconds(7200)

        val segunda = useCase(id)

        assertEquals(ErrorServicio.NoExiste, segunda.exceptionOrNull())
        assertEquals(INSTANTE_BASE, repositorio.almacenado(id).deletedAt)
    }

    @Test
    fun `un id olvidado a proposito devuelve NoExiste`() = runTest {
        repositorio.guardar(servicio(id = id))
        repositorio.olvidar(id)

        val resultado = useCase(id)

        assertEquals(ErrorServicio.NoExiste, resultado.exceptionOrNull())
    }

    @Test
    fun `un fallo al leer el servicio antes de borrar no se convierte en NoExiste`() = runTest {
        val fallo = IllegalStateException("fallo de sqlite")
        repositorio.guardar(servicio(id = id))
        repositorio.falloObtenerPorId = fallo

        val resultado = useCase(id)

        assertEquals(fallo, resultado.exceptionOrNull())
        assertTrue(!repositorio.llamadas.contains("eliminar"))
    }
}