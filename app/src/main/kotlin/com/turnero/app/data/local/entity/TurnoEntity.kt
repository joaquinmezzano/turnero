package com.turnero.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.turnero.app.domain.model.EstadoTurno
import java.time.Instant
import java.util.UUID

/**
 * Fila de la tabla `turnos`.
 *
 * `id` es un `UUID` generado en Kotlin (obligatorio para sync futuro) y no un
 * autoincremental: Room lo persiste nativamente como BLOB de 16 bytes, sin converter.
 *
 * **`estado` no tiene type converter.** `EstadoTurno` es el primer enum que entra a la base
 * y Room 2.8.5 lo persiste solo, como TEXT con el nombre de la constante, via
 * `EnumColumnTypeAdapter`. Agregar un converter a proposito cambiaria la afinidad de la
 * columna y el `identityHash` del esquema; ver `Converters.kt`.
 *
 * `duracionMin` es el **snapshot** de la duracion del servicio al momento de agendar, no
 * una relacion. Si el servicio cambia de duracion, estos turnos no se mueven (ver `Turno`).
 *
 * **Indice sobre `inicio`.** A diferencia de `clientes` y `servicios`, que no tienen indice
 * porque sus consultas no filtran por rango, las dos consultas nuevas del slice (turnos de
 * un dia y solapamiento) filtran por rango de `inicio`. Sin indice, cada doble toque
 * recorre la tabla entera.
 *
 * **El indice no es parcial, y no por decision.** El diseno pide
 * `... ON turnos(inicio) WHERE deletedAt IS NULL`, pero el `@Index` de Room 2.8.5 no tiene
 * forma de expresar el `WHERE`: sus unicos parametros son `value`, `orders`, `name` y
 * `unique` (verificado por bytecode en `room-common`). El indice termina siendo completo y
 * cubre igualmente el rango; lo que se pierde es acotar las filas borradas, que son una
 * fraccion del historico. Queda anotado en el reporte del slice para decidir si vale la
 * pena crear el indice parcial a mano con SQL crudo en un `Migration` cuando la base llegue
 * a un dispositivo real.
 */
@Entity(
    tableName = "turnos",
    indices = [Index(value = ["inicio"], name = "idx_turnos_vivos_inicio")],
)
data class TurnoEntity(
    @PrimaryKey val id: UUID,
    val clienteId: UUID,
    val servicioId: UUID,
    val inicio: Instant,
    val duracionMin: Int,
    val estado: EstadoTurno,
    val notas: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
)