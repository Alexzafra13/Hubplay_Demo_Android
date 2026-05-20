# Publicación en Google Play Store

Este documento cubre la parte **manual** del proceso. La parte automatizable
(firma del AAB, derivación de `versionCode`, subida vía API) ya vive en
`.github/workflows/release.yml` y en `app/build.gradle.kts`; estos pasos te
preparan los secretos y la cuenta para que ese workflow funcione.

**Tiempo total**: ~2 horas la primera vez, ~5 min cada release nuevo.

---

## 1. Cuenta de desarrollador (one-time, ~30 min)

1. Ve a [play.google.com/console](https://play.google.com/console).
2. Paga la cuota única de **$25** (tarjeta personal). No es suscripción.
3. Verifica identidad — Google pide D.N.I. + selfie desde 2023 para
   cuentas personales. Tarda 1-3 días en aprobar.
4. (Si la app va a ser comercial) crea una cuenta de pago en
   [Payments Center](https://play.google.com/console/u/0/paymentscenter).

---

## 2. Crear la app en Play Console (~10 min)

1. **Crear aplicación**:
   - Nombre: `HubPlay`
   - Idioma por defecto: Español (España)
   - App o juego: App
   - Gratis o de pago: Gratis (puedes cambiar después)
   - Acepta las declaraciones de política
2. **Anota el applicationId**: debe coincidir con `com.alex.hubplay` del
   `app/build.gradle.kts`. Si quieres cambiarlo, cámbialo AHORA porque
   después de la primera subida queda inmutable de por vida.
3. En **Configuración → Firma de aplicación**:
   - Activa **"Firma de aplicaciones de Play"** (Play App Signing).
   - Google te da dos opciones: que ellos generen la upload key, o
     que tú subas la tuya. **Recomendado: que tú la generes y la subas**
     (sección 3 abajo) — así el workflow puede firmar AABs igualmente.

---

## 3. Generar el upload keystore (~5 min)

En tu máquina local (NO en CI):

```bash
keytool -genkey -v \
  -keystore hubplay-upload.keystore \
  -alias upload \
  -keyalg RSA -keysize 2048 -validity 25000 \
  -storetype PKCS12

# Te pedirá:
#   - Keystore password    → INVÉNTATE UNA. Guárdala (1Password, etc).
#   - Nombre, organización, ciudad, …
#   - Key password         → puede ser la misma que la del keystore
```

**Guarda el fichero `hubplay-upload.keystore` con tu vida** — si lo pierdes
y NO activaste Play App Signing en Google, pierdes el control de la app
en Play Store para siempre. Si SÍ activaste Play App Signing, Google
puede regenerarte la upload key contactándolos.

### Subir la clave pública a Play Console

1. En Play Console → **Configuración → Firma de aplicaciones**.
2. Exporta la clave pública del keystore:
   ```bash
   keytool -export -rfc -alias upload \
     -keystore hubplay-upload.keystore \
     -file upload_certificate.pem
   ```
3. Sube `upload_certificate.pem` en la Play Console donde te lo pida.

---

## 4. Service Account en Google Cloud (~15 min)

La API de Play Store NO acepta usuarios humanos — necesita una cuenta de
servicio de Google Cloud con permisos delegados desde Play Console.

1. Ve a [console.cloud.google.com](https://console.cloud.google.com/) →
   crea un proyecto (`hubplay-android-publishing` o similar).
2. **APIs y servicios → Biblioteca** → busca "Google Play Android
   Developer API" → **Habilitar**.
3. **IAM y administración → Cuentas de servicio → Crear cuenta de servicio**:
   - Nombre: `play-publisher`
   - Rol: ninguno (los permisos los das desde Play Console, no aquí)
4. En la cuenta de servicio recién creada → **Claves → Añadir clave →
   JSON**. Te descarga `play-publisher-XXXX.json`.
5. **Guárdalo igual de bien que el keystore.**

### Dar permisos a la service account en Play Console

1. Vuelve a Play Console → **Usuarios y permisos → Invitar usuarios**.
2. Email: el `client_email` que aparece en el JSON descargado (algo como
   `play-publisher@hubplay-XXX.iam.gserviceaccount.com`).
3. Permisos: **"Versiones — Liberar a tracks de pruebas"** + **"Liberar
   a producción, excluir, etc."** (si quieres permitir promote a prod
   desde el workflow). Para empezar, sólo "tracks de prueba" es más
   seguro.
4. Aplicable a: **Tu app HubPlay** (no a toda la cuenta).
5. Invita. La service account no recibe email — los permisos se aplican
   inmediato.

---

## 5. GitHub Secrets (~5 min)

En `github.com/Alexzafra13/Hubplay_Demo_Android/settings/secrets/actions`:

| Nombre del secret                       | Cómo se obtiene                                                 |
| --------------------------------------- | --------------------------------------------------------------- |
| `RELEASE_KEYSTORE_BASE64`               | `base64 -w0 hubplay-upload.keystore` (Linux) o `base64 -i hubplay-upload.keystore` (macOS). Copia la salida. |
| `RELEASE_KEYSTORE_PASSWORD`             | La que pusiste al generar el keystore.                          |
| `RELEASE_KEY_ALIAS`                     | `upload` (o el alias que usaras con `-alias`).                  |
| `RELEASE_KEY_PASSWORD`                  | La key password (puede ser igual que la del keystore).          |
| `PLAY_SERVICE_ACCOUNT_JSON_BASE64`      | `base64 -w0 play-publisher-XXXX.json`. Copia la salida.         |

Verifica los dos base64 antes de pegar:

```bash
# Debería devolver el binario original
base64 -d <<< "$(cat secret-clipboard.txt)" | head -c 16 | xxd

# Para el JSON, decode + jq debe parsear:
base64 -d <<< "$(cat secret-clipboard.txt)" | jq -e .client_email
```

---

## 6. Primera subida MANUAL del AAB (~10 min)

Google exige que la primera versión de una app suba "a mano" por la
Play Console antes de aceptar uploads por API. Sin esto, el workflow
fallaría en el primer `publishReleaseBundle`.

1. En tu máquina, exporta las env vars y haz un build manual:
   ```bash
   export RELEASE_KEYSTORE_PATH=$(pwd)/hubplay-upload.keystore
   export RELEASE_KEYSTORE_PASSWORD='…'
   export RELEASE_KEY_ALIAS=upload
   export RELEASE_KEY_PASSWORD='…'
   export VERSION_CODE=1
   export VERSION_NAME=0.1.0

   ./gradlew :app:bundleRelease
   # → app/build/outputs/bundle/release/app-release.aab
   ```
2. En Play Console → **Versiones → Pruebas → Pruebas internas →
   Crear nueva versión** → sube `app-release.aab`.
3. Añade lista de testers (mínimo 14 cuentas Gmail).
4. Rellena los formularios obligatorios (Política de privacidad,
   Data safety, anuncios, categoría, …). Google no te deja avanzar
   sin estos. Tarda ~30 min la primera vez.
5. Click **"Enviar versión a revisión"**.
6. Espera a que aparezca como **"Available on internal testing"** (5 min
   a varias horas).

A partir de aquí los tags `vX.Y.Z` que empujes a `main` se subirán
solos al track interno.

---

## 7. Flujo de release normal (~2 min cada vez)

```bash
git tag v0.3.0
git push origin v0.3.0
```

El workflow `release.yml`:

1. Calcula `versionCode = 300` y `versionName = "0.3.0"` del tag.
2. Decodifica keystore + service account de los secrets.
3. `bundleRelease` → AAB firmado.
4. `publishReleaseBundle --track internal` → sube a internal testing
   en estado **draft**.
5. Sube `mapping.txt` (de R8) como artifact — útil para deobfuscar
   stacktraces de crashes.

En la Play Console verás un nuevo AAB en "Internal testing → Drafts".
**Click "Send for review"** para mover de draft a available (paso manual
intencional para que no se pueda producir un release accidentalmente).

Para promocionar de internal → alpha → beta → production:

- Manual en Play Console (recomendado, te da un día para retractarte).
- O `workflow_dispatch` de `release.yml` con `track: alpha` / `beta` /
  `production` y el `versionCode` del AAB ya subido.

---

## 8. Caveats que vas a aprender por las malas

- **versionCode SIEMPRE creciente**: si subes el 200 hoy y mañana
  intentas subir el 199 te rechaza para siempre. Por eso lo derivamos
  del tag semver.
- **Política de Data Safety obligatoria desde 2022**: hay que declarar
  qué datos recopila la app (Auth tokens cuentan). Tarda 15 min y se
  hace en Play Console, no en código.
- **Closed testing exige 14 testers durante 14 días** antes de poder
  ir a producción (regla nueva de 2024 para developer accounts
  personales). Empieza pronto el closed testing aunque la app esté a
  medias.
- **App Signing reset**: si pierdes el upload keystore pero Play App
  Signing está activado, contacta soporte de Play Console y te
  re-emiten un upload certificate nuevo en ~48h. Sin Play App Signing,
  la app está muerta y hay que publicar una nueva con otro
  applicationId.
- **El primer rechazo es normal**: Google revisa la primera versión a
  mano, las siguientes son auto. Espera 2-14 días la primera vez.
  Posibles rechazos típicos: descripción de privacidad incompleta,
  permisos mal documentados, captura de pantalla de Android TV faltante
  (si declaras leanback).
- **Internal Testing NO necesita revisión**: las subidas vía API a
  internal aparecen en minutos sin revisión humana. Pero promote a
  alpha/beta/production sí requiere review (de 1h a 7 días).

---

## Checklist para el día que te lances

- [ ] Cuenta Play Console pagada y verificada.
- [ ] App `com.alex.hubplay` creada en Play Console.
- [ ] Play App Signing activado.
- [ ] `hubplay-upload.keystore` generado y guardado en sitio seguro.
- [ ] `upload_certificate.pem` subido a Play Console.
- [ ] Proyecto Google Cloud creado, API habilitada.
- [ ] Service account `play-publisher` creada + JSON descargado.
- [ ] Service account invitada en Play Console con permisos de
      "Versiones – tracks de prueba".
- [ ] 5 GitHub secrets configurados.
- [ ] Política de privacidad escrita y publicada en una URL pública.
- [ ] Data Safety declarado en Play Console.
- [ ] Capturas de pantalla preparadas (teléfono + tablet + TV si
      declaras leanback).
- [ ] Icono 512×512 PNG transparente listo.
- [ ] Feature graphic 1024×500 PNG listo.
- [ ] Primera versión subida a mano e internal testing live.
- [ ] Primer tag `v0.1.0` empujado y el workflow lo subió solo.

A partir de ahí: `git tag v0.x.y && git push --tags` y a otra cosa.
