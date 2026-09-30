# Turnero

Aplicación Android nativa de **gestión de turnos para un profesional individual**. Diseñada como agenda personal offline-first, con contacto directo con el cliente por WhatsApp/SMS/llamada.

No es un marketplace de reservas ni un portal para clientes: el único usuario de la app es el profesional que gestiona su propia agenda.

> **Agentes de IA:** leé [`AGENTS.md`](AGENTS.md) antes de tocar código. Es el contrato técnico y de workflow del proyecto.

---

## Tabla de contenidos

1. [Características](#características)
2. [Stack tecnológico](#stack-tecnológico)
3. [Arquitectura](#arquitectura)
4. [Estructura del proyecto](#estructura-del-proyecto)
5. [Modelo de dominio](#modelo-de-dominio)
6. [Reglas de datos](#reglas-de-datos)
7. [Requisitos de desarrollo](#requisitos-de-desarrollo)
8. [Testing](#testing)
9. [Roadmap hacia el MVP](#roadmap-hacia-el-mvp)
10. [Licencia](#licencia)

---

## Características

### MVP (version actual)

- **Agenda visual** con vistas día, semana y mes.
- **Gestión de turnos**: alta, edición, cancelación y cambio de estado (pendiente, confirmado, atendido, ausente, cancelado).
- **Ficha de cliente** con datos de contacto, notas privadas e historial de turnos.
- **Catálogo de servicios** configurables (nombre, duración, precio, color).
- **Bloqueos de agenda** (feriados, vacaciones, huecos ocupados).
- **Horarios de atención** por día de la semana.
- **Recordatorios locales** al profesional antes de cada turno (`AlarmManager`).
- **Contacto directo con el cliente** con un tap: WhatsApp, SMS o llamada.
- **Búsqueda y filtros** por cliente, fecha, servicio o estado.
- **Tema claro/oscuro** con Material 3 (color dinámico en Android 12+).
- **100% offline**.

### Fase 2

- Backup automático en la nube (Firebase Storage) con recuperación al reinstalar.
- Autenticación con Google Sign-In.
- **CalendarContract** — sincronización opcional con el calendario nativo.
- **Contacts Provider** — autocompletado de clientes desde la agenda del teléfono.
- **Storage Access Framework** — exportar reportes a PDF/CSV.
- Crashlytics y Analytics.

> **Por qué esas cuatro están fuera del MVP:** cada una es un permiso runtime con su propio
> camino de denegación que hay que diseñar, probar y explicar, más generación de PDF y acceso a
> ContentProviders que pueden lanzar `SecurityException`. El MVP se enfoca en el loop
> *agenda → turno → cliente → recordatorio*, que es lo que hace que un profesional abandone
> la app. Se construyen encima, sin refactor, en Fase 2.

### Fase 3 (Play Store)

- Íconos adaptativos, splash screen API, políticas de privacidad.
- Localización multi-idioma.
- Accesibilidad completa (TalkBack, contrastes, tamaños dinámicos).

---

## Stack tecnológico

### Toolchain

| | Versión | Nota |
|---|---|---|
| Android Gradle Plugin | 9.4.0 | `navigation 2.10.x` exige AGP ≥ 9.2 |
| Gradle | 9.6.0 | Vía wrapper |
| Kotlin | 2.4.10 | |
| KSP | 2.3.11 | KSP2. KSP1 no soporta Kotlin 2.1+ |
| JDK | 17 | `jvmToolchain(17)` |
| Android Studio | Quail 4 (2026.1.4) | Opcional: el wrapper alcanza |
| `compileSdk` | 37 | Requerido por Compose 1.12.1 |
| `targetSdk` | 36 | Play lo exige desde 31 ago 2026 |
| `minSdk` | 26 | Trae `java.time` nativo |

> `compileSdk` va deliberadamente **por delante** de `targetSdk`: `compileSdk` solo habilita APIs
> nuevas, no cambia el comportamiento en runtime. Play exige `targetSdk ≥ 36`; Compose 1.12.1
> exige `compileSdk ≥ 37`.

### Cliente Android

| Componente | Tecnología |
|---|---|
| Lenguaje | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Arquitectura | MVVM + Clean Architecture (ui / domain / data) |
| Navegación | Navigation Compose 2.10.2 (rutas type-safe con kotlinx-serialization) |
| Inyección de dependencias | Hilt 2.60.1 |
| Asincronía | Coroutines + Flow / StateFlow |
| Preferencias | DataStore Preferences 1.2.1 |
| Logging | Timber 5.0.1 |
| Serialización | kotlinx-serialization-json |

> **Hilt 2.60.1 es un piso, no una preferencia.** Con 2.59.x el build falla con
> `Provided Metadata instance has version 2.4.0, while maximum supported version is 2.3.0`:
> no soporta el metadata de Kotlin 2.4.

### Persistencia local

- **Room 2.8.5** (SQLite) con **KSP** como procesador de anotaciones.
- Entidades con **UUID como PK** y campos `createdAt` / `updatedAt` / `deletedAt` desde el día uno, para habilitar sync futuro sin migraciones dolorosas.
- **Soft delete**: toda `@Query` filtra `deletedAt IS NULL`.
- Migraciones versionadas. El schema JSON se exporta a `app/schemas/` y **se versiona en git**, para poder testear migraciones.
- **Fechas con `java.time` nativo** (`Instant`, `LocalDate`, `LocalTime`, `ZoneId`). No se usa `kotlinx-datetime`: `minSdk 26` ya trae `java.time` y la librería solo agrega conversores y fricción de interoperabilidad.
- Type converters para `Instant`, `LocalTime`, `LocalDate`, `LocalDateTime`, `UUID` y enums.

### Notificaciones y background

- `AlarmManager` (`setExactAndAllowWhileIdle`) para recordatorios puntuales de cada turno, **con fallback** cuando no hay permiso de alarma exacta.
- `BroadcastReceiver` con `RECEIVE_BOOT_COMPLETED`, `ACTION_TIME_CHANGED` y `ACTION_TIMEZONE_CHANGED` para reprogramar: **las alarmas no sobreviven a un reinicio**.
- `WorkManager` para tareas periódicas (backup, limpieza de turnos vencidos).
- `NotificationCompat` con canales separados (turnos vs. sistema).
- `POST_NOTIFICATIONS` (Android 13+) se pide en runtime. `USE_EXACT_ALARM` (Android 13+) se declara y justifica en Play Console: Play lo restringe a apps de alarma y calendario.

### Comunicación con clientes (sin backend)

- `Intent` con deep link `https://wa.me/<número>?text=<mensaje>` para WhatsApp.
- `Intent` con `smsto:` para SMS.
- `Intent` con `tel:` para llamadas.
- `Intent` con `mailto:` para email.
- Plantillas de mensaje configurables desde ajustes.

### Build, calidad y CI

- **Gradle Kotlin DSL** + **Version Catalogs** (`gradle/libs.versions.toml`).
- **Detekt 1.23.8** para análisis estático y formato (su ruleset `formatting` envuelve ktlint).
- **GitHub Actions** (`.github/workflows/ci.yml`): en cada PR corre `assembleDebug` +
  `testDebugUnitTest` + `detekt`. Los tests instrumentados corren **solo en `main`**, porque
  el emulador cuesta ~10 min de boot.
- **Fastlane** para automatizar el deploy a Play Store (fase 3).

> **No se usa ktlint por separado.** Correr detekt y ktlint como dos formatters
> independientes produce violaciones mutuas de indentación y orden de imports que no se
> pueden arreglar. Detekt ya expone las reglas de ktlint vía su ruleset `formatting`.

---

## Arquitectura

La app sigue **Clean Architecture** en tres capas más una capa transversal, dentro de **un solo módulo Gradle**:

```
┌─────────────────────────────────────────────┐
│                     ui                      │  ← Compose, ViewModels, Navigation
├─────────────────────────────────────────────┤
│                    domain                   │  ← Modelos puros, casos de uso, contratos
├─────────────────────────────────────────────┤
│                    data                    │  ← Room, mappers, implementación de repos
└─────────────────────────────────────────────┘
              ↑ core (utils transversales) ↑
```

### Reglas de dependencia

- `ui` depende de `domain`, **nunca** de `data`.
- `domain` **no depende de nada** de Android ni de frameworks concretos. Es Java/Kotlin puro.
- `data` implementa las interfaces declaradas en `domain`.
- `core` es utilizable desde cualquier capa.
- Los `ViewModel` invocan **UseCases**, nunca repositorios directamente.
- Los repositorios exponen `Flow` para que la UI reaccione automáticamente a cambios en la DB.

### Un solo módulo, a propósito

Pasar a multi-módulo es posible pero **no es mecánico**: mueve el DI, rompe los `internal`
visibles entre capas y agrega configuración de build por módulo. El límite de paquetes más
`domain-reviewer` dan la misma garantía arquitectónica a una fracción del costo. No se
considera multi-módulo durante el MVP.

### Beneficios

- **Testeable**: `domain` se testea con JUnit puro, sin Android.
- **Reemplazable**: cambiar Room por otra fuente de datos no toca la UI.

---

## Estructura del proyecto

```
turnero/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml              ← version catalog (fuente de verdad de versiones)
│   └── wrapper/                        ← Gradle 9.6.0
├── config/detekt/detekt.yml
├── .github/workflows/ci.yml
├── AGENTS.md                           ← contrato técnico para agentes
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    ├── schemas/                        ← export de Room (versionado en git)
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml     ← los permisos de notif. se agregan en su slice
        │   ├── kotlin/com/turnero/app/
        │   │   ├── TurneroApp.kt                 ← @HiltAndroidApp, canales de notif.
        │   │   ├── MainActivity.kt               ← @AndroidEntryPoint
        │   │   │
        │   │   ├── di/                           ← módulos Hilt
        │   │   │   ├── DatabaseModule.kt         ← provee Room DB + DAOs
        │   │   │   ├── RepositoryModule.kt       ← binds interfaces → impls
        │   │   │   └── AlarmModule.kt            ← provee AlarmManager / Scheduler
        │   │   │
        │   │   ├── core/                         ← utilidades transversales
        │   │   │   ├── notification/
        │   │   │   │   ├── NotificationChannels.kt
        │   │   │   │   ├── TurnoNotifier.kt
        │   │   │   │   ├── TurnoAlarmScheduler.kt
        │   │   │   │   └── TurnoAlarmReceiver.kt ← BroadcastReceiver (boot / tz)
        │   │   │   ├── intent/
        │   │   │   │   └── ContactIntents.kt     ← whatsapp / sms / tel / email
        │   │   │   ├── datetime/
        │   │   │   │   ├── DateFormatters.kt
        │   │   │   │   └── ClockProvider.kt      ← inyectable, testeable
        │   │   │   └── ext/
        │   │   │       └── Extensions.kt
        │   │   │
        │   │   ├── domain/                       ← lógica pura, SIN Android
        │   │   │   ├── model/
        │   │   │   │   ├── Turno.kt
        │   │   │   │   ├── Cliente.kt
        │   │   │   │   ├── Servicio.kt
        │   │   │   │   ├── Bloqueo.kt
        │   │   │   │   ├── HorarioAtencion.kt
        │   │   │   │   └── EstadoTurno.kt        ← enum
        │   │   │   ├── repository/               ← interfaces
        │   │   │   │   ├── TurnoRepository.kt
        │   │   │   │   ├── ClienteRepository.kt
        │   │   │   │   ├── ServicioRepository.kt
        │   │   │   │   └── BloqueoRepository.kt
        │   │   │   └── usecase/
        │   │   │       ├── CrearTurnoUseCase.kt        ← valida solapamientos + agenda alarma
        │   │   │       ├── ActualizarTurnoUseCase.kt
        │   │   │       ├── CancelarTurnoUseCase.kt     ← cancela alarma también
        │   │   │       ├── ObtenerAgendaDiaUseCase.kt
        │   │   │       ├── ObtenerAgendaSemanaUseCase.kt
        │   │   │       ├── BuscarTurnosUseCase.kt
        │   │   │       └── ObtenerHuecosDisponiblesUseCase.kt
        │   │   │
        │   │   ├── data/                         ← implementación de dominio
        │   │   │   ├── local/
        │   │   │   │   ├── TurneroDatabase.kt    ← @Database, versión 1
        │   │   │   │   ├── Converters.kt
        │   │   │   │   ├── entity/
        │   │   │   │   │   ├── TurnoEntity.kt        ← + createdAt/updatedAt/deletedAt
        │   │   │   │   │   ├── ClienteEntity.kt
        │   │   │   │   │   ├── ServicioEntity.kt     ← precioCentavos: Long
        │   │   │   │   │   ├── BloqueoEntity.kt
        │   │   │   │   │   └── HorarioAtencionEntity.kt
        │   │   │   └── dao/
        │   │   │       ├── TurnoDao.kt           ← queries por rango devuelven Flow
        │   │   │       ├── ClienteDao.kt
        │   │   │       ├── ServicioDao.kt
        │   │   │       ├── BloqueoDao.kt
        │   │   │       └── HorarioAtencionDao.kt
        │   │   ├── mapper/
        │   │   │   ├── TurnoMapper.kt            ← Entity ↔ Domain
        │   │   │   ├── ClienteMapper.kt
        │   │   │   └── …
        │   │   └── repository/
        │   │       ├── TurnoRepositoryImpl.kt
        │   │       ├── ClienteRepositoryImpl.kt
        │   │       ├── ServicioRepositoryImpl.kt
        │   │       └── BloqueoRepositoryImpl.kt
        │   │   └── preferences/
        │   │       └── AjustesDataStore.kt       ← plantilla WA, tema, primer día
        │   │
        │   └── ui/                               ← Compose
        │       ├── theme/Color.kt, Type.kt, Theme.kt
        │       ├── navigation/NavGraph.kt, Screen.kt
        │       ├── components/                  ← TurnoCard, EstadoChip, …
        │       └── screens/
        │           ├── agenda/                  ← día / semana / mes
        │           ├── turno_editor/
        │           ├── turno_detalle/
        │           ├── clientes/
        │           ├── servicios/
        │           ├── busqueda/                ← feature del MVP
        │           └── ajustes/                 ← horarios, plantilla WA, tema
        │
        │   └── res/
        │       ├── values/strings.xml           ← TODO el texto visible
        │       ├── values/themes.xml, colors.xml
        │       ├── drawable/
        │       ├── mipmap-anydpi-v26/           ← ícono adaptativo
        │       └── xml/backup_rules.xml, data_extraction_rules.xml
        │
        ├── test/kotlin/com/turnero/app/          ← JUnit 5: use cases, ViewModels, mappers
        │   └── testing/                         ← fakes compartidos
        └── androidTest/kotlin/com/turnero/app/   ← JUnit 4: DAOs, pantallas
```

---

## Modelo de dominio

```
Cliente 1 ──── * Turno * ──── 1 Servicio
                 │
                 └── EstadoTurno (enum)

HorarioAtencion (uno por día de semana)
Bloqueo (rangos que anulan disponibilidad)
```

### Turno

- `id: UUID`
- `clienteId: UUID`
- `servicioId: UUID`
- `inicio: Instant`
- `duracionMin: Int` — **snapshot**, ver abajo
- `estado: EstadoTurno`
- `notas: String?`
- `createdAt`, `updatedAt`, `deletedAt: Instant?`

### EstadoTurno

`PENDIENTE`, `CONFIRMADO`, `ATENDIDO`, `AUSENTE`, `CANCELADO`.

### Cliente

- `id: UUID`
- `nombre: String`
- `telefono: String?`
- `email: String?`
- `notas: String?`
- Auditoría (`createdAt`, `updatedAt`, `deletedAt`).

### Servicio

- `id: UUID`
- `nombre: String`
- `duracionMin: Int`
- `precioCentavos: Long?` — **no `BigDecimal`**, ver abajo
- `color: Int` (para diferenciar en la agenda)
- Auditoría.

### Bloqueo

- `id: UUID`
- `inicio: Instant`
- `fin: Instant`
- `motivo: String?`
- Auditoría.

### HorarioAtencion

- `id: UUID`
- `diaSemana: DayOfWeek`
- `horaInicio: LocalTime`
- `horaFin: LocalTime`
- Auditoría.

---

## Reglas de datos

Estas reglas son lo que hace que el código sea testeable y sincronizable. No son preferencias.

### Soft delete

Toda entidad lleva `createdAt`, `updatedAt` y `deletedAt`. Borrar es marcar `deletedAt`, nunca
un `DELETE`. **Toda `@Query` filtra `deletedAt IS NULL`** — omitirlo devuelve filas borradas
y es el bug más difícil de detectar en la UI.

### `precio` como centavos, no `BigDecimal`

`precioCentavos: Long`. SQLite no tiene tipo decimal, y guardar `BigDecimal` obliga a traer
todas las filas a Kotlin para sumar. Con `Long`, el reporte de ingresos del MVP se resuelve
con `SUM(precioCentavos)` en SQL. Además evita el clásico bug de escala y redondeo al
convertir a texto.

### `duracionMin` en `Turno` es un snapshot, no una relación

`Turno.duracionMin` duplica `Servicio.duracionMin` **a propósito**: si un servicio cambia de
duración, los turnos ya agendados no se mueven. "Simplificarlo" a un JOIN es una regresión.

### El tiempo es inyectado

`Instant.now()` y `LocalDate.now()` están prohibidos en `domain` y `data`. Todo "ahora" pasa
por el `ClockProvider` inyectado. Sin eso, los use cases no se pueden testear de forma
determinista.

### Fechas y zonas horarias

Se guarda `Instant` y se convierte a la zona del dispositivo al renderizar. Los horarios de
atención son `LocalTime` (hora local del profesional), no `Instant`: el profesional piensa
en "los martes de 9 a 18", no en un instante UTC.

### Errores

Repositorios y use cases devuelven `Result<T>`. No se lanzan excepciones cruzando la frontera
de dominio. Los ViewModel traducen el error a un campo en el `UiState`.

### Interfaz

- **Cero strings hardcodeados**: todo texto visible pasa por `stringResource()`.
  La Fase 3 promete localización; retrofitear strings en ~15 pantallas es el error más caro
  del proyecto.
- Sin colores literales en screens: `MaterialTheme.colorScheme`. El único lugar con colores
  literales es `ui/theme/`.
- `key =` en todo `LazyColumn` con items dinámicos.
- `state hoisting`: el estado vive en el ViewModel; el composable solo lo pinta.

---

## Requisitos de desarrollo

- **JDK 17** (no 21 ni 8: `jvmToolchain(17)` y Gradle 9.6 requieren 17).
- **Android SDK**: `platforms/android-37.0`, `build-tools;36.0.0`, `platform-tools`.
- **Gradle**: no hace falta instalarlo, el wrapper resuelve la 9.6.0.
- Un dispositivo o emulador Android 8.0+ (API 26) para los tests instrumentados.

### Configuración del SDK

`ANDROID_HOME` apunta al SDK. El proyecto usa `local.properties` (`sdk.dir`) si existe; ese
archivo **está en `.gitignore`** porque contiene rutas absolutas de máquina.

```bash
export JAVA_HOME=/ruta/al/jdk-17
export ANDROID_HOME=$HOME/Android/Sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
```

> `sdkmanager` está deprecado. El replacement es el CLI `android`:
> `android sdk install platforms/android-37.0`. Los identificadores de paquete usan `/`
> como separador, no `;`.

---

## Testing

| Capa | Ubicación | Herramientas |
|---|---|---|
| Use cases, ViewModels, mappers | `app/src/test/` | **JUnit 5** (Jupiter) + MockK + Turbine |
| DAOs de Room | `app/src/androidTest/` | **JUnit 4** + `Room.inMemoryDatabaseBuilder` |
| Pantallas Compose | `app/src/androidTest/` | **JUnit 4** + Compose UI Test |

**Por qué el mixto:** JUnit 5 corre en `src/test` vía el plugin
`de.mannodermaus.android-junit`. Los instrumentados siguen con JUnit 4 +
`AndroidJUnitRunner` porque `androidx.test` está construido sobre JUnit 4. Forzar Jupiter en
instrumentados agrega un runner que no compra nada. `src/test` incluye `junit-vintage-engine`,
así que tests JUnit 4 también funcionan ahí.

### Estrategia

- **UseCases**: unit tests puros con fakes de los repositorios.
- **DAOs**: tests instrumentados con Room in-memory, **incluyendo el caso de soft delete**.
- **ViewModels**: `kotlinx-coroutines-test` + `MainDispatcherRule`, verificando emisiones de
  `StateFlow` con Turbine.
- **UI**: Compose UI Tests para las pantallas críticas (Agenda, Editor de turno).

### Comandos

```bash
./gradlew assembleDebug          # build
./gradlew testDebugUnitTest      # tests JVM — feedback rápido
./gradlew detekt                 # análisis estático
./gradlew connectedDebugAndroidTest   # instrumentados — requiere emulador (~10 min)

# Verificación completa antes de cerrar un slice:
./gradlew clean assembleDebug testDebugUnitTest detekt

# Test puntual:
./gradlew testDebugUnitTest --tests "com.turnero.app.domain.usecase.CrearTurnoUseCaseTest"
```

En CI los instrumentados corren **solo en `main`**: el emulador cuesta ~10 min de boot y no
justifica pagarlo en cada PR.

---

## Roadmap hacia el MVP

Once slices, en orden. Cada uno termina con
`./gradlew clean assembleDebug testDebugUnitTest detekt` en verde y es un PR.

| # | Slice | Entregable |
|---|---|---|
| 0 | **Bootstrap** | Toolchain, scaffold, CI, detekt, AGENTS.md, subagentes. |
| 1 | **Cableado** | `Servicio` de punta a punta: entity → dao → mapper → repo → use case → ViewModel → screen. Prueba que DI, KSP y las capas funcionan. |
| 2 | **Clientes** | Listado, ficha, alta/edición, búsqueda por nombre. |
| 3 | **Turnos** | CRUD + estados + validación de solapamiento. |
| 4 | **Agenda día** | Timeline con turnos, huecos y bloqueos. FAB de alta. |
| 5 | **Agenda semana/mes** | Vistas condensadas, navegación por período. |
| 6 | **Horarios y huecos** | Horarios por día, `ObtenerHuecosDisponiblesUseCase`. |
| 7 | **Recordatorios** | `AlarmManager` + fallback sin permiso + receiver de boot y cambio de zona. |
| 8 | **Contacto** | `ContactIntents` (WhatsApp/SMS/tel) + plantilla configurable. |
| 9 | **Búsqueda y filtros** | Filtros por cliente, fecha, servicio y estado. |
| 10 | **Tema y pulido** | Tema claro/oscuro, auditoría de `strings.xml`, accesibilidad. |

Criterio de salida por slice: los tres comandos en verde, la capa `domain` sin imports de
Android (verificado por `domain-reviewer`), y tests para la lógica nueva.

---

## Licencia

Por definir.

---

## Autor

Proyecto personal.
