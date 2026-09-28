# Turnero

Aplicación Android nativa de **gestión de turnos para un profesional individual**. Diseñada como agenda personal offline-first, con backup en la nube y contacto directo con el cliente por WhatsApp/SMS/llamada.

No es un marketplace de reservas ni un portal para clientes: el único usuario de la app es el profesional que gestiona su propia agenda.

---

## Tabla de contenidos

1. [Características](#características)
2. [Stack tecnológico](#stack-tecnológico)
3. [Arquitectura](#arquitectura)
4. [Estructura del proyecto](#estructura-del-proyecto)
5. [Modelo de dominio](#modelo-de-dominio)
6. [Requisitos de desarrollo](#requisitos-de-desarrollo)
7. [Configuración inicial](#configuración-inicial)
8. [Testing](#testing)
9. [Roadmap](#roadmap)
10. [Licencia](#licencia)

---

## Características

### MVP (versión actual)

- **Agenda visual** con vistas día, semana y mes.
- **Gestión de turnos**: alta, edición, cancelación y cambio de estado (pendiente, confirmado, atendido, ausente, cancelado).
- **Ficha de cliente** con datos de contacto, notas privadas e historial de turnos.
- **Catálogo de servicios** configurables (nombre, duración, precio, color).
- **Bloqueos de agenda** (feriados, vacaciones, huecos ocupados).
- **Horarios de atención** por día de la semana.
- **Recordatorios locales** al profesional antes de cada turno (`AlarmManager`).
- **Contacto directo con el cliente** con un tap: WhatsApp, SMS, llamada o email.
- **Búsqueda y filtros** por cliente, fecha, servicio o estado.
- **Reportes básicos**: turnos por período, ingresos estimados, tasa de ausentismo.
- **Tema claro/oscuro** con Material 3.
- **100% offline**.

### Fase 2

- Backup automático en la nube (Firebase Storage) con recuperación al reinstalar.
- Autenticación con Google Sign-In.
- Crashlytics y Analytics.

### Fase 3 (Play Store)

- Íconos adaptativos, splash screen API, políticas de privacidad.
- Localización multi-idioma.
- Accesibilidad completa (TalkBack, contrastes, tamaños dinámicos).

---

## Stack tecnológico

### Cliente Android

| Componente | Tecnología |
|---|---|
| Lenguaje | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Arquitectura | MVVM + Clean Architecture (ui / domain / data) |
| Navegación | Navigation Compose |
| Inyección de dependencias | Hilt |
| Asincronía | Coroutines + Flow / StateFlow |
| Preferencias | DataStore Preferences |
| Logging | Timber |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 34 (Android 14) |

### Persistencia local

- **Room** (SQLite) con KSP como procesador de anotaciones.
- Entidades con **UUID como PK** y campos `createdAt` / `updatedAt` / `deletedAt` desde el día uno, para habilitar sync futuro sin migraciones dolorosas.
- Migraciones versionadas.
- Type converters para `Instant`, `LocalDate`, `UUID` y enums (`kotlinx-datetime`).

### Backup en la nube (fase 2)

- **Firebase Auth** — Google Sign-In para identificar la cuenta del backup.
- **Firebase Storage** — sube el archivo `.db` comprimido periódicamente.
- **WorkManager** — orquesta el backup automático cada X horas o al detectar cambios.
- **Firebase Crashlytics** — reporte de crashes en producción.

### Notificaciones y background

- `AlarmManager` (`setExactAndAllowWhileIdle`) para recordatorios puntuales de cada turno.
- `WorkManager` para tareas periódicas (backup, limpieza de turnos vencidos).
- `NotificationCompat` con canales separados (turnos vs. sistema).
- Manejo de `POST_NOTIFICATIONS` (Android 13+) y `SCHEDULE_EXACT_ALARM` (Android 12+).

### Comunicación con clientes (sin backend)

- `Intent` con deep link `https://wa.me/<número>?text=<mensaje>` para WhatsApp.
- `Intent` con `smsto:` para SMS.
- `Intent` con `tel:` para llamadas.
- `Intent` con `mailto:` para email.
- Plantillas de mensaje configurables desde ajustes.

### Integraciones con el sistema

- **CalendarContract API** — sincronización opcional con el calendario nativo.
- **Contacts Provider** — autocompletado de clientes desde la agenda del teléfono.
- **Storage Access Framework** — exportar reportes a PDF/CSV al almacenamiento elegido.

### Testing

| Capa | Herramientas |
|---|---|
| Unit tests | JUnit 5 + MockK |
| Tests de Flow | Turbine |
| Tests de DAO | Room in-memory database |
| Tests de UI | Compose UI Test |

### Build, calidad y CI

- **Gradle Kotlin DSL** + **Version Catalogs** (`libs.versions.toml`).
- **Detekt** y **ktlint** para calidad estática.
- **GitHub Actions** para CI (build + tests en cada push).
- **Fastlane** para automatizar el deploy a Play Store (fase 3).

---

## Arquitectura

La app sigue **Clean Architecture** en tres capas más una capa transversal:

```
┌─────────────────────────────────────────────┐
│                     ui                      │  ← Compose, ViewModels, Navigation
├─────────────────────────────────────────────┤
│                    domain                   │  ← Modelos puros, casos de uso, contratos
├─────────────────────────────────────────────┤
│                     data                    │  ← Room, mappers, implementación de repos
└─────────────────────────────────────────────┘
              ↑ core (utils transversales) ↑
```

### Reglas de dependencia

- `ui` depende de `domain`, nunca de `data`.
- `domain` no depende de nada de Android ni de frameworks concretos.
- `data` implementa las interfaces declaradas en `domain`.
- `core` es utilizable desde cualquier capa.
- Los `ViewModel` invocan **UseCases**, nunca repositorios directamente.
- Los repositorios exponen `Flow` para que la UI reaccione automáticamente a cambios en la DB.

### Beneficios

- **Testeable**: `domain` se testea con JUnit puro, sin Android.
- **Reemplazable**: cambiar Room por otra fuente de datos no toca la UI.
- **Escalable**: pasar a multi-módulo es mecánico cuando el proyecto crezca.

---

## Estructura del proyecto

```
turnero/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/
│   └── libs.versions.toml              ← version catalog
└── app/
    ├── build.gradle.kts                ← plugins: android app, kotlin, hilt, ksp
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml         ← POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM
        ├── kotlin/com/turnero/app/
        │   │
        │   ├── TurneroApp.kt                          ← @HiltAndroidApp, canales de notif.
        │   ├── MainActivity.kt                        ← @AndroidEntryPoint
        │   │
        │   ├── di/                                    ← módulos Hilt
        │   │   ├── DatabaseModule.kt                  ← provee Room DB + DAOs
        │   │   ├── RepositoryModule.kt                ← binds interfaces → impls
        │   │   └── AlarmModule.kt                     ← provee AlarmManager / Scheduler
        │   │
        │   ├── core/                                  ← utilidades transversales
        │   │   ├── notification/
        │   │   │   ├── NotificationChannels.kt
        │   │   │   ├── TurnoNotifier.kt
        │   │   │   ├── TurnoAlarmScheduler.kt
        │   │   │   └── TurnoAlarmReceiver.kt          ← BroadcastReceiver
        │   │   ├── intent/
        │   │   │   └── ContactIntents.kt              ← whatsapp / sms / tel / email
        │   │   ├── datetime/
        │   │   │   ├── DateFormatters.kt
        │   │   │   └── ClockProvider.kt               ← inyectable, testeable
        │   │   └── ext/
        │   │       └── Extensions.kt
        │   │
        │   ├── domain/                                ← lógica pura, sin Android
        │   │   ├── model/
        │   │   │   ├── Turno.kt
        │   │   │   ├── Cliente.kt
        │   │   │   ├── Servicio.kt
        │   │   │   ├── Bloqueo.kt
        │   │   │   ├── HorarioAtencion.kt
        │   │   │   └── EstadoTurno.kt                 ← enum
        │   │   ├── repository/                        ← interfaces
        │   │   │   ├── TurnoRepository.kt
        │   │   │   ├── ClienteRepository.kt
        │   │   │   ├── ServicioRepository.kt
        │   │   │   └── BloqueoRepository.kt
        │   │   └── usecase/
        │   │       ├── CrearTurnoUseCase.kt           ← valida solapamientos + agenda alarma
        │   │       ├── ActualizarTurnoUseCase.kt
        │   │       ├── CancelarTurnoUseCase.kt        ← cancela alarma también
        │   │       ├── ObtenerAgendaDiaUseCase.kt
        │   │       ├── ObtenerAgendaSemanaUseCase.kt
        │   │       ├── BuscarClienteUseCase.kt
        │   │       └── ObtenerHuecosDisponiblesUseCase.kt
        │   │
        │   ├── data/                                  ← implementación de dominio
        │   │   ├── local/
        │   │   │   ├── TurneroDatabase.kt             ← @Database, versión 1
        │   │   │   ├── Converters.kt
        │   │   │   ├── entity/
        │   │   │   │   ├── TurnoEntity.kt             ← + createdAt/updatedAt/deletedAt
        │   │   │   │   ├── ClienteEntity.kt
        │   │   │   │   ├── ServicioEntity.kt
        │   │   │   │   ├── BloqueoEntity.kt
        │   │   │   │   └── HorarioAtencionEntity.kt
        │   │   │   └── dao/
        │   │   │       ├── TurnoDao.kt                ← queries por rango devuelven Flow
        │   │   │       ├── ClienteDao.kt
        │   │   │       ├── ServicioDao.kt
        │   │   │       └── BloqueoDao.kt
        │   │   ├── mapper/
        │   │   │   ├── TurnoMapper.kt                 ← Entity ↔ Domain
        │   │   │   ├── ClienteMapper.kt
        │   │   │   └── …
        │   │   ├── repository/
        │   │   │   ├── TurnoRepositoryImpl.kt
        │   │   │   ├── ClienteRepositoryImpl.kt
        │   │   │   ├── ServicioRepositoryImpl.kt
        │   │   │   └── BloqueoRepositoryImpl.kt
        │   │   └── preferences/
        │   │       └── AjustesDataStore.kt            ← plantilla WA, tema, primer día
        │   │
        │   └── ui/                                    ← Compose
        │       ├── theme/
        │       │   ├── Color.kt
        │       │   ├── Type.kt
        │       │   └── Theme.kt                       ← Material 3, dark mode
        │       ├── navigation/
        │       │   ├── NavGraph.kt
        │       │   └── Screen.kt                      ← sealed class con rutas + args
        │       ├── components/
        │       │   ├── TurnoCard.kt
        │       │   ├── AgendaDia.kt                   ← timeline con huecos y turnos
        │       │   ├── SelectorFecha.kt
        │       │   ├── SelectorHora.kt
        │       │   ├── EstadoChip.kt
        │       │   ├── EmptyState.kt
        │       │   └── ConfirmDialog.kt
        │       └── screens/
        │           ├── agenda/
        │           │   ├── AgendaScreen.kt            ← vista día/semana con FAB "+"
        │           │   ├── AgendaViewModel.kt
        │           │   └── AgendaUiState.kt
        │           ├── turno_editor/
        │           │   ├── TurnoEditorScreen.kt       ← alta y edición
        │           │   ├── TurnoEditorViewModel.kt
        │           │   └── TurnoEditorUiState.kt
        │           ├── turno_detalle/
        │           │   ├── TurnoDetalleScreen.kt      ← estado + acciones
        │           │   ├── TurnoDetalleViewModel.kt
        │           │   └── TurnoDetalleUiState.kt
        │           ├── clientes/
        │           │   ├── ClientesListScreen.kt
        │           │   ├── ClienteDetalleScreen.kt    ← ficha + historial
        │           │   ├── ClienteEditorScreen.kt
        │           │   └── ClientesViewModel.kt
        │           ├── servicios/
        │           │   ├── ServiciosScreen.kt
        │           │   ├── ServicioEditorScreen.kt
        │           │   └── ServiciosViewModel.kt
        │           └── ajustes/
        │               ├── AjustesScreen.kt           ← horarios, plantilla WA, tema
        │               ├── HorariosScreen.kt
        │               └── AjustesViewModel.kt
        │
        └── res/
            ├── values/
            │   ├── strings.xml
            │   ├── colors.xml
            │   └── themes.xml
            ├── drawable/
            │   └── ic_launcher_foreground.xml
            └── mipmap-anydpi-v26/
                └── ic_launcher.xml

app/src/test/kotlin/com/turnero/app/                  ← unit tests
├── domain/usecase/CrearTurnoUseCaseTest.kt
└── data/repository/TurnoRepositoryImplTest.kt

app/src/androidTest/kotlin/com/turnero/app/           ← tests instrumentados
├── data/local/TurnoDaoTest.kt
└── ui/screens/agenda/AgendaScreenTest.kt
```

---

## Modelo de dominio

Entidades principales y relaciones:

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
- `duracionMin: Int`
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
- `precio: BigDecimal?`
- `color: Int` (para diferenciar en la agenda)
- Auditoría.

### Bloqueo

- `id: UUID`
- `inicio: Instant`
- `fin: Instant`
- `motivo: String?`

### HorarioAtencion

- `id: UUID`
- `diaSemana: DayOfWeek`
- `horaInicio: LocalTime`
- `horaFin: LocalTime`

---

## Requisitos de desarrollo

- **Android Studio** Ladybug (2024.2.1) o superior.
- **JDK 17**.
- **Android SDK 34** instalado.
- **Gradle 8.x** (se descarga automáticamente vía wrapper).
- Un dispositivo físico Android 8+ o emulador API 26+.

---

### Estrategia

- **UseCases**: unit tests puros con fakes de los repositorios.
- **DAOs**: tests instrumentados con Room in-memory.
- **ViewModels**: unit tests con Turbine para verificar emisiones de `StateFlow`.
- **UI**: Compose UI Tests para las pantallas críticas (Agenda, Editor de turno).

---

## Licencia

Por definir.

---

## Autor

Proyecto personal.