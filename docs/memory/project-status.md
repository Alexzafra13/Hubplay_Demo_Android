# Estado del proyecto — HubPlay Android

> **Última sesión**: 2026-05-20 — rama `claude/kotlin-project-review-faLKV`
> (multi-perfil "Who's watching?").
> **Estado**: feature-complete contra la lista priorizada por el usuario,
> ahora con gating multi-perfil tras login. CI verde sobre 8.10.2 + Java
> 17 con detekt en modo soft (deuda viva sigue pendiente). EPG grid y
> "Recientemente visto" siguen abiertos.
> **Leer este fichero al inicio de cada sesión** para retomar contexto.

---

## 🧑‍🤝‍🧑 Sesión 2026-05-20 — WhoIsWatching (multi-perfil)

Primer bloque del plan "paridad con web" — gate de selección de perfil
después del device pairing. Decisión del usuario: **recordar perfil hasta
logout** (no pedir cada arranque).

### Wire

- **Backend ya estaba**: `GET /api/v1/me/profiles` devuelve el subtree
  del usuario actual (`profileListResponse` en `internal/api/handlers/auth.go`),
  `POST /api/v1/auth/switch-profile` `{ profile_id, pin, device_name,
  device_id }` mintea tokens nuevos para el target. Ambos siguen el
  envelope `{ data: ... }`.
- **DTOs nuevos** en `data/api/dto/ProfileDto.kt`: `ProfileSummaryDto`,
  `ProfilesResponse`, `SwitchProfileRequest`, `SwitchProfileData`,
  `SwitchProfileResponse`.
- **`HubplayApi`**: añadidos `listProfiles()` (GET) y `switchProfile(body)`
  (POST). El bearer actual va vía el AuthInterceptor; el server valida
  same-parent antes de emitir los nuevos tokens.

### Estado persistido

`TokenStore` ahora guarda dos nuevos campos:
- `active_profile_id` → el perfil que el usuario picó. Vive hasta el
  próximo logout / forget-server / "Cambiar perfil" en Settings.
- `active_profile_name` → label cacheado para que la TopNav / Settings
  pinte el nombre sin un fetch extra.

`AuthState` se amplió con esos dos campos. Helpers nuevos:
`setActiveProfile(id, name)`, `clearActiveProfile()` (suspend y blocking).
`clear()` y `forgetServer()` también borran ambos.

### Gating del NavGraph

Tres-estados al arranque (en `ui/HubplayApp.kt`):
- no token → `Login`
- token + `activeProfileId == null` → `WhoIsWatching`
- token + `activeProfileId != null` → `Home`

Tras login (`onAuthenticated`), siempre se navega a `WhoIsWatching`
con `popUpTo(Login, inclusive)`. El propio screen decide:
- profiles fetch null (red) → muestra error + retry
- profiles vacío → SkipToHome (solo deploy, sin nada que pickear)
- profiles == 1 → pin del solo y SkipToHome (no se ve la pantalla)
- profiles > 1 → grid de avatares

Auto-skip ≤ 1 mirror del web (`useEffect(() => navigate("/", { replace }))`
en `WhoIsWatching.tsx`).

### Picker

`ui/whoiswatching/WhoIsWatchingScreen.kt` — grid adaptativo (minSize
180dp) de tiles circulares con:
- avatar URL del backend si lo hay (Coil + main OkHttp authenticated).
- fallback: círculo coloreado con iniciales. Color por hash FNV-1a 32-bit
  del nombre del perfil sobre la misma paleta de 8 colores que el web
  (`web/src/utils/avatarColor.ts`). Si el perfil tiene `avatar_color`
  override (hex), gana.
- badge "Protegido con PIN" cuando aplica.
- click sin PIN → switch directo.
- click con PIN → modal con `OutlinedTextField` (`NumberPassword`),
  máximo 4 dígitos, "Desbloquear" habilitado sólo cuando se cumple
  la longitud. Wrong PIN → `isError` + texto rojo, dialog queda abierto.

