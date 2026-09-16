# Estado del proyecto — HubPlay Android (TV)

> Entrypoint de cada sesión. Corto a propósito: lo que hace falta para
> retomar sin releer código. Histórico de sesiones en `archive/`.
> Última actualización: **2026-09-15**.

---

## 1. Dónde estamos

- **Rama**: `main` == `origin/main`. CI verde (`detekt` estricto +
  unitarios + `assembleDebug`). Backend hermano: `Alexzafra13/HubPlay_demo`.
- **Estado funcional en la Mi TV real**: login/emparejado, Inicio (hero +
  rails), catálogo de pelis/series, detalle, reproducción HLS con chrome
  propio (ver §4a), TV en
  directo con preview, buscar, colecciones, salvapantallas, intro de
  marca. Todo verificado contra el servidor de producción del autor.
- **Lo que NO está probado**: móvil/tablet (compila, sin usar desde
  junio), release firmada real (solo AAB de CI), Compose UI tests.

## 2. Cómo se prueba (lo que funciona de verdad)

**Dispositivos**
- **Mi TV real**: Xiaomi MiTV-AFKR0, Android 11 / API 30, 1920×1080 a
  320 dpi (= 960×540 dp). adb por Wi-Fi: `adb connect 192.168.1.132`.
  Se cae con frecuencia (standby/Wi-Fi): reintentar con
  `adb disconnect 192.168.1.132:5555; adb connect 192.168.1.132`; si no
  hace ping, está apagada → pedir al usuario que la encienda.
- **Emulador**: AVD `Television_4K` (3840×2160 a 640 dpi = mismos 960×540
  dp). El host es `10.0.2.2`. No tiene artwork si apunta al server local.

**Servidores**
- Producción del autor: `https://hubplay.duckdns.org` — está en su
  propia LAN (`192.168.1.100`, Docker, HTTP en `:8097`, nginx TLS en 443).
  Usuario admin `admin`; la contraseña **no se guarda en el repo ni en
  memoria**: pedirla. Token: `POST /api/v1/auth/login {"username","password"}`
  → `access_token` (caduca en ~1 h).
- Dev local del backend: `./bin/hubplay.exe --config config/local/hubplay.yaml`
  en `HubPlay_demo` (puerto 8097, bind 0.0.0.0). El debug de la app
  permite cleartext (`app/src/debug/AndroidManifest.xml`); la release no.

**Flujo típico**
```bash
D=192.168.1.132:5555
adb -s $D install -r app/build/outputs/apk/debug/app-debug.apk
adb -s $D shell am start -n com.alex.hubplay.debug/com.alex.hubplay.MainActivity
adb -s $D shell input keyevent KEYCODE_DPAD_DOWN     # RIGHT/LEFT/UP/CENTER/BACK
adb -s $D shell input text "https://hubplay.duckdns.org"
adb -s $D exec-out screencap -p > cap.png              # y mirar la imagen
adb -s $D logcat -d | grep -A40 "FATAL EXCEPTION"
adb -s $D shell run-as com.alex.hubplay.debug cat files/crash-log.txt   # CrashLogger persiste el último crash
adb -s $D shell pm clear com.alex.hubplay.debug        # volver al login
```
- **Emparejar**: la app muestra un código `XXXX-YYYY`; aprobarlo con el
  token admin: `POST /api/v1/auth/device/approve {"user_code":"XXXX-YYYY"}`
  **contra la misma URL que usa la app** (si eligió `http://192.168.1.100:8097`,
  aprobar ahí; con la URL pública devuelve 401).
- **Descubrimiento LAN**: con el servidor local de dev arrancado, la TV
  lo encuentra sola por UDP y salta al emparejado (auto-skip si solo hay
  uno). "Cambiar servidor" abre la lista. Producción aparece por el
  barrido de subred hasta que despliegue la imagen con `internal/discovery`.
