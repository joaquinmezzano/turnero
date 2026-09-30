package com.turnero.app.domain.usecase

import com.turnero.app.domain.model.ErrorServicio
import com.turnero.app.testing.COLOR_BASE
import com.turnero.app.testing.FakeClockProvider
import com.turnero.app.testing.FakeServicioRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CrearServicioUseCaseTest {

    private val repositorio = FakeServicioRepository()
    private val reloj = FakeClockProvider()
    private val useCase = CrearServicioUseCase(repositorio, reloj)

    @Test
    fun `el alta devuelve el UUID del servicio persistido`() = runTest {
        val id = useCase(nombre = "Corte", duracionMin = 30, precioCentavos = 5_000L, color = COLOR_BASE)
            .getOrThrow()

        assertEquals(id, repositorio.vivos().single().id)
    }

    @Test
    fun `el alta sella createdAt y updatedAt con el instante del reloj`() = runTest {
        useCase(nombre = "Corte", duracionMin = 30, precioCentavos = 5_000L, color = COLOR_BASE)

        val guardado = repositorio.vivos().single()
        assertEquals(reloj.instante, guardado.createdAt)
        assertEquals(reloj.instante, guardado.updatedAt)
    }

    @Test
    fun `el servicio nascent no tiene deletedAt`() = runTest {
        useCase(nombre = "Corte", duracionMin = 30, precioCentavos = 5_000L, color = COLOR_BASE)

        assertNull(repositorio.vivos().single().deletedAt)
    }

    @Test
    fun `el nombre se guarda sin los espacios sobrantes`() = runTest {
        useCase(nombre = "  Corte de pelo  ", duracionMin = 30, precioCentavos = 5_000L, color = COLOR_BASE)

        assertEquals("Corte de pelo", repositorio.vivos().single().nombre)
    }

    @Test
    fun `un nombre vacio se rechaza con NombreVacio`() = runTest {
        val resultado = useCase(nombre = "", duracionMin = 30, precioCentavos = null, color = COLOR_BASE)

        assertEquals(ErrorServicio.NombreVacio, resultado.exceptionOrNull())
    }

    @Test
    fun `un nombre de solo espacios se rechaza con NombreVacio`() = runTest {
        val resultado = useCase(nombre = "   ", duracionMin = 30, precioCentavos = null, color = COLOR_BASE)

        assertEquals(ErrorServicio.NombreVacio, resultado.exceptionOrNull())
    }

    @Test
    fun `una duracion de cero se rechaza con DuracionInvalida`() = runTest {
        val resultado = useCase(nombre = "Corte", duracionMin = 0, precioCentavos = null, color = COLOR_BASE)

        assertEquals(ErrorServicio.DuracionInvalida, resultado.exceptionOrNull())
    }

    @Test
    fun `una duracion negativa se rechaza con DuracionInvalida`() = runTest {
        val resultado = useCase(nombre = "Corte", duracionMin = -15, precioCentavos = null, color = COLOR_BASE)

        assertEquals(ErrorServicio.DuracionInvalida, resultado.exceptionOrNull())
    }

    @Test
    fun `un precio negativo se rechaza con PrecioInvalido`() = runTest {
        val resultado = useCase(nombre = "Corte", duracionMin = 30, precioCentavos = -1L, color = COLOR_BASE)

        assertEquals(ErrorServicio.PrecioInvalido, resultado.exceptionOrNull())
    }

    @Test
    fun `un precio de cero es valido porque significa sin costo`() = runTest {
        val resultado = useCase(nombre = "Consulta", duracionMin = 15, precioCentavos = 0L, color = COLOR_BASE)

        assertNull(resultado.exceptionOrNull())
        assertEquals(0L, repositorio.vivos().single().precioCentavos)
    }

    @Test
    fun `un precio null es valido porque significa a consultar`() = runTest {
        val resultado = useCase(nombre = "Diagnostico", duracionMin = 45, precioCentavos = null, color = COLOR_BASE)

        assertNull(resultado.exceptionOrNull())
        assertNull(repositorio.vivos().single().precioCentavos)
    }

    @Test
    fun `una validacion fallida no toca el repositorio`() = runTest {
        useCase(nombre = "", duracionMin = 30, precioCentavos = null, color = COLOR_BASE)
        useCase(nombre = "Corte", duracionMin = 0, precioCentavos = null, color = COLOR_BASE)
        useCase(nombre = "Corte", duracionMin = 30, precioCentavos = -1L, color = COLOR_BASE)

        assertTrue(repositorio.llamadas.isEmpty(), "Se llamó al repositorio: ${repositorio.llamadas}")
        assertTrue(repositorio.vivos().isEmpty())
    }

    @Test
    fun `un fallo del repositorio vuelve como Result failure`() = runTest {
        val fallo = IllegalStateException("Room no pudo insertar")
        repositorio.falloCrear = fallo

        val resultado = useCase(nombre = "Corte", duracionMin = 30, precioCentavos = null, color = COLOR_BASE)

        assertEquals(fallo, resultado.exceptionOrNull())
    }

    @Test
    fun `dos altas seguidas generan UUID distintos`() = runTest {
        val primero = useCase("Corte", 30, null, COLOR_BASE).getOrThrow()
        val segundo = useCase("Coloracion", 90, null, COLOR_BASE).getOrThrow()

        assertTrue(primero != segundo)
    }
}