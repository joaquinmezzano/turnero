# Slice 3 — Turnos: diseño

Estado: aprobado para implementar.
Fecha: 2026-09-30.

Este slice entrega `Turno` de punta a punta —CRUD, máquina de estados, validación de
solapamiento y estadísticas de cliente— e invierte la arquitectura de información: **Turnos
pasa a ser el hub de la app y Clientes queda como destino secundario**.

No es "la agenda". La agenda con huecos, bloques de horario y bloqueos es del Slice 4; acá
la grilla es una lista de 15 horas agendables.

---

## 1. Por qué cambia la arquitectura de información

Con `Servicio` y `Cliente` solos, Clientes era el arranque natural. Al entrar los turnos, el
turno pasa a ser la unidad de trabajo: el profesional abre la app para ver a quién tiene hoy,
no para buscar un cliente. Por eso `Turnos` es el destino de arranque y la barra inferior
baja a dos destinos.

| | Antes (Slice 2) | Después (Slice 3) |
|---|---|---|
| Arranque | `Clientes` | `Turnos` |
| Barra inferior | Clientes, Servicios | Turnos, Clientes |
| `Servicios` | destino de barra | ícono de overflow en el top bar de Turnos |

`Servicios` sale de la barra porque es un catálogo, no un destino de uso diario, y porque
mantener tres destinos con uno vacío de propósito se paga en cada pantalla.

---

## 2. Dominio

### `EstadoTurno`

Los cinco valores del README, sin cambios:

```
PENDIENTE   CONFIRMADO   ATENDIDO   AUSENTE   CANCELADO
```

**`LIBRE` no es un valor de `EstadoTurno`.** Es la ausencia de fila: un horario está libre
cuando ningún turno lo ocupa, y eso se deriva al pintar. No se materializa una fila por
hora ni por día, porque eso haría crecer la tabla sin límite guardando filas que no son
turnos.

### `Turno`

```
id: UUID
clienteId: UUID
servicioId: UUID
inicio: Instant
duracionMin: Int          ← snapshot del servicio, nunca un JOIN
estado: EstadoTurno
notas: String?
createdAt, updatedAt: Instant
deletedAt: Instant?
```

`duracionMin` se copia del `Servicio` al crear el turno. Si se cambia la duración del
servicio, los turnos ya agendados no se mueven. Esto ya está prometido en `strings.xml` y en
`README.md`, y es la razón por la que el campo existe en lugar de derivarse.

**No se agrega `servicioNombre` al `Turno`.** Ver §5: se resuelve sin tocar el modelo.

### `VentanaAtencion`

```kotlin
data class VentanaAtencion(val desde: LocalTime, val hasta: LocalTime)

val VENTANA_POR_DEFECTO = VentanaAtencion(LocalTime.of(8, 0), LocalTime.of(22, 0))
```

Es un valor inyectado en los use cases que validan, no una constante leída dentro de la
función. El Slice 6 mete `HorarioAtencion` por día de semana y así cambia la implementación
sin tocar los call sites.

### Reglas de la ventana

- Solo importa la **hora de inicio**: `inicio` debe caer dentro de `[08:00, 22:00]`.
- Un turno puede **terminar después de las 22:00**. Aceptado explícitamente.
- Las 22:00 son una hora de inicio válida, inclusive.

### Anclaje a la hora

Todo turno arranca en punto: 10:00, 11:00, 12:00. La hora es la unidad de reserva. Un
servicio de más de 60 minutos ocupa varias celdas: uno de 90 minutos arrancando 10:00 ocupa
la de 10:00 y la de 11:00.

`Servicio.duracionMin` no queda acotado por la ventana de atención.

### No agendar en el pasado

`inicio >= ahora`, con el reloj inyectado y la **zona del dispositivo**. No hay GMT-3
hardcodeado en ninguna parte: el corte es el del teléfono, que para el caso de uso que motivó
la regla es GMT-3.

Como la hora en curso ya empezó, combinado con el anclaje a la hora el primer horario
agendable es **el próximo punto de hora**. A las 10:20, lo más temprano es 11:00.

---

## 3. Máquina de estados

`pre-horario` significa `ahora <= fin del turno`; `post-horario`, `ahora > fin del turno`,
con `fin = inicio + duracionMin`.

| Desde | Hacia | Condición |
|---|---|---|
| `PENDIENTE` | `CONFIRMADO` | siempre |
| `PENDIENTE` | *LIBRE* | siempre — libera el horario con soft delete |
| `CONFIRMADO` | `ATENDIDO` | siempre |
| `CONFIRMADO` | `AUSENTE` | solo post-horario |
| `CONFIRMADO` | `CANCELADO` | pre o post-horario |
| `CANCELADO` | *LIBRE* | solo pre-horario — libera el horario con soft delete |
| `CANCELADO` | `PENDIENTE` | solo pre-horario |
| `CANCELADO` | `CONFIRMADO` | solo pre-horario |

