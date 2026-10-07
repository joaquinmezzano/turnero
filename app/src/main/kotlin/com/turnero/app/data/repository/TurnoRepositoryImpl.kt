package com.turnero.app.data.repository

import androidx.room.withTransaction
import com.turnero.app.core.result.capturandoErrores
import com.turnero.app.data.local.TurneroDatabase
import com.turnero.app.data.local.dao.TurnoDao
import com.turnero.app.data.mapper.toDomain
import com.turnero.app.data.mapper.toEntity
import com.turnero.app.domain.model.ESTADOS_QUE_BLOQUEAN
import com.turnero.app.domain.model.ErrorTurno
import com.turnero.app.domain.model.EstadoTurno
import com.turnero.app.domain.model.EstadisticasCliente
import com.turnero.app.domain.model.Turno
import com.turnero.app.domain.model.TurnoConServicio
import com.turnero.app.domain.model.fin
import com.turnero.app.domain.repository.TurnoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Implementacion Room de [TurnoRepository].
 *
 * No contiene reglas de negocio: traduce entidad <-> modelo y envuelve las excepciones de
 * SQLite en `Result` para que ninguna cruce la frontera de dominio. Las reglas (que estados
 * bloquean, que transiciones existen) viven en `domain` y llegan por parametro.
 *
 * Usa `capturandoErrores` y no `runCatching` para que la cancelacion de coroutines no se
 * reporte como un fallo de negocio.
 *
 * **La base se inyecta entera, y no solo el DAO, por una razon**: el chequeo de solapamiento
 * y la escritura tienen que ir en la misma transaccion, y Room resuelve eso con
 * `withTransaction`, que es una extension de `RoomDatabase`. Si el chequeo fuera un metodo
 * publico del repositorio, la UI tendria que hacer la carrera en dos pasos, que es
 * justamente la doble reserva que esto evita.
 */
