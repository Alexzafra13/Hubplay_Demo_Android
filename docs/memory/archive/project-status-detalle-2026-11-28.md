# Detalle de sesiones — 2026-11-26 (noche) a 2026-11-28

> Segunda tanda de "que la app tenga todo lo que da el backend". Solo
> arqueología: el estado vivo está en `project-status.md`.

## Qué se hizo (rama `claude/stoic-feynman-pyojkw`, app y backend)

- **Errores de reproducción** (38230ee): `PlayerState.errorCode` +
  `HubplayPlayer.retry()`; mensaje según el tipo (red, formato, tiempo de
  espera…) y OK reintenta. En directo, un fallo se avisa UNA vez por canal
  con `POST /channels/{id}/playback-failure` (`LiveFailure.kindOf`:
  manifest/network/media/timeout/unknown), que el backend usa para la
  salud de canales. Logo del estudio sobre placa blanca (los PNG oscuros
  no se leían).
- **Subtítulos y calidad** (ef1704b): en Opciones del reproductor,
  tamaño (S/M/L/XL: 0,8/1/1,3/1,6) y estilo (contorno, sombra, caja) con
  `CaptionStyleCompat`, guardados en `SharedPreferences("hubplay_player")`;
  calidad Auto o una variante HLS (`QualityOptions`, override de pista de
  vídeo). **Retardo de subtítulos NO**: Media3 1.5 no tiene API (habría que
  reescribir los cues a mano).
- **Dispositivos conectados** (3b10c4d): Ajustes → Seguridad. `GET/DELETE
  /me/sessions`; "esta tele" va primera y no se cierra. El backend la
  reconoce por la cabecera `X-Hubplay-Refresh-Token` (la app no usa
  cookies; cambio d69e56e del backend).
- **Rails de descubrimiento en Inicio** (15b9afe): tras el layout del
  servidor y en el orden de la web: Porque viste X
  (`/me/home/because-you-watched`), Recomendados (`/me/home/recommended`),
  Colecciones (`/collections`, abren su ficha) y Canales favoritos
  (`/favorites/channels`). Solo si tienen algo; en paralelo con los de
  amigos (`supervisorScope`, un fallo no tumba el resto). No son
  configurables desde Ajustes → Inicio (los añade el cliente).
- **Series de amigos** (23ed81a + backend 6dfc204): `GET
  /me/peers/{peer}/items/{id}/children` (temporadas / episodios, sin
  caché). La ficha reutiliza `SeriesEpisodesPanel`; Reproducir usa
  `SeriesResumeResolver` dentro de la temporada abierta con el progreso
  propio de cada episodio. Serie sin carpetas de temporada = temporada
  única. Un amigo con HubPlay viejo → 502 → aviso.
- **Panel de episodios** (también series propias): el foco caía en el
  menú lateral al abrirlo (el botón "Episodios" desaparece), la cabecera
  quedaba debajo del menú y Atrás salía de la ficha. Arreglado: foco a la
  temporada abierta, `padding(start = SIDEBAR_WIDTH)`, `BackHandler`.
- **Guía completa en rejilla** (7ead3de): `LiveGuideScreen` desde el pie
  del menú de TV en directo (`LiveTvFooterActions`). Ventana desde la
  media hora anterior a +6 h (`GuideWindow`), 6 dp por minuto, un
  `ScrollState` horizontal compartido por regla y filas, título pegado
  al borde visible en la fase de layout (`stickToVisibleStart`) y un
  `BringIntoViewSpec` propio para que ↑/↓ no salte en el tiempo. Sin
  línea de "ahora" en las filas (tachaba títulos): la barra de progreso
  de cada programa en directo acaba en "ahora" y la regla lleva la marca.
  Comparte el ViewModel de TV en directo vía `getBackStackEntry`.
- **CI** (47f17e2): las capturas Robolectric corren en CI como tests de UI
  (fallan si una pantalla peta o falta un botón) y suben los PNG como
  artefacto `screenshots`. `ROBOLECTRIC_REPO_URL` al espejo de Google
  (Central daba 429). Run verde: ~4 min en total.
- **Baseline profile a mano** (47e883c): `app/src/main/baseline-prof.txt`
  con comodines (`HSPLcom/alex/hubplay/**;->**(**)**` y OkHttp, Retrofit,
  Moshi); AGP los expande (13 154 métodos de la app). Solo release. Sin
  medir en la tele; uno generado con Macrobenchmark en el aparato sería
  más fino (aquí no hay KVM para un emulador).

## Backend en la misma rama

- 5ab807f: estudios con 0 títulos. Causa: filas de `studios` de backfill
  sin `tmdb_id` + el refresco de metadatos que borraba y no re-enlazaba.
  `ensureStudioWithTMDB` adopta la fila vieja con el mismo slug;
  migración 059 re-enlaza `studio_id` NULL por slug.
- d69e56e: `X-Hubplay-Refresh-Token` para `/me/sessions` sin cookies.
- 6dfc204: `GET /peer/items/{id}/children` (origen, ACL `can_browse`,
  404 igual para "no existe" y "no es tuyo") + proxy
  `/me/peers/{peer}/items/{id}/children`; `SharedItem` gana
  `season_number`, `episode_number`, `duration_ticks`.

## Limitaciones conocidas

- Progreso de episodios de amigos: se guarda y se reanuda desde la ficha,
  pero **no sale en "Seguir viendo de tus amigos"** (ese rail une con
  `federation_item_cache`, que solo guarda items raíz).
- La web aún no usa el endpoint de hijos (la serie de un amigo sigue sin
  episodios allí).
- Nada de esta tanda está probado en la TV real.
