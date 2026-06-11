# HubPlay Android — Contexto de proyecto

> Cliente Android nativo para [HubPlay](https://github.com/Alexzafra13/HubPlay_demo).
> Single-Activity Compose, Media3 ExoPlayer, mDNS LAN discovery,
> screensaver Jellyfin-style. Sirve para móvil + tablet + Android TV
> (Leanback declarado en Manifest).
>
> **Leer `docs/memory/project-status.md` al inicio de cada sesión** para
> retomar contexto. Ese fichero captura qué se ha hecho, qué falta,
> convenciones y lecciones aprendidas.

---

## Stack pinneado

Versiones en `gradle/libs.versions.toml` con comentarios explicando el porqué. La regla operativa: cuando se sube algo, sube en bloque (AGP + Kotlin + KSP + Retrofit + OkHttp + Compose BOM).

- AGP 8.7.3 · Kotlin 2.0.21 · KSP 2.0.21-1.0.28
- Gradle 8.10.2 · JDK 17 (Temurin en CI)
- Compose BOM 2024.12.01 · Media3 1.5.0
- OkHttp 4.12.0 + Retrofit 2.11.0 (pinneado — 5.x necesita Kotlin 2.2 metadata)
- Moshi 1.15.2 + KSP codegen · Coil 3.0.4 · kotlinx-coroutines 1.9.0
- detekt 1.23.7 + ktlint-formatting · ZXing 3.5.3 · gradle-play-publisher 3.12.1

---

## Comandos habituales

```bash
./gradlew :app:assembleDebug         # APK debug
./gradlew :app:installDebug          # instala en device conectado
./gradlew :app:testDebugUnitTest     # unitarios JVM (src/test/kotlin)
./gradlew :app:detekt                # static analysis (config/detekt.yml)
./gradlew :app:detektBaseline        # regenera baseline tras aceptar findings
./gradlew refreshOpenApiSpec         # actualiza openapi-cached.yaml del backend
./gradlew :app:openApiGenerate       # regenera el cliente Retrofit
./gradlew :app:bundleRelease         # AAB firmado (necesita env vars de release)
```

CI corre en cada push: `openApiGenerate → detekt → testDebugUnitTest → assembleDebug`.

---

## Layout

```
app/src/main/kotlin/com/alex/hubplay/
  HubplayApp.kt              # Application — instala CrashLogger + SingletonImageLoader
  MainActivity.kt            # Single Activity — dispatch overrides para IdleController
  data/                      # Repos, interceptors, DTOs, store
  player/                    # HubplayPlayer (Media3 wrapper) + capabilities
  ui/                        # Composables por feature: login, home, livetv, player, …
app/src/main/res/
  values/strings.xml         # es-ES (default locale)
  values-en/strings.xml      # en (114 keys, parity verificado)
  drawable/                  # brand_mark, brand_wordmark, ic_launcher
config/detekt.yml            # static analysis config (Compose-tuned)
config/detekt-baseline.xml   # findings grandfathered
docs/
  memory/project-status.md   # ESTADO PROYECTO — leer al arrancar sesión
  PLAY_STORE.md              # pasos manuales para publicar
  PRIVACY.md                 # política template
  PLAY_STORE_DATA_SAFETY.md  # cheat-sheet del form de Data Safety
.github/workflows/
  ci.yml                     # build + tests + static analysis
  release.yml                # tag v* → AAB firmado → Play Store internal
```

---

## Memoria de proyecto

Ver `docs/memory/` (versionado en git) para contexto entre sesiones:

- `project-status.md` — estado actual, qué se hizo, qué falta, próximos
  pasos, convenciones, lecciones aprendidas.

**Leer `docs/memory/project-status.md` al inicio de cada sesión**.

---

## Convenciones rápidas

- **DI manual** vía `AppContainer`. No Hilt hasta cruzar ~15 nodos.
- **ViewModels** con `viewModelScope` + StateFlow + factory companion.
- **DTOs** `@JsonClass(generateAdapter = true)`; envueltos en
  `*Response` porque el backend siempre devuelve `{ "data": ... }`.
- **@Immutable** en data classes que cruzan parámetros @Composable —
  Kotlin no infiere estabilidad para `List<String>` y similares.
- **Strings de usuario en `strings.xml`** (Composables). Strings de
  ViewModels todavía hardcoded — refactor distinto.
- **Detekt en modo estricto** — un finding nuevo rompe CI. Si es
  legítimo: arreglar o `./gradlew detektBaseline` y commitear.
- **Tests con `UnconfinedTestDispatcher`** cuando hay launches anidados
  en mutex (ver `ProgressReporterTest`). Inyectar `TimeSource` cuando
  el código lee wall-clock.
- **CI sin wrapper jar** — usa `setup-gradle@v4` con `gradle-version`.

---

## Backlog estratégico

Ver `docs/memory/project-status.md → Backlog priorizado`. Lo más
candente para la siguiente sesión:

1. ~~**Pantalla de reorder canales + hide canales**~~ — HECHO.
2. ~~**"Recientemente visto"** filtro auto en sidebar Live TV~~ — HECHO
   (2026-06-10, server-backed vía `/me/channels/continue-watching`).
3. **Vista EPG grid completa** como pantalla alternativa (NO tocar
   el inicio actual de Live TV).
4. **Tests de Compose UI** + emulator en CI.
5. **Baseline Profile** (necesita device real).

Descartados explícitamente: MediaSession (Android Auto), Cast.
