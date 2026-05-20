# Política de privacidad — HubPlay para Android

**Última actualización**: por completar antes de publicar.
**Aplicación**: HubPlay Android (`com.alex.hubplay`)
**Contacto**: por completar antes de publicar (email de contacto público).

> **Nota para quien aloje esta política**: este fichero es la
> plantilla. Para Play Store necesitas publicarlo en una URL
> pública (GitHub Pages, tu dominio, lo que sea) y dar de alta esa
> URL en Play Console → Política de privacidad. Ajusta nombre,
> email y fecha antes de publicar.

---

## 1. Resumen en una línea

**HubPlay no recopila datos personales en sus propios servidores.**
La app se conecta exclusivamente al servidor HubPlay que tú decidas
configurar (un servidor self-hosted que tú o un tercero administra).
Todos los datos sobre tu actividad viven en ese servidor — no nuestros.

---

## 2. Qué datos maneja la app

### 2.1 Datos que la app guarda localmente en tu dispositivo

- **URL del servidor HubPlay** al que has emparejado el dispositivo.
- **Tokens de autenticación** (access token + refresh token JWT)
  emitidos por tu servidor.
- **Caché de imágenes** (carátulas, fondos, logos de canales)
  almacenada en el directorio privado de la app, hasta 256 MB.
- **Logs de crashes** (últimos 10 stack traces) si la app se cierra
  inesperadamente, accesibles desde Ajustes → Diagnóstico para que
  puedas pegarlos cuando reportes un problema. No se envían a nadie
  automáticamente.

Todos estos datos residen únicamente en el dispositivo y se eliminan
al desinstalar la app o al pulsar "Cerrar sesión" / "Cambiar servidor"
desde Ajustes.

### 2.2 Datos que la app envía al servidor HubPlay que tú configures

- **Credenciales de pareo** durante el flujo device-code RFC 8628.
- **Progreso de reproducción** (posición en segundos, marcado de
  visto) para sincronizar entre dispositivos.
- **Favoritos** que marques en la app.
- **Identidad del dispositivo** (nombre legible "HubPlay-Android")
  para que aparezca en la lista de sesiones activas del servidor.

Lo que ese servidor haga con esos datos depende del propietario y
política de ese servidor. **Nosotros, como desarrolladores de la
app, no recibimos copia de nada.**

### 2.3 Datos que la app NO recopila

- **Sin analítica de uso** (Firebase Analytics, Google Analytics,
  Mixpanel, etc.). Cero.
- **Sin telemetría de crashes** a servicios de terceros (Crashlytics,
  Bugsnag, Sentry, …). Los stack traces viven sólo on-device.
- **Sin identificadores publicitarios** (Advertising ID).
- **Sin SDKs de redes sociales** (Facebook, Google Sign-In, …).
- **Sin ubicación geográfica** del dispositivo.
- **Sin contactos, fotos, calendario, sensores ni micrófono**.

---

## 3. Permisos solicitados y por qué

La app declara los siguientes permisos en su Manifest:

| Permiso | Para qué se usa |
| --- | --- |
| `INTERNET` | Conectar con tu servidor HubPlay (única conexión saliente). |
| `ACCESS_NETWORK_STATE` | Detectar si hay red antes de iniciar streams. |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Reservados para futuro audio en segundo plano. No utilizados hoy. |
| `WAKE_LOCK` | Evitar que la pantalla se apague durante reproducción. |

No se solicitan permisos de **ubicación**, **cámara**, **micrófono**,
**contactos**, **almacenamiento externo**, **identificadores** ni
**Bluetooth**.

---

## 4. Descubrimiento en red local (mDNS)

Al abrir la pantalla de login, la app **escucha** anuncios mDNS del
tipo `_http._tcp` para detectar servidores HubPlay en tu LAN. Esta
escucha:

- No envía paquetes salientes.
- No transmite ningún dato del usuario.
- Se detiene en cuanto sales de la pantalla de login.
- No requiere permisos especiales en Android 8.0+.

---

## 5. Menores de edad

La app no está dirigida específicamente a menores de 13 años. La
clasificación por edades exacta depende del contenido que el operador
de cada servidor HubPlay decida exponer.

---

## 6. Cambios a esta política

Si modificamos qué datos maneja la app, actualizaremos esta página y
mostraremos un aviso dentro de la app la próxima vez que se abra.

---

## 7. Contacto

Para preguntas sobre privacidad o ejercer derechos sobre los datos
que tu servidor HubPlay tenga (no nosotros), contacta a:

- **Para la app Android**: por completar (email público).
- **Para los datos almacenados en tu servidor HubPlay**: el operador
  de ese servidor (tú mismo si es self-hosted).
