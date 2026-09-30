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

**Qué.** `ServiciosScreen.kt`, `ServicioEditorDialog.kt`, `ClientesScreen.kt`,
`ClienteEditorDialog.kt` y `ClienteDetalleScreen.kt` tienen `"Corte de pelo"`,
`"Coloración"`, `"Juan Pérez"`, `"Ana"` en sus `@Preview`.

**Por qué se deja.** No son `Text()`: son el campo `nombre` de un `Servicio` o `Cliente` de
prueba, y los previews nunca se componen en la app. AGENTS.md apunta a texto *visible al
usuario* y esto no lo es. Meterlos en `strings.xml` ataría el preview a un recurso.

**Disparador.** Si los previews se convierten en capturas de Play Store.

---

## Slice 2 — `Clientes`

### `clientes` sin índice sobre `nombre`

**Qué.** Mismo caso que `servicios`: `ClienteDao.observarTodos()` es
`WHERE deletedAt IS NULL ORDER BY nombre COLLATE NOCASE`, sin ningún índice. Nota que
`ClienteEntity.kt` ya apunta acá, así que la referencia estaba rota: el ítem no existía.

**Por qué se puede postergar.**Igual que en servicios: el archivo de un profesional son
cientos de filas, y un full scan con sort sobre eso es submilisegundo. Además la búsqueda
**no** va a SQL: es acento-insensible en Kotlin, imposible de expresar con `LIKE`.

**Disparador.** Miles de clientes, o el slice 2 de sync (Fase 2).

**Trampa igual a la de servicios:** el índice hay que declararlo con la collation explícita
o no sirve el `ORDER BY`.

---

### TOCTOU en `ActualizarClienteUseCase`

**Qué.** Espejo exacto del de `Servicio`: el use case lee la fila y después la actualiza
sin transacción; `ClienteDao.actualizar` es `@Update`, resuelve **por PK solamente** y no
mira `deletedAt`. Un soft delete en la ventana intermedia resucitaría la fila. El KDoc de
`ActualizarClienteUseCase.kt` ya lo declara, pero el ítem de acá solo nombraba a
`ActualizarServicioUseCase`.

**Por qué se puede postergar.** Mono-usuario y offline, y la UI abre un diálogo modal: no
hay dos escrituras concurrentes.

**Disparador.** Escritura concurrente sobre la misma fila: import de contactos, sync, o un
ViewModel que dispare `actualizar` sin pasar por el diálogo.

**Cómo se arregla.** `UPDATE ... WHERE id = :id AND deletedAt IS NULL` con un `@Query`
condicional que devuelva `Int`, como ya hace `marcarEliminado`.

---

### Un borrado concurrente se reporta como error genérico

**Qué.** Si `marcarEliminado` afecta 0 filas, `ClienteRepositoryImpl` (y
`ServicioRepositoryImpl`) hacen `check(...)`, que lanza `IllegalStateException`.
`capturandoErrores` la envuelve y `ErrorRes.kt` cae en `else -> R.string.error_generico`.
El dominio ya tiene `ErrorCliente.NoExiste` para ese caso y el usuario nunca lo ve.

**Por qué se puede postergar.** La ventana es chica: `EliminarClienteUseCase` consulta antes
de borrar, así que solo se alcanza con dos escrituras simultáneas, que hoy no existen.

**Disparador.** Lo mismo que la ventana TOCTOU de arriba. **Arreglarlos juntos**: en vez de
`check`, tirar `ErrorCliente.NoExiste` cuando el `@Query` devuelve 0.

---

### `OrdenAlfabetico` usa `Locale.getDefault()` dentro de `data/`

**Qué.** El orden que ve el usuario depende del locale del teléfono, y no es inyectable:
cambia el resultado sin que nadie cambie el código. Un test que afirme el orden corre bajo
el locale default de la JVM de CI.

**Por qué se puede postergar.** Es la misma clase de dependencia implícita de entorno que
`ClockProvider` vino a eliminar para el reloj, pero acá el locale es correcto por defecto en
un teléfono y en la JVM de CI también es estable.

**Disparador.** El primer test de orden que falle solo por locale, o un usuario con el
teléfono en un locale que no sea el de sus datos.

---

### Regex de contacto duplicadas entre `domain` y `ui`

