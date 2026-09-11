# Estado del proyecto — HubPlay Android (TV)

> Entrypoint de cada sesión. Corto a propósito: lo que hace falta para
> retomar sin releer código. Histórico de sesiones en `archive/`.
> Última actualización: **2026-09-11** (noche).

---

## 1. Dónde estamos

- **Rama**: `main` == `origin/main`. CI verde (`detekt` estricto +
  unitarios + `assembleDebug`). Backend hermano: `Alexzafra13/HubPlay_demo`.
- **Estado funcional en la Mi TV real**: login/emparejado, Inicio (hero +
  rails), catálogo de pelis/series, detalle, reproducción HLS, TV en
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
- **Tráiler**: `TrailerHost` (claim por item, `revealed`, `hideNow`);
  el backdrop de Home baja a alpha 0 con 700 ms cuando se revela y vuelve
  a 1 con snap al ocultarse (y el WebView se oculta con snap: nadie lo ve).
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

## 5. Pendiente (orden sugerido)

1. **Crash al navegar el rail "En directo ahora" de Inicio** (reportado
   por el usuario el 2026-09-11, no reproducido aún: la TV se apagó).
   Primer paso: `run-as … cat files/crash-log.txt` y logcat mientras se
   navega. Sospechosos: `ChannelPreviewPlayer` en el hero (ExoPlayer que
   se crea/libera al entrar y salir del rail) y la petición de tráiler
   para un `LiveChannel` (`fetchItemDetail` de un id de canal).
2. Hero con canal en directo enfocado: queda vacío hasta que arranca la
   preview; mostrar nombre + programa.
3. Un host con dos IPs sale dos veces en "Servidores en tu red" (dedupe
   es por URL).
4. Detalle sin artwork (mucho vacío), `CollectionDetailScreen` con
   tarjetas más altas, títulos de ítems sin identificar (metadata del
   servidor), probar en móvil.
5. Backlog de largo plazo: EPG grid completa, tests de Compose UI en
   CI, Baseline Profile. Descartados: MediaSession/Android Auto, Cast.
