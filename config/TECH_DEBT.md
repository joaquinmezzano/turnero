# Deuda técnica registrada

Cada ítem dice **qué** se postergó, **por qué** se puede postergar y **qué** lo dispara. Un ítem
sin disparador conocido es una excusa, no una deuda.

Nada de esto bloquea el MVP. Todo esto explota en algún momento si no se atiende.

---

## Slice 1 — `Servicio`

### `servicios` sin índice sobre `nombre`

**Qué.** No hay ningún índice. La query es `WHERE deletedAt IS NULL ORDER BY nombre`.

**Por qué se puede postergar.** La tabla guarda el catálogo de un profesional: decenas de
filas, no millones. Un full scan con sort sobre 50 filas es submilisegundo. Además
`obtenerPorId` ya lo sirve el índice de la PK.

**La trampa.** Un índice normal sobre `nombre` **no sirve** este `ORDER BY`: SQLite ordena
el índice con collation `BINARY`, que no es la de la query, así que el planner igual va a
sortear. Hay que declarar la collation explícitamente:

```sql
CREATE INDEX idx_servicios_vivos_nombre
  ON servicios(nombre COLLATE NOCASE) WHERE deletedAt IS NULL;
```

Ojo: hoy el orden que ve el usuario lo aplica un `Collator` de locale en Kotlin
(`core/text/OrdenAlfabetico.kt`), no el `ORDER BY`. El índice cubre la query de SQL, no el
sort de Kotlin. Cuando la agenda consulte el catálogo por nombre, evaluá de nuevo.

**Disparador.** El catálogo pasa de ~100 filas, o aparecen búsquedas por nombre.

---

### TOCTOU en `ActualizarServicioUseCase`

**Qué.** El use case lee la fila y después la actualiza, sin transacción. En la ventana
intermedia otro flujo podría soft-deletearla; el `@Update` resuelve **por PK solamente** y no
mira `deletedAt`, así que escribiría la copia leída — con `deletedAt = null` — y
**resucitaría la fila**.

**Por qué se puede postergar.** La app es mono-usuario y offline. La UI abre un diálogo
modal, así que no hay dos escrituras concurrentes. La probabilidad real hoy es cero.

**Disparador.** Cualquier escritura concurrente sobre la misma fila: importación de un
catálogo, sync, o un ViewModel que dispare `actualizar` sin pasar por el diálogo.

**Cómo se arregla.** El patrón ya está en el repo: un `@Query` condicional que devuelve
`Int`, como `marcarEliminado` hace. Falta el equivalente
`UPDATE ... WHERE id = :id AND deletedAt IS NULL`, o un `@Transaction`.

---

### `capturandoErrores` no cubre `Error` ni `Throwable`

**Qué.** El helper captura `Exception`, no `Error`. Un `OutOfMemoryError` o un
`StackOverflowError` se propagan en vez de convertirse en `Result.failure`.

**Por qué está bien así.** `Error` no es recuperable: convertirlo en `Result.failure`
deja la app en un estado que no puede seguir. Que reviente es lo correcto.

**Disparador.** Ninguno. Está documentado para que nadie lo "arregle" a `Throwable`.

---

### El `SnackbarHost` del `Scaffold` queda detrás de los diálogos

**Qué.** `ServiciosRoute` compone los diálogos fuera del `Scaffold`, así que el
`SnackbarHost` que vive dentro queda por debajo del scrim. Un error que llegue con un
diálogo abierto no se ve.

**Por qué se puede postergar.** Hoy es inalcanzable: el diálogo se cierra de forma síncrona
antes de que un error pueda llegar.

**Disparador.** El primer flujo que deje un diálogo abierto mientras se dispara una
escritura. El Slice 3 (turnos) es el candidato: guardar un turno y volver atrás.

**Cómo se arregla.** Mover el `SnackbarHost` a un `Box` raíz por encima de los diálogos.

---

### Separador decimal del campo de precio hardcodeado

**Qué.** `aTextoPrecio()` produce `"$unidades,${resto}"` con coma fija, y `aCentavos()`
hace `replace(',', '.')` sin aceptar separador de miles. En un device en `en-US`, editar un
servicio precarga el campo con formato argentino, y `"1.234,56"` no parsea: el usuario ve
"El precio no puede ser negativo", que es un mensaje **falso**.

