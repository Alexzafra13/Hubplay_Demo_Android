# Detalle de sesiones — 2026-11-26

> Volcado de las sesiones del 2026-11-26 que ya no caben en
> `project-status.md`. Solo arqueología: el estado vivo está allí.

## Sesión 2026-11-26 (mañana): visual, con el banco de capturas

Arreglado tras verlo en capturas:
- **"A continuación" vacío**: `/me/next-up` no mandaba imágenes → tarjetas
  con una "C" y hero en negro. Backend (rama `claude/stoic-curie-9je0wx`
  de HubPlay_demo) manda fotograma + póster/fondo/logo de serie; la app
  los mapea (`NextUpItemDto.toEpisode`). **Hay que desplegar el backend.**
- Episodios en Inicio: el hero y la tarjeta solo decían la serie; ahora
  "T3 · E1 · Fuego cruzado" en el hero y "T3 · E1" en el pie de tarjeta
  (`EpisodeLabel.kt`, `R.string.episode_code`). Continuar viendo usa el
  logo de la serie en el hero.
- Inicio: el título del rail siguiente asomaba cortado a media letra en
  el borde inferior → fundido de 28 dp.
- Catálogo/Buscar: la primera carátula salía 10 dp más estrecha
  (`propagateMinConstraints` en la Box del foco inicial).
- Ficha de serie: "Seguir S2·…" cortado → botones de ancho intrínseco
  ("Seguir T2·E4" entero, "T" de temporada).

Segunda tanda (pedido del usuario):
- **Inicio estilo Netflix/Prime**: con el foco en los rails, bajo el rail
  activo se lee entero el encabezado del siguiente y el borde de sus
  tarjetas se funde (`NEXT_RAIL_PEEK` 74 dp; el hero reducido es
  alto − rail − asomo ≈ 195 dp, antes 46 % fijo). Último rail: nada debajo.
- **Menú lateral en todas las pantallas** tras el login (ficha, serie,
  colección, persona, estudio, Ajustes, orden de canales): `shell(...)`
  en `HubplayNavGraph`. La ficha/serie ya no tienen cabecera propia
  (volver + marca): volver = tecla Atrás. Rails de la ficha alineados con
  el póster. Persona/estudio/colección/Ajustes conservan su botón volver.
- **Tráiler Inicio → ficha sin cortes**: si la ficha tardaba > 500 ms en
  cargar, el host ocultaba el tráiler y lo recargaba al llegar el item
  (salto). `HoldTrailerWhileLoading` reclama lo que suena mientras carga.
  Sin probar en la TV (el WebView no se simula).
- Vista previa de la ficha (tráiler, 5 s sin tocar): también se oculta el
  menú lateral (`LocalShellChrome.hideSidebar`, fundido de 600 ms); la
  primera tecla lo devuelve. Nunca se oculta con el menú desplegado.

Reproductor VOD (pedido: "como Jellyfin"):
- **Saltar intro / resumen / créditos** con los `segments` que ya da
  `/items/{id}` (mismo filtro que la web: confianza ≥ 0,7, se va 0,5 s
  antes del final; créditos solo si hay siguiente episodio → salta a él).
  Chrome oculto: píldora abajo a la derecha y OK salta. Chrome visible:
  botón al final de la fila. Lógica en `PlayerExtras` (testeada).
- **Menú Opciones** (engranaje): velocidad 0,5–2×, imagen (ajustar /
  ampliar / estirar → `resizeMode` del PlayerView), capítulos del fichero
  (salta; marca el actual) e información (directo/remux/transcode, códec,
  resolución, fps, canales). `PlayerOptionsSheet.kt`.
- Sin probar en la TV. Si el servidor no ha detectado segmentos o el
  fichero no trae capítulos, esas partes simplemente no salen.
- **Miniaturas al avanzar** (trickplay): `/items/{id}/trickplay.json`
  (sin sobre `data`; 503 mientras ffmpeg genera → reintento cada 10 s,
  10 veces) + `trickplay.png`. La hoja (hasta ~4800×2700) se descarga a
  `cacheDir/trickplay/` y `BitmapRegionDecoder` recorta solo la miniatura
  (RGB_565, LRU de 12): nunca se decodifica entera. Se ve 1,5 s tras cada
  salto, sobre la barra en su posición, con la hora. `TrickplayPreview.kt`.
  Robolectric no implementa BitmapRegionDecoder (sale negro): las capturas
  inyectan un recorte en memoria.
- Pendiente del reproductor: calidad/bitrate y tamaño/estilo/retardo de
  subtítulos.

Revisado y bien: ficha de película, rails de carátulas, En directo, menú
lateral, catálogo, buscar. **Sin revisar aún**: reproductor (chrome VOD y
directo), TV en directo, login/emparejado, Ajustes, colecciones, personas.

## Sesión 2026-11-26 (tarde): federación, sesiones del servidor, crash de rails

