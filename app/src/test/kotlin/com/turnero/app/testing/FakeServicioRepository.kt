package com.turnero.app.testing

import com.turnero.app.domain.model.Servicio
import com.turnero.app.domain.repository.ServicioRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
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
     *
     * Es `var` y no `val` porque [fallarLectura] lo reabre: un `Channel` cerrado con causa
     * no vuelve a entregar nada, asi que sin el reemplazo una caida de lectura seria
     * irreversible en el test y no se podria probar que `reintentar()` recupera la
     * pantalla. En produccion el `Flow` de Room tampoco muere: el `catch` del ViewModel es
     * terminal, pero la base sigue ahi y una nueva suscripcion lee el snapshot.
     */
    private var consulta: Channel<List<Servicio>> = nuevoCanal()

    /**
     * El `Flow` se arma **por lectura**, no una vez y guardado.
     *
     * Si se guardara el `receiveAsFlow()` de un canal en un `val`, seguiria apuntando al
     * canal viejo despues de un [fallarLectura] y `reintentar()` volveria a suscribirse al
     * stream ya cerrado, que solo sabe devolver la excepcion. Es lo mismo que hace Room:
     * cada `collect` abre una consulta nueva.
     *
     * El `onStart` hace dos cosas. Una, contar las colecciones: los dos contadores se
     * balancean porque el `onCompletion` de abajo corre tambien cuando este bloque tira,
     * y eso es justo lo que hay que poder observar — si el `reintentar()` del ViewModel no
     * cancelara la coleccion anterior, quedarian dos suscriptores vivos al mismo stream.
     * Dos, entregar el [falloPendiente] que dejó [fallarLectura].
     */
    val servicios: Flow<List<Servicio>>
        get() = consulta.receiveAsFlow()
            .onStart {
                coleccionesIniciadas++
                coleccionesActivas++
                falloPendiente?.let { fallo ->
                    falloPendiente = null
                    throw fallo
                }
            }
            .onCompletion { coleccionesActivas-- }

    /**
     * Fallo que todavia no vio ninguna coleccion, para que lo vea la proxima.
     *
     * Vive aca y no en [fallarLectura] porque depende de si el stream tenia suscriptores:
     * ver el KDoc de ese metodo.
     */
    private var falloPendiente: Throwable? = null

    /** Colecciones abiertas ahora mismo sobre el stream. */
    var coleccionesActivas: Int = 0
        private set

    /** Colecciones abiertas en total, incluidas las ya terminadas o canceladas. */
    var coleccionesIniciadas: Int = 0
        private set

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
     *
     * **El fallo es transitorio.** Al terminar cierra el canal con la excepcion y abre uno
     * nuevo, asi la lectura siguiente funciona: en produccion el `Flow` de Room tampoco
     * muere con un error, el `catch` del ViewModel es el que es terminal. Sin el
     * reemplazo no se podria probar que `reintentar()` recupera la pantalla.
     *
     * **El fallo no se pierde si todavia no habia nadie suscrito**, que es el caso
     * normal: `viewModelScope` usa un dispatcher que no corre hasta que el test avanza el
     * reloj, asi que un `fallarLectura` inmediatamente despues de construir el ViewModel
     * cerraria un canal sin un solo collector y nadie veria nunca la excepcion. Por eso,
     * cuando no hay colecciones abiertas, el error queda pendiente para la proxima
     * suscripcion — y solo para una, que es lo que hace la base cuando se recupera.
     */
    fun fallarLectura(error: Throwable) {
        if (coleccionesActivas == 0) falloPendiente = error
        consulta.close(error)
        consulta = nuevoCanal()
    }

    private fun nuevoCanal(): Channel<List<Servicio>> = Channel(Channel.BUFFERED)

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