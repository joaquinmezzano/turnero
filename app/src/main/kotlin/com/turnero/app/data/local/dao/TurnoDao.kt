package com.turnero.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.turnero.app.data.local.entity.TurnoEntity
import com.turnero.app.data.local.projection.ConteoEstado
import com.turnero.app.data.local.projection.TurnoConServicioFila
import com.turnero.app.data.local.projection.UltimaVisitaFila
import com.turnero.app.domain.model.EstadoTurno
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

/**
 * DAO de turnos.
 *
 * **Toda `@Query` filtra `deletedAt IS NULL`**, con la unica excepcion deliberada de las
 * queries que resuelven el nombre del servicio de un turno: esas filtran `turnos.deletedAt`
 * siempre, pero **no** exigen que el servicio siga vivo. El motivo esta escrito en el KDoc
 * de cada una, porque por fuera parece una violacion de AGENTS.md.
 */
@Dao
interface TurnoDao {

    /**
     * Turnos cuyo `inicio` cae en `[desde, hasta)`, ordenados por hora.
     *
     * Semiabierto a proposito: un turno que arranca exactamente en `hasta` es del dia
     * siguiente. Es el mismo criterio del solapamiento, por el mismo motivo.
     */
    @Query(
        "SELECT * FROM turnos " +
            "WHERE deletedAt IS NULL AND inicio >= :desde AND inicio < :hasta " +
            "ORDER BY inicio ASC",
    )
    fun observarDelDia(desde: Instant, hasta: Instant): Flow<List<TurnoEntity>>

    /**
     * Igual que [observarDelDia] pero con el nombre y el color del servicio resueltos por
     * `JOIN`.
     *
     * **Excepcion deliberada y obligatoria a la regla de "toda query filtra `deletedAt`":
     * esta query NO filtra `servicios.deletedAt`.** Filtra `turnos.deletedAt IS NULL`
     * siempre; lo que no exige es que el servicio siga vivo.
     *
     * Motivo: `turnos` guarda snapshot de `duracionMin` pero no del nombre del servicio. Si
     * un servicio se borra, los turnos historicos que lo usaron no tendrian nombre ni color
     * que mostrar, y un `JOIN` filtrado devolveria vacio justo donde el dato existe. "Este
     * turno fue un Corte de pelo" es un hecho historico, no un dato vigente. Sin esta
     * excepcion, la grilla y el historial perderian informacion real.
     *
     * El rango es semiabierto `[desde, hasta)` con el mismo criterio que [observarDelDia].
     */
    @Query(
        "SELECT t.*, s.nombre AS servicioNombre, s.color AS servicioColor " +
            "FROM turnos t " +
            "INNER JOIN servicios s ON s.id = t.servicioId " +
            "WHERE t.deletedAt IS NULL AND t.inicio >= :desde AND t.inicio < :hasta " +
            "ORDER BY t.inicio ASC",
    )
    fun observarDelDiaConServicio(desde: Instant, hasta: Instant): Flow<List<TurnoConServicioFila>>

    /** Turnos del cliente, del mas reciente al mas antiguo. Sin limite: es el historial. */
    @Query(
        "SELECT * FROM turnos " +
            "WHERE deletedAt IS NULL AND clienteId = :clienteId " +
            "ORDER BY inicio DESC, createdAt DESC",
    )
    fun observarDeCliente(clienteId: UUID): Flow<List<TurnoEntity>>

    /**
     * Igual que [observarDeCliente] pero con el nombre y el color del servicio resueltos
     * por `JOIN`.
     *
     * **Misma excepcion deliberada que [observarDelDiaConServicio]**: no filtra
     * `servicios.deletedAt`, porque el servicio de un turno historico es un hecho, no un
     * dato vigente. Filtra `turnos.deletedAt IS NULL` siempre. El motivo completo esta en
     * el KDoc de [observarDelDiaConServicio].
     */
    @Query(
        "SELECT t.*, s.nombre AS servicioNombre, s.color AS servicioColor " +
            "FROM turnos t " +
            "INNER JOIN servicios s ON s.id = t.servicioId " +
            "WHERE t.deletedAt IS NULL AND t.clienteId = :clienteId " +
            "ORDER BY t.inicio DESC, t.createdAt DESC",
    )
    fun observarDeClienteConServicio(clienteId: UUID): Flow<List<TurnoConServicioFila>>

    /**
     * Turnos vivos que **solapan** con el intervalo `[desde, hasta)` y ademas ocupan el
     * horario.
     *
     * El intervalo es semiabierto, asi que la interseccion es `inicio < hasta AND fin >
     * desde`, exactamente la regla de `seSolapan` en `domain`. Un turno que termina 10:00
     * y otro que arranca 10:00 no se tocan.
     *
     * `fin` no existe como columna porque se deriva de `inicio + duracionMin`, y por eso se
     * calcula en SQL: asi el filtro se resuelve en SQLite sin traer filas a Kotlin. Como
     * los turnos arrancan en punto, el caso mayoritario es "misma hora", pero un servicio
     * de mas de 60 minutos ocupa varias celdas y la interseccion tiene que ser general.
     *
     * @param desde momento de arranque del intervalo buscado.
     * @param hasta momento de fin del intervalo buscado (excluido del choque).
     * @param excluirId turno a ignorar, para que editar uno no lo haga chocar consigo
     * mismo. `null` significa "no excluir ninguno".
     * @param estados estados que bloquean el horario, resuelto por el dominio
     * (`ESTADOS_QUE_BLOQUEAN`) y no escrito aqui, para que la regla no viva en dos lados.
     */
    @Query(
        "SELECT * FROM turnos " +
            "WHERE deletedAt IS NULL " +
            "AND estado IN (:estados) " +
            "AND inicio < :hasta " +
            "AND (inicio + duracionMin * 60000) > :desde " +
            "AND (:excluirId IS NULL OR id != :excluirId) " +
            "ORDER BY inicio ASC",
    )
    suspend fun obtenerQueSolapan(
        desde: Instant,
        hasta: Instant,
        excluirId: UUID?,
        estados: List<String>,
    ): List<TurnoEntity>