### Repo

`data/ProfileRepository.kt` expone `list()` (con server-URL aware
absolutize para los avatar URLs), `switch(profileId, pin?, displayName?)`
que persiste tokens + activeProfileId atómicamente, y
`pinCurrentAsActive(profileId, displayName)` para el auto-skip de solo
accounts. Constructor primario toma lambdas (testeable sin Android
Context), constructor secundario toma TokenStore (production).

`SwitchResult` sealed: `Success` / `InvalidPin` (401) / `NotAllowed`
(403) / `Failure(message)`.

### Settings

Nueva tarjeta "Perfil" (sólo cuando `activeProfileName != null`) con:
- label "Activo" + nombre del perfil actual.
- botón "Cambiar perfil" → `clearActiveProfileBlocking()` + navigate
  WhoIsWatching con `popUpTo(Home, inclusive)`. Stack queda
  `[WhoIsWatching]` para que tras pickear se pueda popUpTo limpio a
  `[Home]`.

### Tests

`data/ProfileRepositoryTest.kt` — 6 unitarios:
- DTO → domain mapping (display_name fallback a username, avatar URL
  absolutización).
- list devuelve `null` en fallo de red (no empty list — la distinción
  importa para no auto-skipear cuando la red ha caído).
- list devuelve `[]` en deploy sin profiles → solo + SkipToHome.
- switch éxito persiste tokens + activeProfileId.
- switch 401 (wrong PIN) NO persiste (security: si persistiera, el
  brute-force loguearía con el bearer rotado de la víctima).
- switch sin tokens en respuesta → Failure.
- pinCurrentAsActive sólo escribe el flag, no toca tokens.

`FakeApi` (privada en el test) implementa los 28 endpoints de
`HubplayApi` con `TODO()` para los irrelevantes (mismo patrón que
`ProgressReporterTest`).

`ProgressReporterTest.FakeApi` extendido con stubs `listProfiles` /
`switchProfile` para que compile.

### Strings (i18n)

Nuevas keys en `values/strings.xml` + `values-en/strings.xml`:
`who_title`, `who_subtitle`, `who_loading`, `who_error`, `who_locked`,
`who_pin_dialog_title`, `who_pin_dialog_subtitle`, `who_pin_input_hint`,
`who_pin_action_unlock`, `who_pin_action_cancel`, `who_pin_error_invalid`,
`who_switch_failed`, `who_avatar_cd`, `settings_section_profile`,
`settings_profile_help`, `settings_action_change_profile`,
`settings_label_active_profile`. Paridad ES / EN verificada.

### Lo que NO se hizo (próximos bloques del plan)

Backlog del usuario por orden:
1. ~~WhoIsWatching~~ ✅
2. PersonDetail + StudioDetail (click en cast/director del ItemDetail).
3. Collections (lista + detalle, ruta `/collections`).
4. MyNotifications (campana en TopNav + SSE event `notification.created`).
5. ChangePassword (sección en Settings, no pantalla aparte).
6. Federation (peers/libraries/items/player — el bloque más pesado).

Después: blurhash placeholders en MediaCard, versión del servidor en
Settings.

Deuda viva del backlog anterior sigue: re-apretar detekt
(`ignoreFailures = false`), "Recientemente visto" en sidebar Live TV,
EPG grid alternativo, tests Compose UI + emulator en CI, Baseline
Profile.

---

## Snapshot rápido

- **Branch viva**: `claude/review-android-project-O2OMf` (21 commits encima de `main`).
- **Backend**: `Alexzafra13/HubPlay_demo` — la app habla con `/api/v1/`. El backend de la última sesión añadió **mDNS auto-anuncio** (`hubplay.local`) que esta app ya consume.
- **CI**: `.github/workflows/ci.yml` — green. detekt en modo estricto (`ignoreFailures = false`).
- **Release**: `.github/workflows/release.yml` — preparado, dormido hasta que se empuje un tag `v*` y se llenen los 5 secrets.
- **Versionado**: `versionCode 3 / versionName 0.2.0` por defecto en `app/build.gradle.kts`; release calcula del tag.