Estados finales, sin salida: `ATENDIDO`, `AUSENTE` en post-horario y `CANCELADO` en
post-horario.

Las dos filas a *LIBRE* no son transiciones de enum: son un **soft delete** del turno, que
ya es el mecanismo de la app para sacar algo de circulación sin perder la fila.

`CANCELADO` en post-horario es final mientras que en pre-horario es reversible, y no es
inconsistente: cancelar un turno que ya terminó es un hecho consumado, deshacerlo escribiría
que el turno nunca existió.

### Dónde vive la tabla

En `domain`, como función pura:

```kotlin
fun accionesDisponibles(turno: Turno, ahora: Instant): Set<AccionTurno>
```

`AccionTurno` modela lo que la UI pinta y lo que el use case ejecuta:

```
CONFIRMAR   MARCAR_ATENDIDO   MARCAR_AUSENTE   CANCELAR
VOLVER_A_PENDIENTE   LIBERAR
```

La UI **no decide**: dibuja lo que devuelve la función. El use case vuelve a llamar la misma
función antes de escribir, así que un futuro sync o import no puede colar una transición
inválida saltándose la UI. Una sola fuente de verdad, testeable sin Android.

---

## 4. Validación de solapamiento

Intervalo semiabierto `[inicio, fin)`. Dos turnos se solapan si sus celdas se tocan.

- Bloquean el horario: `PENDIENTE`, `CONFIRMADO`, `ATENDIDO`.
- Liberan el horario: `AUSENTE`, `CANCELADO`. El turno sigue existiendo y visible en la
  grilla, pero no impide agendar.

Semiabierto significa que un turno que termina 10:00 y otro que arranca 10:00 **no** se
solapan: es el caso más común y tratarlo como choque llenaría la agenda de errores.

Al ser la hora la unidad de reserva, el caso mayoritario es simplemente "misma hora".

### Atomicidad

Chequeo e inserción van dentro de una transacción de Room. Es el único punto del slice donde
un doble toque es un escenario realista, y es exactamente la forma de una doble reserva.

---

## 5. Datos

### Schema v3, sin migración

La v1 y la v2 nunca salieron de desarrollo, así que la v3 se resetea: se borra `2.json` y se
exporta `3.json`. Mismo criterio que el Slice 2, ya registrado en `config/TECH_DEBT.md`.

### Tabla `turnos`

Índice parcial, que acá sí se justifica de entrada y a diferencia de `servicios` y
`clientes`, porque las dos consultas nuevas filtran por rango de `inicio`:

```sql
CREATE INDEX idx_turnos_vivos_inicio ON turnos(inicio) WHERE deletedAt IS NULL;
```

### `estado` sin converter

`EstadoTurno` es el primer enum que entra a la base. Verificado por bytecode en
`room-compiler` 2.8.5: existe `EnumColumnTypeAdapter`, gatillado por
`BuiltInConverterFlags.getEnums()`, y el round-trip usa `Enum.valueOf`. La columna queda
**TEXT** con el nombre de la constante y **no** hace falta tocar `Converters.kt`.

### Excepción deliberada a la regla de `deletedAt`

Las queries que resuelven **el nombre del servicio de un turno** no filtran
`servicios.deletedAt`.

Motivo: `Turno` guarda snapshot de `duracionMin` pero no del nombre. Si un servicio se borra,
los turnos históricos que lo usaron no tienen nombre que mostrar, y el `JOIN` filtrado
devolvería vacío justo donde el dato existe.

Estas queries filtran **siempre** `turnos.deletedAt IS NULL`. Lo que no hacen es exigir que
el servicio siga vivo, porque "este turno fue un Corte de pelo" es un hecho histórico, no
un dato vigente. Sin el servicio borrado, la ficha del cliente y la última visita pierden
información real.

Va escrito con nombre y motivo en el KDoc de cada query, porque por fuera parece una
violación de `AGENTS.md`.

---

## 6. Estadísticas del cliente

Proyección de solo lectura. **Sin tabla nueva y sin caché**: si hubiera contadores
materializados habría que invalidarlos en cada cambio de estado, y cualquier olvido
mostraría números falsos.

Sale de dos consultas:

1. `GROUP BY estado` sobre los turnos no borrados del cliente.
2. El turno más reciente por `inicio`, **de cualquier estado**, con el nombre de su servicio.

### La trampa de los ceros

`GROUP BY` **omite** los estados sin filas. Un cliente con 3 atendidos y 0 cancelados no
devuelve fila `CANCELADO`. El mapeo tiene que rellenar los cinco estados en cero, o la
ficha muestra un hueco donde debería decir `0`.

### Cómo se muestra

```
Última visita
15/03/2026 (Corte de pelo · Cancelado)
```

