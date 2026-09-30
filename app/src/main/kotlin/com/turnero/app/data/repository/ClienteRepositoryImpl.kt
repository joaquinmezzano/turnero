package com.turnero.app.data.repository

import com.turnero.app.core.result.capturandoErrores
import com.turnero.app.core.text.ordenandoPor
import com.turnero.app.data.local.dao.ClienteDao
import com.turnero.app.data.mapper.toDomain
import com.turnero.app.data.mapper.toEntity
import com.turnero.app.domain.model.Cliente
import com.turnero.app.domain.repository.ClienteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Implementación Room de [ClienteRepository].
 *
 * No contiene lógica de negocio: solo traduce entidad <-> modelo y envuelve las
 * excepciones de SQLite en `Result` para que ninguna cruce la frontera de dominio. Las
 * validaciones viven en los use cases.
 *
 * Usa `capturandoErrores` y no `runCatching` para que la cancelación de coroutines no se
 * reporte como un fallo de negocio.
 */
class ClienteRepositoryImpl @Inject constructor(
    private val dao: ClienteDao,
) : ClienteRepository {

    /**
     * El orden alfabético real lo aplica [ordenandoPor], no el `ORDER BY` del DAO:
     * `COLLATE NOCASE` de SQLite no ordena en español. Ver [OrdenAlfabetico].
     */
    override fun observarTodos(): Flow<List<Cliente>> =
        dao.observarTodos().map { entidades -> entidades.map { it.toDomain() }.ordenandoPor { it.nombre } }

    override suspend fun obtenerPorId(id: UUID): Result<Cliente?> = capturandoErrores {
        dao.obtenerPorId(id)?.toDomain()
    }

    override suspend fun crear(cliente: Cliente): Result<UUID> = capturandoErrores {
        check(dao.insertar(cliente.toEntity()) != -1L) {
            "Room no pudo insertar el cliente ${cliente.id}"
        }
        cliente.id
    }

    override suspend fun actualizar(cliente: Cliente): Result<Unit> = capturandoErrores {
        check(dao.actualizar(cliente.toEntity()) == 1) {
            "Room no actualizó ninguna fila para el cliente ${cliente.id}"
        }
    }

    override suspend fun eliminar(id: UUID, momento: Instant): Result<Unit> = capturandoErrores {
        check(dao.marcarEliminado(id, momento) == 1) {
            "Room no marcó como eliminado al cliente $id"
        }
    }
}