---

## Stack pinneado (NO bumpear piecewise)

Todos están en `gradle/libs.versions.toml` con comentarios sobre el porqué del pin. La regla operativa: cuando alguno suba, suben EN BLOQUE (AGP + Kotlin + KSP + Retrofit + OkHttp + Compose BOM).

- AGP 8.7.3 · Kotlin 2.0.21 · KSP 2.0.21-1.0.28
- Gradle 8.10.2 (CI usa `gradle/actions/setup-gradle@v4`, no necesita wrapper jar commiteado)
- Compose BOM 2024.12.01 · Media3 1.5.0
- OkHttp 4.12.0 + Retrofit 2.11.0 (5.x / 2.12.x necesitan Kotlin 2.2 metadata — pinned)
- Coil 3.0.4 · Moshi 1.15.2 · kotlinx-coroutines 1.9.0
- ZXing 3.5.3 (QR encoder, pure Java)
- gradle-play-publisher 3.12.1
- detekt 1.23.7 + ktlint-backed formatting

JDK objetivo: 17 (compileOptions + kotlinOptions). CI provisiona Temurin 17.

---

## Arquitectura

Single-Activity Compose. DI manual vía `AppContainer` (manual hasta cruzar ~15 nodos, hoy ~10).

```
app/src/main/kotlin/com/alex/hubplay/
├── HubplayApp.kt              # Application; instala SingletonImageLoader + CrashLogger
├── MainActivity.kt            # Single Activity; dispatch overrides → IdleController
├── data/
│   ├── AppContainer.kt        # DI manual — singletons del proceso, refresh screensaver pool al login
│   ├── TokenStore.kt          # DataStore: tokens + serverUrl; clear() vs forgetServer()
│   ├── BaseUrlInterceptor.kt  # Rewrite scheme/host/port en runtime
│   ├── AuthInterceptor.kt     # Bearer + auto-refresh en 401 (single-refresh-in-flight)
│   ├── DeviceCodeRepository.kt
│   ├── HomeRepository.kt      # Items, Latest, Trending, Continue Watching, Next Up, Search
│   ├── LiveTvRepository.kt    # IPTV channels + EPG + favorites
│   ├── ProgressReporter.kt    # Throttled PUT /me/progress + markPlayed; TimeSource inyectable
│   ├── MeEventsStream.kt      # SSE /me/events listener (okhttp-sse)
│   ├── LanDiscovery.kt        # mDNS via NsdManager (_http._tcp.)
│   ├── IdleController.kt      # Screensaver idle timer (3 min default)
│   ├── ScreensaverImageSource.kt
│   ├── CrashLogger.kt         # On-device crash log ring buffer (10 entries, filesDir)
│   ├── TimeSource.kt          # `fun interface` para tests del throttle
│   └── api/                   # HubplayApi (hand-written) + AuthApi (generated)
├── player/                    # HubplayPlayer (Media3 wrapper) + ClientCapabilities
└── ui/
    ├── HubplayApp.kt          # Root composable — NavGraph + ScreensaverOverlay
    ├── theme/                 # HubPlay dark theme + Accent palette
    ├── components/            # QrCode, BackPill, HeroIconButton, HeroCtaButton
    ├── nav/                   # Routes + NavGraph
    ├── login/                 # QR + manual code + mDNS auto-skip
    ├── home/                  # Netflix-style hero + rails (Continue, Trending, Latest x lib, LiveNow)
    │   └── components/        # MediaCard, HeroSection, FocusedHero, TopNav, …
    ├── catalog/               # Movies / Series grid
    ├── detail/                # Movie / episode detail
    ├── series/                # Series + seasons + episodes + resume resolver
    ├── livetv/                # Channels grid + sidebar + EPG row + hero + preview
    ├── player/                # PlayerScreen (VOD + Live) + TrackSelectionSheet
    ├── search/                # Full-text catalogue search
    ├── settings/              # Server info, diagnostic logs, logout, change-server
    └── screensaver/           # Jellyfin-style idle slideshow
```

