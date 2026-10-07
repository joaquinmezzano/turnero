package com.turnero.app.data.local.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.turnero.app.domain.model.ESTADOS_QUE_BLOQUEAN
import com.turnero.app.domain.model.EstadoTurno
import com.turnero.app.testing.COLOR_BASE
import com.turnero.app.testing.INSTANTE_BASE
import com.turnero.app.testing.InMemoryTurneroDatabaseRule
import com.turnero.app.testing.entidadServicio
import com.turnero.app.testing.entidadTurno
import com.turnero.app.testing.finDelDia
import com.turnero.app.testing.inicioDelDia
import com.turnero.app.testing.instanteDeTurno
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Tests del DAO de turnos contra Room de verdad. JUnit 4 a proposito: `androidx.test` esta
 * construido sobre JUnit 4 y no sobre Jupiter (ver AGENTS.md).
 *
 * Nombres de tests en camelCase y no con backticks de la suite de `src/test`: `androidTest`
 * pasa por el dexer, y D8 rechaza los nombres de clase con espacios (`Space characters in
 * SimpleName ... are not allowed prior to DEX version 040`).
 *
 * El foco son dos reglas que solo se pueden verificar corriendo SQL de verdad:
 *
 * 1. **`deletedAt IS NULL` en toda query.** La regla de oro del proyecto: el bug mas
 *    facil de introducir al agregar una consulta y el mas dificil de ver en la UI,
 *    porque la fila borrada aparece sin ningun sintoma raro. Por eso cada query se
 *    prueba al menos una vez con un turno eliminado adentro.
 * 2. **El intervalo semiabierto `[inicio, fin)` de `obtenerQueSolapan`.** Es la query
 *    que decide si el horario esta ocupado; un signo mal puesto (`>=` en vez de `>`)
 *    llenaria la agenda de "solapamientos" contra turnos que no chocan, o dejaria pasar
 *    dobles reservas. La regla de dominio `seSolapan` ya esta testeada en `src/test`; lo
 *    que solo se puede probar aca es que el SQL la implemente igual.
 *
 * La excepcion deliberada a la regla de soft delete (`observarUltimaVisita`, que no
 * filtra `servicios.deletedAt`) tiene el motivo escrito en el KDoc del test que la cubre:
 * por fuera parece una violacion de AGENTS.md y alguien podria "arreglarla" sin leerlo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TurnoDaoTest {

    @get:Rule
    val baseDeDatos = InMemoryTurneroDatabaseRule()

    private val dao: TurnoDao get() = baseDeDatos.turnoDao

    /** DAO de servicios de la regla, para armar los JOINs de `observarUltimaVisita`. */
    private val servicios: ServicioDao get() = baseDeDatos.dao

    /**
     * Los estados que bloquean, en la forma que el DAO espera: **nombres** de la
     * constante `estado`, porque la columna es TEXT. Es la misma lista que calcula
     * `TurnoRepositoryImpl` para pasarle a la query.
     */
    private val nombresQueBloquean: List<String> = ESTADOS_QUE_BLOQUEAN.map { it.name }

    // ---------------------------------------------------------------- observarDelDia

    @Test
    fun observarDelDiaDevuelveLosTurnosDelRangoOrdenadosPorHora() = runTest {
        dao.insertar(entidadTurno(inicio = instanteDeTurno(12)))
        dao.insertar(entidadTurno(inicio = instanteDeTurno(10)))
        dao.insertar(entidadTurno(inicio = instanteDeTurno(21)))

        val delDia = dao.observarDelDia(inicioDelDia(), finDelDia()).first()

        assertEquals(
            listOf(instanteDeTurno(10), instanteDeTurno(12), instanteDeTurno(21)),
            delDia.map { it.inicio },
        )
    }

    @Test
    fun observarDelDiaEsSemiabiertoYDejaFueraAlTurnoDelLimiteSuperior() = runTest {
        // Un turno que arranca exactamente en `hasta` es del dia siguiente: es el mismo
        // criterio del solapamiento, por el mismo motivo. Los cuatro turnos de aca estan
        // "en el reloj" de alguien, pero solo uno cae en [08:00, 22:00).
        val enElLimiteInferior = entidadTurno(inicio = instanteDeTurno(8))
        val enElLimiteSuperior = entidadTurno(inicio = instanteDeTurno(22))
        val delDiaAnterior = entidadTurno(inicio = instanteDeTurno(10, dia = 15))
        val delDiaSiguiente = entidadTurno(inicio = instanteDeTurno(10, dia = 17))
        dao.insertar(enElLimiteInferior)
        dao.insertar(enElLimiteSuperior)
        dao.insertar(delDiaAnterior)
        dao.insertar(delDiaSiguiente)

        val delDia = dao.observarDelDia(instanteDeTurno(8), instanteDeTurno(22)).first()

        assertEquals(listOf(enElLimiteInferior.id), delDia.map { it.id })
    }

    @Test
    fun unTurnoSoftDeletedDesapareceDeObservarDelDia() = runTest {
        val liberado = entidadTurno(inicio = instanteDeTurno(10))
        val vivo = entidadTurno(inicio = instanteDeTurno(11))
        dao.insertar(liberado)
        dao.insertar(vivo)

        dao.marcarEliminado(liberado.id, INSTANTE_BASE.plusSeconds(60))

        // El vivo queda como control: si la query devolviera vacia por cualquier otra
        // razon, este test no estaria comprobando el filtro de deletedAt.
        val delDia = dao.observarDelDia(inicioDelDia(), finDelDia()).first()
        assertEquals(listOf(vivo.id), delDia.map { it.id })
    }

    // ---------------------------------------------------------------- observarDeCliente

    @Test
    fun observarDeClienteFiltraPorClienteOrdenaDelMasRecienteAlMasAntiguoYEscondeLosEliminados() = runTest {
        val cliente = UUID.randomUUID()
        val otroCliente = UUID.randomUUID()
        dao.insertar(entidadTurno(clienteId = cliente, inicio = instanteDeTurno(10)))
        dao.insertar(entidadTurno(clienteId = cliente, inicio = instanteDeTurno(12)))
        dao.insertar(entidadTurno(clienteId = otroCliente, inicio = instanteDeTurno(11)))
        val liberado = entidadTurno(clienteId = cliente, inicio = instanteDeTurno(14))
        dao.insertar(liberado)
        dao.marcarEliminado(liberado.id, INSTANTE_BASE.plusSeconds(60))

        val historial = dao.observarDeCliente(cliente).first()

        assertEquals(
            listOf(instanteDeTurno(12), instanteDeTurno(10)),
            historial.map { it.inicio },
        )
    }

    // ---------------------------------------------------------------- obtenerQueSolapan

    @Test
    fun dosTurnosConLaMismaHoraSeSolapan() = runTest {
        val existente = entidadTurno(inicio = instanteDeTurno(10), estado = EstadoTurno.CONFIRMADO)
        dao.insertar(existente)

        // La franja que se quiere reservar: [10:00, 11:00). Con turnos anclados en punto,
        // "misma hora" es el caso mayoritario del solapamiento.
        val encontrados = dao.obtenerQueSolapan(
            desde = instanteDeTurno(10),
            hasta = instanteDeTurno(11),
            excluirId = null,
            estados = nombresQueBloquean,
        )

        assertEquals(listOf(existente.id), encontrados.map { it.id })
    }

    @Test
    fun unTurnoQueTerminaALas10YOtroQueArrancaALas10NoSeSolapan() = runTest {
        // 09:00 + 60 min = fin 10:00 exacto. El intervalo semiabierto hace que el punto
        // de contacto no cuente como choque: si este test falla, alguien cambio el
        // `> :desde` por un `>=` y lleno la agenda de solapamientos falsos.
        val queTermina = entidadTurno(inicio = instanteDeTurno(9), duracionMin = 60)
        dao.insertar(queTermina)

        val encontrados = dao.obtenerQueSolapan(
            desde = instanteDeTurno(10),
            hasta = instanteDeTurno(11),
            excluirId = null,
            estados = nombresQueBloquean,
        )

        assertTrue("El contacto exacto no es solapamiento", encontrados.isEmpty())
    }

    @Test
    fun unTurnoQueArrancaEnElLimiteSuperiorDelIntervaloBuscadoNoSeSolapa() = runTest {
        // La otra mitad del semiabierto: `inicio < :hasta` sin `<=`. El turno existente
        // empieza 11:00 y el intervalo buscado termina 11:00.
        val posterior = entidadTurno(inicio = instanteDeTurno(11))
        dao.insertar(posterior)

        val encontrados = dao.obtenerQueSolapan(
            desde = instanteDeTurno(9),
            hasta = instanteDeTurno(11),
            excluirId = null,
            estados = nombresQueBloquean,
        )

        assertTrue(encontrados.isEmpty())
    }

    @Test
    fun unServicioDe90MinutosChocaConUnaHoraPosteriorAunqueNoEmpieceAhi() = runTest {
        // 10:00 + 90 min = 11:30: ocupa la celda de 11:00 sin arrancar en ella. El `fin`
        // no es columna, se calcula en SQL (`inicio + duracionMin * 60000`), y este es el
        // test que verifica esa aritmetica contra datos reales.
        val largo = entidadTurno(inicio = instanteDeTurno(10), duracionMin = 90)
        dao.insertar(largo)

        val encontrados = dao.obtenerQueSolapan(
            desde = instanteDeTurno(11),
            hasta = instanteDeTurno(12),
            excluirId = null,
            estados = nombresQueBloquean,
        )

        assertEquals(listOf(largo.id), encontrados.map { it.id })
    }

    @Test
    fun soloLosEstadosQueLleganPorParametroBloqueanElHorario() = runTest {
        dao.insertar(entidadTurno(inicio = instanteDeTurno(10), estado = EstadoTurno.CANCELADO))
        dao.insertar(entidadTurno(inicio = instanteDeTurno(10), estado = EstadoTurno.AUSENTE))
        dao.insertar(entidadTurno(inicio = instanteDeTurno(10), estado = EstadoTurno.PENDIENTE))

        val encontrados = dao.obtenerQueSolapan(
            desde = instanteDeTurno(10),
            hasta = instanteDeTurno(11),
            excluirId = null,
            estados = nombresQueBloquean,
        )

        assertEquals(
            listOf(EstadoTurno.PENDIENTE),
            encontrados.map { it.estado },
        )
    }

    @Test
    fun laListaDeEstadosEsLaQueDecideQueBloqueaNoUnaListaFijaEnLaQuery() = runTest {
        dao.insertar(entidadTurno(inicio = instanteDeTurno(10), estado = EstadoTurno.CANCELADO))
        dao.insertar(entidadTurno(inicio = instanteDeTurno(10), estado = EstadoTurno.ATENDIDO))

        val soloCancelados = dao.obtenerQueSolapan(
            desde = instanteDeTurno(10),
            hasta = instanteDeTurno(11),
            excluirId = null,
            estados = listOf("CANCELADO"),
        )
        val soloAtendidos = dao.obtenerQueSolapan(
            desde = instanteDeTurno(10),
            hasta = instanteDeTurno(11),
            excluirId = null,
            estados = listOf("ATENDIDO"),
        )

        assertEquals(listOf(EstadoTurno.CANCELADO), soloCancelados.map { it.estado })
        assertEquals(listOf(EstadoTurno.ATENDIDO), soloAtendidos.map { it.estado })
    }

    @Test
    fun excluirIdDejaFueraAEseTurnoSinEsconderALosDemas() = runTest {
        val primero = entidadTurno(inicio = instanteDeTurno(10))
        val segundo = entidadTurno(inicio = instanteDeTurno(10))
        dao.insertar(primero)
        dao.insertar(segundo)

        val sinExcluir = dao.obtenerQueSolapan(
            desde = instanteDeTurno(10),
            hasta = instanteDeTurno(11),
            excluirId = null,
            estados = nombresQueBloquean,
        )
        val sinPrimero = dao.obtenerQueSolapan(
            desde = instanteDeTurno(10),
            hasta = instanteDeTurno(11),
            excluirId = primero.id,
            estados = nombresQueBloquean,
        )
        val conIdDesconocido = dao.obtenerQueSolapan(
            desde = instanteDeTurno(10),
            hasta = instanteDeTurno(11),
            excluirId = UUID.randomUUID(),
            estados = nombresQueBloquean,
        )

        assertEquals(setOf(primero.id, segundo.id), sinExcluir.map { it.id }.toSet())
        assertEquals(listOf(segundo.id), sinPrimero.map { it.id })
        assertEquals(2, conIdDesconocido.size)
    }

    @Test
    fun unTurnoEliminadoNoFiguraEntreLosQueSolapan() = runTest {
        val liberado = entidadTurno(inicio = instanteDeTurno(10))
        dao.insertar(liberado)
        dao.marcarEliminado(liberado.id, INSTANTE_BASE.plusSeconds(60))

        val encontrados = dao.obtenerQueSolapan(
            desde = instanteDeTurno(10),
            hasta = instanteDeTurno(11),
            excluirId = null,
            estados = nombresQueBloquean,
        )

        assertTrue(
            "Una fila con deletedAt no puede bloquear el horario",
            encontrados.isEmpty(),
        )
    }

    @Test
    fun obtenerQueSolapanDevuelveLosChoquesOrdenadosPorHoraDeInicio() = runTest {
        dao.insertar(entidadTurno(inicio = instanteDeTurno(12)))
        dao.insertar(entidadTurno(inicio = instanteDeTurno(11)))
        dao.insertar(entidadTurno(inicio = instanteDeTurno(10)))

        val encontrados = dao.obtenerQueSolapan(
            desde = instanteDeTurno(10),
            hasta = instanteDeTurno(13),
            excluirId = null,
            estados = nombresQueBloquean,
        )

        assertEquals(
            listOf(instanteDeTurno(10), instanteDeTurno(11), instanteDeTurno(12)),
            encontrados.map { it.inicio },
        )
    }

    // ---------------------------------------------------------------- observarConteosPorEstado

    @Test
    fun observarConteosPorEstadoAgrupaSoloLosTurnosVivosDelCliente() = runTest {
        val cliente = UUID.randomUUID()
        val otroCliente = UUID.randomUUID()
        dao.insertar(entidadTurno(clienteId = cliente, inicio = instanteDeTurno(10)))
        dao.insertar(entidadTurno(clienteId = cliente, inicio = instanteDeTurno(11)))
        dao.insertar(
            entidadTurno(
                clienteId = cliente,
                inicio = instanteDeTurno(12),
                estado = EstadoTurno.CONFIRMADO,
            ),
        )
        val liberado = entidadTurno(
            clienteId = cliente,
            inicio = instanteDeTurno(13),
            estado = EstadoTurno.CANCELADO,
        )
        dao.insertar(liberado)
        dao.marcarEliminado(liberado.id, INSTANTE_BASE.plusSeconds(60))
        // De otro cliente: tiene que quedar afuera aunque este vivo.
        dao.insertar(entidadTurno(clienteId = otroCliente, inicio = instanteDeTurno(10)))

        val conteos = dao.observarConteosPorEstado(cliente).first()

        // `GROUP BY` omite los estados sin filas (el relleno en cero lo hace el dominio),
        // por eso se compara como mapa y no como lista: el orden de las filas no esta
        // garantizado. Si el liberado contara, apareceria CANCELADO -> 1.
        assertEquals(
            mapOf(EstadoTurno.PENDIENTE to 2, EstadoTurno.CONFIRMADO to 1),
            conteos.associate { it.estado to it.cantidad },
        )
    }

    @Test
    fun unClienteSinTurnosNoDevuelveFilasDeConteo() = runTest {
        val conteos = dao.observarConteosPorEstado(UUID.randomUUID()).first()

        assertTrue(conteos.isEmpty())
    }

    // ---------------------------------------------------------------- observarUltimaVisita

    /**
     * El nombre del servicio **sobrevive al soft delete del servicio**.
     *
     * Este test cubre la unica excepcion deliberada a la regla de "toda query filtra
     * `deletedAt`": `observarUltimaVisita` **no** filtra `servicios.deletedAt` (si filtra
     * `turnos.deletedAt`, siempre). El motivo: `turnos` guarda snapshot de `duracionMin`
     * pero no del nombre, asi que si el servicio se borrara, un `JOIN` filtrado devolveria
     * vacio justo donde el dato existe y la ultima visita de la ficha perdia "Corte de
     * pelo" — un hecho historico, no un dato vigente.
     *
     * Si este test falla, **no se arregla el test**: alguien aplico el grep de `deletedAt`
     * de AGENTS.md sobre esta query y la "corrigio". El KDoc de la query lo explica.
     */
    @Test
    fun elNombreDelServicioSobreviveAlSoftDeleteDelServicio() = runTest {
        val servicio = entidadServicio(nombre = "Corte de pelo")
        servicios.insertar(servicio)
        val cliente = UUID.randomUUID()
        dao.insertar(
            entidadTurno(
                clienteId = cliente,
                servicioId = servicio.id,
                inicio = instanteDeTurno(10),
            ),
        )

        servicios.marcarEliminado(servicio.id, INSTANTE_BASE.plusSeconds(60))

        // Tiene que seguir saliendo el nombre aunque el servicio ya no este vivo: "este
        // turno fue un Corte de pelo" es un hecho historico.
        val visita = dao.observarUltimaVisita(cliente).first()
        assertEquals("Corte de pelo", visita.single().servicioNombre)
        assertEquals(EstadoTurno.PENDIENTE, visita.single().estado)
    }

    @Test
    fun observarUltimaVisitaIgnoraLosTurnosEliminados() = runTest {
        val servicio = entidadServicio(nombre = "Corte")
        servicios.insertar(servicio)
        val cliente = UUID.randomUUID()
        val turno = entidadTurno(
            clienteId = cliente,
            servicioId = servicio.id,
            inicio = instanteDeTurno(10),
        )
        dao.insertar(turno)

        dao.marcarEliminado(turno.id, INSTANTE_BASE.plusSeconds(60))

        assertTrue(
            "La query filtra turnos.deletedAt aunque no filtra el del servicio",
            dao.observarUltimaVisita(cliente).first().isEmpty(),
        )
    }

    @Test
    fun observarUltimaVisitaTomaElTurnoMasRecienteDeCualquierEstado() = runTest {
        val servicio = entidadServicio(nombre = "Coloracion")
        servicios.insertar(servicio)
        val cliente = UUID.randomUUID()
        dao.insertar(
            entidadTurno(
                clienteId = cliente,
                servicioId = servicio.id,
                inicio = instanteDeTurno(10, dia = 2),
                estado = EstadoTurno.ATENDIDO,
            ),
        )
        dao.insertar(
            entidadTurno(
                clienteId = cliente,
                servicioId = servicio.id,
                inicio = instanteDeTurno(15),
                estado = EstadoTurno.CANCELADO,
            ),
        )

        val visita = dao.observarUltimaVisita(cliente).first().single()

        // Un CANCELADO reciente es informacion de contacto: el estado no se filtra al
        // elegir el mas reciente, se filtra recien al calcular los conteos.
        assertEquals(EstadoTurno.CANCELADO, visita.estado)
        assertEquals(instanteDeTurno(15), visita.inicio)
        assertEquals("Coloracion", visita.servicioNombre)
    }

    @Test
    fun observarUltimaVisitaVieneVaciaSiElClienteNoTieneTurnos() = runTest {
        val visita = dao.observarUltimaVisita(UUID.randomUUID()).first()

        assertTrue(visita.isEmpty())
    }

    // ---------------------------------------------------------------- agenda e historial con JOIN

    /**
     * La agenda del dia resuelve el nombre del servicio con un `JOIN` y **no filtra
     * `servicios.deletedAt`**, por el mismo motivo que `observarUltimaVisita`: "este turno
     * fue un Corte de pelo" es un hecho historico, y un servicio dado de baja no debe
     * dejar la grilla sin nombre ni color.
     */
    @Test
    fun laAgendaConServicioSobreviveAlSoftDeleteDelServicio() = runTest {
        val servicio = entidadServicio(nombre = "Corte de pelo")
        servicios.insertar(servicio)
        dao.insertar(
            entidadTurno(servicioId = servicio.id, inicio = instanteDeTurno(10)),
        )

        servicios.marcarEliminado(servicio.id, INSTANTE_BASE.plusSeconds(60))

        val delDia = dao.observarDelDiaConServicio(inicioDelDia(), finDelDia()).first()
        assertEquals("Corte de pelo", delDia.single().servicioNombre)
        assertEquals(COLOR_BASE, delDia.single().servicioColor)
    }

    @Test
    fun laAgendaConServicioSigueFiltrandoLosTurnosEliminados() = runTest {
        val servicio = entidadServicio()
        servicios.insertar(servicio)
        val liberado = entidadTurno(servicioId = servicio.id, inicio = instanteDeTurno(10))
        val vivo = entidadTurno(servicioId = servicio.id, inicio = instanteDeTurno(11))
        dao.insertar(liberado)
        dao.insertar(vivo)

        dao.marcarEliminado(liberado.id, INSTANTE_BASE.plusSeconds(60))

        // El vivo queda como control: si la query devolviera vacia por la baja del
        // servicio, este test no estaria comprobando el filtro de turnos.deletedAt.
        val delDia = dao.observarDelDiaConServicio(inicioDelDia(), finDelDia()).first()
        assertEquals(listOf(vivo.id), delDia.map { it.id })
    }

    @Test
    fun elHistorialConServicioSobreviveAlSoftDeleteDelServicio() = runTest {
        val servicio = entidadServicio(nombre = "Corte de pelo")
        servicios.insertar(servicio)
        val cliente = UUID.randomUUID()
        dao.insertar(
            entidadTurno(
                clienteId = cliente,
                servicioId = servicio.id,
                inicio = instanteDeTurno(10),
            ),
        )

        servicios.marcarEliminado(servicio.id, INSTANTE_BASE.plusSeconds(60))

        val historial = dao.observarDeClienteConServicio(cliente).first()
        assertEquals("Corte de pelo", historial.single().servicioNombre)
    }

    @Test
    fun elHistorialConServicioSigueFiltrandoLosTurnosEliminados() = runTest {
        val servicio = entidadServicio()
        servicios.insertar(servicio)
        val cliente = UUID.randomUUID()
        val turno = entidadTurno(clienteId = cliente, servicioId = servicio.id)
        dao.insertar(turno)

        dao.marcarEliminado(turno.id, INSTANTE_BASE.plusSeconds(60))

        assertTrue(dao.observarDeClienteConServicio(cliente).first().isEmpty())
    }

    @Test
    fun obtenerPorIdConServicioTraeElNombreAunqueElServicioEsteBorrado() = runTest {
        val servicio = entidadServicio(nombre = "Corte")
        servicios.insertar(servicio)
        val turno = entidadTurno(servicioId = servicio.id)
        dao.insertar(turno)

        servicios.marcarEliminado(servicio.id, INSTANTE_BASE.plusSeconds(60))

        val conServicio = dao.obtenerPorIdConServicio(turno.id)
        assertEquals("Corte", conServicio?.servicioNombre)
        assertEquals(turno.id, conServicio?.id)
    }

    @Test
    fun obtenerPorIdConServicioDevuelveNullSiElTurnoFueLiberado() = runTest {
        val servicio = entidadServicio()
        servicios.insertar(servicio)
        val turno = entidadTurno(servicioId = servicio.id)
        dao.insertar(turno)
        dao.marcarEliminado(turno.id, INSTANTE_BASE.plusSeconds(60))

        assertNull(dao.obtenerPorIdConServicio(turno.id))
    }

    // ---------------------------------------------------------------- marcarEliminado

    @Test
    fun marcarEliminadoDejaLaFilaEnLaTablaConDeletedAtInformado() = runTest {
        val turno = entidadTurno(inicio = instanteDeTurno(10))
        val momento = INSTANTE_BASE.plusSeconds(60)
        dao.insertar(turno)

        val afectadas = dao.marcarEliminado(turno.id, momento)

        // La fila **no** se borra: es lo que habilita el sync futuro y el historial.
        // Se lee por SQL crudo porque el DAO, justamente, esconde las filas borradas.
        assertEquals(1, afectadas)
        assertEquals(1, contarFilas())
        assertEquals(momento.toEpochMilli(), leerDeletedAt())
    }

    @Test
    fun marcarEliminadoSacaAlTurnoDeObservarDelDiaYObtenerPorId() = runTest {
        val turno = entidadTurno(inicio = instanteDeTurno(10))
        dao.insertar(turno)

        dao.marcarEliminado(turno.id, INSTANTE_BASE.plusSeconds(60))

        assertTrue(dao.observarDelDia(inicioDelDia(), finDelDia()).first().isEmpty())
        assertNull(dao.obtenerPorId(turno.id))
    }

    @Test
    fun marcarEliminadoEsIdempotenteYNoPisaLaFechaOriginal() = runTest {
        val turno = entidadTurno()
        val primerMomento = INSTANTE_BASE.plusSeconds(60)
        dao.insertar(turno)

        val primera = dao.marcarEliminado(turno.id, primerMomento)
        val segunda = dao.marcarEliminado(turno.id, INSTANTE_BASE.plusSeconds(7_200))

        assertEquals("La primera baja tiene que tocar una fila", 1, primera)
        assertEquals("La segunda baja no deberia tocar ninguna", 0, segunda)
        assertEquals(primerMomento.toEpochMilli(), leerDeletedAt())
    }

    @Test
    fun marcarEliminadoDeUnIdInexistenteNoTocaNingunaFila() = runTest {
        dao.insertar(entidadTurno())

        val afectadas = dao.marcarEliminado(UUID.randomUUID(), INSTANTE_BASE.plusSeconds(60))

        assertEquals(0, afectadas)
        assertNull(leerDeletedAt())
    }

    // ---------------------------------------------------------------- insertar / obtenerPorId

    @Test
    fun insertarDevuelveUnRowidValidoYObtenerPorIdDevuelveElMismoTurno() = runTest {
        val turno = entidadTurno(
            inicio = instanteDeTurno(10),
            estado = EstadoTurno.CONFIRMADO,
            notas = "Aviso por WhatsApp",
        )

        val rowid = dao.insertar(turno)

        assertNotEquals("El repositorio trata -1 como error de Room", -1L, rowid)
        assertEquals(turno, dao.obtenerPorId(turno.id))
    }

    @Test
    fun insertarDosVecesElMismoIdNoDuplicaLaFila() = runTest {
        val turno = entidadTurno()
        dao.insertar(turno)

        val error = runCatching { dao.insertar(turno.copy(notas = "pisada")) }
            .exceptionOrNull()

        assertTrue(
            "Se esperaba un choque de clave primaria y llego: $error",
            error is SQLiteConstraintException,
        )
        assertEquals(1, contarFilas())
        assertNull("El insert fallido no pudo pisar la fila original", dao.obtenerPorId(turno.id)?.notas)
    }

    @Test
    fun obtenerPorIdDevuelveNullParaUnTurnoEliminado() = runTest {
        val turno = entidadTurno()
        dao.insertar(turno)

        dao.marcarEliminado(turno.id, INSTANTE_BASE.plusSeconds(60))

        assertNull(dao.obtenerPorId(turno.id))
    }

    @Test
    fun obtenerPorIdDevuelveNullParaUnIdQueNoExiste() = runTest {
        assertNull(dao.obtenerPorId(UUID.randomUUID()))
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Cuenta **todas** las filas de `turnos`, incluidas las borradas.
     *
     * Necesario porque cualquier lectura por el DAO esconde las filas con `deletedAt`,
     * y lo que hay que verificar es justamente que la baja no las destruyo.
     */
    private fun contarFilas(): Int =
        baseDeDatos.db.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM turnos")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getInt(0)
            }

    /**
     * Lee `deletedAt` de la unica fila de `turnos`.
     *
     * Mismo patron que `ClienteDaoTest.leerColumna`: asume una sola fila, porque los
     * tests que la usan insertan un unico turno y contar filas de mas seria una senal de
     * que el escenario no es el que el test cree.
     */
    private fun leerDeletedAt(): Long? =
        baseDeDatos.db.openHelper.readableDatabase
            .query("SELECT deletedAt FROM turnos")
            .use { cursor ->
                assertEquals("Se esperaba exactamente una fila", 1, cursor.count)
                assertTrue(cursor.moveToFirst())
                if (cursor.isNull(0)) null else cursor.getLong(0)
            }
}