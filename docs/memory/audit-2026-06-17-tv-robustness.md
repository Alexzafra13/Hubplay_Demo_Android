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

> Los tres fixes de abajo son de **capa de datos / sin foco UI**, por eso se
> aplicaron sin device. Verificados contra el gate de CI con detekt
> standalone (`detekt-cli 1.23.7 + detekt-formatting`, config + baseline del
> repo, `--jvm-target 17`) → EXIT=0 sobre todo `app/src/main/kotlin`. Lo que
> NO se puede verificar aquí es `assembleDebug` (sin SDK Android); revisión
> de tipos manual.

### #1 — ExoPlayer `Player.Listener` no se desregistra ⚠️→ARREGLADO
`player/HubplayPlayer.kt`. `release()` solo llamaba `exoPlayer.release()`.
Matiz: NO es un leak real (el listener lo posee el propio ExoPlayer, que se
libera, y el wrapper es `remember`-scoped → todo el grafo es recolectable).
Pero `removeListener` antes de `release()` es el orden documentado y evita
que un callback en vuelo toque `_state` tras liberar. **Aplicado**: listener
extraído a `val playerListener` + `removeListener` en `release()`. Mismo
patrón pendiente en `ChannelPreviewPlayer.kt` (no tocado aquí).

### #11 — `LanDiscovery` crash por `ResolveListener` compartido ✅→ARREGLADO
`data/LanDiscovery.kt`. Reusaba UNA instancia de `ResolveListener` para todos
los `resolveService`, con un guard `resolving` **por-nombre**; pero la
restricción del framework ("un resolve a la vez") es **global** y reusar el
listener lanza `IllegalArgumentException: listener already in use` (en API
34+ esa firma está deprecada justo por esto). Dos servicios `_http._tcp` con
"hubplay" resolviendo a la vez → crash sin capturar. **Aplicado**: listener
fresco por resolve (`newResolveListener(name)`) + `runCatching` defensivo
alrededor de `resolveService`.

### #7 — Bucle de reconexión SSE infinito en fallo no recuperable ✅→ARREGLADO
`data/MeEventsStream.kt`. `while(true)` reintentaba cada ~2s ante CUALQUIER
throwable, incluido un 401/403 que el AuthInterceptor ya no puede refrescar
(sesión revocada server-side) → martilleo eterno de `/me/events`. **Aplicado**:
`onFailure` distingue 401/403 (cierra con `NonRetryableSseException`) y
`events()` hace `return@flow` en ese caso, parando el loop; el resto de
errores siguen reintentando como antes.

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

**#7 — Bucle de reconexión SSE infinito** → **ARREGLADO** (ver arriba).

### MEDIUM

**#5 — Resolución de siguiente episodio cruzando temporada** ❌ DESCARTADO
Releído: `firstEpisode` usa `minByOrNull`, que devuelve el PRIMER mínimo en
orden estable — equivalente al patrón `sortedBy{}.firstOrNull()` de los
otros métodos. Con todo sin numerar devuelve el primero en orden de
scanner (correcto); con algunos numerados elige el de menor número
(correcto, es lo que quieres). No es bug.

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
  evicta. ⚠️ DESCARTADO por ahora: impacto de memoria despreciable (haría
  falta enfocar decenas de miles de items distintos en una sesión); no
  compensa el riesgo de añadir un LRU.
- **#11** `LanDiscovery` listener compartido → **ARREGLADO** (ver arriba).
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

Ya aplicados sin device (capa de datos): **#1**, **#7**, **#11**. Los dos que
tocará validar en cuanto haya device/emulador TV son **#2 (auto-play)** y
**#3 (recuperación de error)** porque salen en minutos de uso normal con
mando, y requieren tocar UI/foco (por eso NO se aplicaron a ciegas). El resto
es pulido incremental.