### Servidor-side notas que importan a la app

- Backend convención: todas las respuestas vienen como `{ "data": ... }`. Los DTOs (`*Response`) wrappean. Los repos peel.
- Errors: `{ "error": { code, message } }` — Retrofit los lanza como `HttpException`, los repos los catchan.
- Ticks de Jellyfin: 10_000_000 por segundo. `ProgressReporter` convierte.
- mDNS: el backend registra `_http._tcp` con hostname `hubplay.local` (configurable). `LanDiscovery` filtra por nombre que contenga "hubplay".

---

## Lo que se ha construido en esta rama (21 commits)

### Auth + emparejamiento
- Login con **QR + código manual**, lado a lado en pantallas anchas, apilados en móvil. QR codifica `verification_uri_complete` (RFC 8628).
- **mDNS LAN discovery** estilo Steam Link — auto-skip de stage 1 si hay exactamente 1 servidor.
- Logout vs Cambiar servidor separados (`TokenStore.clear()` vs `forgetServer()`).

### Reproducción + sync
- **Progress reporting**: PUT `/me/progress` throttled 10s, flush en dispose, markPlayed al 95%. `TimeSource` inyectable para tests.
- **SSE `/me/events`** con auto-reconnect; HomeViewModel refresca cross-device.
- **Track selection** (audio + subtítulos) vía ModalBottomSheet con Media3 Tracks API.
- **Keep-screen-on** durante playback (`PlayerView.keepScreenOn = true`).
- **Screensaver idle** estilo Jellyfin: Ken Burns + crossfade 14s, clock + brand watermark. 3 min idle, suspendido durante playback.

### Catálogo
- Home Netflix-style con hero + multi-rails configurables (`/me/home/layout`).
- Detail screen con corazón de favoritos + trailer overlay.
- Series screen con seasons + episodes + resume resolver (4 modos).
- Movies/Series catalog grids.
- Live TV: channels + sidebar (con bottom Settings + Reorder placeholder) + EPG row + hero + preview.
- **Search** como tab dedicada (debounce 300ms, min 2 chars).
- **Favoritos** en movies/series (POST `/me/progress/{id}/favorite`).

### Settings + diagnóstico
- SettingsScreen con server info, app version/build, logout/forget-server, diagnostic logs viewer.
- CrashLogger on-device sin terceros (ring buffer 10 entradas en `filesDir`).

### Build + calidad
- CI: openApiGenerate → detekt → tests → assembleDebug. Uploads test/detekt reports + APK.
- Release workflow: tag `v*` → AAB firmado → Play Store internal track (gradle-play-publisher).
- detekt 1.23.7 con config tuned para Compose (PascalCase OK, max line 140, MagicNumber permisivo en padding, etc.). Baseline en `config/detekt-baseline.xml` (empty), modo estricto.
- R8/ProGuard rules para Moshi, Retrofit, OkHttp SSE, Media3, ZXing, Compose annotations.
- @Immutable en 11 data classes calientes (MediaItem, LiveChannel, EpgProgram, todos los *UiState).
- Coil cache tuned (30% memoria, 256MB disco, crossfade 300ms).
- **Splash screen** branded con backport API 26+.

### i18n
- **114 strings extraídos** a `values/strings.xml` (es-ES) + `values-en/strings.xml` (en).
- Tab enum refactor: `val label: String` → `@StringRes val labelRes: Int`.
- Format strings con argumentos posicionales (`S%1$d · E%2$d`).
- ViewModels NO refactorizados — sus errores Spanish siguen hardcoded, refactor distinto.

