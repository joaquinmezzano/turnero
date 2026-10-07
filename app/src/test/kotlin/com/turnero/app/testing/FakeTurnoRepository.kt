package com.turnero.app.testing

import com.turnero.app.domain.model.ErrorTurno
import com.turnero.app.domain.model.EstadoTurno
import com.turnero.app.domain.model.EstadisticasCliente
import com.turnero.app.domain.model.Turno
import com.turnero.app.domain.model.TurnoConServicio
import com.turnero.app.domain.repository.TurnoRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import java.time.Instant
import java.util.UUID

/**
 * Falso en memoria de [TurnoRepository].
 *
 * Calcado de los falsos de `Cliente` y `Servicio`: reproduce el borrado logico
 * (`eliminar` marca `deletedAt` y la fila sigue en el almacen) y expone `llamadas`, que
 * permite afirmar "una transicion invalida no toca el repositorio", que de otro modo no se
 * puede observar.
 *
 * **El solapamiento NO se implementa acá.** En el repositorio real vive dentro de una
 * transaccion de Room, y un falso que lo reprodujera estaria probando el falso y no el
 * codigo de produccion: el choque se cubre contra Room de verdad en `androidTest` y la
 * regla pura en [com.turnero.app.domain.model.seSolapan]. Para el camino de error,
 * [falloCrear] y [falloActualizar] alcanzan para simular el `ErrorTurno.Solapamiento` que
 * devuelve el repositorio real.
 */
class FakeTurnoRepository : TurnoRepository {

    private val almacen = LinkedHashMap<UUID, Turno>()

    /**
     * Servicios simulados para la proyeccion con `JOIN`. El falso no tiene tabla de
     * servicios: los tests solo necesitan afirmar que el nombre y el color que devuelve la
     * proyeccion llegan a la UI, no la mecanica del `JOIN` (eso se cubre en `androidTest`
     * contra Room de verdad).
     */
    private val serviciosProyectados = mutableMapOf<UUID, ProyeccionServicio>()

    /** Nombre y color que toma la proyeccion cuando el test no registro el servicio. */
    private val servicioPorDefecto = ProyeccionServicio("Servicio", null)

    /**
     * El `Flow` se arma **por lectura**, no una vez y guardado, como en los otros falsos:
     * despues de un [fallarLectura] el canal viejo esta cerrado, y un `Flow` guardado
     * seguiria apuntando a el y no tendria nada que re-emitir.
     */
    private var consultaDelDia: Channel<List<TurnoConServicio>> = nuevoCanalDelDia()

    private var consultaDeCliente: Channel<List<TurnoConServicio>> = nuevoCanalDeCliente()

    private var consultaDeEstadisticas: Channel<EstadisticasCliente> = nuevoCanalDeEstadisticas()

    /** Nombres de los metodos llamados, en orden de invocacion. */
    val llamadas = mutableListOf<String>()

    /** Fallos forzados, uno por operacion. `null` = la operacion funciona. */
    var falloObtenerPorId: Throwable? = null
    var falloCrear: Throwable? = null
    var falloActualizar: Throwable? = null
    var falloCambiarEstado: Throwable? = null
    var falloEliminar: Throwable? = null

    /** Fallo que todavia no vio ninguna coleccion, para que lo vea la proxima. */
    private var falloPendiente: Throwable? = null

    /** Colecciones abiertas ahora mismo sobre cualquiera de los tres streams. */
    var coleccionesActivas: Int = 0
        private set

    /** Colecciones abiertas en total, incluidas las ya terminadas o canceladas. */
    var coleccionesIniciadas: Int = 0
        private set

    /** Turnos no liberados, en orden de insercion. */
    fun vivos(): List<Turno> = almacen.values.filter { it.deletedAt == null }

    /**
     * La fila tal cual esta guardada, **incluida si esta soft-deleted**.
     *
     * Es la unica forma de comprobar que la liberacion dejo la fila y solo le puso
     * `deletedAt`: [vivos] la esconde justamente para poder afirmar que la baja ocurrio.
     */
    fun almacenado(id: UUID): Turno =
        checkNotNull(almacen[id]) { "No hay ninguna fila guardada con el id $id" }

    fun guardar(turno: Turno) {
        almacen[turno.id] = turno
    }

    /** Da de baja el id sin marcar `deletedAt`: simula un registro que nunca existio. */
    fun olvidar(id: UUID) {
        almacen.remove(id)
    }

    /** Registra el servicio que la proyeccion le va a asignar a los turnos con ese id. */
    fun proyectarServicio(servicioId: UUID, nombre: String, color: Int? = null) {
        serviciosProyectados[servicioId] = ProyeccionServicio(nombre, color)
    }

    suspend fun emitirDelDia(turnos: List<Turno>) {
        consultaDelDia.send(turnos.map { conServicio(it) })
    }

    suspend fun emitirDeCliente(turnos: List<Turno>) {
        consultaDeCliente.send(turnos.map { conServicio(it) })
    }

