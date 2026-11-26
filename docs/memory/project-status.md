# Estado del proyecto — HubPlay Android (TV)

> Entrypoint de cada sesión. Corto a propósito. El detalle de cada
> sesión (medidas, por qué de cada decisión) está en `archive/`; el último
> volcado largo es `archive/project-status-detalle-2026-11-26.md`.
> Última actualización: **2026-11-26** (tarde).

## 1. Dónde estamos

- Rama de trabajo `claude/stoic-feynman-pyojkw` (sin mergear a `main`):
  federación, sesiones del servidor, quitar de Continuar viendo y el crash
  de rails. CI local verde (detekt + 136 unitarios + APK + 14 capturas).
  Backend hermano: `Alexzafra13/HubPlay_demo`, misma rama (federación con
  DirectPlay y pósters en miniatura). **Ninguno de los dos está en la TV.**
- Funciona en la Mi TV real (lo de antes de hoy): login/emparejado, Inicio,
  catálogo, ficha (película y serie), reproductor VOD con chrome propio
  (audio, subtítulos, saltar intro, opciones, miniaturas), TV en directo,
  buscar, colecciones, salvapantallas, intro, Ajustes → Inicio.
- **Hecho hoy y sin probar en la TV**: "Compartido contigo" (pestaña,
  ficha, reproducción, rails en Inicio, búsqueda), liberar sesiones al
  salir/zapear, quitar de Continuar viendo (mantener OK), logout en el
  servidor. Detalle en el archivo del 2026-11-26.
- Sin probar: móvil/tablet, release firmada real, tests de Compose UI.

## 2. Cómo se prueba

- **Mi TV**: `adb connect 192.168.1.132` (Android 11, 960×540 dp). Se cae
  a menudo: `adb disconnect …; adb connect …`; si no hay ping, está
  apagada. Compilar desde Git Bash con `JAVA_HOME=~/.jdks/jbr-21.0.7`:
  `./gradlew :app:detekt :app:testDebugUnitTest :app:assembleDebug -q`.
- **Teclas al mando SOLO con `tools/tvperf/tvkeys.sh`** (`launch`, `k KEY`):
  comprueba que HubPlay está en primer plano. Una tecla suelta ya instaló
  una app de Play Store y escribió en un campo de contraseña de Google.
- **Foco y pantalla**: `tools/tvperf/dump.sh [focus]` (árbol de
  accesibilidad). `screencap` sale blanco o viejo con vídeo por hardware.
  Con el chrome del reproductor, pausar antes de inspeccionar (auto-oculta
  a 4,5 s y cada dump tarda 2-3 s).
- **Rendimiento**: `tools/tvperf/README.md` (framestats por fases, atrace,
  perfil de métodos). Tras `adb install` compilar el dex
  (`cmd package compile -m speed -f com.alex.hubplay.debug`) o el arranque
  mide 3-4 s de más.
- Emparejar, servidores, descubrimiento LAN, trampas del box: ver el
  archivo del 2026-09-11.
- **Capturas sin TV ni emulador** (2026-11-26): Robolectric pinta las
  pantallas reales a 960×540 dp (xhdpi) con datos inventados y carátulas
  generadas, y simula el mando (`press(KEYCODE_DPAD_…)`). PNG en
  `app/build/screenshots/`:
  `./gradlew :app:testDebugUnitTest -Pscreenshots --tests '*screenshots*'`
  (sin `-Pscreenshots` no corren: CI igual de rápido). Código en
  `src/test/.../screenshots/` (`ScreenshotTest`, `SampleCatalog`,
  `FakeArtwork`; `press`, `longPress`, `focusText`). Limitaciones: el foco
  INICIAL de cada pantalla no es fiable (lo fija la primera pulsación), ni
  vídeo, ni tráiler, ni rendimiento, y las ventanas `Dialog` quedan en modo
  táctil (otra razón para usar overlays). Detecta crashes de composición
  al desmontar (así salió el de `focusRestorer`).
- **Sesión en la nube (sin Android Studio)**: SDK con
  `commandlinetools-linux-*` → `sdkmanager "platforms;android-35"
  "build-tools;35.0.0"` y `local.properties` con `sdk.dir`; Gradle 8.14
  sirve. Maven Central da 429: init script en `~/.gradle/init.d/` que
  cambia `repo.maven.apache.org` por
  `https://maven-central.storage-download.googleapis.com/maven2/`, y
  `ROBOLECTRIC_REPO_URL` con esa misma URL para las capturas.

## 3. Arquitectura viva (lo que no se deduce del código en 1 min)

- **Raíz** (`ui/HubplayApp.kt`): capa 0 `TrailerHostOverlay` (único
  WebView, se crea al primer tráiler o a los 5 s), NavHost sin crossfade,
  salvapantallas, `BrandIntro`. `TvShell + NavSidebar` arman las pantallas
  de primer nivel; las filas del menú van `fillMaxWidth` (si no, "derecha"
  no salía del menú).
