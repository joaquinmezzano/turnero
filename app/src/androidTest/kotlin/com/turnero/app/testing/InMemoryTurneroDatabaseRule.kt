package com.turnero.app.testing

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.turnero.app.data.local.TurneroDatabase
import com.turnero.app.data.local.dao.ClienteDao
import com.turnero.app.data.local.dao.ServicioDao
import com.turnero.app.data.local.dao.TurnoDao
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Abre una [TurneroDatabase] in-memory por test y la cierra al terminar.
 *
 * In-memory y no un archivo real porque el objetivo es probar las `@Query` (sobre todo
 * que filtran `deletedAt`), no la persistencia en disco. Reutilizar la base entre tests
 * haria que un test dependa del orden de ejecucion.
 *
 * `allowMainThreadQueries()` hace falta porque los tests de DAO llaman a las funciones
 * `suspend` desde el hilo principal. Es aceptable aca: la base es in-memory y no hay
 * I/O real, y en produccion Room corre las queries en su propio executor.
 */
class InMemoryTurneroDatabaseRule : TestWatcher() {

    private lateinit var baseDeDatos: TurneroDatabase

    val dao: ServicioDao get() = baseDeDatos.servicioDao()

    val clienteDao: ClienteDao get() = baseDeDatos.clienteDao()

    val turnoDao: TurnoDao get() = baseDeDatos.turnoDao()

    /** Acceso a la base cruda, para leer filas que el DAO esconde (p.ej. las borradas). */
    val db: TurneroDatabase get() = baseDeDatos

    override fun starting(description: Description) {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        baseDeDatos = Room.inMemoryDatabaseBuilder(contexto, TurneroDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    override fun finished(description: Description) {
        baseDeDatos.close()
    }
}