### Tests
- 19 unitarios verdes: SeriesResumeResolver (6), normalizeServerUrl (6), ProgressReporter (7 — incluye 2 nuevos del throttle window con `TimeSource` fake).

### Play Store prep
- `docs/PLAY_STORE.md` — pasos manuales end-to-end (cuenta, App Signing, service account, secrets).
- `docs/PRIVACY.md` — política de privacidad template.
- `docs/PLAY_STORE_DATA_SAFETY.md` — cheat-sheet del form de Data Safety.

---

## Backlog priorizado

### Primer commit de la próxima sesión (DEUDA viva)

**Re-apretar detekt**. En esta rama intenté `ignoreFailures = false`
(commit `887d93c`) y CI cayó por findings pre-existentes. Lo dejé
revertido a soft mode (`ignoreFailures = true`) para que el PR sea
mergeable. La próxima sesión:

1. En el run rojo del CI (commit `887d93c`), baja el artefacto
   `detekt-report` desde la pestaña Actions del repo.
2. Abre `app/build/reports/detekt/detekt.html` del zip.
3. Por cada finding decide: fix o baseline.
   - Para fix: edita el código, re-pushea.
   - Para baseline: `./gradlew :app:detektBaseline` en local genera
     `config/detekt-baseline.xml` con todos los findings actuales.
     Commit ese fichero. Hace que esos findings concretos queden
     "grandfathered" pero código nuevo siga teniendo el gate.
4. Flip `ignoreFailures` de `true` a `false` en `app/build.gradle.kts`.
5. Push. CI debe pasar verde.

Sin esto, detekt sigue corriendo y subiendo el report en cada run pero
no falla CI por findings nuevos. Funcional pero no es el gate que
queremos a medio plazo.

### Próxima sesión candidata (Live TV polish)

1. ~~**Pantalla de reorder + hide canales con sync multidispositivo**~~ — **HECHO** (rama `claude/kotlin-app-development-s0o78`).
   - **Backend ya estaba**: `PUT /me/iptv/channels/order`,
     `PUT /me/iptv/channels/{id}/visibility`, `DELETE /me/iptv/channels/order`.
     El listado normal (`/libraries/{id}/channels`) ya aplica el overlay
     del usuario server-side (`GetChannelsForUser`), y con
     `?include_hidden=true` devuelve todos los canales (con `hidden`
     flag) para la pantalla de personalización.
   - **Android `HubplayApi`**: 3 endpoints añadidos a mano (no via OpenAPI;
     las rutas no están documentadas todavía en `openapi.yaml` del backend).
     Extendido `listChannels` con `?include_hidden`.
   - **`LiveTvRepository`** — wrappers `fetchChannelsForPersonalisation`,
     `replaceChannelOrder`, `setChannelVisibility`, `resetChannelOrder`.
     `LiveChannel` ahora lleva `hidden: Boolean`, `userPosition: Int?`.
   - **`ChannelOrderViewModel`** — server-first:
     - Carga via personalisation view (todos los canales con flag hidden).
     - Toggle hide: optimista + `PUT visibility` (per-channel, payload chico),
       rollback en fallo.
     - Move ↑/↓: optimista + `PUT order` debounced 300ms (coalesce de holds rápidos).
     - Reset: `DELETE order` y refetch.
   - **`LiveTvViewModel`** — drop del overlay local. La lista que vuelve
     del backend ya viene en orden personalizado. Observa
     `ChannelOrderStore.prefsFlow` con `drop(1)` como señal de
     "edit happened, refetch ahora".
   - **`ChannelOrderStore`** — pasa de source-of-truth a cache write-through
     + bus de señal. Mismo blob JSON (`hubplay_channel_prefs`). Los helpers
     `applyPrefs` se eliminaron (yagn).
   - **Sync multidispositivo + live-push SSE**: TV1 edita → backend
     persiste → publica `user.channel.order.updated` en `/me/events`. TV2
     lo recibe vía `MeEventsStream`, `LiveTvViewModel.observeServerEvents`
     dispara `load()` y el orden nuevo aparece en el instante sin
     renavegar. El evento solo lleva `user_id` en Data (filtrado per-user
     en el lado servidor); contenido irrelevante, el tick es lo que cuenta.
   - **Type-a-number para mover** (estilo TV): teclear dígitos sobre una
     fila enfocada en la pantalla de reorder dispara `appendMoveDigit`.
     La fila se ilumina con borde accent y el slot LCN cambia a "→ 47";
     tras 1.2s de pausa (o Enter/OK) la fila se mueve a la posición 47.
     Backspace borra dígitos, Esc/Back cancela. Buffer cap 4 dígitos.
     Saltar del foco a otra fila durante el buffer cancela el move
     pendiente — evita latching cuando el usuario navega.