- **Inicio** (`ui/home`): hero carrusel + `LazyColumn` de rails de alto
  fijo (`RailHeight`, cards de 180 dp de alto, `RAIL_CARD_HEIGHT`). ↑ desde
  el primer rail fuerza el modo hero y pide foco a Reproducir; con el foco
  en el hero se limpia la card (`onHeroFocused`) y vuelve el carrusel.
  Subir entre rails se anima calculando la distancia (todos miden igual).
  Layout de rails: `GET/PUT /me/home/layout` por usuario (= por perfil);
  `HomeRepository.layoutVersion` hace recargar Inicio al guardar desde
  Ajustes → Inicio. Tipos hoy: continue_watching, next_up, trending,
  live_now, latest_in_library; otros necesitan backend.
- **Ficha** (`ui/components/HeroDetail.kt`, Detalle y Series): backdrop +
  tráiler fijos, hero de un viewport y rails con alto mínimo de un viewport;
  al entrar el foco en los rails la sección se pega arriba con
  `animateScrollTo` (el bring-into-view de Compose no sirve para saltos
  largos). Identificar es un overlay (no `Dialog`) con campos ligeros que
  solo componen el `OutlinedTextField` al editar; la búsqueda quita
  "(2019)" del título (`identifyQuery`). `ItemMetadataController` lo
  comparten Detail y Series (declarar `tools` antes del `init`).
- **Reproductor VOD** (`ui/player/VodPlayerChrome.kt`): chrome en Compose
  (el controlador de Media3 nunca recibía teclas); `PlayerView` sin foco ni
  controlador. Logo arriba a la izquierda, reloj a la derecha, abajo barra
  (← → ±30 s), `pos / dur` y "Termina", botones −30 / play / +30 /
  siguiente / audio / subtítulos. Back se gestiona en `handleKey`. Carga:
  backdrop con Ken Burns y línea fina. Pistas de audio desde
  `media_streams` de la ficha: elegir una repide `/info?audio=N` y el
  master `?audio=N` en la posición (el backend transcodifica una pista).
- **Subtítulos** (`ui/player/TrackOptions.kt`, 2026-09-16): lista según
  `media_streams`. Texto (SRT…) → `SingleSampleMediaSource` con el WebVTT de
  `/stream/{id}/subtitles/{stream_index}`, mezclado con `MergingMediaSource`
  (el `HlsMediaSource.Factory` ignora `subtitleConfigurations`); se activa
  por `Format.id`, que ExoPlayer prefija con el índice de fuente
  ("1:hubplay-sub-4"). Media3 1.5 exige `experimentalSetLegacyDecodingEnabled`
  en el `TextRenderer` para VTT crudo (`LegacyTextRenderersFactory`); se
  usa SingleSample a propósito porque no descarga nada hasta elegir la pista.
  Imagen (PGS/DVD/ASS) → `?subtitle=N` en el master (el servidor quema).
  El VTT lo extrae ffmpeg leyendo el fichero entero (~40 s en frío en un
  MKV grande): cliente con timeout de 180 s y "warm-up" de la primera pista
  al arrancar para que el backend (con caché desde 2026-09-16) tenga todas
  listas. Apagados al empezar; elección no se arrastra al siguiente episodio.
- **Tráiler**: protocolo real del IFrame API (`infoDelivery` cada ~270 ms,
  fin 1,5 s antes por tiempo), watchdog lee `host.revealed`, lo que capture
  el bridge JS debe ser un objeto estable.
- **Datos**: Moshi codegen (nada de `KotlinJsonAdapterFactory` propio:
  cargaba kotlin-reflect, 1,1 s de arranque); `NullToEmptyListAdapterFactory`;
  toda lista con `key = { it.id }` deduplica o Compose aborta el proceso.
  `AppContainer`: OkHttp/Retrofit perezosos con `prewarm()` en un hilo
  (el TrustManager propio obliga a leer todas las CA).
- **Federación** (`data/PeersRepository`, `ui/shared/`): todo por
  `/me/peers/…` de NUESTRO servidor (la app nunca ve la URL del amigo).
  `Content.Shared` (kind `MediaKind.Shared`, `peerId`, `remoteKind`) pasa
  por rails, tarjetas (píldora "● Pedro", `LocalSharedBadge`) y
  `HomeActions.play/open`, que deciden canal / amigo / catálogo propio en
  un solo sitio. `PeersRepository.cached()` (LRU) da título y póster a la
  ficha y al reproductor. Las series de amigos no se reproducen (sin
  episodios). La pestaña sale con `SharedAvailability.hasPeers`.
