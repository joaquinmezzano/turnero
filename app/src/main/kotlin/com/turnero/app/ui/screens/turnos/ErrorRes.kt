package com.turnero.app.ui.screens.turnos

import androidx.annotation.StringRes
import com.turnero.app.R
import com.turnero.app.domain.model.ErrorTurno

@StringRes
fun Throwable.aErrorResTurno(): Int = when (this) {
    is ErrorTurno.SinServicio -> R.string.error_turno_sin_servicio
    is ErrorTurno.SinCliente -> R.string.error_turno_sin_cliente
    is ErrorTurno.Solapamiento -> R.string.error_turno_solapamiento
    is ErrorTurno.EnElPasado -> R.string.error_turno_en_el_pasado
    is ErrorTurno.FueraDeVentana -> R.string.error_turno_fuera_de_ventana
    is ErrorTurno.TransicionInvalida -> R.string.error_turno_transicion_invalida
    is ErrorTurno.NoExiste -> R.string.error_turno_no_existe
    else -> R.string.error_generico
}
