# Probar HubPlay en device/emulador (runbook)

Qué se puede verificar **automáticamente** y qué necesita tus ojos, y cómo
lanzar cada cosa cuando estés en el PC con Android Studio.

> Resumen del reparto:
> - **Automático (lo corre la máquina/IA):** que compila, unit tests, smoke
>   test instrumentado (la app arranca y el Login compone sin crashear), y un
>   recorrido D-pad guionizado que detecta crashes/ANR y deja capturas.
> - **Humano (tú):** fluidez, estética, que el vídeo se vea bien, glitches de
>   foco sutiles, y comportamiento en TV reales (Mi Box S, Fire TV…).

---

## 1. Niveles de verificación

| Nivel | Comando | Necesita | Qué garantiza |
| --- | --- | --- | --- |
| Compila + estilo | `gradle :app:detekt :app:assembleDebug` | nada extra | build sano, sin findings |
| Unit tests (JVM) | `gradle :app:testDebugUnitTest` | nada extra | lógica (resolvers, repos, VMs testables) |
| Smoke instrumentado | `gradle :app:connectedDebugAndroidTest` | emulador/device | **la app arranca y el Login renderiza sin crashear** |
| Recorrido D-pad | `scripts/tv-smoke.sh` | device + app instalada | no crashea en un tour real + capturas + logcat |
| Juicio humano | — | TV real | fluidez, estética, vídeo, foco |

---

## 2. Crear un emulador de Android TV (una vez)

En Android Studio: **Device Manager → Create Device → TV → Android TV (1080p)
→** una system image (API 30+ recomendada) **→ Finish**. O por consola:

```bash
sdkmanager "system-images;android-30;android-tv;x86"
avdmanager create avd -n tv30 -k "system-images;android-30;android-tv;x86" -d tv_1080p
emulator -avd tv30
```

Arranca el emulador (o conecta una TV con depuración USB) antes de los
comandos `connected*` / del script.

---

## 3. Smoke test instrumentado (Compose UI)

`app/src/androidTest/kotlin/.../LaunchSmokeTest.kt` — lanza `MainActivity`,
espera a idle y asercia que se ve `R.string.login_title`. Con eso solo ya se
ejerce `HubplayApp.onCreate` + `AppContainer` + theme + NavGraph + Login.

```bash
# con un emulador/TV conectado:
gradle :app:connectedDebugAndroidTest
# reporte HTML: app/build/reports/androidTests/connected/index.html
```

> ⚠️ Este test se escribió **sin** poder arrancar un emulador (entorno
> remoto). En la primera ejecución local: si falla por el nodo aserción,
> ajústalo (otra `startDestination`, splash, etc.). **Siguiente paso para
> tests menos frágiles:** añadir `Modifier.testTag("...")` a los composables
> clave (login, rieles del Home, botón Play) y asercionar por tag en vez de
> por texto.

### En CI

`.github/workflows/ui-tests.yml` arranca un emulador de Android TV
(`reactivecircus/android-emulator-runner`) y corre `connectedDebugAndroidTest`.
Hoy es **manual** (`workflow_dispatch`) y **no es gate de merge** — es lento y
la matriz de emulador (api-level/target/arch) puede necesitar ajuste en la
primera vuelta (si no hay imagen `android-tv` para esa combinación, prueba
`target: google_apis`). Cuando pase en verde un par de veces, cambia el
trigger a `push` para convertirlo en gate.

---

## 4. Recorrido D-pad guionizado (`scripts/tv-smoke.sh`)

Para ejercer flujos que necesitan servidor (Home, Player) contra tu servidor
HubPlay real ya emparejado en el device:

```bash
scripts/tv-smoke.sh                 # app ya instalada
scripts/tv-smoke.sh --install       # build + install primero
scripts/tv-smoke.sh --serial <id>   # varios devices conectados
```

Hace: relanza la app limpia, recorre el D-pad (abajo/derecha/OK/atrás/izq.),
captura un PNG numerado por paso, vuelca `logcat-full.txt` /
`logcat-crash.txt` / `logcat-app.txt`, y al final dice si el proceso sigue
vivo y si hubo `FATAL EXCEPTION`/ANR. Todo en `build/tv-smoke/<timestamp>/`.

Edita el bloque "D-pad tour" del script para guionizar TUS pantallas
concretas (p. ej. Login→Home→Play una vez emparejado). Revisa las capturas en
orden + `logcat-app.txt`.

---

## 5. Lo que NINGÚN automatismo cubre

- **Fluidez / jank**: usa Macrobenchmark + Perfetto para métricas; el "se
  siente suave" lo juzgas tú. Especialmente en TV de gama baja.
- **Estética y alineación**: las capturas detectan roturas, no si está bonito.
- **Vídeo**: logcat confirma estado `PLAYING`; que la imagen se vea bien no.
- **Hardware real**: el emulador ≠ Mi Box S. mDNS quisquilloso, embed de
  YouTube del trailer, cert TOFU y rendimiento se validan en cajas reales.