- **Reproductor y servidor** (`data/StreamSessions`, `PlayerRequest`,
  `PlayerDeps`): al salir/pasar de episodio `DELETE /stream/{id}/session`;
  en directo `?v=<viewer>` y baja al zapear (y a los 4 s al salir, para
  que la vista previa herede la sesión). La ruta del reproductor lleva
  `live=true` (sin `/items` + `/info`) o `peer=`. El progreso se escribe
  en el scope de la app (`ProgressReporter` + `ProgressSink`: local o de
  amigo); en `viewModelScope` el último guardado se perdía al salir.
- **Rails sin `focusRestorer`** (`BaseRail` en `HomeRail.kt`): un
  `FocusRequester` por tarjeta y `focusProperties.enter` a la última
  enfocada. `focusRestorer` (Compose 1.7) fija la tarjeta al salir el foco
  y, si desaparece de los datos, la app se cerraba al salir de Inicio. No
  volver a usarlo en listas cuyos items pueden desaparecer.
- **Overlays, no `Dialog`**: Identificar y "¿Quitar de Continuar viendo?"
  (`RemoveFromContinueOverlay`) son overlays con `exit = Cancel`, foco
  inicial en la acción segura, Atrás cierra y el foco vuelve al rail. Una
  ventana nueva costaba 0,5-0,9 s en el TV box. Pulsación larga de OK:
  `Modifier.onLongSelect` en el rail (fase preview, se traga el KeyUp).
- **API < 33**: `URLEncoder.encode(x, "UTF-8")` (la sobrecarga con
  `Charset` no existe en la Mi TV). minSdk 26.
- **Detekt estricto**: `LongMethod` ≤ 80, `LongParameterList` ≤ 7,
  `MagicNumber`, `ImportOrdering`, sin `;` en una línea. Si es legítimo,
  `./gradlew detektBaseline` y commitear.

## 4. Rendimiento (Mi TV, referencia)

- Rail con caché caliente: 29 % de frames con tirón en debug, 18 % en
  release. Con tráiler el box va a ~28 fps fijos (GPU): es el suelo.
- Arranque en frío con dex compilado: 1,2-1,4 s. Abrir la ficha desde
  Inicio: ~300 ms de UI en caliente, el doble en frío (clases + JIT; solo
  lo arregla un Baseline Profile en release).

## 5. Última sesión (2026-11-26, dos tandas)

Mañana: revisión visual con el banco de capturas (A continuación con
artwork, Inicio estilo Netflix, menú en todas las pantallas, saltar
intro, opciones y miniaturas en el reproductor). Tarde: todo lo que el
backend daba y la app no usaba (federación, sesiones, quitar de
Continuar viendo, logout real), arranques más rápidos y un crash latente
de rails. Detalle y porqués: `archive/project-status-detalle-2026-11-26.md`.

## 6. Pendiente (orden sugerido)

0. **Mergear las dos ramas** `claude/stoic-feynman-pyojkw` (app y
   backend) con el CI terminado en verde, **desplegar el backend** en el
   servidor de la TV e instalar la app. Probar en la Mi TV, por este orden:
   - Inicio: "A continuación" con imágenes; mantener OK en Continuar
     viendo → Quitar; salir de Inicio tras quitar (el crash arreglado).
   - Reproductor: pasar de episodio con un fichero que transcodifique (ya
     no debe dar 503); salir y volver a entrar.
   - TV en directo: zapear rápido por varios canales y comprobar en el
     panel del servidor que las sesiones de transmux bajan al momento;
     abrir un canal desde la vista previa (debe salir al instante).
   - Federación (con un segundo servidor emparejado): pestaña Compartido,
     ficha, reproducir un MP4 y un MKV, subtítulos, reanudar, Inicio y
     Buscar. Cerrar sesión y ver que desaparece de "Tus dispositivos".
1. Rails nuevos para Ajustes → Inicio (backend + app): canales favoritos,
   colecciones, recomendado, estrenos, por género, "porque viste X"
   (`/me/home/because-you-watched` ya existe en el backend y la app no lo
   usa). Ideas propuestas el 2026-09-16, sin decidir. Los rails de amigos
   tampoco son configurables todavía.
2. Reproductor: calidad/bitrate; tamaño/estilo/retardo de subtítulos;
   `POST /channels/{id}/playback-failure` cuando un canal no arranca (el
   backend lo usa para la salud de canales y la app no lo manda).
3. Federación: series de amigos (el backend solo comparte items de primer
   nivel) y quitar de "Seguir viendo de tus amigos" (no hay endpoint).
4. Estudio Lucasfilm devuelve 0 títulos (backend). Detalle sin artwork,
   `CollectionDetailScreen`, probar en móvil.
5. Largo plazo: Baseline Profile (necesita el aparato), EPG grid, tests de
   Compose UI en CI, gestión de dispositivos (`/me/sessions`) en Ajustes.
