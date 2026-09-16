# Estado del proyecto — HubPlay Android (TV)

> Entrypoint de cada sesión. Corto a propósito. El detalle de cada
> sesión (medidas, por qué de cada decisión) está en `archive/`; el último
> volcado largo es `archive/project-status-detalle-2026-09-12-a-16.md`.
> Última actualización: **2026-09-16**.

## 1. Dónde estamos

- `main` == `origin/main`, CI verde (detekt estricto + unitarios + APK).
  Backend hermano: `Alexzafra13/HubPlay_demo` (dev local en
  `http://192.168.1.100:8097`).
- Funciona en la Mi TV real: login/emparejado, Inicio, catálogo, ficha
  (película y serie), reproductor VOD con chrome propio (audio y
  subtítulos del servidor), TV en directo,
  buscar, colecciones, salvapantallas, intro, Ajustes → Inicio.
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

## 5. Pendiente (orden sugerido)

1. Rails nuevos para Ajustes → Inicio (backend + app): canales favoritos,
   colecciones, recomendado, estrenos, por género, "para terminar hoy".
   Ideas propuestas al usuario el 2026-09-16, sin decidir.
2. Reproductor: velocidad/calidad si se quiere; TV en directo sin tocar.
   Subtítulos HECHO (2026-09-16); el servidor de la TV (`192.168.1.100`)
   aún corre un backend sin la caché de VTT: desplegar.
3. ~~Hero con canal en directo enfocado queda vacío hasta la preview~~ —
   HECHO 2026-09-16: `LiveChannelHeroFallback` (tinte con `logoBg` + logo o
   iniciales arriba a la derecha) como `fallback` del `ChannelPreviewPlayer`
   de Inicio, y `HeroInfo`/`LiveChannelCard` caen a título/iniciales si el
   logo no carga (muchos `/channels/{id}/logo` dan 404 en el servidor).
4. ~~Un host con dos IPs sale dos veces en "Servidores en tu red"~~ — HECHO
   2026-09-16 en app + backend: el servidor anuncia un id estable
   (`server.instance_id`) por mDNS (TXT `id`), UDP (`id`) y `/health`
   (`server_id`); `LanServer.serverId` + `sameServerAs` deduplican en
   `LoginViewModel`. Con backends antiguos (sin id) se sigue comparando
   por URL. Sin probar en real hasta desplegar el backend.
5. Estudio Lucasfilm devuelve 0 títulos (backend). Detalle sin artwork,
   `CollectionDetailScreen`, probar en móvil.
6. Largo plazo: Baseline Profile, EPG grid, tests de Compose UI en CI.