- **Rendimiento**: `adb shell dumpsys gfxinfo com.alex.hubplay.debug reset`,
  navegar, `dumpsys gfxinfo …` (jank %, percentiles); `framestats` da
  por frame; `atrace -t 6 gfx view -a com.alex.hubplay.debug` para ver
  RenderThread/GPU. Medir siempre con caché de imágenes caliente.

**Trampas conocidas del box**
- `screencap` devuelve **blanco** mientras hay vídeo por hardware (tráiler,
  reproducción): verificar por logcat (GETs de segmentos HLS) o `dumpsys audio`.
- `monkey -p … LEANBACK_LAUNCHER` a veces no lanza; `am start -n` sí.
- Si `pm list packages` no muestra el paquete pero `install` dice
  Success: quedó "desinstalado para el usuario" → `adb uninstall` y reinstalar.
- El teclado del TV (Gboard) se abre solo al enfocar un `TextField`;
  la app lo cierra con reintento (ver `LoginScreen.PrimaryUrlInput`).
- Cada `adb shell input keyevent` tarda ~0,3-0,5 s: no sirve para medir
  cadencias finas.

## 3. Arquitectura viva (solo lo que no se deduce del código en 1 min)

- **Capas de la raíz** (`ui/HubplayApp.kt`): capa 0 `TrailerHostOverlay`
  (único WebView de YouTube, `LAYER_TYPE_NONE`), encima el NavHost con
  pantallas de fondo transparente donde toca (Home), salvapantallas
  (z 100), `BrandIntro` (z 200, una vez por proceso, espera a
  `BrandIntroGate` que abre `MainActivity` cuando el splash del sistema
  se retira). Al cambiar de ruta se oculta el tráiler (salvo Home/Detail/
  Series) y se cierra el IME (salvo Login).
- **TvShell + NavSidebar** (`ui/components`): armazón de todas las
  pantallas de primer nivel. Sidebar plegado a iconos (56 dp), se expande
  al recibir foco con scrim; el foco entra por la pestaña activa. Home
  pasa `padContent = false` (su backdrop llega al borde) y por eso
  TvShell **no pinta fondo** ahí: si lo pintara taparía el WebView.
- **Home** (`ui/home`): hero (46 % de alto cuando el foco está en rails)
  + `LazyColumn` de rails de altura fija por estilo (`Landscape` 228 dp
  para "Continuar viendo/Siguiente", `PosterCompact` 285 dp para
  "Tendencias/Lo último") con `contentPadding` inferior para que el último
  rail suba entero. Backdrop en `HomeBackdrop` (fundido de Coil +
  `ModulateAlpha`, cero graphicsLayers a pantalla completa). Foco de
  card → `focusBus` (150 ms para hero, 500 ms para tráiler).
- **Tráiler**: `TrailerHost` (claim por item, `revealed`, `fadeOutOnHide`,
  `hideNow`); el backdrop de Home/Detail/Series baja a alpha 0 con 700 ms
  cuando se revela. Al ocultarse: **snap** si es por cambio de card/
  navegación/error (la WebView de debajo puede estar pintando la
  end-screen de YouTube) y **fundido de 700 ms** si el tráiler acabó solo
  (`trailerBackdropAlphaSpec` en `TrailerHostOverlay.kt`).
- **Protocolo real del IFrame API** (medido en la Mi TV, 2026-09-12):
  YouTube manda `infoDelivery` cada ~270 ms con `info.currentTime` y la
  duración en `info.progressState.duration`; los cambios de estado son
  otro `infoDelivery` con `info.playerState` (+ `info.duration`). **No**
  hay `onStateChange` separado ni respuestas numéricas a
  `getCurrentTime`/`getDuration`. El fin se detecta 1,5 s antes por
  tiempo (graceful) con `playerState=0` y atasco por reloj como redes;
  tras `ended` el JS ignora todo (el mensaje de ENDED trae
  `currentTime=duration` y antes volvía a revelar la end-screen: era el
  "frame con botón de replay").