Pedido del usuario: "revisar la app para terminarla: rendimiento,
usable y con todo lo que el backend proporciona".

Inventario backend vs app (rutas de `openapi.yaml` contra las llamadas
Retrofit/URLs de la app). Huecos que importaban y se cerraron:

- **Federación entera** (`/me/peers/*`): la app no llamaba a nada.
  - `PeersApi` + `PeersRepository` (DTO → `Content.Shared`, caché LRU de
    256 items para ficha y reproductor: el servidor no tiene "GET item" de
    un amigo, solo listados), `SharedAvailability` (enseña la pestaña solo
    con amigos emparejados; arranca en `false`).
  - Pestaña **Compartido** (`ui/shared/SharedScreen`): un rail por
    biblioteca compartida ("Pedro · Películas") + "Seguir viendo de tus
    amigos". El rail con foco se pega arriba (bring-into-view anulado y
    `animateScrollToItem`), relleno final para que el último también suba.
    Sin píldora del amigo en rails de una sola biblioteca
    (`LocalSharedBadge`).
  - Ficha (`SharedDetailScreen`) con el mismo `HeroDetailScaffold`
    (`HeroToggles.showFavorite = false`), Reanudar / Desde el principio
    (reglas de la web: nada a partir del 90 %). Series de amigos: se ven
    pero no se reproducen (el servidor solo comparte items de primer
    nivel; `parent_id IS NULL`).
  - Reproductor: `PlayerRequest.peerId` → `POST /me/peers/{p}/stream/{i}/session`
    con nuestras capacidades → master HLS en NUESTRO servidor; progreso por
    `PeerProgressSink`; subtítulos de texto por la sesión
    (`…/session/{sid}/subtitles/{index}`). Sin audio alternativo ni
    capítulos (no viajan).
  - Inicio: rails "Seguir viendo de tus amigos" y "Novedades de tus
    amigos" al final (`HomeRailType.SharedContinue/SharedRecent`, solo si
    tienen algo; no vienen del layout del servidor). Buscar: sección "De
    tus amigos".
  - Tarjetas de amigos: píldora "● Pedro" (verde de la web).
- **Liberar sesiones del servidor** (`StreamSessions`):
  - VOD: `DELETE /stream/{id}/session` al salir y al pasar de episodio. El
    cupo de transcode por usuario es 1 sin aceleración y el reaper tarda
    90 s: el siguiente episodio podía dar 503.
  - Directo: id de viewer `?v=` en `/channels/{id}/stream` (el 302 lo
    propaga a `/hls/index.m3u8`) y `DELETE /channels/{id}/hls/viewer` al
    zapear (inmediato) y al salir (4 s de margen: la vista previa de TV en
    directo vuelve a pedir el mismo canal y hereda la sesión caliente).
    Libera ffmpeg y la conexión al proveedor IPTV (muchos dan 1 por cuenta).
- **Arranques**: directo sin `/items` + `/info` (daban 404; la ruta lleva
  `live=true`); VOD con `/items` y `/info` en paralelo;
  `ClientCapabilities.probe()` cacheado y precalculado en `prewarm()`
  (`MediaCodecList` en el hilo principal en cada play).
- **Progreso final perdido**: el `flush` al salir iba en `viewModelScope`,
  que se cancela justo entonces. Ahora `PlayerDeps.backgroundScope` (scope
  de la app). `ProgressReporter` generalizado con `ProgressSink`.
- **Cerrar sesión** revoca el refresh token (`POST /auth/logout`) con el
  cliente SIN `AuthInterceptor` (con el access caducado, el interceptor
  refrescaba y resucitaba la sesión borrada).
- **Quitar de Continuar viendo**: mantener OK → overlay (no `Dialog`, ver
  Identificar) → `DELETE /me/continue-watching/{id}`; optimista.
- **Crash latente** (encontrado con las capturas): un item que desaparece
  de un rail con el foco en otro rail → al salir de Inicio "Release should
  only be called once" (`focusRestorer` de Compose 1.7 fija el item al
  salir el foco y lo libera dos veces). Pasaba ya con el SSE de otro
  dispositivo. `BaseRail` sin `focusRestorer`: un `FocusRequester` por
  tarjeta + `focusProperties.enter` a la última enfocada si está
  compuesta. Regresión: `HomeScreenshots.railItemRemovedWhileFocusElsewhere`.

Backend (rama `claude/stoic-feynman-pyojkw` de HubPlay_demo):
- Federación + DirectPlay = 409: el amigo respondía `WRONG_METHOD` a la
  playlist de calidad si decidía DirectPlay (MP4 H.264/AAC). Arreglado con
  `StartSessionRequest.RequireHLS` (rebaja a remux). Afectaba también a la web.
- Proxy de pósters de amigos reenvía `?w=` (miniatura en vez del original).

Entorno cloud sin TV: ver "Cómo se prueba" (SDK por cmdline-tools, espejo
de Maven, `ROBOLECTRIC_REPO_URL`).
