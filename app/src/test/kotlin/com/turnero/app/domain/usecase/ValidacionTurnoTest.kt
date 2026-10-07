package com.turnero.app.domain.usecase

import com.turnero.app.domain.model.ErrorTurno
import com.turnero.app.domain.model.VENTANA_POR_DEFECTO
import com.turnero.app.testing.FECHA_BASE
import com.turnero.app.testing.ZONA_BASE
import com.turnero.app.testing.instanteDeTurno
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Validacion del inicio: ventana de atencion, anclaje a la hora y prohibicion de agendar en
 * el pasado.
 *
 * Todas las horas son hora local de [ZONA_BASE] (GMT-3). El instante de referencia para el
 * pasado es fijo y reconocible, nunca `Instant.now()`.
 */
class ValidacionTurnoTest {

    private val zona = ZONA_BASE

    /**
     * 07:00 del dia base, antes de la apertura.
     *
     * El reloj va antes de la ventana a proposito: asi los tests de ventana no dependen de
     * la hora que elijan y la regla del pasado queda aislada en los tests que pasan su
     * propio `ahoraDeReferencia`.
     */
    private val ahora: Instant = instanteDeTurno(hora = 7)

    private fun validar(
        hora: Int,
        ahoraDeReferencia: Instant = ahora,
        minuto: Int = 0,
    ) = validarInicioTurno(
        fecha = FECHA_BASE,
        hora = LocalTime.of(hora, minuto),
        ahora = ahoraDeReferencia,
        zona = zona,
        ventana = VENTANA_POR_DEFECTO,
    )

    // ------------------------------------------------------------------ ventana

    @Test
    fun `las ocho de la manana es una hora de inicio valida`() {
        assertNull(validar(hora = 8, ahoraDeReferencia = instanteDeTurno(hora = 7)))
    }

    @Test
    fun `las diez de la noche es una hora de inicio valida`() {
        // `hasta` es inclusive como hora de inicio: 22:00 es la ultima hora agendable.
        assertNull(validar(hora = 22))
    }

    @Test
    fun `una hora antes de la apertura se rechaza`() {
        assertEquals(ErrorTurno.FueraDeVentana, validar(hora = 7))
    }

    @Test
    fun `una hora despues del cierre se rechaza`() {
        assertEquals(ErrorTurno.FueraDeVentana, validar(hora = 23))
    }

    @Test
    fun `un turno puede terminar despues del cierre porque solo importa el inicio`() {
        // Un turno de 21:00 a 23:00 es valido: lo que decide la ventana es la hora en que
        // arranca, no la hora en que termina.
        val inicio = instanteDeTurno(hora = 21)

        assertNull(validar(hora = 21, ahoraDeReferencia = instanteDeTurno(hora = 7)))
        assertEquals(23, LocalTime.ofInstant(inicio.plusSeconds(120 * 60), zona).hour)
    }

    @Test
    fun `los minutos que se pasan del borde se truncan antes de validar`() {
        // El anclaje a la hora es previo a la validacion: 22:30 se guarda como 22:00 y se
        // valida como 22:00. No es una tolerancia escondida: la hora es la unidad de
        // reserva, asi que la UI nunca manda minutos, y si los mandara lo que se guarda es
        // el punto de hora mas cercano hacia abajo.
        assertNull(validar(hora = 22, minuto = 30, ahoraDeReferencia = instanteDeTurno(hora = 7)))
        assertEquals(ErrorTurno.FueraDeVentana, validar(hora = 7, minuto = 59, ahoraDeReferencia = instanteDeTurno(hora = 7)))
    }

    // ------------------------------------------------------------------ anclaje

    @Test
    fun `el anclaje deja el minuto y el segundo en cero`() {
        val inicio = anclarInicio(FECHA_BASE, LocalTime.of(10, 37), zona)
        val local = inicio.atZone(zona)

        assertEquals(0, local.minute)
        assertEquals(0, local.second)
        assertEquals(0, local.nano)
    }

