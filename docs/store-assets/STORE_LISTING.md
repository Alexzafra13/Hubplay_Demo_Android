# Ficha de Play Store — HubPlay Android

Texto y assets listos para pegar en **Play Console → Crecimiento → Ficha
de Store principal**. Los límites de caracteres los impone Google; el
texto de abajo ya cabe.

> **Importante para la review**: HubPlay es un **cliente**. No funciona
> solo — necesita un servidor HubPlay self-hosted al que conectarse.
> Déjalo explícito en la descripción para que el revisor de Google no
> rechace la app por "no se puede usar" (es el motivo de rechazo más
> común en clientes de media server). Para la review, dales credenciales
> de un servidor demo en las "Instrucciones de acceso" de la consola.

---

## Identidad

- **Nombre de la app** (≤30): `HubPlay`
- **applicationId**: `com.alex.hubplay`
- **Categoría sugerida**: Entretenimiento
- **Etiquetas**: reproductor multimedia, vídeo, TV

---

## Español (es-ES) — idioma por defecto

**Descripción corta** (≤80 caracteres):

```
Tu servidor de cine, series y TV en directo, en tu Android TV.
```

**Descripción completa** (≤4000 caracteres):

```
HubPlay es el cliente para Android TV, tablet y móvil de tu servidor
multimedia HubPlay self-hosted. Conéctate a tu propio servidor y
disfruta de tu colección de películas, series y canales de TV en
directo en la pantalla grande, con una interfaz pensada para el mando
a distancia.

IMPORTANTE: HubPlay necesita un servidor HubPlay propio para funcionar.
La app no aloja ni distribuye ningún contenido: solo reproduce el que
hay en el servidor que tú configures.

CARACTERÍSTICAS

• Catálogo estilo Netflix/Prime: portadas, fondos, tráilers y rieles de
  "Continuar viendo", "Tendencias" y novedades por biblioteca.
• Ficha detallada con sinopsis, reparto y equipo, "más como esto" y
  navegación a la filmografía de cada actor o director.
• Reproductor con selección de audio y subtítulos, reanudación
  automática y salto automático al siguiente episodio.
• TV en directo (IPTV): canales con guía de programación (EPG),
  favoritos, reordenar y ocultar canales, y filtro de vistos
  recientemente.
• Sincronización entre dispositivos: el progreso, los favoritos y tus
  ajustes se guardan en tu servidor y aparecen en todas tus pantallas.
• Multi-perfil con PIN: cada miembro de la casa tiene su sesión.
• Emparejamiento sencillo: escanea un QR o teclea un código corto, con
  descubrimiento automático del servidor en tu red local.
• Salvapantallas estilo cine cuando dejas la TV inactiva.
• Privacidad por diseño: sin publicidad, sin analítica y sin telemetría
  a terceros. Tus datos se quedan entre tu dispositivo y tu servidor.

Optimizada para el mando de Android TV y Fire TV, y también utilizable
en tablet y móvil.
```

---

## English (en-US)

**Short description** (≤80 chars):

```
Your self-hosted movies, shows and live TV server, right on your TV.
```

**Full description** (≤4000 chars):

```
HubPlay is the Android TV, tablet and phone client for your self-hosted
HubPlay media server. Connect to your own server and enjoy your movies,
TV shows and live channels on the big screen, with an interface built
for the remote control.

IMPORTANT: HubPlay requires your own HubPlay server to work. The app
does not host or distribute any content — it only plays what lives on
the server you configure.

FEATURES

• Netflix/Prime-style catalog: posters, backdrops, trailers and
  "Continue watching", "Trending" and per-library "New" rails.
• Rich detail pages with synopsis, full cast & crew, "more like this"
  and navigation to each actor's or director's filmography.
• Player with audio and subtitle track selection, automatic resume and
  auto-play of the next episode.
• Live TV (IPTV): channels with EPG guide, favorites, channel reorder
  and hide, and a recently-watched filter.
• Cross-device sync: progress, favorites and settings live on your
  server and follow you to every screen.
• Multi-profile with PIN: everyone in the house gets their own session.
• Easy pairing: scan a QR or type a short code, with automatic server
  discovery on your local network.
• Cinematic screensaver when the TV goes idle.
• Privacy by design: no ads, no analytics, no third-party telemetry.
  Your data stays between your device and your server.

Optimized for the Android TV / Fire TV remote, and also usable on
tablet and phone.
```

---

## Assets gráficos (en este mismo directorio salvo el banner)

| Asset | Fichero | Tamaño | Dónde va |
| --- | --- | --- | --- |
| Icono de la app | `icon-512.png` | 512×512 PNG | Ficha → Icono de la app |
| Gráfico de funciones | `feature-graphic-1024x500.png` | 1024×500 PNG | Ficha → Gráfico de funciones |
| Banner de TV | `../../app/src/main/res/drawable-xhdpi/tv_banner.png` | 320×180 PNG | Ya empaquetado en la app vía `android:banner`. Subir también como **TV banner** en la ficha. |
| Capturas teléfono | (pendiente) | mín. 2, 16:9 o 9:16 | Ficha → Capturas de teléfono |
| Capturas Android TV | (pendiente) | mín. 1, 1920×1080 | **Obligatorias** por declarar leanback |

Los tres primeros se regeneran con `python3 scripts/gen_store_assets.py`.

### Capturas — pendientes (necesitan device/emulador)

Google exige **al menos 1 captura de Android TV** porque el Manifest
declara `leanback`. Sugerencia de pantallas a capturar (1920×1080):

1. Home con hero + rieles.
2. Ficha de una película (sinopsis + reparto).
3. TV en directo con la guía/EPG.
4. Reproductor con la hoja de selección de pistas.
5. Selector de perfiles (Who's watching).

Cómo capturar en emulador TV:

```bash
# Crea un AVD de Android TV (1080p) en Android Studio, arráncalo y:
adb shell screencap -p /sdcard/shot.png && adb pull /sdcard/shot.png
```
