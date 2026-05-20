# HubPlay Android

Cliente Android nativo para [HubPlay](https://github.com/Alexzafra13/HubPlay_demo) — servidor de media self-hosted estilo Plex/Jellyfin.

> **Estado:** scaffolding inicial. Login con device-code y stub de Home funcionales. Player y rails llegan en la siguiente iteración.

---

## Stack

Última generación **estable comprobada** (un peldaño por debajo de AGP 9, evitando el churn de migración a built-in Kotlin / Variant API estricta):

- **Kotlin 2.0.21** + **Jetpack Compose** (Material 3, single-Activity)
- **Compose BOM 2024.12.01** — alinea todos los artefactos de Compose
- **Media3 / ExoPlayer 1.5.0** para streaming HLS
- **Retrofit 2.12.0** + **OkHttp 5.3.2** + **Moshi 1.15.2** (codegen vía KSP 2.0.21-1.0.28)
- **AndroidX DataStore 1.1.1** para tokens (con backup desactivado)
- **Coil 3.0.4** para imágenes (network engine OkHttp split-out)
- **kotlinx-coroutines 1.9.0**
- **OpenAPI Generator 7.10.0** — el cliente Retrofit se autogenera del `openapi-cached.yaml` en cada build
- **AGP 8.7.3** + **Gradle 8.10.2** (require JDK 17)

`minSdk = 26` (Android 8.0+, ~95% de devices) · `compileSdk = 35` (Android 15) · `targetSdk = 35` · Java 17.

> **Política de versiones**: este set funciona junto, comprobado. Cuando subas algo, sube **el grupo coherente**: AGP + Gradle + Kotlin + KSP + Compose BOM cambian juntos en bumps grandes. Subir una sola pieza suele romper varias otras (lo aprendí por las malas).

---

## Arquitectura

```
app/src/main/kotlin/com/alex/hubplay/
├── HubplayApp.kt              # Application, instancia AppContainer (DI manual)
├── MainActivity.kt            # Single-Activity host de Compose
├── ui/
│   ├── theme/                 # Compose Theme (dark Plex-style)
│   ├── nav/                   # NavHost + rutas tipadas
│   ├── login/                 # LoginScreen + LoginViewModel (device-code)
│   └── home/                  # HomeScreen (placeholder)
└── data/
    ├── AppContainer.kt        # DI manual — singletons del proceso
    ├── TokenStore.kt          # DataStore + Flow reactivo de AuthState
    ├── BaseUrlInterceptor.kt  # Reescribe scheme/host/port en runtime
    ├── AuthInterceptor.kt     # Bearer + auto-refresh en 401
    └── DeviceCodeRepository.kt # Pairing /auth/device/{start,poll}
```

**¿Por qué DI manual y no Hilt?** Hoy el grafo tiene 5 singletons; Hilt añade ~1.2 MB al APK y mucha annotation surface por nada. Cuando crucemos los ~15 nodos lo migramos.

**¿Por qué un único Activity?** Toda la navegación vive en Compose (`HubplayNavGraph`). Sin fragments, sin extra Activities — el lifecycle es trivial de razonar.

---

## Cliente API auto-generado

El backend HubPlay sirve `openapi.yaml` en `/api/v1/openapi.yaml` (2889 líneas) y tiene un `openapi_drift_test.go` en CI que falla si el spec se desincroniza del router. Eso significa que el cliente generado **siempre está al día con el backend en `main`**.

El plugin de OpenAPI Generator (`org.openapi.generator`) lee el spec en cada build y emite código Kotlin en `app/build/generated/openapi/src/main/kotlin/`:

```
com.alex.hubplay.api          ← interfaces Retrofit (AuthApi, ItemsApi, …)
com.alex.hubplay.api.model    ← data classes con @JsonClass(generateAdapter=true)
com.alex.hubplay.api.invoker  ← infraestructura mínima
```

Para usarlo:

```kotlin
val authApi: AuthApi = retrofit.create(AuthApi::class.java)
val response = authApi.deviceStart(DeviceStartRequest())
```

### Refrescar el spec

El archivo `openapi-cached.yaml` está commiteado al repo para que el build funcione offline (CI, dev sin el server arriba, code-review legible). Cuando el backend ship endpoints nuevos:

```bash
./gradlew refreshOpenApiSpec
git diff openapi-cached.yaml      # revisa qué cambió
git commit -am "openapi: sync con backend HEAD"
```

`refreshOpenApiSpec` baja el spec de `https://hubplay.duckdns.org/api/v1/openapi.yaml` (configurable en `app/build.gradle.kts`).

---

## Auth flow (device-code)

1. Usuario teclea la URL del server en LoginScreen y pulsa Continuar.
2. App → `POST /auth/device/start` → server devuelve `user_code` (corto, ej. `B4XK-9P2T`) y `device_code` (secreto largo).
3. App muestra el `user_code` y empieza a pollear `POST /auth/device/poll` cada 2s.
4. Usuario abre HubPlay en el navegador → Ajustes → Dispositivos → introduce el `user_code` y aprueba.
5. Próximo poll devuelve `access_token` + `refresh_token`. Se persisten en DataStore. La `AuthStateFlow` flipa y el host composable navega a Home.

El `AuthInterceptor` se encarga del refresh automático en 401 con un único refresh por proceso (AtomicBoolean) — varios 401 paralelos no disparan races.

---

## Cómo arrancar (Android Studio)

1. **Abrir el repo en Android Studio Hedgehog (2023.1) o superior.**
2. Android Studio detecta el `build.gradle.kts` y propone "Sync Project with Gradle Files" — acepta. Esto genera el Gradle Wrapper (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`) que están en `.gitignore`.
3. Espera a que el primer build descargue dependencias (~5 min con caché vacía). El plugin de OpenAPI corre en este paso y genera el cliente.
4. **Run** ▶️ sobre un emulador o device físico. Asegúrate de que tu server HubPlay sea accesible desde el device (LAN o internet).

### Build desde CLI (cuando el wrapper exista)

```bash
./gradlew assembleDebug          # APK debug
./gradlew installDebug           # instala en device conectado
./gradlew refreshOpenApiSpec     # actualiza spec del server
./gradlew :app:openApiGenerate   # regenera cliente sin build completo
./gradlew :app:testDebugUnitTest # unitarios (src/test/kotlin)
```

---

## CI

`.github/workflows/ci.yml` corre en cada push a `main` o ramas
`claude/**` y en cada PR contra `main`:

1. `:app:openApiGenerate` — regenera el cliente Retrofit desde el spec.
2. `:app:testDebugUnitTest` — unitarios JVM (resolver de series, normalización de URL, throttling del ProgressReporter, …).
3. `:app:assembleDebug` — APK debug completo, ejercita el toolchain entero.

Sube como artefactos: reportes de tests siempre (también en rojo) +
APK debug si el build pasa. Usa `gradle/actions/setup-gradle@v4` con
Gradle 8.10.2 (igual que el wrapper local), así no hace falta tener el
`gradle-wrapper.jar` commiteado.

---

## Roadmap inmediato

- [ ] **HomeScreen real** — `/me/home/layout` + rails (Continue Watching, Next Up, Latest, Trending, LiveNow). Misma forma que el web client.
- [ ] **PlayerScreen** — Media3 ExoPlayer con `master.m3u8` + capability negotiation vía `X-Hubplay-Client-Capabilities`.
- [ ] **MediaSession + foreground service** para background audio + Android Auto.
- [ ] **SSE listener** sobre `/me/events` para sync cross-device de progreso/played/favorite.
- [ ] **Detail screens** (movie, series, season).
- [ ] **Search**.
- [ ] **Live TV** con EPG grid simplificada.
- [ ] **Cast** (Chromecast) — ya que el server emite HLS estándar, debería funcionar con `cast-framework` con poco glue.

---

## Decisiones de diseño que me ahorran sufrimiento futuro

- **`BaseUrlInterceptor` reescribe scheme/host/port en runtime** → un solo `Retrofit` instance que sirve para cualquier server al que el usuario apunte la app. Sin tener que reconstruir el cliente al cambiar de server (LAN ↔ remote).
- **`refreshClient` es un OkHttp **separado** sin AuthInterceptor** → cuando un refresh devuelve 401 NO recursa en sí mismo.
- **Auto-backup desactivado para `datastore/`** (`res/xml/data_extraction_rules.xml`) → un usuario que restaura backup en otro device tiene que volver a parear, no hereda credenciales.
- **`readTimeout = 0` en el cliente principal** → necesario porque `/me/events` mantiene la conexión SSE abierta indefinidamente. Los timeouts cortos van en el cliente de refresh.
- **Versionado del API**: el backend está en `/api/v1/`. La regla operativa es **nunca quitar campos** (solo añadir) y **nunca cambiar tipos**. Para cambios incompatibles inevitables: bump a `/v2/` y mantener `/v1/` durante meses.

---

## Licencia

MIT. Consulta el [repo del backend](https://github.com/Alexzafra13/HubPlay_demo) para el grueso del proyecto.