**Qué.** `ValidacionCliente.kt` define `FORMATO_EMAIL` y `FORMATO_TELEFONO`; `ClienteEditorDialog.kt`
tiene copias idénticas para validar en vivo. Hoy son iguales carácter por carácter, y nada
ata las dos copias salvo un test de `ValidacionCliente`.

**Por qué se puede postergar.** La divergencia rompería el feedback inmediato del formulario
(el usuario escribe algo que el dominio va a rechazar y ve el error después), no la
correctitud: la validación autoritativa es la de `domain`.

**Disparador.** La primera regla nueva de validación, o el primer test de UI de un campo de
contacto.

**Cómo se arregla.** Exponer el validador del dominio como predicado público y que la UI lo
reuse, o mover los patrones a `core/`.

---

### Sin límites de longitud en `nombre` ni `notas`

**Qué.** `validarCliente` chequea presencia y formato, pero no tamaño, y el diálogo no usa
`maxLength`. No hay regla de contrato ni del README que lo exija.

**Por qué se puede postergar.** Hoy el único productor es el formulario.

**Disparador.** Fase 2: el import del **Contacts Provider** crea clientes sin pasar por el
formulario, y un contacto con un campo de notas gigante entra directo.

---

### `1.json` fue borrado: toda base v1 en disco crashea

**Qué.** Decisión deliberada: la v1 nunca salió de desarrollo, así que en vez de una
migración falsa se borró `app/schemas/.../1.json` y se exportó la v2. Room lanza
`IllegalStateException` al abrir, y **no** hay `fallbackToDestructiveMigration()` por
diseño.

**Por qué se acepta.** Una migración `1→2` inventada costaría más de lo que ahorra: nunca
hubo usuarios que perder.

**Consecuencias que hay que tener presentes.** (a) Ya no se puede escribir ni testear una
`Migration(1, 2)`: la historia del esquema se reinicia. (b) Cualquier emulador o máquina de
desarrollo con un `turnero.db` v1 **crashea al abrir la app**; se resuelve borrando los
datos de la app, no tocando el código. (c) `2.json` tiene que entrar al commit: sin el
schema versionado no se puede testear ninguna migración futura.

**Disparador.** El próximo bumpeo de versión. Ahí la v2 sí estará publicada y la historia
empieza en serio: `1.json` deja de ser borrable.

---

### El argumento de ruta de la ficha no está verificado end-to-end

**Qué.** `UuidSerializer` resuelve el `NavType` por `serialName` (androidx.navigation
compara contra `"kotlin.String"` y compañía, **no** por `kind`), así que el descriptor pide
el de `String.serializer()`. Ese contrato está fijado por un test JVM
(`UuidSerializerTest`), y `ClienteDetalleViewModelTest` cubre el ViewModel, pero que la
navegación escriba ese `String` en el `Bundle` y la ficha se abra de verdad solo se
comprueba con un emulador.

**Por qué se puede postergar.** No hay emulador local; `connectedDebugAndroidTest` corre
solo en `main` de CI.

**Disparador.** Primer CI sobre `main` de la rama con Slice 2, o el primer `adb` de
desarrollo. **Ahí hay que correr un test instrumentado que navegue a la ficha y vuelva
atrás**; `ClientesFilaSemanticaTest` no cubre navegación real.

---

### Layout no adaptativo para tablet

**Qué.** `ClientesScreen` y `ClienteDetalleScreen` usan `fillMaxSize()` sin tope de ancho. En
una tablet de 10" apaisada una `Card` se estira de borde a borde con 16dp de margen.

**Por qué se puede postergar.** `AlertDialog` ya viene capeado por Material 3 en 560dp, así
que los diálogos no son el problema. El MVP es para teléfono.

**Disparador.** Fase 3, o el primer `androidTest` en una pantalla de tablet.

---

### Nada ejercita la localización

**Qué.** `res/` tiene `values/` y `values-night/`, ningún `values-<lang>/`. Todo está
externalizado — la promesa estructural de Fase 3 está cumplida — pero un `%1$s` mal contado
o una `'` sin escapar en `strings.xml` no lo agarra CI.

**Por qué se puede postergar.** Fase 3 tiene que crear el esqueleto de traducciones; sin él,
un `values-es/` de prueba no compra nada.

**Disparador.** La primera traducción real.

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