El turno más reciente de cualquier estado, no solo `ATENDIDO`: un `CANCELADO` reciente es
información de contacto y el usuario lo quiere ver. Entre paréntesis van el servicio y el
estado de ese turno.

El estado **no** se filtra al elegir el más reciente; se filtra al calcular los conteos.

### Donde vive

En la ficha del cliente, en un bloque de estadísticas arriba del historial de turnos.

Esto cierra el diferimiento explícito del Slice 2, que dejó el historial de turnos "pendiente
de la Slice 3".

---

## 7. UI

### `TurnosScreen`

- Top bar con la fecha del día y flechas ‹ › para moverse de día.
- Ícono de overflow con `Servicios`.
- 15 filas, una por hora agendable de 08:00 a 22:00.
- Hora ocupada → chip con el nombre del cliente y el color de su servicio.
- Hora libre → fila vacía.
- Tap en una hora ocupada abre `TurnoDetalle`. Tap en una libre abre el alta con esa hora
  puesta.
- Un turno que termina después de las 22:00 **no** agrega filas: se queda en su celda de
  inicio mostrando su hora real de fin.

### Alta de turno

Dos entradas, como se pidió:

- **Desde Turnos**: la hora viene fijada por el tap; se elige cliente (con el buscador que ya
  existe) y servicio.
- **Desde la ficha del cliente**: el cliente viene fijado; se elige día, hora y servicio.

### `TurnoDetalleScreen`

Datos del turno, las **únicas** acciones que `accionesDisponibles` devuelva, editar y borrar.

El botón de borrar **solo aparece cuando `LIBERAR` está entre las acciones disponibles**, y
eso tiene una consecuencia que conviene que sea explícita: de `CONFIRMADO` no se borra, se
cancela primero. Cancelar un turno confirmado que todavía no empieza es reversible;
liberar de entrada el horario es un salto que la tabla no permite. Lo mismo con `ATENDIDO`,
`AUSENTE` y `CANCELADO` post-horario, que no ofrecen ninguna salida.

Quien quiera el horario libre tiene el camino de siempre: cancelar, y después liberar.

### Ficha del cliente

Suma historial de turnos y bloque de estadísticas. Es el lugar donde la máquina de estados se
vuelve accionable sobre turnos viejos.

---

## 8. Fuera de alcance

Se implementan en su propio slice, no acá:

| Slice | Qué |
|---|---|
| 4 | Agenda día: huecos, `HorarioAtencion` por día, bloqueos, FAB de alta |
| 5 | Agenda semana y mes |
| 6 | Horarios de atención por día de semana |
| 7 | Recordatorios con `AlarmManager` |
| 8 | Intenidos de contacto (WhatsApp, SMS, teléfono) |
| 9 | Búsqueda y filtros |

La grilla de este slice es una lista de horas agendables. No calcula huecos porque no conoce
el horario de atención, que es del Slice 6.

---

## 9. Testing

| Ubicación | Qué |
|---|---|
| `src/test` | Matriz de transiciones exhaustiva: 5 estados × condición temporal, verificando sobre todo que los estados finales no tienen salida |
| `src/test` | Solapamiento: choque, contacto exacto, servicios que ocupan varias celdas, estados que bloquean vs. liberan |
| `src/test` | No agendar en el pasado, anclaje a la hora, ventana 08:00–22:00, turno que termina después del cierre |
| `src/test` | Snapshot de `duracionMin`: cambiar el servicio no mueve el turno |
| `src/test` | Estadísticas: conteos con ceros en los cinco estados, última visita de cualquier estado, formato |
| `src/test` | ViewModels de lista, detalle y alta |
| `androidTest` | DAO: consulta del día, consulta de solapamiento, y que el nombre del servicio sobreviva al soft delete |

El uso del índice **no** se afirma en un test: eso exige leer el plan de ejecución con
`EXPLAIN QUERY PLAN` sobre una base real, y Room en memoria no lo expone. Queda verificado a
mano al armar el índice, y registrado en `config/TECH_DEBT.md` si alguna vez hay que medirlo.

La matriz de transiciones es el test de mayor valor del slice: es una tabla finita y
verificable exhaustivamente, y es donde un error se traduce en historial falso.

`ClienteDaoTest` y `ServicioDaoTest` siguen siendo la base del `androidTest`.

---

## 10. Deudas que este slice crea

Se agregan a `config/TECH_DEBT.md` al cerrar el slice.

| Deuda | Disparador |
|---|---|
| `dd/mm/aaaa` hardcodeado en la última visita | Fase 3, junto con el separador decimal del precio ya registrado |
| `VentanaAtencion` constante en vez de `HorarioAtencion` | Slice 6 |
| La excepción de `servicios.deletedAt` puede leerse como una violación de la regla | Cuando alguien más aplique el grep de `deletedAt` y la encuentre |
| La grilla no conoce horarios de atención y muestra 15 filas fijas | Slice 6 |
