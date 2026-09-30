package com.turnero.app.domain.usecase

import com.turnero.app.core.text.normalizarParaBuscar
import com.turnero.app.core.text.ordenandoPor
import com.turnero.app.domain.model.Cliente
import com.turnero.app.domain.repository.ClienteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Stream de clientes no eliminados, con filtro por nombre.
 *
 * **El filtro vive acá y no en el DAO, a propósito.** Un `LIKE` de SQLite solo es
 * case-insensitive para ASCII, así que `LIKE '%optica%'` no encuentra `"Óptica"` y
 * `LIKE '%óptica%'` no encuentra `"Optica"`: falla en las dos direcciones, que es
 * exactamente como escribe un usuario rioplatense. Un catálogo de clientes de un
 * profesional son cientos de filas y filtrar en memoria es instantáneo, así que el
 * problema no aparece y la única razón para hacerlo en SQL sería ahorrar unos
 * milisegundos que no se notan.
 *
 * La comparación es por **subcadena** y no por prefijo: el usuario recuerda "Pérez" de un
 * cliente que se llama "Pérez, Ana" o "Ana Pérez", y el nombre no siempre empieza ahí.
 *
 * El **criterio** se normaliza una sola vez, al construir el `Flow`, y no en cada
 * emisión: si dependiera de la emisión, escribir una letra en el buscador costaría una
 * normalización por tecla. Los **nombres** sí se normalizan por fila y por emisión, y
 * eso es deliberado: un `Flow` de Room re-emite en cada alta, baja o edición, así que
 * cachear los nombres normalizados exigiría invalidar la cache en la misma escritura que
 * la origina, que es más caro y más fácil de arruinar que volver a normalizar cien
 * cadenas.
 *
 * **El orden.** El `ORDER BY nombre COLLATE NOCASE` del DAO ordena A-Z en ASCII, y en
 * español eso manda `"Álvarez"`, `"Ángela"` o `"Ñuñez"` detrás de `"zebra"`. El orden que
 * el usuario espera lo da [ordenandoPor], con un `Collator` de locale, y lo aplica
 * [ClienteRepository] en cada emisión.
 *
 * Con filtro, el orden se vuelve a aplicar acá. Filtrar una lista ordenada conserva el
 * orden, así que la operación es redundante sobre el repositorio de hoy; queda porque el
 * contrato de este caso de uso es "esto sale en orden de diccionario en español" para el
 * camino de la búsqueda, y porque depender de que la capa de abajo ya ordene es una
 * coincidencia que alguien puede romper por separado. Sin filtro no se repite el `Collator`
 * por la razón contraria: no hay nada que filtrar y la lista ya viene ordenada.
 *
 * @param consulta texto libre. Vacío o en blanco devuelve todos los clientes.
 */
class ObtenerClientesUseCase @Inject constructor(
    private val repository: ClienteRepository,
) {

    operator fun invoke(consulta: String = ""): Flow<List<Cliente>> {
        // El `trim` va antes de normalizar porque [normalizarParaBuscar] no recorta, y
        // buscar `" óptica "` tiene que encontrar lo mismo que `"óptica"`.
        val criterio = normalizarParaBuscar(consulta.trim())
        return repository.observarTodos().map { clientes ->
            if (criterio.isEmpty()) {
                clientes
            } else {
                clientes
                    .filter { normalizarParaBuscar(it.nombre).contains(criterio) }
                    .ordenandoPor { it.nombre }
            }
        }
    }
}
