package com.turnero.app.data.repository

import com.turnero.app.core.result.capturandoErrores
import com.turnero.app.core.text.ordenandoPor
import com.turnero.app.data.local.dao.ServicioDao
import com.turnero.app.data.mapper.toDomain
import com.turnero.app.data.mapper.toEntity
import com.turnero.app.domain.model.Servicio
import com.turnero.app.domain.repository.ServicioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Implementacion Room de [ServicioRepository].
 *
 * No contiene logica de negocio: solo traduce entidad <-> modelo y envuelve las
 * excepciones de SQLite en `Result` para que ninguna cruce la frontera de dominio.
 * Las validaciones viven en los use cases.
 *
 * Usa `capturandoErrores` y no `runCatching` para que la cancelacion de coroutines no
 * se reporte como un fallo de negocio.
 */
class ServicioRepositoryImpl @Inject constructor(
    private val dao: ServicioDao,
) : ServicioRepository {

    /**
     * El orden alfabetico real lo aplica [ordenandoPor], no el `ORDER BY` del DAO:
     * `COLLATE NOCASE` de SQLite no ordena en espanol. Ver [OrdenAlfabetico].
     */
    override fun observarTodos(): Flow<List<Servicio>> =
        dao.observarTodos().map { entidades -> entidades.map { it.toDomain() }.ordenandoPor { it.nombre } }

    override suspend fun obtenerPorId(id: UUID): Result<Servicio?> = capturandoErrores {
        dao.obtenerPorId(id)?.toDomain()
    }

    override suspend fun crear(servicio: Servicio): Result<UUID> = capturandoErrores {
        check(dao.insertar(servicio.toEntity()) != -1L) {
            "Room no pudo insertar el servicio ${servicio.id}"
        }
        servicio.id
    }

    override suspend fun actualizar(servicio: Servicio): Result<Unit> = capturandoErrores {
        check(dao.actualizar(servicio.toEntity()) == 1) {
            "Room no actualizo ninguna fila para el servicio ${servicio.id}"
        }
    }

    override suspend fun eliminar(id: UUID, momento: Instant): Result<Unit> = capturandoErrores {
        check(dao.marcarEliminado(id, momento) == 1) {
            "Room no marco como eliminado al servicio $id"
        }
    }
}
