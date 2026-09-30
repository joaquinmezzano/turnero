package com.turnero.app.domain.usecase

import com.turnero.app.core.datetime.ClockProvider
import com.turnero.app.domain.model.ErrorCliente
import com.turnero.app.testing.FakeClockProvider
import com.turnero.app.testing.FakeClienteRepository
import com.turnero.app.testing.INSTANTE_BASE
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class CrearClienteUseCaseTest {

    private val repositorio = FakeClienteRepository()
    private val reloj = FakeClockProvider()
    private val useCase = CrearClienteUseCase(repositorio, reloj)

    @Test
    fun `guarda el cliente y devuelve el id generado`() = runTest {
        val id = useCase("Ana Perez", "+54 11 5555-0100", "ana@example.com", null)

        assertEquals(repositorio.vivos().single().id, id.getOrNull())
        assertEquals("Ana Perez", repositorio.vivos().single().nombre)
    }

    @Test
    fun `el id tiene forma de UUID y no de autoincremental`() = runTest {
        val id = useCase("Ana", null, null, null).getOrNull()

        assertNotNull(id)
        // Un autoincremental se vería como un número entero. La aserción va sobre el
        // formato, que es lo que descarta el autoincremental sin depender del orden de
        // inserción ni de un `SELECT last_insert_rowid()`.
        val texto = id.toString()
        assertEquals(36, texto.length, "Un UUID tiene 36 caracteres con guiones: $texto")
        assertEquals(4, texto.count { it == '-' }, "Un UUID tiene 4 guiones: $texto")
    }

    @Test
    fun `las marcas de tiempo salen del reloj inyectado`() = runTest {
        reloj.instante = INSTANTE_BASE.plusSeconds(7200)

        useCase("Ana", null, null, null)

        val guardado = repositorio.vivos().single()
        assertEquals(reloj.instante, guardado.createdAt)
        assertEquals(reloj.instante, guardado.updatedAt)
        assertNull(guardado.deletedAt)
    }

    @Test
    fun `el reloj se consulta una sola vez por alta`() = runTest {
        // `createdAt` y `updatedAt` salen del mismo `now()`: si se llamara dos veces y el
        // reloj avanzara en el medio, la fila nacería con `updatedAt` posterior a `createdAt`
        // sin que nadie la haya tocado.
        var llamadas = 0
        val relojQueCuenta = object : ClockProvider {
            override fun now(): Instant {
                llamadas++
                return INSTANTE_BASE
            }
        }
        val useCaseConReloj = CrearClienteUseCase(repositorio, relojQueCuenta)

        useCaseConReloj("Ana", null, null, null)

        assertEquals(1, llamadas, "Se llamó al reloj $llamadas veces")
    }

    @Test
    fun `guarda el nombre sin los espacios sobrantes`() = runTest {
        useCase("  Ana Perez  ", null, null, null)

        assertEquals("Ana Perez", repositorio.vivos().single().nombre)
    }

    @Test
    fun `un dato en blanco se guarda como null y no como cadena vacia`() = runTest {
        // Guardar `""` y guardar `null` no es lo mismo: `""` se muestra como un campo vacío
        // en la ficha y el `Phone` de Android lo lee como un número inválido.
        useCase("Ana", "   ", "  ", "  ")

        val guardado = repositorio.vivos().single()
        assertNull(guardado.telefono)
        assertNull(guardado.email)
        assertNull(guardado.notas)
    }

    @Test
    fun `un dato con espacios sobrantes se guarda recortado`() = runTest {
        useCase("Ana", "  +54 11 5555-0100  ", "  ana@example.com  ", null)

        val guardado = repositorio.vivos().single()
        assertEquals("+54 11 5555-0100", guardado.telefono)
        assertEquals("ana@example.com", guardado.email)
    }

    @Test
    fun `telefono y email son opcionales`() = runTest {
        // El caso más común de un cliente nuevo: nombre y nada más. Si el teléfono o el
        // email fueran obligatorios, el alta sería casi imposible.
        val resultado = useCase("Ana", null, null, null)

        assertNull(resultado.exceptionOrNull())
        assertEquals(1, repositorio.vivos().size)
    }

    @Test
    fun `un nombre vacio devuelve NombreVacio y no escribe nada`() = runTest {
        val resultado = useCase("   ", null, null, null)

        assertEquals(ErrorCliente.NombreVacio, resultado.exceptionOrNull())
        assertTrue(repositorio.llamadas.isEmpty(), "Se llamó al repositorio: ${repositorio.llamadas}")
        assertTrue(repositorio.vivos().isEmpty())
    }

    @Test
    fun `un email sin arroba devuelve EmailInvalido`() = runTest {
        val resultado = useCase("Ana", null, "ana.example.com", null)

        assertEquals(ErrorCliente.EmailInvalido, resultado.exceptionOrNull())
        assertTrue(repositorio.vivos().isEmpty())
    }

    @Test
    fun `un email con espacios adentro devuelve EmailInvalido`() = runTest {
        val resultado = useCase("Ana", null, "ana perez@example.com", null)

        assertEquals(ErrorCliente.EmailInvalido, resultado.exceptionOrNull())
    }

    @Test
    fun `un telefono con letras devuelve TelefonoInvalido`() = runTest {
        val resultado = useCase("Ana", "llamar 5555-0100", null, null)

        assertEquals(ErrorCliente.TelefonoInvalido, resultado.exceptionOrNull())
        assertTrue(repositorio.vivos().isEmpty())
    }

    @Test
    fun `la validacion del nombre corre antes que la del email`() = runTest {
        // Con los dos campos malos, el error que se ve es el del nombre: el orden importa
        // porque la pantalla muestra un error y el usuario corregiría el que vea primero.
        val resultado = useCase("  ", null, "esto-no-es-un-email", null)

        assertEquals(ErrorCliente.NombreVacio, resultado.exceptionOrNull())
    }

    @Test
    fun `un fallo al escribir vuelve como Result failure`() = runTest {
        val fallo = IllegalStateException("Room no inserto la fila")
        repositorio.falloCrear = fallo

        val resultado = useCase("Ana", null, null, null)

        assertEquals(fallo, resultado.exceptionOrNull())
    }

    @Test
    fun `dos clientes con el mismo nombre son dos filas distintas`() = runTest {
        // El nombre no es clave única: dos Ana Pérez son dos personas.
        val primero = useCase("Ana Perez", null, null, null).getOrNull()
        val segundo = useCase("Ana Perez", null, null, null).getOrNull()

        assertEquals(2, repositorio.vivos().size)
        assertNotEquals(primero, segundo, "Se reusó el id $primero")
    }
}