class TurnoRepositoryImpl @Inject constructor(
    private val dao: TurnoDao,
    private val db: TurneroDatabase,
) : TurnoRepository {

    override fun observarDelDia(desde: Instant, hasta: Instant): Flow<List<TurnoConServicio>> =
        dao.observarDelDiaConServicio(desde, hasta).map { filas -> filas.map { it.toDomain() } }

    override fun observarDeCliente(clienteId: UUID): Flow<List<TurnoConServicio>> =
        dao.observarDeClienteConServicio(clienteId).map { filas -> filas.map { it.toDomain() } }

    /**
     * Combina las dos proyecciones en una sola proyeccion de dominio.
     *
     * El relleno de ceros no se hace aca: lo hace `EstadisticasCliente.desde`, que es donde
     * vive la regla de que los cinco estados esten siempre presentes. Este codigo solo
     * traduce filas.
     */
    override fun observarEstadisticasDeCliente(clienteId: UUID): Flow<EstadisticasCliente> =
        combine(
            dao.observarConteosPorEstado(clienteId),
            dao.observarUltimaVisita(clienteId),
        ) { conteos, ultimaVisita ->
            EstadisticasCliente.desde(
                conteos = conteos.associate { it.estado to it.cantidad },
                ultimaVisita = ultimaVisita.firstOrNull()?.toDomain(),
            )
        }

    override suspend fun obtenerPorId(id: UUID): Result<Turno?> = capturandoErrores {
        dao.obtenerPorId(id)?.toDomain()
    }

    override suspend fun obtenerPorIdConServicio(id: UUID): Result<TurnoConServicio?> =
        capturandoErrores {
            dao.obtenerPorIdConServicio(id)?.toDomain()
        }

    override suspend fun crear(turno: Turno): Result<UUID> = capturandoErrores {
        // El chequeo y la escritura van en el MISMO bloque `withTransaction`. Si el lambda
        // terminara despues del chequeo, la transaccion se cerraria ahi y el `insertar`
        // correria en autocommit: un doble toque haria dos lecturas "libre" y dos inserts
        // sueltos, que es exactamente la doble reserva que la transaccion existe para
        // cerrar. Lanzar adentro revierte todo, y `capturandoErrores` lo convierte en
        // `Result.failure`.
        db.withTransaction {
            if (!hayHueco(turno, excluirId = null)) throw ErrorTurno.Solapamiento
            check(dao.insertar(turno.toEntity()) != -1L) {
                "Room no pudo insertar el turno ${turno.id}"
            }
        }
        turno.id
    }

    override suspend fun actualizar(turno: Turno): Result<Unit> = capturandoErrores {
        // Excluir al propio turno no es un detalle: sin eso, un turno se solaparia consigo
        // mismo y no se podrian editar ni sus notas ni su hora sin cambiar de cliente.
        db.withTransaction {
            if (!hayHueco(turno, excluirId = turno.id)) throw ErrorTurno.Solapamiento
            if (dao.actualizar(
                    id = turno.id,
                    clienteId = turno.clienteId,
                    servicioId = turno.servicioId,
                    inicio = turno.inicio,
                    duracionMin = turno.duracionMin,
                    estado = turno.estado,
                    notas = turno.notas,
                    updatedAt = turno.updatedAt,
                ) == 0
            ) {
                throw ErrorTurno.NoExiste
            }
        }
    }

    /**
     * Cambio de estado con el chequeo de solapamiento **solo cuando la transicion "re-blocka"
     * el horario**.
     *
     * Un turno en `CANCELADO` o `AUSENTE` no bloquea su franja, asi que mientras estuvo en
     * ese estado otro turno pudo haber tomado su hora. Si se lo revuelve a `PENDIENTE` o a
     * `CONFIRMADO`, empieza a bloquear de nuevo y podrian quedar dos turnos ocupando la misma
     * hora — el estado que el solapamiento esta pensado para evitar, llegado por otra puerta.
     *
     * Debe ser atomico por el mismo motivo que [crear]: leer y escribir en dos pasos deja
     * abierto el doble toque.
     */
    override suspend fun cambiarEstado(id: UUID, estado: EstadoTurno, momento: Instant): Result<Unit> =
        capturandoErrores {
            db.withTransaction {
                val actual = dao.obtenerPorId(id)?.toDomain() ?: throw ErrorTurno.NoExiste

                val bloqueaba = actual.estado.name in NOMBRES_DE_LOS_QUE_BLOQUEAN
                val vaABloquear = estado.name in NOMBRES_DE_LOS_QUE_BLOQUEAN
                if (!bloqueaba && vaABloquear && !hayHueco(actual, excluirId = id)) {
                    throw ErrorTurno.Solapamiento
                }

                if (dao.cambiarEstado(id, estado, momento) == 0) throw ErrorTurno.NoExiste
            }
        }

    override suspend fun eliminar(id: UUID, momento: Instant): Result<Unit> = capturandoErrores {
        if (dao.marcarEliminado(id, momento) == 0) throw ErrorTurno.NoExiste
    }

    /**
     * `true` si el horario del turno esta libre en el rango que ocupa.
     *
     * **Solo se llama dentro de `withTransaction`.** Si se usara suelta, el chequeo y el
     * `insertar` de al lado serian dos consultas separadas y un doble toque guardaria dos
     * turnos en la misma hora.
     */
    private suspend fun hayHueco(turno: Turno, excluirId: UUID?): Boolean =
        dao.obtenerQueSolapan(
            desde = turno.inicio,
            hasta = turno.fin(),
            excluirId = excluirId,
            estados = NOMBRES_DE_LOS_QUE_BLOQUEAN,
        ).isEmpty()

    private companion object {
        /**
         * Los estados bloqueantes viajan como nombres porque `estado` es TEXT en la base.
         *
         * Se calcula una sola vez: el conjunto no cambia en ejecucion, y la query lo
         * necesita en cada llamada.
         */
        val NOMBRES_DE_LOS_QUE_BLOQUEAN: List<String> = ESTADOS_QUE_BLOQUEAN.map { it.name }
    }
}