2. **"Recientemente visto"** filtro auto-generado en sidebar Live TV.
   - Storage: lista circular en DataStore (últimos 20 channel IDs vistos).
   - Hook: `LiveTvViewModel.recordWatch` ya existe — solo añadir append al store.
   - Aparece como filtro en `LiveTvSidebar` entre "Favoritos" y los grupos.

### Sesiones posteriores

3. **Vista EPG grid completa** estilo Movistar.
   - **No tocar el inicio actual de Live TV** — el usuario lo quiere preservado.
   - Pantalla ALTERNATIVA accesible desde un botón nuevo en el sidebar o desde un toggle en LiveTvScreen.
   - Grid: filas = canales, columnas = ventana de tiempo (4-6h por defecto), celdas = programas.
   - Scroll horizontal (D-pad ← →) + vertical (↑ ↓). Click en programa → ¿detalle del programa? ¿reproducir canal?

4. **Tests de Compose UI** + emulator action en CI.
   - Necesita configurar `reactivecircus/android-emulator-runner@v2` en CI.
   - Flujos a cubrir: Login → Home → Play, Search → Detail, sidebar Live TV → channel → play.
   - Coste: la matrix de emulator añade ~5-10 min al CI run.

5. **Baseline Profile** — startup perf.
   - Necesita ejecutar Macrobenchmark en device real (no se puede 100% desde CI gratis).
   - Mide cold-start hoy, genera profile, commitea `app/src/main/baseline-prof.txt`.
   - Mejora medible (~15-30% cold start) que Play Store reporta en consola.

### Lo que el usuario explícitamente descartó

- **MediaSession + foreground service** (Android Auto / lockscreen controls). Skipped en sesión 2026-05-20.
- **Cast / Chromecast**. Skipped en sesión 2026-05-20.

### Manual antes de Play Store

- Editar `docs/PRIVACY.md` con email + fecha real.
- Hostearlo en URL pública (GitHub Pages recomendado).
- Capturas (teléfono + Android TV obligatorio porque declaramos leanback).
- Icon 512×512 + feature graphic 1024×500.
- Cumplir `docs/PLAY_STORE.md` paso a paso.
- 5 GitHub Secrets (RELEASE_KEYSTORE_*, PLAY_SERVICE_ACCOUNT_*).
- Closed Testing × 14 testers × 14 días (regla 2024).

---

## Convenciones del proyecto

### Composables

- PascalCase function names (detekt config lo permite).
- `Modifier` siempre como último parámetro con default `Modifier`.
- Modifier chains amplios — detekt max-line 140 col.
- KDoc explicando el *why* en composables no triviales.
- @Immutable en cualquier data class que cruce un parámetro Composable. Aunque Kotlin infiera estabilidad para la mayoría de campos, `List<String>` y similares se tratan como inestables sin la anotación.

### ViewModels

