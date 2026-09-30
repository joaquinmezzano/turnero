package com.turnero.app.domain.usecase

import app.cash.turbine.test
import com.turnero.app.testing.FakeServicioRepository
import com.turnero.app.testing.servicio
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ObtenerServiciosUseCaseTest {

    private val repositorio = FakeServicioRepository()
    private val useCase = ObtenerServiciosUseCase(repositorio)

    @Test
    fun `el caso de uso reenvia el stream del repositorio sin filtrar ni reordenar`() = runTest {
        val primero = servicio(nombre = "Barberia")
        val segundo = servicio(nombre = "Coloracion")

        useCase().test {
            repositorio.emitir(listOf(primero, segundo))

            assertEquals(listOf(primero, segundo), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `el caso de uso emite cada actualizacion del repositorio`() = runTest {
        val corte = servicio(nombre = "Corte")

        useCase().test {
            repositorio.emitir(listOf(corte))
            assertEquals(listOf(corte), awaitItem())

            repositorio.emitir(listOf(corte, servicio(nombre = "Coloracion")))
            assertEquals(listOf("Corte", "Coloracion"), awaitItem().map { it.nombre })

            cancelAndIgnoreRemainingEvents()
        }
    }
}