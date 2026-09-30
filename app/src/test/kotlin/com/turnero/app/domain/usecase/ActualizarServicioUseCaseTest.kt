package com.turnero.app.domain.usecase

import com.turnero.app.domain.model.ErrorServicio
import com.turnero.app.testing.COLOR_BASE
import com.turnero.app.testing.FakeClockProvider
import com.turnero.app.testing.FakeServicioRepository
import com.turnero.app.testing.INSTANTE_BASE
import com.turnero.app.testing.servicio
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class ActualizarServicioUseCaseTest {

    private val repositorio = FakeServicioRepository()
    private val reloj = FakeClockProvider()
    private val useCase = ActualizarServicioUseCase(repositorio, reloj)

    private val id = UUID.fromString("6f1c9a20-1c2e-4f3b-9d5a-2b7c4e8f1a01")

    @Test
    fun `la edicion preserva el createdAt original`() = runTest {
        repositorio.guardar(servicio(id = id, createdAt = INSTANTE_BASE))
        reloj.instante = INSTANTE_BASE.plusSeconds(3600)

        useCase(id, nombre = "Corte corto", duracionMin = 20, precioCentavos = 4_000L, color = COLOR_BASE)

        assertEquals(INSTANTE_BASE, repositorio.vivos().single().createdAt)
    }

    @Test
    fun `la edicion refresca updatedAt con el instante del reloj`() = runTest {
        repositorio.guardar(servicio(id = id, updatedAt = INSTANTE_BASE))
        reloj.instante = INSTANTE_BASE.plusSeconds(3600)

        useCase(id, nombre = "Corte corto", duracionMin = 20, precioCentavos = 4_000L, color = COLOR_BASE)

        assertEquals(INSTANTE_BASE.plusSeconds(3600), repositorio.vivos().single().updatedAt)
    }

    @Test
    fun `la edicion aplica los campos que el usuario toco`() = runTest {
        repositorio.guardar(servicio(id = id))

        useCase(id, nombre = "Coloracion", duracionMin = 90, precioCentavos = 18_500L, color = 0xFF00FF00.toInt())

val guardado = repositorio.vivos().single()
        assertEquals("Coloracion", guardado.nombre)
        assertEquals(90, guardado.duracionMin)
        assertEquals(18_500L, guardado.precioCentavos)
        assertEquals(0xFF00FF00.toInt(), guardado.color)
    }

    @Test
    fun `la edicion guarda el nombre sin los espacios sobrantes`() = runTest {
        repositorio.guardar(servicio(id = id))

        useCase(id, nombre = "  Barberia  ", duracionMin = 30, precioCentavos = null, color = COLOR_BASE)

        assertEquals("Barberia", repositorio.vivos().single().nombre)
    }

    @Test
    fun `editar un id que nunca existio devuelve NoExiste`() = runTest {
        val resultado = useCase(id, nombre = "Corte", duracionMin = 30, precioCentavos = null, color = COLOR_BASE)

        assertEquals(ErrorServicio.NoExiste, resultado.exceptionOrNull())
    }

    @Test
    fun `editar un servicio ya eliminado devuelve NoExiste para no resucitarlo`() = runTest {
        repositorio.guardar(servicio(id = id, deletedAt = INSTANTE_BASE))

        val resultado = useCase(id, nombre = "Corte", duracionMin = 30, precioCentavos = null, color = COLOR_BASE)

        assertEquals(ErrorServicio.NoExiste, resultado.exceptionOrNull())
    }

    @Test
    fun `editar un servicio ya eliminado no lo devuelve a la lista de vivos`() = runTest {
        val eliminado = servicio(id = id, deletedAt = INSTANTE_BASE)
        repositorio.guardar(eliminado)

        useCase(id, nombre = "Corte", duracionMin = 30, precioCentavos = null, color = COLOR_BASE)

        assertTrue(repositorio.vivos().isEmpty())
    }

    @Test
    fun `una validacion fallida no consulta ni escribe en el repositorio`() = runTest {
        repositorio.guardar(servicio(id = id))

        val resultado = useCase(id, nombre = "  ", duracionMin = 30, precioCentavos = null, color = COLOR_BASE)

        assertEquals(ErrorServicio.NombreVacio, resultado.exceptionOrNull())
        assertTrue(repositorio.llamadas.isEmpty(), "Se llamó al repositorio: ${repositorio.llamadas}")
    }

    @Test
    fun `un fallo al leer el servicio existente no se convierte en NoExiste`() = runTest {
        val fallo = IllegalStateException("fallo de sqlite")
        repositorio.guardar(servicio(id = id))
        repositorio.falloObtenerPorId = fallo

        val resultado = useCase(id, nombre = "Corte", duracionMin = 30, precioCentavos = null, color = COLOR_BASE)

        assertEquals(fallo, resultado.exceptionOrNull())
        assertTrue(!repositorio.llamadas.contains("actualizar"))
    }

    @Test
    fun `un fallo al escribir la edicion vuelve como Result failure`() = runTest {
        val fallo = IllegalStateException("Room no actualizo ninguna fila")
        repositorio.guardar(servicio(id = id))
        repositorio.falloActualizar = fallo

        val resultado = useCase(id, nombre = "Corte", duracionMin = 30, precioCentavos = null, color = COLOR_BASE)

        assertEquals(fallo, resultado.exceptionOrNull())
    }
}