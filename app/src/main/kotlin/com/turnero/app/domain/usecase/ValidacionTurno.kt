package com.turnero.app.domain.usecase

import com.turnero.app.domain.model.ErrorTurno
import com.turnero.app.domain.model.VentanaAtencion
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Validacion compartida por los use cases que escriben un `Turno` (alta y edicion).
 *
 * Vive aparte y no como metodo de los use cases por el mismo motivo que
 * `ValidacionCliente.kt`: si `crear` y `actualizar` tuvieran cada uno su copia, el dia que
 * se agregue una regla nueva seguro se olvide en uno de los dos.
 *
 * Devuelve el primer error encontrado o `null` si el inicio es valido.
 */
internal fun validarInicioTurno(
    fecha: LocalDate,
    hora: LocalTime,
    ahora: Instant,
    zona: ZoneId,
    ventana: VentanaAtencion,
): ErrorTurno? {
    // Solo importa la hora de inicio: un turno puede terminar despues del cierre. `hasta`
    // es inclusive, asi que 22:00 arranca bien.
    val horaAnclada = hora.truncatedTo(ChronoUnit.HOURS)
    if (horaAnclada.isBefore(ventana.desde) || horaAnclada.isAfter(ventana.hasta)) {
        return ErrorTurno.FueraDeVentana
    }

    if (anclarInicio(fecha, horaAnclada, zona).isBefore(ahora)) {
        return ErrorTurno.EnElPasado
    }

    return null
}

/**
 * Convierte fecha + hora local en el `Instant` de arranque, **en punto**.
 *
 * El anclaje no es cosmetico: la hora es la unidad de reserva, y el turno se guarda con el
 * minuto y el segundo en cero porque toda la agenda (grilla, solapamiento, recordatorios)
 * razona por hora completa. Construir el `Instant` aca y no en la pantalla tiene una
 * consecuencia util: la UI no puede guardar un turno en 10:37 aunque intente, y la zona la
 * decide el dominio con la inyectada, nunca un `GMT-3` hardcodeado en un ViewModel.
 */
internal fun anclarInicio(fecha: LocalDate, hora: LocalTime, zona: ZoneId): Instant =
    fecha.atTime(hora.hour, 0).atZone(zona).toInstant()