    suspend fun emitirEstadisticas(estadisticas: EstadisticasCliente) {
        consultaDeEstadisticas.send(estadisticas)
    }

    /**
     * Rompe la lectura del dia de la misma forma en que lo haria SQLite: el stream termina
     * con una excepcion, que es lo que el `catch` aguas arriba tiene que ver.
     *
     * **El fallo es transitorio**: al terminar reabre el canal, asi la lectura siguiente
     * funciona. En produccion el `Flow` de Room tampoco muere con un error.
     *
     * **El fallo no se pierde si todavia no habia nadie suscrito**: `viewModelScope` usa un
     * dispatcher que no corre hasta que el test avanza el reloj, asi que un `fallarLectura`
     * inmediato cerraria un canal sin un solo collector y nadie veria la excepcion. Cuando
     * no hay colecciones abiertas, el error queda pendiente para la proxima suscripcion.
     */
    fun fallarLectura(error: Throwable) {
        if (coleccionesActivas == 0) falloPendiente = error
        consultaDelDia.close(error)
        consultaDelDia = nuevoCanalDelDia()
    }

    override fun observarDelDia(desde: Instant, hasta: Instant): Flow<List<TurnoConServicio>> =
        consultaDelDia.receiveAsFlow()
            .map { filas ->
                filas.filter { it.turno.inicio >= desde && it.turno.inicio < hasta }
            }
            .onStart { empezar() }
            .onCompletion { coleccionesActivas-- }

    override fun observarDeCliente(clienteId: UUID): Flow<List<TurnoConServicio>> =
        consultaDeCliente.receiveAsFlow()
            .map { filas ->
                filas.filter { it.turno.clienteId == clienteId }
                    .sortedByDescending { it.turno.inicio }
            }
            .onStart { empezar() }
            .onCompletion { coleccionesActivas-- }

    override fun observarEstadisticasDeCliente(clienteId: UUID): Flow<EstadisticasCliente> =
        consultaDeEstadisticas.receiveAsFlow()
            .onStart { empezar() }
            .onCompletion { coleccionesActivas-- }

    override suspend fun obtenerPorId(id: UUID): Result<Turno?> {
        llamadas += "obtenerPorId"
        falloObtenerPorId?.let { return Result.failure(it) }
        return Result.success(almacen[id]?.takeIf { it.deletedAt == null })
    }

    override suspend fun obtenerPorIdConServicio(id: UUID): Result<TurnoConServicio?> {
        llamadas += "obtenerPorIdConServicio"
        falloObtenerPorId?.let { return Result.failure(it) }
        return Result.success(almacen[id]?.takeIf { it.deletedAt == null }?.let { conServicio(it) })
    }

    override suspend fun crear(turno: Turno): Result<UUID> {
        llamadas += "crear"
        falloCrear?.let { return Result.failure(it) }
        almacen[turno.id] = turno
        return Result.success(turno.id)
    }

    override suspend fun actualizar(turno: Turno): Result<Unit> {
        llamadas += "actualizar"
        falloActualizar?.let { return Result.failure(it) }
        if (!almacen.containsKey(turno.id)) return Result.failure(ErrorTurno.NoExiste)
        almacen[turno.id] = turno
        return Result.success(Unit)
    }

    override suspend fun cambiarEstado(id: UUID, estado: EstadoTurno, momento: Instant): Result<Unit> {
        llamadas += "cambiarEstado"
        falloCambiarEstado?.let { return Result.failure(it) }
        val vivo = almacen[id]?.takeIf { it.deletedAt == null }
            ?: return Result.failure(ErrorTurno.NoExiste)
        almacen[id] = vivo.copy(estado = estado, updatedAt = momento)
        return Result.success(Unit)
    }

    override suspend fun eliminar(id: UUID, momento: Instant): Result<Unit> {
        llamadas += "eliminar"
        falloEliminar?.let { return Result.failure(it) }
        val vivo = almacen[id] ?: return Result.failure(ErrorTurno.NoExiste)
        almacen[id] = vivo.copy(deletedAt = momento, updatedAt = momento)
        return Result.success(Unit)
    }

    private fun conServicio(turno: Turno): TurnoConServicio {
        val servicio = serviciosProyectados[turno.servicioId] ?: servicioPorDefecto
        return TurnoConServicio(
            turno = turno,
            servicioNombre = servicio.nombre,
            servicioColor = servicio.color,
        )
    }

    private fun empezar() {
        coleccionesIniciadas++
        coleccionesActivas++
        falloPendiente?.let { fallo ->
            falloPendiente = null
            throw fallo
        }
    }

    private fun nuevoCanalDelDia(): Channel<List<TurnoConServicio>> = Channel(Channel.BUFFERED)

    private fun nuevoCanalDeCliente(): Channel<List<TurnoConServicio>> = Channel(Channel.BUFFERED)

    private fun nuevoCanalDeEstadisticas(): Channel<EstadisticasCliente> = Channel(Channel.BUFFERED)

    private data class ProyeccionServicio(val nombre: String, val color: Int?)
}