**Por qué se puede postergar.** El MVP es para un usuario rioplatense. Fase 3 trae
localización y ahí hay que revisarlo.

**Disparador.** El primer device fuera de es-AR/es-ES, o el primer `import` de catálogo.

**Cómo se arregla.** `NumberFormat` con el locale, igual que ya hace bien
`CurrencyFormatter`.

---

### `precioCentavos` no se valida en `domain`

**Qué.** `validarServicio` chequea nombre, duración y precio, pero no `color`. El dominio
acepta cualquier `Int` como ARGB, incluido `0` (totalmente transparente).

**Por qué se puede postergar.** El único productor es `ServicioEditorDialog`, que ofrece
ocho tonos de `ServicioColors`. Es inalcanzable.

**Disparador.** Import de catálogo o sync: un payload remoto con `color = 0` pasa la
validación y renderiza un punto invisible.

---

### `ValidacionServicio.kt` es PascalCase para una función

**Qué.** Los archivos de función top-level en Kotlin se nombran en minúscula. Acá está en
PascalCase porque el nombre parece un tipo.

**Por qué no se movió.** Renombrarlo es cosmético y el nombre es legible. Detekt no lo marca.

**Disparador.** Cuando alguien más aplique el mismo criterio y el nombre quede inconsistente
con el resto.

---

### `javax.inject.Inject` en `domain`

**Qué.** Los use cases importan `javax.inject.Inject`. No es una violación de la regla 1
(`android`, `androidx`, `data`), y son anotaciones puras que se borran en runtime.

**Por qué se acepta.** README dice que `domain` "no depende de nada de Android **ni de
frameworks concretos**", y ahí hay tensión real. AGENTS.md prohíbe módulos nuevos durante el
MVP, así que la tensión no se puede resolver sin payload extra.

**Disparador.** El día que se quiera testear `domain` en un módulo JVM puro, esto es lo
primero que hay que sacar.

---

### `core/` no está protegido de importar Android

**Qué.** AGENTS.md dice que `core` es usable desde cualquier capa, y eso es lo que hace
legítimo que `domain` importe `ClockProvider`. Pero nadie verifica automáticamente que
`core/` siga siendo framework-free: hoy es una convención, no una garantía.

**Por qué no se agrega la regla.** Detekt no tiene una regla de imports prohibidos por
paquete. Habría que escribirla a mano o agregar un test de arquitectura.

**Disparador.** El primer `import android.*` en `core/`. En ese momento la pureza de
`domain` se rompe en silencio y el grep manual de AGENTS.md es lo único que lo detecta.

**Cómo se arregla.** Un test de arquitectura en `src/test` que falle si aparece
`android`/`androidx` en `core/`.

---

### Previews con strings en español hardcodeados

**Qué.** `ServiciosScreen.kt` y `ServicioEditorDialog.kt` tienen `"Corte de pelo"` y
`"Coloración"` en sus `@Preview`.

**Por qué se deja.** No son `Text()`: son el campo `nombre` de un `Servicio` de prueba, y
los previews nunca se componen en la app. AGENTS.md apunta a texto *visible al usuario* y
esto no lo es. Meterlos en `strings.xml` ataría el preview a un recurso.

**Disparador.** Si los previews se convierten en capturas de Play Store.

---

## Slice 3+ — `Turnos`

### `Turno.duracionMin` ya está prometido en la UI

**Qué.** `strings.xml` ya le dice al usuario, al confirmar una baja: *"Los turnos que ya lo
tienen asignado no se modifican."* La capa de UI contrató un invariante de datos antes de
que exista el código que lo cumple.

**Disparador.** Slice 3. El soft delete de `Servicio` **no** puede tocar filas de `turno`:
ni por cascade, ni "actualizando la duración", ni reescribiendo snapshots. Y
`Turno.duracionMin` tiene que seguir siendo un `Int` copiado, no un JOIN.

Si alguien lo convierte en relación, ese string pasa a ser mentira y los turnos agendados
se mueven solos.
