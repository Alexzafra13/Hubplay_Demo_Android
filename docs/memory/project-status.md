# Estado del proyecto — HubPlay Android (TV)

> Entrypoint de cada sesión. Corto a propósito. El detalle de cada
> sesión (medidas, por qué de cada decisión) está en `archive/`; los
> últimos volcados son `archive/project-status-detalle-2026-11-26.md` y
> `archive/project-status-detalle-2026-11-28.md`.
> Última actualización: **2026-11-28** (todo mergeado a `main`).
>
> **¿Vienes a instalar y probar en la TV?** Ve directo a
> "§0. Próxima sesión: instalar por adb y probar".

## 0. Próxima sesión: instalar por adb y probar

Todo lo de 2026-11-26..28 está en `main` de los dos repos (app: merge
`df23f01`; backend: merge `d17366b`). Nada está probado aún en la tele.
Orden:

1. **Actualizar el código** en el PC: `git checkout main && git pull` en
   `Hubplay_Demo_Android` (y en `HubPlay_demo` si se va a mirar algo).
2. **Desplegar el backend primero** (sin él, lo nuevo da 404 o sale vacío):
   - En GitHub Actions de `HubPlay_demo`, esperar a que el workflow
     **Docker** de `main` termine en verde: publica
     `ghcr.io/alexzafra13/hubplay_demo:latest`.
   - En el servidor de la TV (`192.168.1.100`, puerto 8097), en la carpeta
     de su compose: `docker compose pull && docker compose up -d`.
     Al arrancar aplica la migración **059** (re-enlaza estudios) tras
     hacer copia de la BD. Comprobar `http://192.168.1.100:8097/api/v1/health`
     y que la ficha de un título de Lucasfilm enlaza a un estudio con títulos.
   - Para probar **series de amigos**, el servidor del amigo también
     necesita esta versión (si no, la app avisa de que no puede listar
     los episodios).
3. **Instalar la app** (Git Bash, ver CLAUDE.md § Entorno Windows):
   ```bash
   adb connect 192.168.1.132        # si falla: adb disconnect; adb connect …
   JAVA_HOME=~/.jdks/jbr-21.0.7 ./gradlew :app:installDebug
   adb -s 192.168.1.132:5555 shell cmd package compile -m speed -f com.alex.hubplay.debug
   source tools/tvperf/tvkeys.sh && launch
   ```
   La debug es `com.alex.hubplay.debug` (convive con la release). El
   `compile -m speed` evita medir 3-4 s de más en frío.
4. **Moverse SOLO con `tools/tvperf/tvkeys.sh`** (`k KEYCODE_DPAD_DOWN`,
   `k KEYCODE_DPAD_CENTER`, `k KEYCODE_BACK`…): comprueba que HubPlay está
   en primer plano antes de mandar la tecla. Ver lo enfocado con
   `tools/tvperf/dump.sh focus`; `screencap` sale blanco con vídeo.
5. **Qué probar** (lo nuevo, de más a menos importante):
   - **Inicio**: rails nuevos tras los del servidor (Porque viste X,
     Recomendados para ti, Colecciones → abre la ficha de la saga,
     Canales favoritos → reproduce); mantener OK en Continuar viendo →
     Quitar; salir de Inicio después (crash arreglado).
   - **Serie propia → Episodios**: el foco nace en la temporada, la
     cabecera no queda bajo el menú y Atrás vuelve a la ficha.
   - **Reproductor**: Opciones → Calidad (Auto / variantes) y Subtítulos
     (tamaño, contorno/sombra/caja; se recuerdan). Cortar la red o abrir
     algo roto: mensaje claro y OK reintenta. Pasar de episodio con un
     fichero que transcodifique.
   - **TV en directo**: zapear rápido (las sesiones bajan en el panel del
     servidor); menú lateral → **Guía completa**: moverse por la rejilla
     (←→ en el tiempo, ↑↓ entre canales sin saltar de hora), que no dé
     tirones con muchos canales y que OK abra el canal. Un canal que no
     arranque debe mostrar error (y el servidor lo marca en su salud).
   - **Ajustes → Seguridad → Dispositivos conectados**: "Esta tele"
     arriba y sin botón de cerrar; cerrar otra sesión.
   - **Compartido** (con un amigo emparejado): película, y **serie**
     (temporadas, episodios, "Seguir T1·E2" tras ver un rato, reanudar).
   - **Estudios**: la ficha de un título enlaza a su estudio con títulos.
6. **Rendimiento** (opcional): framestats de la guía y de Inicio con
   `tools/tvperf/README.md`. El baseline profile solo actúa en release:
   para medirlo hace falta una release firmada (sin keystore sale sin
   firmar y no se instala).
7. Apuntar lo que falle aquí (§6) y en el archivo de la sesión.

