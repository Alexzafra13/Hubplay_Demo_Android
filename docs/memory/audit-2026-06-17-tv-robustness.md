# Auditoría de robustez TV — 2026-06-17

> Revisión **estática** (sin device, sin SDK en el entorno) de los caminos
> de mayor riesgo de cara a "subir a la store + usar de verdad". El código
> compila y CI está verde, pero **nunca se ha ejecutado en hardware real**.
> Objetivo: bugs de corrección/runtime que aparecerían con un mando D-pad,
> no estilo.
>
> Cada hallazgo lleva mi **estado de verificación**:
> - ✅ **Confirmado** leyendo el código.
> - ⚠️ **Matizado** — existe pero menos grave de lo que parece.
> - 🔎 **Plausible** — requiere validar en device.
>
> Lo único aplicado en esta rama es el fix #1 (mecánico y seguro). El resto
> necesita Android Studio + device para validar el fix, por eso queda como
> checklist y no como parche a ciegas.

---

## Aplicado en esta rama

### #1 — ExoPlayer `Player.Listener` no se desregistra ⚠️→ARREGLADO
`player/HubplayPlayer.kt`. `release()` solo llamaba `exoPlayer.release()`.
Matiz: NO es un leak real (el listener lo posee el propio ExoPlayer, que se
libera, y el wrapper es `remember`-scoped → todo el grafo es recolectable).
Pero `removeListener` antes de `release()` es el orden documentado y evita
que un callback en vuelo toque `_state` tras liberar. **Aplicado**: listener
extraído a `val playerListener` + `removeListener` en `release()`. Mismo
patrón pendiente en `ChannelPreviewPlayer.kt` (no tocado aquí).

---

## Pendiente — validar en device antes de parchear

### CRITICAL / HIGH

**#2 — Auto-play siguiente episodio puede re-disparar el countdown** 🔎
`ui/player/NextEpisodeOverlay.kt` + `PlayerViewModel.playNextEpisode`. Al
llegar a `STATE_ENDED` se muestra la card con countdown; al firar,
`playNextEpisode` pone `nextEpisode=null`/`startParams=null` y re-resuelve,
pero `playerState.isEnded` sigue true un frame, y `autoPlayDismissed` se
resetea con `remember(ui.itemId)`. Si el nuevo episodio aún no salió de
ENDED, la card puede re-aparecer. **Fix propuesto**: gate del overlay en
`isEnded && !isPlaying && startParams != null` + ignorar re-entrada en
`playNextEpisode` mientras hay un resolve en vuelo. **Validar**: ver fin de
episodio en una serie larga, observar que NO parpadea la card.

**#3 — Error de playback deja la pantalla sin recuperación + screensaver suprimido** 🔎
`ui/player/PlayerScreen.kt`. En `onPlayerError` se muestra `ui.error` como
texto, pero la pantalla sigue montada con `keepScreenOn=true` e
`idleController.setSuspended(true)` indefinidamente; el único escape es
BACK. **Fix propuesto**: botón Reintentar/Atrás en el estado de error y
soltar keepScreenOn en error terminal. **Validar**: apuntar a un stream
muerto y comprobar que se puede reintentar/salir con claridad.

**#7 — Bucle de reconexión SSE infinito en fallo no recuperable** ✅
`data/MeEventsStream.kt:60`. `while(true)` con backoff ~2s ante CUALQUIER
throwable, incluido un 401/403 que el AuthInterceptor no puede refrescar
(sesión revocada server-side). → martillea `/me/events` cada 2s para
siempre (batería + carga servidor). **Fix propuesto**: en `openSource`,
propagar `response?.code` del `onFailure` y, ante un status no recuperable
(401 tras refresh fallido / 403), terminar el flow o escalar a re-login en
vez de reintentar. **Validar**: revocar la sesión en el servidor con la app
abierta y ver que no entra en bucle.

### MEDIUM

**#5 — Resolución de siguiente episodio cruzando temporada** 🔎
`ui/player/NextEpisodeResolver.kt`. `firstEpisode` usa
`minByOrNull { episodeNumber ?: Int.MAX_VALUE }`; con metadatos sin numerar
(specials/sin escanear) puede elegir el episodio equivocado al saltar de
temporada. **Fix propuesto**: ordenar con la misma regla estable de
`nextAfter` y tomar `firstOrNull()`. **Validar**: season finale → primer
episodio de la siguiente.

**#8 — Auto-tune del preview es demasiado agresivo** 🔎
`ui/player/ChannelPreviewPlayer.kt`. Tras `AUTO_TUNE_MS` (8s) con el foco en
un canal, salta solo a pantalla completa; `lastAutoTunedId` solo bloquea el
mismo id. Un usuario que duda 8s se ve arrastrado al player. **Fix
propuesto**: hacerlo opt-in / explícito. **Validar**: dejar el foco quieto
en un canal y ver si molesta.

**#12 — Pairing: fallo de red = spinner hasta 10 min** ⚠️
`data/DeviceCodeRepository.kt:81`. `resp == null` (red caída O 4xx) → ambos
`Pending`, hasta `expiresInSec` (600s). El comentario dice que es
**intencional** (RFC 8628 slow_down silencioso). Si el servidor muere a
mitad, el usuario espera 10 min. **Decisión de producto**: si se quiere,
distinguir `IOException` (mostrar error tras N fallos seguidos) de
`HttpException` (pending). No es bug claro; dejar como está es defendible.

### LOW / hygiene

- **#10** `TrailerHost.embeddabilityCache` (`ConcurrentHashMap`) nunca
  evicta — acotar a LRU de ~unos cientos. 🔎
- **#11** `LanDiscovery` reusa un `resolveListener` único entre resolves —
  en API 34+ se exige instancia por resolve. 🔎
- **#13** `IdleController` evalúa idle solo en tick de 30s → screensaver
  puede tardar hasta 30s; posible flicker por carrera `setSuspended`/tick.⚠️
- **#14** `HomeRail` `scrollToItem(focusedIndex)` sin re-check de
  `items.size` tras un refresh que encoge la lista (clampa, no crashea, pero
  restaura foco al item equivocado). 🔎

---

## Lo que se vio sólido (sin acción)

- `ProgressReporter`: math de ticks (10_000_000/s) y serialización con mutex
  correctas.
- `PinnedCertTrustManager`: flujo TOFU, clasificación de causa y orden
  system-delegate-first bien escritos y defensivos.
- `PlayerScreen` captura la posición final ANTES de `release()` y resetea
  `idleController.setSuspended(false)` en dispose.
- `NextEpisodeOverlay` usa `rememberUpdatedState(onPlayNow)` (sin captura de
  lambda stale).

---

## Recomendación

Los dos primeros que tocará validar en cuanto haya un device/emulador TV son
**#2 (auto-play)** y **#3 (recuperación de error)** porque salen en minutos
de uso normal con mando. **#7 (SSE)** es el más importante de fondo (drena
batería). El resto es pulido incremental.
