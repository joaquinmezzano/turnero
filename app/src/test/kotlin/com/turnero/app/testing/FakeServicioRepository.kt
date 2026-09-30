package com.turnero.app.testing

import com.turnero.app.domain.model.Servicio
import com.turnero.app.domain.repository.ServicioRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.time.Instant
import java.util.UUID

/**
 * Falso en memoria de [ServicioRepository].
 *
 * Reproduce las dos caracteristicas del repositorio real que los use cases tienen que
 * respetar: el borrado es logico (`eliminar` marca `deletedAt` y la fila sigue en el
 * almacen) y `obtenerPorId` devuelve `null` para lo que ya esta eliminado.
 *
 * Ademas expone `llamadas`, que registra el nombre de cada metodo invocado: permite
 * afirmar "una validacion fallida no toca el repositorio", que de otro modo no se
 * puede observar.
 */
class FakeServicioRepository : ServicioRepository {

    private val almacen = LinkedHashMap<UUID, Servicio>()

    /**
     * Stream que devuelve [observarTodos].
     *
     * Es un `Channel` y no un `MutableSharedFlow` por una razon concreta: para simular
     * que la lectura falla hay que **cerrar el stream con una excepcion**
     * (`close(cause)`), y `MutableSharedFlow` no expone `close`. Con `receiveAsFlow` el
     * error le llega al collector por el canal de excepciones del `Flow`, exactamente
     * como le llega uno de SQLite.
     *
     * Sin `replay`, como un `Flow` de Room: el collector arranca esperando, no con un
     * valor, que es lo que deja observable el estado `cargando = true` del ViewModel.
     */
    private val consulta = Channel<List<Servicio>>(Channel.BUFFERED)

    val servicios: Flow<List<Servicio>> = consulta.receiveAsFlow()

    /** Nombres de los metodos llamados, en orden de invocacion. */
    val llamadas = mutableListOf<String>()

    /** Fallos forzados, uno por operacion. `null` = la operacion funciona. */
    var falloCrear: Throwable? = null
    var falloActualizar: Throwable? = null
    var falloEliminar: Throwable? = null
    var falloObtenerPorId: Throwable? = null

    /** Servicios no eliminados, en orden de insercion. */
    fun vivos(): List<Servicio> = almacen.values.filter { it.deletedAt == null }

    /**
     * La fila tal cual esta guardada, **incluida si esta soft-deleted**.
     *
     * Es la unica forma de comprobar que la baja logica dejo la fila y solo le puso
     * `deletedAt`: [vivos] la esconde justamente para poder afirmar que el borrado ocurrio.
     */
    fun almacenado(id: UUID): Servicio =
        checkNotNull(almacen[id]) { "No hay ninguna fila guardada con el id $id" }

    fun guardar(servicio: Servicio) {
        almacen[servicio.id] = servicio
    }

    /** Da de baja el id sin marcar `deletedAt`: simula un registro que nunca existio. */
    fun olvidar(id: UUID) {
        almacen.remove(id)
    }

    suspend fun emitir(servicios: List<Servicio>) {
        consulta.send(servicios)
    }

    /**
     * Rompe la lectura de la misma forma en que lo haria SQLite: el stream termina con
     * una excepcion, que es lo que el `catch` aguas arriba del ViewModel tiene que ver.
     */
    fun fallarLectura(error: Throwable) {
        consulta.close(error)
    }

    override fun observarTodos(): Flow<List<Servicio>> = servicios

    override suspend fun obtenerPorId(id: UUID): Result<Servicio?> {
        llamadas += "obtenerPorId"
        falloObtenerPorId?.let { return Result.failure(it) }
        return Result.success(almacen[id]?.takeIf { it.deletedAt == null })
    }

    override suspend fun crear(servicio: Servicio): Result<UUID> {
        llamadas += "crear"
        falloCrear?.let { return Result.failure(it) }
        almacen[servicio.id] = servicio
        return Result.success(servicio.id)
    }

    override suspend fun actualizar(servicio: Servicio): Result<Unit> {
        llamadas += "actualizar"
        falloActualizar?.let { return Result.failure(it) }
        almacen[servicio.id] = servicio
        return Result.success(Unit)
    }

    override suspend fun eliminar(id: UUID, momento: Instant): Result<Unit> {
        llamadas += "eliminar"
        falloEliminar?.let { return Result.failure(it) }
        val vivo = almacen[id] ?: return Result.success(Unit)
        almacen[id] = vivo.copy(deletedAt = momento, updatedAt = momento)
        return Result.success(Unit)
    }
}