    /** `@Insert` devuelve el rowid de SQLite, no el UUID. El id lo conoce el modelo. */
    @Insert
    suspend fun insertar(entity: TurnoEntity): Long

    @Query("SELECT * FROM turnos WHERE id = :id AND deletedAt IS NULL")
    suspend fun obtenerPorId(id: UUID): TurnoEntity?

    /**
     * Igual que [obtenerPorId] pero con el nombre y el color del servicio resueltos por
     * `JOIN`.
     *
     * **Misma excepcion deliberada que [observarDelDiaConServicio]**: no filtra
     * `servicios.deletedAt`. La ficha de un turno de un servicio ya dado de baja tiene que
     * seguir mostrando de que servicio fue. Filtra `turnos.deletedAt IS NULL` siempre.
     */
    @Query(
        "SELECT t.*, s.nombre AS servicioNombre, s.color AS servicioColor " +
            "FROM turnos t " +
            "INNER JOIN servicios s ON s.id = t.servicioId " +
            "WHERE t.id = :id AND t.deletedAt IS NULL",
    )
    suspend fun obtenerPorIdConServicio(id: UUID): TurnoConServicioFila?

    /**
     * Edicion completa del turno.
     *
     * Es un `@Query` condicional y no un `@Update` a proposito: `@Update` resuelve por PK
     * solamente y no mira `deletedAt`, asi que escribiria la copia leida con
     * `deletedAt = null` y **resucitaria** un turno liberado en la ventana intermedia. El
     * `AND deletedAt IS NULL` devuelve 0 en ese caso y el repositorio lo traduce a
     * `ErrorTurno.NoExiste`.
     */
    @Query(
        "UPDATE turnos SET clienteId = :clienteId, servicioId = :servicioId, inicio = :inicio, " +
            "duracionMin = :duracionMin, estado = :estado, notas = :notas, updatedAt = :updatedAt " +
            "WHERE id = :id AND deletedAt IS NULL",
    )
    suspend fun actualizar(
        id: UUID,
        clienteId: UUID,
        servicioId: UUID,
        inicio: Instant,
        duracionMin: Int,
        estado: EstadoTurno,
        notas: String?,
        updatedAt: Instant,
    ): Int

    /**
     * Cambio de estado: no toca `inicio`, `clienteId` ni `servicioId`.
     *
     * Es separado de [actualizar] a proposito. El estado se cambia por la maquina de
     * estados, yendo por [actualizar] se abriria una puerta para que una pantalla escribiera
     * un estado arbitrario sin pasar por `accionesDisponibles`.
     */
    @Query("UPDATE turnos SET estado = :estado, updatedAt = :momento WHERE id = :id AND deletedAt IS NULL")
    suspend fun cambiarEstado(id: UUID, estado: EstadoTurno, momento: Instant): Int

    /**
     * Soft delete (`AccionTurno.LIBERAR`): marca `deletedAt`, no borra la fila.
     *
     * El `AND deletedAt IS NULL` la hace idempotente: una segunda liberacion del mismo id
     * devuelve 0 y no pisa la fecha original.
     */
    @Query("UPDATE turnos SET deletedAt = :momento, updatedAt = :momento WHERE id = :id AND deletedAt IS NULL")
    suspend fun marcarEliminado(id: UUID, momento: Instant): Int

    /**
     * Conteo por estado del cliente, para las estadisticas de la ficha.
     *
     * Filtra `deletedAt IS NULL` como toda query: un turno liberado no cuenta. El mapeo
     * rellena en cero los estados que no aparecen.
     */
    @Query(
        "SELECT estado, COUNT(*) AS cantidad FROM turnos " +
            "WHERE deletedAt IS NULL AND clienteId = :clienteId " +
            "GROUP BY estado",
    )
    fun observarConteosPorEstado(clienteId: UUID): Flow<List<ConteoEstado>>

    /**
     * Turno mas reciente del cliente, **de cualquier estado**, con el nombre de su servicio.
     *
     * **Excepcion deliberada y obligatoria a la regla de "toda query filtra `deletedAt`":
     * esta query NO filtra `servicios.deletedAt`.** Filtra `turnos.deletedAt IS NULL`
     * siempre; lo que no exige es que el servicio siga vivo.
     *
     * Motivo: `turnos` guarda snapshot de `duracionMin` pero no del nombre del servicio. Si
     * un servicio se borra, los turnos historicos que lo usaron no tendrian nombre que
     * mostrar, y un JOIN filtrado devolveria vacio justo donde el dato existe. "Este turno
     * fue un Corte de pelo" es un hecho historico, no un dato vigente. Sin esta excepcion,
     * la ficha del cliente y la ultima visita perderian informacion real.
     *
     * El estado **no** se filtra al elegir el mas reciente: un `CANCELADO` reciente es
     * informacion de contacto y el usuario lo quiere ver. El estado se filtra recien al
     * calcular los conteos.
     */
    @Query(
        "SELECT t.inicio AS inicio, t.estado AS estado, s.nombre AS servicioNombre " +
            "FROM turnos t " +
            "INNER JOIN servicios s ON s.id = t.servicioId " +
            "WHERE t.clienteId = :clienteId AND t.deletedAt IS NULL " +
            "ORDER BY t.inicio DESC " +
            "LIMIT 1",
    )
    fun observarUltimaVisita(clienteId: UUID): Flow<List<UltimaVisitaFila>>
}