- **Watchdog del tráiler**: lee `host.revealed`, nunca un `remember(key)`
  local. El bridge JS se crea una vez en `factory` del `AndroidView` y
  captura el estado de la PRIMERA composición: un flag `remember(key)`
  recreado por key nunca lo veía el bridge → a partir del 2.º tráiler el
  watchdog lo ocultaba a los 6 s ("el backdrop vuelve y luego otra vez el
  tráiler"). Regla general: **lo que capture el bridge JS tiene que ser un
  objeto estable** (host, handler), no estado recreado por key.
- **Navegación sin crossfade**: `NavHost` con `EnterTransition.None` /
  `ExitTransition.None` (y pop). El fade de 700 ms por defecto componía
  dos pantallas en capas con alpha sobre el vídeo del tráiler: 36 % de
  frames con tirón al abrir Detalle desde Inicio → ~21 % sin él. Lo que
  queda es el primer frame de Detalle (250-450 ms de measure/layout/draw
  en el box) — siguiente candidato de rendimiento.
- **Detalle (película)** (`ui/detail/DetailScreen.kt`, rediseño 2026-09-12):
  backdrop + tráiler FIJOS detrás (`DetailBackdrop`) y encima una columna
  con scroll: hero de un viewport + `RailsSection` (franja de fundido
  transparente→BgBase y rails sobre fondo sólido: reparto, "Forma parte de
  la colección X" con tarjeta "Ver colección", "Más como esto"). El tráiler
  NO se para al bajar: queda tapado por los rails. El usuario quiso la
  ficha ligera: un solo botón con texto (Reproducir) y debajo iconos
  redondos (`QuickActions`: favorito, visto y — con `can_edit_metadata` de
  `GET /me`, cache en `MetadataTools` — un lápiz que abre Identificar, que
  incluye "Actualizar metadatos"); una línea pequeña dice qué hace el icono
  enfocado. Sinopsis con "Ver más" inline (`ExpandableOverview`), sin
  diálogo de Información. Solo queda el chip de Estudio (la colección va
  al rail). En el diálogo Identificar los campos están deshabilitados
  mientras carga (si no, el foco inicial del Dialog caía en Título y el
  TV abría el teclado) y `usePlatformDefaultWidth = false`.
- **Crash en API < 33**: `URLEncoder.encode(x, Charset)` /
  `URLDecoder.decode(x, Charset)` no existen en Android 11 (la Mi TV):
  Estudio, Persona y Colecciones reventaban al abrirse. Usar siempre la
  sobrecarga con nombre de charset (`"UTF-8"`). minSdk es 26.
- **Foco/scroll en Detalle — LECCIÓN**: el `LocalBringIntoViewSpec` por
  defecto de Compose en Android TV **pivota cada foco al 30 % del
  viewport**; en una columna con scroll eso desplaza la página al mover el
  foco entre botones del hero ("se me baja"). `DetailBringIntoViewSpec`:
  0 si el hijo ya se ve, pivote 0.3 si está fuera. Además: el foco que
  entra en el hero va a Reproducir (`enter`), con foco en el hero la página
  vuelve a 0 (`heroHasFocus` → `animateScrollTo(0)`), y ↑ desde el primer
  rail se intercepta con `onPreviewKeyEvent` (un `focusProperties { up }`
  en el contenedor no llega a las cards del LazyRow). Rails diferidos 2
  frames y `Spacer` final solo con rails, para que el foco inicial no
  encuentre recorrido que desplazar.
- **Ficha compartida** (`ui/components/HeroDetail.kt`, 2026-09-12 tarde):
  `HeroDetailScaffold(config, rails)` es la ficha de Detalle Y de Series
  (`HeroDetailConfig`: item, header con etiqueta "SERIES" y meta, CTAs
  Reproducir + secundaria "Episodios", toggles, actions, nav). Series usa
  póster compacto (170 dp) para que quepan los tres botones y los iconos;
  etiquetas cortas del resolver ("Seguir S1·E3", "Ver S1·E1"). Series no
  tiene toggle de visto. `ItemMetadataController` (ui/metadata) da permiso,
  refresh e identify a los DOS ViewModels (Detail migrado 2026-09-15;
  declarar `tools` ANTES del `init` que lo usa o NPE). Los rails van con
  `contentPadding` dentro del LazyRow (la card enfocada crece un 8 % y se
  recortaba) y sobre un velo al 72 % para que el tráiler/backdrop siga
  viéndose al bajar. **Los rails tienen alto mínimo de un viewport**
  (`RailsSection(minHeight)`) y **al entrar el foco en ellos la sección se
  pega arriba** (`railsHaveFocus` → `animateScrollTo(railsTop, tween(220))`,
  simétrico a `heroHasFocus` → 0): reparto arriba, siguiente rail debajo,
  cero restos del hero (2026-09-16, pedido por el usuario). El
  `HeroBringIntoViewSpec` devuelve 0 para hijos del hero y para el salto
  hero → rails (lo hace la ficha) y solo pivota al 30 % en rails más
  profundos. Lección: **la animación de bring-into-view de Compose no sirve
  para un salto largo** (avanzaba a 14 px/frame y se cortaba a mitad,
  medido con log en la Mi TV); para saltos deterministas, `animateScrollTo`
  explícito y spec a 0.
- **Identificar es un overlay, no un `Dialog`** (2026-09-15): abrir una
  ventana `Dialog` con su propia composición costaba 0,5-0,9 s de hilo
  principal en la Mi TV (primer frame de 500 ms) y al cerrarla la ficha se
  quedaba sin foco. `IdentifyOverlay` se compone dentro de la ficha (velo +
  panel, `zIndex 50`), atrapa el foco con `focusProperties { exit = Cancel }`,
  Back lo cierra (`BackHandler`) y al cerrarse el foco vuelve al lápiz
  (`identifyFocus` en `HeroDetailScaffold`). Foco inicial en Buscar, nunca
  en el campo (el TV abriría el teclado). **Los campos son ligeros**
  (`IdentifyField`): un Box con borde que solo compone el
  `OutlinedTextField` real al pulsarlo (el perfil en frío decía que los
  dos campos de Material eran 590 de los 652 ms de abrir el panel).
  Medido en la Mi TV en frío: primer frame del panel 20 ms (antes 560 ms
  con `Dialog`), bloqueo total ~0,4 s (la lista de resultados) frente a
  ~1,5 s. Verificado: sin teclado al abrir, foco al primer resultado,
  CENTER en el campo abre el campo real con teclado, Back cierra y el
  foco vuelve al lápiz. La búsqueda se siembra con
  `identifyQuery(title)`: quita "(2019)" / "[2019]" / "- 2019" del final
  (títulos sin identificar llevan el año de la carpeta y TMDb devolvía 0
  resultados); un número suelto ("Blade Runner 2049") se respeta. Test en
  `ui/metadata/IdentifyQueryTest`.
- **Menú lateral y foco a la DERECHA** (2026-09-15): las filas del sidebar
  tenían ancho "wrap" (etiquetas de distinto largo), y la búsqueda 2D de
  foco a la derecha encontraba otra fila más ancha del propio menú antes
  que el contenido: el mando daba vueltas por el menú. Arreglo:
  `fillMaxWidth()` en `SidebarRow`. Regla: **las filas de un menú vertical
  deben compartir rectángulo horizontal**.
- **Coste de abrir la ficha desde Inicio con tráiler** (medido 2026-09-15,
  debug, en caliente; `fs.py`/atrace/`am profile` en la sesión): ~300 ms
  de hilo principal repartidos en 4 frames (36/60/134/67 ms): desmontar
  Inicio (`Compose:onForgotten` 40 ms), componer + medir el hero (100 ms;
  todo dentro de la subcomposición de `BoxWithConstraints`), rails 45 ms.
  En frío (primera ficha del proceso) el doble o más. Dentro del hero:
  botones e iconos ~45 ms, fila de metadatos con 9 `Text` ~12 ms, Coil
  ~60 ms arrancando ~20 `AsyncImage`, rasterizar iconos vectoriales 30 ms,
  texto 65 ms. Hecho: fila de metadatos en UN solo `Text`
  (`HeroMetaRow`), `HeroIconButton` sin `material3.IconButton` (ripple +
  tamaño mínimo), mapeo de `fetchChildren` en `Dispatchers.Default` (Series
  mapeaba episodios en main mientras se componía el hero). Con vídeo, el
  box va a ~28 fps fijos (RenderThread + GPU 33 ms/frame): es el suelo.
  Siguientes candidatos: componer el hero en dos frames (esenciales →
  resto), cachear los `VectorPainter` de los iconos, Baseline Profile
  (release) para el arranque en frío.
- **Vista previa** (idea del usuario): tráiler sonando + página arriba +
  sin diálogo + 5 s sin mando → todo se desvanece y la carátula viaja a la
  esquina inferior izquierda (una capa con escala/desplazamiento leída en
  `graphicsLayer`, sin recomponer). La primera tecla solo despierta (se
  consume, salvo Back). `rememberPreviewMode` en HeroDetail.kt.
- **Estudio**: `GET /studios/lucasfilm-ltd` devuelve 0 títulos aunque hay
  Star Wars en la biblioteca (backend), y el logo negro de Lucasfilm no se
  ve sobre fondo oscuro (poner fondo claro o tinte al logo del estudio).
- **Imágenes**: el backend redimensionaba `?w=N` con vecino más cercano
  (`internal/imaging/thumbnail.go`) → backdrops "pixelados". Ahora
  Catmull-Rom (`x/image/draw`), JPEG 85, miniaturas versionadas
  (`_r2` en fichero y ETag). El salvapantallas pide `w=1920`
  (`withImageWidth`); Home/Detail siguen a 1280 por coste de textura.
  Tras desplegar el backend, la caché de Coil de la TV sigue sirviendo
  las viejas hasta 24 h (`max-age=86400`): borrar datos de la app o esperar.
- **Descubrimiento LAN** (`data/LanDiscovery.kt` + `LanProbe.kt`):
  mDNS (`_http._tcp`, filtra "hubplay") ‖ UDP broadcast `HUBPLAY-DISCOVER/1`
  a 41860 (respuesta `{product,name,port,url?}`; URL = IP origen + port,
  o `url` si viene) ‖ barrido HTTP de la /24 en 8096/8097 contra
  `/api/v1/health` (marca `"product":"hubplay"` o claves antiguas).
  Dedupe por URL. Auto-skip al único servidor tras 2 s.
- **Datos**: Moshi con `NullToEmptyListAdapterFactory` (el backend puede
  mandar `"genres": null`); `cleanChannelName` quita sufijos técnicos de
  M3U. Imágenes con `?w=N` del backend (`IMG_W_CARD` 400, `IMG_W_BACKDROP` 1280).
- **Detekt**: estricto. Reglas que más muerden: `MagicNumber` (constantes
  con nombre), `LongMethod` ≤ 80, `LongParameterList` ≤ 8 (baseline en
  `config/detekt-baseline.xml`), `ReturnCount` ≤ 5, `ImportOrdering`
  lexicográfico con `java/javax/kotlin` al final, sin líneas en blanco
  dobles ni antes de `}`.

## 4. Rendimiento en la Mi TV (referencia)

Navegación por un rail con caché caliente (8 pulsaciones):

| Build | Frames con tirón | p50 | p99 |
|---|---|---|---|
| Antes de 2026-09-11 (debug) | 64 % | 19 ms | 73 ms |
| Ahora (debug) | 29 % | 11 ms | 65 ms |
| Ahora (release minificada) | 18 % | 9 ms | 34 ms |

Cuello: GPU del box (RenderThread espera ~15 ms/frame con capas a
pantalla completa) + picos en UI thread al componer cards nuevas
(`measureAndLayout` hasta 28 ms). En frío (carátulas por red) sigue
alto: decodificación + subida de texturas. Ya quitado: `Crossfade` +
`Modifier.alpha` del backdrop, doble capa de `AnimatedContent` en
`HeroInfo`, sombra animada y placeholder bajo imágenes en `MediaCard`,
capa hardware del WebView. Siguientes candidatos si hace falta: recortar
el backdrop al 70 % superior, aligerar `MediaCard`, probar Baseline Profile.

## 4a. Reproductor de VOD (rediseño 2026-09-16)

- **Chrome en Compose** (`ui/player/VodPlayerChrome.kt`, `VodPlayerLayer`),
  ya no el `PlayerControlView` de Media3: en TV el foco del mando lo
  tenía el árbol de Compose y las teclas nunca llegaban al controlador
  ("toco botones y no hace nada"). `PlayerView` va con
  `useController = false`, `SHOW_BUFFERING_NEVER`, `isFocusable = false` y
  `FOCUS_BLOCK_DESCENDANTS` (si la vista podía coger el foco, los clics
  de Compose fallaban a ratos).
- **Disposición** (referencia: Jellyfin, leído por árbol de accesibilidad
  porque el screencap sale blanco con vídeo): todo abajo a la izquierda:
  logo/título + subtítulo ("Serie · T1 · E3" o el año), barra de progreso
  enfocable (← → ±10 s), `55:36` / `1:53:52 · Termina 2:21`, y fila de
  botones redondos (`HeroIconButton`): play/pausa, −10, +10, siguiente
  episodio (si lo hay), audio y subtítulos.
- **Carga**: backdrop del item con logo/título y una línea de progreso
  fina + "Cargando…" hasta el primer frame (`firstFrameShown`), nada de
  spinner en el centro. Buffering a mitad: rueda pequeña arriba a la
  derecha.
- **Teclas**: oculto → ← → saltan y enseñan, OK/↑/↓ enseñan, Play/Pause
  del mando alterna; visible → foco en los botones, Back oculta
  (**gestionado en `handleKey`, no vía `BackHandler`**: el del chrome no
  llegaba a ejecutarse y Back salía del reproductor), se oculta solo a
  los 4,5 s solo mientras reproduce. Al cerrarse, el foco vuelve al Box
  raíz (`rootFocus`) para seguir capturando teclas.
- `PlayerUiState` lleva `subtitle`, `backdropUrl`, `logoUrl` (rutas del
  backend; `absoluteImage` en PlayerScreen las absolutiza con `?w=`).
- Verificado en la Mi TV: carga con backdrop, mostrar/ocultar, pausa,
  saltos exactos en pausa, foco estable al pulsar, Back oculta y Back sale.
- **Segunda vuelta (2026-09-16 noche, feedback del usuario)**: logo/título
  arriba a la izquierda (carga y chrome), controles compactos (botones de
  40 dp, barra de 3 dp, tiempos a 13 sp), Ken Burns lento del backdrop
  mientras carga, y el foco va a Play en cuanto se montan los controles
  (`LaunchedEffect(Unit)` en `ControlsBlock`: antes se pedía antes de
  existir y al entrar no se podía navegar hasta que el chrome reaparecía).
- **Pistas de audio**: el backend transcodifica UNA pista por sesión
  (`-map 0:a:N`), así que el HLS solo expone una y ExoPlayer solo veía esa.
  Jellyfin enseña todas porque las lee de la ficha. Ahora igual:
  `ItemDetailDto.mediaStreams` (`media_streams` de `GET /items/{id}`) →
  `PlayerUiState.audioTracks` ("Español · 5.1 · EAC3"), el selector las
  lista y elegir una llama a `selectAudio(ordinal, posSec)`: nuevo
  `/stream/{id}/info?audio=N` (la decisión puede cambiar) y, si no es direct
  play, nuevo master `?audio=N` reanudando en la posición; en direct play se
  aplica en ExoPlayer (N-ésimo grupo de audio). `N` es el índice 0-based
  entre las pistas de audio (lo que ffmpeg entiende), igual que el web.
  Verificado: Capitán América (2025) lista 3 pistas, elegir "Inglés" pide
  `info?audio=2` + `master.m3u8?audio=2` y sigue donde iba.
- **Tercera vuelta (2026-09-16, feedback)**: solo el logo arriba (24 dp,
  sin año; los episodios sí llevan "Serie · T1 · E3"), "Termina H:mm"
  encima de la barra a la derecha y "pos / duración" debajo a la
  izquierda, saltos de 30 s (botones y barra), audio y subtítulos en dos
  iconos que abren solo su lista (`TrackSelectionSheet(section)`), y al
  cerrar la hoja el foco vuelve al icono que la abrió (`opener` en
  `ControlButtons`; mientras la hoja está abierta el chrome no se
  auto-oculta). Animación de ocultado: el logo sube y los controles bajan,
  se desvanecen y se encogen al 96 % (`scaleOut` con origen abajo-izq),
  380 ms; al mostrar suben con `FastOutSlowIn`.
- Pendiente del reproductor: los subtítulos siguen siendo los que ve
  ExoPlayer (el HLS lleva los que ffmpeg extrae); podrían listarse también
  desde `media_streams` con `?sub=N`. Velocidad/calidad si se quiere. El
  directo (`LivePlayerChrome`) no se ha tocado.
- **Trampa de pruebas (grave)**: si HubPlay no está en primer plano (el
  salvapantallas del sistema, el launcher tras un `am start` que tarda
  6 s en subir, Play Store…) las teclas caen donde sea: el 2026-09-16 un
  CENTER instaló una app de Play Store y otro escribió en un campo de
  contraseña de Google. Enviar teclas SOLO con `tools/tvperf/tvkeys.sh`
  (`k KEY` comprueba `mCurrentFocus` antes; `launch` espera a que suba).
  Con el chrome del reproductor, pausar primero: si no, el auto-ocultado
  de 4,5 s se dispara entre dumps (cada `uiautomator dump` tarda 2-3 s).

## 4c. Inicio y Ajustes → Inicio (2026-09-16 noche)

- **Cards a la misma altura**: `RAIL_CARD_HEIGHT = 180.dp` en MediaCard;
  `railCardWidth(style)` da 320 dp (16:9) o 120 dp (2:3). Un solo alto de
  rail (`RailHeight` 273 dp) para todos, incluido "En directo ahora"
  (`LIVE_CARD_WIDTH` 320). Antes cada rail medía distinto y al pasar de
  uno a otro la página saltaba.
- **↑ desde el primer rail**: los botones del hero solo se componen con
  `isLanding`, así que `focusProperties { up }` no tenía destino. Ahora el
  primer rail intercepta ↑ (`onPreviewKeyEvent`), pone `heroButtonsFocused`
  y `wantHeroFocus`, y un `LaunchedEffect` pide el foco a Reproducir un
  frame después. Con el foco en el hero, `viewModel.onHeroFocused()` limpia
  la card (sin consumir la puerta del primer foco) y vuelve el carrusel con
  su rotación (verificado: rota a los 8 s tras volver).
- **Subir entre rails se anima**: el rail por encima de la ventana no está
  en `visibleItemsInfo`, pero como todos miden `RailHeight` la distancia es
  exacta (`firstVisibleItemScrollOffset + n × RailHeight`) → `animateScrollBy`.
- **Ajustes → Inicio** (`ui/settings/HomeLayoutScreen.kt` + ViewModel):
  lista `GET /me/home/layout` completa (ocultos incluidos), OK alterna
  mostrar/ocultar, flechas suben/bajan, cada cambio hace `PUT` (por usuario
  = por perfil, mismo dato que usa la web) y `HomeRepository.layoutVersion`
  sube para que `HomeViewModel` recargue Inicio. `homeRailTitle()` es el
  título compartido. `SettingsScreen` recibe ahora `SettingsActions`.
  Tipos que el backend admite hoy: continue_watching, next_up, trending,
  live_now, latest_in_library (uno por biblioteca). Para "Canales
  favoritos", "Colecciones", "Recomendado para ti" o "Más vistos" hace falta
  añadir el tipo en `validSectionType` + `defaultLayout` del backend y un
  endpoint de datos; la app ya ignora tipos desconocidos sin romperse.
- `config/detekt-baseline.xml` regenerado (la firma de `SettingsScreen`
  cambió y arrastraba entradas obsoletas).

## 4b. Arranque en frío (medido 2026-09-16, debug, Mi TV)

- **Lo que más pesa NO es la app**: tras cada `adb install` el sistema aún
  no ha optimizado el dex y `DexFile.openDexFile` cuesta 3,7 s al crear el
  ClassLoader (TotalTime 6,3 s). Con `adb shell cmd package compile -m
  speed -f com.alex.hubplay.debug` el arranque baja a **1,2-1,4 s**. Al
  medir arranque, compilar primero. En release lo resuelve Play (perfil
  en la nube) y, mejor aún, un Baseline Profile.
- Dentro de la app (perfil `am start --start-profiler`): `AppContainer`
  costaba 2,1 s en el hilo principal → 0,7 s. Quitado: `ChannelOrderStore`
  usaba `KotlinJsonAdapterFactory` (cargaba kotlin-reflect entero, 1,1 s)
  → `@JsonClass(generateAdapter = true)` + adaptador perezoso; los
  `OkHttpClient` con TrustManager propio (OkHttp lee todas las CA del
  sistema en `sslSocketFactory`, 0,5 s) son `by lazy`, los Retrofit usan
  `callFactory { mainOkHttp.newCall(it) }` y `AppContainer.prewarm()` los
  construye en un hilo aparte desde `Application.onCreate`. El WebView del
  tráiler (Chromium, 0,6 s) ya no se crea en el primer frame: al primer
  tráiler o a los 5 s (`rememberWebViewWanted`).
- `moshi-kotlin` sigue como dependencia solo porque el `Serializer`
  generado por OpenAPI lo importa; el código propio va por codegen.
- Quedan dos ráfagas de ~0,8-1 s de hilo principal tras el primer frame
  (composición de Inicio en frío + llegada de datos). Siguiente paso si se
  quiere seguir: perfilar esas dos ráfagas y Baseline Profile.

## 5. Pendiente (orden sugerido)

1. ~~Crash al navegar el rail "En directo ahora"~~ **resuelto 2026-09-12**:
   era `IllegalArgumentException: Key … was already used` del LazyRow al
   volver a entrar en el rail (`scrollToItem`): el backend repetía un
   canal con dos programas de EPG solapados. Arreglado en los dos lados
   (backend `sqlLiveNow` elige un programa por canal; `BaseRail` y el
   catálogo deduplican por id). Lección: **toda lista con `key = { it.id }`
   debe deduplicar** o Compose aborta el proceso entero.
2. Hero con canal en directo enfocado: queda vacío hasta que arranca la
   preview; mostrar nombre + programa.
2b. Primer frame de Detalle: ver "Coste de abrir la ficha" en §3 (medido y
   parcialmente aliviado el 2026-09-15). Verificado en la Mi TV: rails con
   alto mínimo (reparto al 33 % con el hero fuera) y overlay Identificar.
   En frío la ficha sigue costando ~4 frames de 400 ms (clases + JIT):
   eso solo lo arregla un Baseline Profile en release. Herramientas de
   medida en `tools/tvperf/` (README con el flujo).
2c. Estudio Lucasfilm sigue devolviendo 0 títulos (backend) — visto de
   nuevo el 2026-09-15 desde la ficha de The Mandalorian.
3. Un host con dos IPs sale dos veces en "Servidores en tu red" (dedupe
   es por URL).
4. Detalle sin artwork (mucho vacío), `CollectionDetailScreen` con
   tarjetas más altas, títulos de ítems sin identificar (metadata del
   servidor), probar en móvil.
5. Backlog de largo plazo: EPG grid completa, tests de Compose UI en
   CI, Baseline Profile. Descartados: MediaSession/Android Auto, Cast.