    @Test
    fun `el anclaje de las diez de Argentina es el instante esperado`() {
        // 10:00 GMT-3 es 13:00 UTC. Si esto cambia, alguien metio un offset fijo.
        assertEquals(
            Instant.parse("2026-03-16T13:00:00Z"),
            anclarInicio(FECHA_BASE, LocalTime.of(10, 0), zona),
        )
    }

    @Test
    fun `la misma fecha y hora en dos zonas son dos instantes distintos`() {
        // Si el codigo hardcodeara una zona, las dos columnas darian el mismo resultado.
        val madrid = ZoneId.of("Europe/Madrid")

        val enBuenosAires = anclarInicio(FECHA_BASE, LocalTime.of(10, 0), zona)
        val enMadrid = anclarInicio(FECHA_BASE, LocalTime.of(10, 0), madrid)

        assertEquals(LocalTime.of(10, 0), LocalTime.ofInstant(enBuenosAires, zona))
        assertEquals(LocalTime.of(10, 0), LocalTime.ofInstant(enMadrid, madrid))
        assertFalse(enBuenosAires == enMadrid)
    }

    @Test
    fun `el anclaje respeta el cambio de hora de la zona`() {
        // Argentina no cambia de offset, asi que la prueba usa una zona que si lo hace: el
        // mismo 10:00 local es un `Instant` distinto antes y despues del cambio. El cambio es
        // el 8 de marzo a las 02:00 local, asi que el 8 a las 10:00 ya esta en EDT: se comparan
        // el 7 (EST) con el 9 (EDT), no el dia de la transicion.
        val nuevaYork = ZoneId.of("America/New_York")
        val antesDelCambio = anclarInicio(fecha = FECHA_BASE.withMonth(3).withDayOfMonth(7), hora = LocalTime.of(10, 0), zona = nuevaYork)
        val despuesDelCambio = anclarInicio(fecha = FECHA_BASE.withMonth(3).withDayOfMonth(9), hora = LocalTime.of(10, 0), zona = nuevaYork)

        assertEquals(-5 * 3600, antesDelCambio.atZone(nuevaYork).offset.totalSeconds)
        assertEquals(-4 * 3600, despuesDelCambio.atZone(nuevaYork).offset.totalSeconds)
    }

    // ------------------------------------------------------------------ pasado

    @Test
    fun `agendar en una hora ya pasada se rechaza`() {
        assertEquals(ErrorTurno.EnElPasado, validar(hora = 9, ahoraDeReferencia = instanteDeTurno(hora = 10))) // sigue con 10:00 de referencia
    }

    @Test
    fun `agendar exactamente en el instante actual es valido`() {
        // La regla es `inicio >= ahora`, no `>`: agendar en este mismo minuto es legitimo.
        assertNull(validar(hora = 10, ahoraDeReferencia = instanteDeTurno(hora = 10)))
    }

    @Test
    fun `un minuto despues del instante actual ya esta en el pasado`() {
        assertEquals(
            ErrorTurno.EnElPasado,
            validar(hora = 10, ahoraDeReferencia = instanteDeTurno(hora = 10).plusSeconds(60)),
        )
    }

    @Test
    fun `la ventana se valida antes que el pasado`() {
        // Un horario de las 23:00 esta fuera de ventana y en el pasado a la vez. Se reporta
        // el primer motivo, y es el de la forma: las 23:00 no se pueden agendar ni en un
        // dia futuro.
        assertEquals(
            ErrorTurno.FueraDeVentana,
            validar(hora = 23, ahoraDeReferencia = instanteDeTurno(hora = 10)),
        )
    }

    @Test
    fun `la hora siguiente es siempre agendable`() {
        assertNull(validar(hora = 11, ahoraDeReferencia = instanteDeTurno(hora = 7)))
    }
}