Las capturas Robolectric (artefacto `screenshots` de cada run de CI, o
`./gradlew :app:testDebugUnitTest -Pscreenshots --tests '*screenshots*'`)
enseñan cómo debería verse cada pantalla, para comparar con la tele.

## 1. Dónde estamos

- Todo en `main` (2026-11-28), app y backend
  (`Alexzafra13/HubPlay_demo`: federación con DirectPlay, series de
  amigos, estudios, `/me/sessions` sin cookies). CI verde (detekt +
  unitarios + **capturas como tests de UI** + APK). **Ni el backend está
  desplegado en el servidor de la TV ni la app instalada**: ver §0.
- Funciona en la Mi TV real (lo anterior al 2026-11-26): login/emparejado,
  Inicio, catálogo, ficha (película y serie), reproductor VOD con chrome
  propio, TV en directo, buscar, colecciones, salvapantallas, intro,
  Ajustes → Inicio.
- **Hecho y sin probar en la TV** (2026-11-26 a 28): "Compartido contigo"
  completo (también series con temporadas y episodios), liberar sesiones,
  quitar de Continuar viendo, logout real; errores de reproducción con
  reintento y aviso al servidor de canales caídos; tamaño/estilo de
  subtítulos y calidad; dispositivos conectados; rails Porque viste X,
  Recomendados, Colecciones y Canales favoritos; guía completa en rejilla;
  panel de episodios arreglado; baseline profile. Detalle en los archivos.
- Sin probar: móvil/tablet, release firmada real.

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
  al desmontar (así salió el de `focusRestorer`). **Corren en CI** (paso
  "Compose UI") y los PNG quedan en el artefacto `screenshots` del run.
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
  ficha y al reproductor. Series de amigos: `children()` →
  `SharedSeriesState` → mismo `SeriesEpisodesPanel` que las propias. La
  pestaña sale con `SharedAvailability.hasPeers`.
- **Inicio, rails del cliente**: tras el layout del servidor,
  `HomeViewModel.withExtras` añade Porque viste X, Recomendados,
  Colecciones, Canales favoritos y luego los de amigos (orden de la web;
  `CLIENT_RAILS`). No son configurables en Ajustes → Inicio.
- **Guía en rejilla** (`ui/livetv/LiveGuideScreen.kt`, `GuideWindow.kt`):
  un `ScrollState` horizontal compartido por regla y filas; título pegado
  al borde visible en layout (no recompone); `GuideBringIntoViewSpec`
  evita saltos en el tiempo al moverse. Comparte el ViewModel de TV en
  directo (`getBackStackEntry`). El pie del menú de TV en directo va en
  `LiveTvFooterActions`.
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
- **Baseline profile**: `app/src/main/baseline-prof.txt` escrito a mano
  con comodines (app + OkHttp/Retrofit/Moshi). Solo afecta a release.
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

## 5. Última sesión (2026-11-26 noche a 2026-11-28)

"Que tenga todo lo que da el backend": errores de reproducción y salud
de canales, subtítulos/calidad, dispositivos, rails de descubrimiento,
series de amigos (con endpoint nuevo en el backend), guía en rejilla,
tests de UI en CI y baseline profile. Detalle, porqués y limitaciones:
`archive/project-status-detalle-2026-11-28.md`.

## 6. Pendiente (orden sugerido)

0. ~~Mergear~~ (hecho 2026-11-28). **Desplegar el backend e instalar la
   app** siguiendo §0. Probar en la Mi TV:
   - Inicio: rails nuevos (Porque viste X, Colecciones → ficha, Canales
     favoritos → reproduce); mantener OK en Continuar viendo → Quitar.
   - Reproductor: pasar de episodio con un fichero que transcodifique;
     Opciones → tamaño/estilo de subtítulos y calidad; cortar la red para
     ver el mensaje de error y reintentar con OK.
   - TV en directo: zapear rápido (las sesiones bajan en el panel del
     servidor); **Guía completa**: moverse por la rejilla, que no dé
     tirones y que OK abra el canal.
   - Serie propia → Episodios: el foco nace en la temporada y Atrás
     vuelve al hero.
   - Federación (segundo servidor emparejado, con el backend nuevo en los
     dos): película y **serie** de un amigo (temporadas, episodio, reanudar).
   - Ajustes → Dispositivos conectados: cerrar otra sesión.
   - Medir el arranque en frío de una build release (baseline profile).
1. Episodios de amigos en "Seguir viendo de tus amigos" (backend: cachear
   hijos o guardar título en `federation_progress`); la web tampoco usa
   aún el endpoint de hijos.
2. Retardo de subtítulos (Media3 no lo da: desplazar cues a mano).
3. Rails del cliente configurables en Ajustes → Inicio (backend + app).
4. Baseline profile generado en el aparato (Macrobenchmark) si el de
   comodines se queda corto. Probar en móvil.
