# AGENTS.md

Instrucciones para agentes que trabajan en **Turnero** (app Android nativa de gestión de turnos, offline-first).

`README.md` es la especificación de producto. Este archivo es el contrato técnico y de workflow. Si se contradicen, gana este.

---

## Entorno (verificado en esta máquina)

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-temurin-jdk
export ANDROID_HOME=/home/joaquin/Android/Sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
```

Ya está en `~/.bashrc.d/android.sh`. Shell nuevo → funciona solo.

**Trampa principal de esta máquina:** el `java` del PATH del sistema es **JDK 8**, que no puede correr Gradle 9.6. Si `./gradlew` falla con un error de versión de Java, el problema es `JAVA_HOME`, no el proyecto.

- Android SDK: `~/Android/Sdk` (platforms 36 y 37, build-tools 36.0.0, platform-tools)
- `sdkmanager` está deprecado. El replacement es el CLI `android`: `android sdk install platforms/android-37.0`
- No hay Android Studio instalado. No dependas de él; usá el wrapper.

---

## Comandos

```bash
./gradlew assembleDebug          # build (verifica que todo compila)
./gradlew testDebugUnitTest      # tests JVM  — feedback rápido
./gradlew detekt                 # análisis estático + formato
./gradlew connectedDebugAndroidTest   # instrumentados — requiere emulador/dispositivo
```

**Verificación completa antes de dar cualquier feature por terminada:**

```bash
./gradlew clean assembleDebug testDebugUnitTest detekt
```

**Test o análisis puntual:**

```bash
./gradlew testDebugUnitTest --tests "com.turnero.app.domain.usecase.CrearTurnoUseCaseTest"
./gradlew detekt --input app/src/main/kotlin/com/turnero/app/domain
```

`connectedDebugAndroidTest` cuesta ~10 min de boot de emulador. En CI corre **solo en `main`**, no en cada PR. No lo ejecutes salvo que el cambio toque Room/permisos/Compose UI.

Si tocás un plugin, la config o las versiones: primero los tres comandos de arriba, después el instrumentado.

---

## Toolchain (fijado en `gradle/libs.versions.toml`)

| | Versión | Nota |
|---|---|---|
| AGP | 9.4.0 | No bajar: `navigation 2.10.x` exige AGP ≥ 9.2 |
| Gradle | 9.6.0 | El wrapper ya está; no uses un `gradle` global |
| Kotlin | 2.4.10 | |
| KSP | 2.3.11 | KSP2. KSP1 no soporta Kotlin 2.1+ |
| `compileSdk` | 37 | Requerido por Compose 1.12.1 |
| `targetSdk` | 36 | Play lo exige desde 31 ago 2026 |
| `minSdk` | 26 | Trae `java.time` nativo |
| Hilt | 2.60.1 | **2.59.x falla** con Kotlin 2.4 (metadata 2.4 no soportado) |
| Compose BOM | 2026.09.00 | |
| Room | 2.8.5 | |

### Gotchas de AGP 9 que ya_costan tiempo

- **No apliques `org.jetbrains.kotlin.android`.** AGP 9.0+ trae Kotlin integrado y falla el build si lo aggregate. Usá solo `kotlin.compose`, `kotlin.serialization` y `ksp`.
- **`buildConfig = true`** es opt-in desde AGP 8. Ya está habilitado en `app/build.gradle.kts`.
- **Room exporter schemas:** ya configurado (`ksp { arg("room.schemaLocation", ...) }` → `app/schemas/`). Cuando bumpees la versión de Room, **subí el schema JSON a git**. Sin eso no se pueden testear migraciones.
- **16 KB page size:** requisito de Play desde nov 2025. El app es Kotlin puro, pero AndroidX inyecta `.so` (`libandroidx.graphics.path.so`, `libdatastore_shared_counter.so`). Verificar tras bumpear deps:
  ```bash
  unzip -l app/build/outputs/apk/debug/app-debug.apk | grep '\.so$'
  # cada .so debe tener alineación ELF 0x4000
  ```
  Si alguna dependencia trae `.so` con alineación 4 KB, el upload a Play es rechazado.
- **`detekt`:** el `source.setFrom` usa rutas relativas a `:app` (`src/main/kotlin`, no `app/src/main/kotlin`). Escribir la ruta con prefijo `app/` produce `NO-SOURCE` silenciosamente.
- **detekt ≥ 1.23 no tiene `buildUponDefaultConfig`.** El default se aplica siempre.

---

## Arquitectura

Clean Architecture en 3 capas dentro de **un solo módulo** (`app/`). No crees módulos nuevos.

```
ui/      Compose, ViewModels, Navigation    → depende de domain, NUNCA de data
domain/  modelos, use cases, interfaces      → sin Android, sin frameworks
data/    Room, mappers, implementaciones    → implementa lo declarado en domain
core/    transversales (datetime, intent, notification)
```

**Reglas que no se negocian** (el subagente `domain-reviewer` las verifica):

1. `domain` **no importa** `android.*`, `androidx.*` ni `data.*`. Es Java/Kotlin puro.
2. Los **ViewModel invocan UseCases, nunca repositorios**.
3. `data` implementa las interfaces de `domain`; el binding va en `di/`.
4. `core` es usable desde cualquier capa.

**Nombres de archivos en español, código en inglés.** Los use cases usan verbos: `CrearTurnoUseCase`, `ObtenerAgendaDiaUseCase`. Esto no es negociable: la capitalización CUDAtyle española (`AgendaDiaUseCase`) y la lowercase (`.kt` = snake_case) conviven y hay que respetarlas.

---

## Reglas de datos

**Soft delete obligatorio.** Toda entidad lleva `createdAt`, `updatedAt`, `deletedAt`. Esto habilita sync futuro.

> **Toda `@Query` de Room filtra `deletedAt IS NULL`.** Olvidarlo devuelve filas borradas y es el bug más fácil de introducir y más difícil de detectar en la UI. Verificá cada DAO que toques o agregues.

- **PK = `UUID`**, generado en Kotlin, nunca autoincremental. Necesario para sync.
- **Fechas:** `java.time` nativo (`Instant`, `LocalDate`, `LocalTime`, `ZoneId`). **No uses `kotlinx-datetime`** — `minSdk 26` ya trae `java.time`; la librería es redundante y agrega conversores.
- **Type converters** en `data/local/Converters.kt`. Room NO soporta solos: `Instant`, `LocalTime`, `LocalDate`, `UUID`, `LocalDateTime`. Los enums sí.
- **`precio` de `Servicio` se guarda como `Long` centavos** (`precioCentavos`), no `BigDecimal`. SQLite no tiene tipo decimal y con `Long` el reporte de ingresos se resuelve con `SUM()` en SQL en vez de traer todas las filas a Kotlin.
- **Zona horaria:** guardá `Instant`, convertí a la zona del dispositivo al renderizar. Los horarios de atención son `LocalTime` (hora local del profesional), no `Instant`.
- **Nunca `Instant.now()` / `LocalDate.now()` en `domain` ni `data`.** Usá el `ClockProvider` inyectado (`core/datetime/ClockProvider.kt`). Es lo que hace testeable el modelo.

### `duracionMin` en `Turno` NO es un JOIN a `Servicio`

`Turno.duracionMin` es un **snapshot deliberado**. Si la duración de un servicio cambia, los turnos ya agendados no deben moverse. No lo "simplifiques" a un JOIN.

---

## Notificaciones y alarmas

`AlarmManager` es la parte más frágil del proyecto. Si tocás algo acá, leé esto primero.

- **API 31+ exige permiso** para alarmas exactas. Sin él, `setExactAndAllowWhileIdle` **lanza `SecurityException`**. Siempre:
  ```kotlin
  if (alarmManager.canScheduleExactAlarms()) { /* setExact... */ } else { /* fallback */ }
  ```
- Declarar `USE_EXACT_ALARM` (API 33+) y justificarlo en Play Console. Play lo restringe a apps de alarma/calendario.
- `POST_NOTIFICATIONS` (API 33+) se pide en runtime, no al instalar.
- **Las alarmas no sobreviven al reinicio.** Sin un `RECEIVE_BOOT_COMPLETED` receiver que reprograme los turnos futuros, el feature se rompe silenciosamente.
- También hay que manejar `ACTION_TIME_CHANGED` y `ACTION_TIMEZONE_CHANGED`.
- Los canales de notificación se crean en `TurneroApp.onCreate()`.

**El manifest todavía NO declara permisos de notificación** (se agregan en el slice de recordatorios). No los des por existentes.

---

## UI (Compose)

- **Cero strings hardcodeados.** Todo vía `stringResource()` → `res/values/strings.xml`. La Fase 3 promete localización; retrofitear strings en ~15 pantallas es el error más caro del proyecto. `MainActivity` es la única excepción temporal.
- Sin colores literales (`Color.Red`, `0xFF...`) en screens: usá `MaterialTheme.colorScheme`.
- `state hoisting`: el state vive en el ViewModel, el composable solo lo pinta.
- `key =` en todo `LazyColumn` con items dinámicos.
- Accesibilidad: contenido descriptivo en iconos con `contentDescription`.

---

## Testing

| Ubicación | Motor | Para qué |
|---|---|---|
| `app/src/test/` | **JUnit 5** (Jupiter) + MockK + Turbine | use cases, ViewModels, mappers |
| `app/src/androidTest/` | **JUnit 4** + Room in-memory + Compose UI Test | DAOs, pantallas |

**Por qué el mixto:** JUnit 5 corre en `src/test` vía el plugin `de.mannodermaus.android-junit`. Los instrumentados siguen con JUnit 4 + `AndroidJUnitRunner` porque androidx.test está construido sobre JUnit 4. Forzar Jupiter en instrumentados agrega `android-test-runner` y no compra nada.

- `src/test` incluye `junit-vintage-engine`, así que **tests JUnit 4 también funcionan ahí**.
- Tests de Flow: `app.cash.turbine` (`Flow.test { assertEquals(...) }`).
- Tests de ViewModel: `kotlinx-coroutines-test` con `MainDispatcherRule` (estándar JUnit, no JUnit 5) para inyectar el dispatcher.
- DAOs: `@RunWith(AndroidJUnit4::class)` + `Room.inMemoryDatabaseBuilder` en `androidTest`.
- Fakes de repositorio compartidos en `app/src/test/kotlin/.../testing/`. No dupliques el mismo fake en cada test.

**Detekt corre solo sobre `src/main` y `src/test`**; los instrumentados quedan fuera.

---

## Git

- Rama principal: `main`.
- Un slice del roadmap = un PR.
- **No commitees** rutas absolutas de máquina: ni `org.gradle.java.home` en `gradle.properties` ni `sdk.dir` en `local.properties` (ya está en `.gitignore`). Rompen el CI y cualquier otro clone.
- Los schemas de Room (`app/schemas/`) **sí** van a git.

---

## Subagentes disponibles

Versionados en `.opencode/agent/`. Delegá:

| Agente | Para qué |
|---|---|
| `domain-reviewer` | Read-only. Valida las 4 reglas de capas, `deletedAt IS NULL` en queries, ausencia de `*.now()`, `Result<T>` en repos |
| `test-author` | Escribe tests de use cases/ViewModels y de DAOs instrumentados, y mantiene los fakes |
| `feature-builder` | Implementa un slice vertical completo: entity → dao → mapper → repo → usecase → viewmodel → screen |
| `compose-reviewer` | Revisa screens: strings, theming, `key`, state hoisting |

---

## Roadmap

El MVP se construye en 11 slices, en orden. El detalle de cada uno está en `README.md` → *Roadmap hacia el MVP*. Cada slice termina con los tres comandos de arriba en verde.
