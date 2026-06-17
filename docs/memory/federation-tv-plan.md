# Federación en la TV — plan e implementación

> Decidido con Alex (2026-06-17): **paridad completa con tests**, visibilidad
> **opción "rieles en Home + sección dedicada"** (recomendada, espejo de la web).
> El emparejar peers es admin (web); la TV solo **consume** peers ya emparejados.
>
> Se hace en **fases CI-verificadas**. Las fases de UI/foco/player/Live TV
> necesitan validación en device (no se puede aquí sin SDK/emulador).

## Doctrina de UX (copiada de la web `PeersPage`)
"Una biblioteca compartida debe sentirse de primera clase, solo con una
insignia pequeña de atribución. El usuario piensa 'ojeo las pelis de mis
amigos', no 'entro al servidor X → biblioteca Y'." → content-first, rejilla
unificada + badge "de {peer}".

## Endpoints (todos `{ data: ... }`, posters proxy same-origin)
- `GET /me/peers` → peers conectados
- `GET /me/peers/libraries` → bibliotecas (×peer) aplanadas (rejilla unificada)
- `GET /me/peers/{peer}/libraries/{lib}/items?limit&offset` → catálogo paginado
- `GET /me/peers/search?q=` → búsqueda federada (best-effort)
- `GET /me/peers/recent?limit` → "novedades en peers"
- `GET /me/peers/continue-watching` → seguir viendo cross-peer
- `POST /me/peers/{peer}/stream/{item}/session` → abre sesión → master.m3u8
- `POST /me/peers/{peer}/items/{item}/progress` → reporta resume

## Fases
- **Fase 1 — capa de datos + tests (HECHO, CI-verde).**
  - `data/api/dto/FederationDtos.kt` — DTOs.
  - `data/Federation.kt` — dominio (`ConnectedPeer`, `PeerLibrary`, `PeerItem`,
    `PeerStreamSession`) + `FederationMapper` puro (absolutize `?w=`, ticks→s).
  - `data/FederationRepository.kt` — interfaz + impl (peel envelope + map).
  - `HubplayApi` — 8 endpoints peer. Fakes de test actualizados.
  - `FederationMapperTest` — 6 tests JVM (mapeo, poster, progreso).
- **Fase 2 — UI (pendiente, validar foco en device):**
  - `FederationRepository` a `AppContainer` (DI).
  - Pantalla "Servidores": tira de peers + rejilla unificada por tipo
    (Películas/Series/Live TV) con badge + chips de permiso. Entrada en sidebar.
  - `peerId/libraryId` → items (reusa `CatalogScreen`/`MediaCard`) → detalle.
  - Rieles peer en Home ("Novedades/Seguir viendo en servidores"), carga
    best-effort (no bloquea Home), con badge en `MediaCard`.
  - Nav: rutas `Route.Peers`, `Route.PeerLibrary`, `Route.PeerItem`.
- **Fase 3 — Player modo-peer:** `PlayerViewModel` resuelve vía
  `openStreamSession` (master.m3u8) en vez de `/stream/info`; progreso a
  `reportProgress`. Reusa ExoPlayer.
- **Fase 4 — búsqueda federada + Live TV federada:** mezclar hits de peers en
  Search; canales en vivo de peers (`can_livetv`).

## Notas
- Posters peer son rutas proxy del propio backend → aplican `?w=` (thumbnail).
- Todo best-effort: un peer lento/caído nunca bloquea la UI (el backend hace
  fan-out con timeout por peer).
- Atribución: `PeerItem` lleva `peerId/peerName/libraryId` hasta el play.
