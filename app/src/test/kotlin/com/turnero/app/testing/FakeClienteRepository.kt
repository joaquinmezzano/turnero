package com.turnero.app.testing

import com.turnero.app.domain.model.Cliente
import com.turnero.app.domain.repository.ClienteRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import java.time.Instant
import java.util.UUID

/**
 * Falso en memoria de [ClienteRepository], calcado del de `Servicio`.
 *
 * Reproduce las dos características del repositorio real que los use cases tienen que
 * respetar: el borrado es lógico (`eliminar` marca `deletedAt` y la fila sigue en el
 * almacén) y `obtenerPorId` devuelve `null` para lo que ya está eliminado.
 *
 * Unlike el de servicios, el alta y la edición guardan el `Cliente` tal como llega,
 * porque la normalización vive en los use cases y probarla acá probaría el fake.
 */
class FakeClienteRepository : ClienteRepository {

    private val almacen = LinkedHashMap<UUID, Cliente>()

    /**
     * Stream que devuelve [observarTodos].
     *
     * Es un `Channel` y no un `MutableSharedFlow` por una razón concreta: para simular
     * que la lectura falla hay que **cerrar el stream con una excepción**
     * (`close(cause)`), y `MutableSharedFlow` no expone `close`. Con `receiveAsFlow` el
     * error le llega al collector por el canal de excepciones del `Flow`, exactamente
     * como le llega uno de SQLite.
     *
     * Sin `replay`, como un `Flow` de Room: el collector arranca esperando, no con un
     * valor, que es lo que deja observable el estado `cargando = true` del ViewModel.
     *
     * Es `var` y no `val` porque [fallarLectura] lo reabre: un `Channel` cerrado con causa
     * no vuelve a entregar nada, así que sin el reemplazo una caída de lectura sería
     * irreversible en el test y no se podría probar que `reintentar()` recupera la
     * pantalla. En producción el `Flow` de Room tampoco muere: el `catch` del ViewModel es
     * terminal, pero la base sigue ahí y una nueva suscripción lee el snapshot.
     */
    private var consulta: Channel<List<Cliente>> = nuevoCanal()

    /**
     * El `Flow` se arma **por lectura**, no una vez y guardado.
     *
     * Si se guardara el `receiveAsFlow()` de un canal en un `val`, seguiría apuntando al
     * canal viejo después de un [fallarLectura] y `reintentar()` volvería a suscribirse al
     * stream ya cerrado, que solo sabe devolver la excepción. Es lo mismo que hace Room:
     * cada `collect` abre una consulta nueva.
     *
     * El `onStart` hace dos cosas. Una, contar las colecciones: los dos contadores se
     * balancean porque el `onCompletion` de abajo corre también cuando este bloque tira,
     * y eso es justo lo que hay que poder observar — si el `reintentar()` del ViewModel no
     * cancelara la colección anterior, quedarían dos suscriptores vivos al mismo stream.
     * Dos, entregar el [falloPendiente] que dejó [fallarLectura].
     */
    val clientes: Flow<List<Cliente>>
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
     * Fallo que todavía no vio ninguna colección, para que lo vea la próxima.
     *
     * Vive acá y no en [fallarLectura] porque depende de si el stream tenía suscriptores:
     * ver el KDoc de ese método.
     */
    private var falloPendiente: Throwable? = null

    /** Colecciones abiertas ahora mismo sobre el stream. */
    var coleccionesActivas: Int = 0
        private set

    /** Colecciones abiertas en total, incluidas las ya terminadas o canceladas. */
    var coleccionesIniciadas: Int = 0
        private set

    /** Nombres de los métodos llamados, en orden de invocación. */
    val llamadas = mutableListOf<String>()

    /** Fallos forzados, uno por operación. `null` = la operación funciona. */
    var falloCrear: Throwable? = null
    var falloActualizar: Throwable? = null
    var falloEliminar: Throwable? = null
    var falloObtenerPorId: Throwable? = null

    /** Clientes no eliminados, en orden de inserción. */
    fun vivos(): List<Cliente> = almacen.values.filter { it.deletedAt == null }

    /**
     * La fila tal cual está guardada, **incluida si está soft-deleted**.
     *
     * Es la única forma de comprobar que la baja lógica dejó la fila y solo le puso
     * `deletedAt`: [vivos] la esconde justamente para poder afirmar que el borrado ocurrió.
     */
    fun almacenado(id: UUID): Cliente =
        checkNotNull(almacen[id]) { "No hay ninguna fila guardada con el id $id" }

    fun guardar(cliente: Cliente) {
        almacen[cliente.id] = cliente
    }

    /** Da de baja el id sin marcar `deletedAt`: simula un registro que nunca existió. */
    fun olvidar(id: UUID) {
        almacen.remove(id)
    }

    suspend fun emitir(clientes: List<Cliente>) {
        consulta.send(clientes)
    }

    /**
     * Rompe la lectura de la misma forma en que lo haría SQLite: el stream termina con
     * una excepción, que es lo que el `catch` aguas arriba del ViewModel tiene que ver.
     *
     * **El fallo es transitorio.** Al terminar cierra el canal con la excepción y abre uno
     * nuevo, así la lectura siguiente funciona: en producción el `Flow` de Room tampoco
     * muere con un error, el `catch` del ViewModel es el que es terminal. Sin el
     * reemplazo no se podría probar que `reintentar()` recupera la pantalla.
     *
     * **El fallo no se pierde si todavía no hay nadie suscrito.** `runTest` vacía la cola
     * del `TestCoroutineScheduler` antes de la primera línea del cuerpo, así que el
     * `collect` que abre el `init` del ViewModel ya está activo cuando el test llama a
     * `fallarLectura`, y el error le llega directo por el canal de excepciones. Cuando no
     * lo está —típico si el ViewModel se arma dentro del cuerpo del test, después de que
     * `runTest` vació la cola— `close(error)` no tendría a quién avisarle, así que el error
     * queda pendiente para la próxima suscripción, y solo para una: es lo que hace la base
     * cuando se recupera.
     */
    fun fallarLectura(error: Throwable) {
        if (coleccionesActivas == 0) falloPendiente = error
        consulta.close(error)
        consulta = nuevoCanal()
    }

    private fun nuevoCanal(): Channel<List<Cliente>> = Channel(Channel.BUFFERED)

    override fun observarTodos(): Flow<List<Cliente>> = clientes

    override suspend fun obtenerPorId(id: UUID): Result<Cliente?> {
        llamadas += "obtenerPorId"
        falloObtenerPorId?.let { return Result.failure(it) }
        return Result.success(almacen[id]?.takeIf { it.deletedAt == null })
    }

    override suspend fun crear(cliente: Cliente): Result<UUID> {
        llamadas += "crear"
        falloCrear?.let { return Result.failure(it) }
        almacen[cliente.id] = cliente
        return Result.success(cliente.id)
    }

    override suspend fun actualizar(cliente: Cliente): Result<Unit> {
        llamadas += "actualizar"
        falloActualizar?.let { return Result.failure(it) }
        almacen[cliente.id] = cliente
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