- Manual DI: factory companion + `viewModel(factory = ...)` en NavGraph.
- StateFlow para state, MutableSharedFlow para eventos one-shot.
- `viewModelScope` para todo lo que vive con la pantalla.
- Errores en español como string en el state (no localizado — pendiente refactor con Resources injection).

### Data layer

- DTOs `@JsonClass(generateAdapter = true)` + Moshi-KSP.
- Repositories mapean DTOs → domain types (MediaItem, LiveChannel, …).
- HTTP errors no se propagan al UI: cada call en `runCatching` con `Log.w` + return seguro.
- Ticks (10M/s) se convierten en seconds en los repos antes de llegar al UI.

### Network

- `AuthInterceptor` con single-refresh-in-flight (AtomicBoolean) → varios 401 paralelos no disparan races.
- `BaseUrlInterceptor` reescribe scheme/host/port → un Retrofit instance único para cualquier servidor.
- `mainOkHttp` con `readTimeout = 0` (SSE friendly); `refreshClient` separado con timeouts cortos para no recursar en auth.

### Tests

- `src/test/kotlin/` (declared en `sourceSets`).
- runTest con `UnconfinedTestDispatcher` cuando hay launches anidados en mutex (ver `ProgressReporterTest` KDoc).
- Inyectar `TimeSource` cuando el código lee `System.currentTimeMillis()`.
- FakeApi en lugar de mocks — declarar TODO() en métodos no usados para que tests rompan si alguien añade dependencia accidental.

### CI

- Workflows en `.github/workflows/` — `ci.yml` (push + PR), `release.yml` (tags + manual dispatch).
- `setup-gradle@v4` con `gradle-version: 8.10.2` — no necesita wrapper jar commiteado.
- Test reports + detekt report siempre subidos como artifacts (incluso en rojo).

---

## Cosas que aprendí por las malas en esta rama

1. **`gradle-daemon-jvm.properties`** generado por Android Studio rompía CI por exigir JDK 21 JetBrains. Lo borramos. Si Android Studio lo regenera localmente, NO commitear.

2. **`backgroundScope` + `StandardTestDispatcher` + scope.launch anidado en Mutex.withLock = tests que fallan silenciosamente**. Solución: `UnconfinedTestDispatcher` + plain `CoroutineScope(Dispatchers.Unconfined)`.

3. **`Modifier.androidx.compose.foundation.clickable`** NO compila. Importar `clickable` y usar `Modifier.clickable(...)`.

4. **`R8 strips @Immutable / @Stable`** silenciosamente si no hay regla de keep — Compose stability inference depende de ellas en builds release. Ya en `proguard-rules.pro`.

5. **`installSplashScreen()` ANTES de `super.onCreate`** o el theme de launch sigue activo durante la primera frame de Compose.

6. **mDNS service type**: el backend usa `_http._tcp.` (con punto final). Si alguna TV no resuelve, probar sin el punto — algunos NsdManager son quisquillosos.

7. **`Tab.x.label: String`** se convirtió en `Tab.x.labelRes: Int` durante el strings extraction. Si alguien añade un nuevo Tab, añadir entry en `R.string.nav_tab_*`.

8. **`lateinit container`** en MainActivity necesita guard en `dispatchKeyEvent` — la framework puede mandar eventos antes de `onCreate`.

---

## Cómo arrancar la próxima sesión

1. `git fetch origin && git checkout claude/review-android-project-O2OMf`.
2. Abrir en Android Studio → Sync Project (genera wrapper).
3. Leer este fichero + `docs/PLAY_STORE.md` si vas a tocar release.
4. Si la sesión es la de **reorder canales**: revisa el placeholder `onReorderChannels` en `LiveTvSidebar.kt` y el commit `4238b6a` para el contexto del sidebar redesign.
5. Para cualquier cambio que pase por CI: `./gradlew :app:detekt :app:testDebugUnitTest :app:assembleDebug` debería pasar localmente antes de pushear.
