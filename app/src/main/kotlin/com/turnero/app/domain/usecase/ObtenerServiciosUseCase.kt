package com.turnero.app.domain.usecase

import com.turnero.app.domain.model.Servicio
import com.turnero.app.domain.repository.ServicioRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Stream de servicios no eliminados.
 *
 * No lleva logica todavia: en el slice 1 el unico filtro posible es el del repositorio
 * (`deletedAt IS NULL`). El caso de uso existe igual porque desde aca en adelante se le
 * van a colgar reglas de negocio (busqueda, orden, cupos) sin tocar la pantalla.
 */
class ObtenerServiciosUseCase @Inject constructor(
    private val repository: ServicioRepository,
) {
    operator fun invoke(): Flow<List<Servicio>> = repository.observarTodos()
}
