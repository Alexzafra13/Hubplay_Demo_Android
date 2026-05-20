# Play Store — Declaración de Data Safety

Cheat-sheet de cómo rellenar la sección **Data Safety** en Play
Console (`Política → Seguridad de los datos`) para HubPlay Android.

Google introdujo este formulario en 2022; toda app nueva tiene que
completarlo antes de poder publicar a producción. Las respuestas
abajo reflejan lo que la app realmente hace — alineado con
`PRIVACY.md`.

---

## Bloque 1 — "Data collection and security"

### ¿Tu app recopila o comparte alguno de los tipos de datos del usuario requeridos?

> **Respuesta: NO**

Justificación: la app habla únicamente con un servidor que el usuario
configura, y el desarrollador no opera ni recibe copia de ese
servidor. Los datos viajan entre el dispositivo del usuario y un
servidor que el propio usuario administra (o un servidor de un
tercero que el usuario eligió). En la terminología de Play Store eso
NO es "recopilar" — el desarrollador de la app no es el destinatario
de los datos.

### ¿Tus datos están cifrados en tránsito?

> **Respuesta: SÍ**

Justificación: la app fuerza HTTPS para servidores accesibles desde
internet. Para servidores en LAN el operador puede usar HTTP plano si
elige; la app no lo bloquea (el escenario casa-con-HubPlay-en-NAS es
intencional). La política se documenta en la app.

### ¿Los usuarios pueden solicitar la eliminación de sus datos?

> **Respuesta: SÍ**

Justificación:
- En la propia app: Ajustes → Cerrar sesión (borra tokens) o Cambiar
  servidor (borra tokens + URL).
- Desinstalar la app borra todo lo local.
- Para datos almacenados en el servidor HubPlay del usuario: lo
  gestiona el operador de ese servidor (que normalmente ES el propio
  usuario en setups self-hosted).

---

## Bloque 2 — "Data types"

Cuando Google te pregunte por categorías concretas, marca todas como
"Not collected" — la app no transmite ningún dato al desarrollador.

Posibles preguntas trampa por las que NO debes marcar nada como
"collected":

- **Personal info** (nombre, email, …): la app guarda credenciales
  LOCALMENTE para autenticarse contra el servidor del usuario, pero
  no las envía a un servidor del desarrollador.
- **Financial info**: ninguno.
- **Health & fitness**: ninguno.
- **Messages**: ninguno.
- **Photos & videos**: la app REPRODUCE vídeo del servidor del
  usuario, pero no accede a la galería ni a la cámara del dispositivo.
- **Audio**: igual que vídeo.
- **Files & docs**: ninguno.
- **Calendar, contacts**: ninguno.
- **App activity / interactions**: ninguno (sin analytics).
- **Web browsing**: ninguno.
- **App info & performance / crash logs / diagnostics**: la app
  guarda crash logs LOCALMENTE para soporte; no se envían al
  desarrollador. Marca "Not collected".
- **Device or other IDs**: la app envía un nombre legible
  ("HubPlay-Android") al servidor del usuario para identificar la
  sesión, pero NO envía Android ID, IMEI ni Advertising ID.

---

## Bloque 3 — "Security practices"

### "Is data encrypted in transit?"
- **Yes**. HTTPS por defecto, HTTP sólo permitido para servidores LAN
  declarados por el usuario.

### "Can users request that their data be deleted?"
- **Yes**, como se explica arriba.

### "Do you commit to follow the Play Families Policy?"
- **No aplica** salvo que vayas a clasificar como para niños.

### "Has your app been independently validated against a global security standard?"
- **No** (responde "No" a no ser que tengas un audit formal).

---

## Bloque 4 — Política de privacidad

Aquí pegas la URL pública donde alojas `docs/PRIVACY.md` (GitHub
Pages funciona bien). Si la URL es 404 o no responde, Google rechaza
la app.

**Tip**: prueba la URL en incógnito antes de pegarla. Y mantén la
política con la misma fecha de revisión que el `versionName` que
subes; los reviewers a veces comparan.

---

## Bloque 5 — Otros formularios obligatorios pre-publicación

Mientras estás en Política, Google te pide:

| Sección | Respuesta corta |
| --- | --- |
| **Anuncios** | No. La app no muestra ads. |
| **Acceso al contenido** | Restringido — requiere credenciales del servidor. Marca "Sí, requiere login". |
| **Clasificación por edades** | Cuestionario IARC. Honestidad: la app permite reproducir lo que sea que esté en el servidor, así que el rating depende del contenido. Para servidores con películas y series modernas → ESRB Teen / PEGI 12 suele ser razonable. |
| **Categoría** | Reproductores y editores de vídeo |
| **Tipo de app** | Aplicación |
| **Gratis o de pago** | Gratis |
| **Países** | Marca al menos España + EU + Andorra para empezar. Globally rollouts pueden esperar. |
| **Permisos sensibles** | Ninguno declarado; no llega a pedirte justificación. |

---

## Bloque 6 — Lista de assets pre-publicación

Lo que te va a pedir Play Console y NO tienes que sacar de la app:

- [ ] **Icono** 512×512 PNG, sin transparencia en el cuadrado.
- [ ] **Imagen destacada** 1024×500 PNG.
- [ ] **Capturas de pantalla**:
  - Teléfono: mínimo 2, máximo 8. Mínimo 320 px lado corto.
  - Tablet 7": opcional pero recomendable.
  - Tablet 10": opcional.
  - Android TV (Leanback): mínimo 1 si declaras leanback (lo haces).
- [ ] **Descripción corta** (máx 80 chars).
- [ ] **Descripción completa** (máx 4 000 chars). Plantilla en
      `docs/PLAY_STORE_LISTING.md` (TODO crear cuando llegue el
      momento).
- [ ] **URL política de privacidad** — URL pública de `PRIVACY.md`.

---

## Bloque 7 — Closed Testing antes de Producción

Política nueva de 2024: para developer accounts personales nuevos,
Google exige:
- Al menos **14 testers únicos** en un track Closed Testing.
- **14 días continuados** con la app instalada y testeada por esos
  usuarios.
- Antes de poder solicitar promoción a Producción.

No es opcional — sin esos 14×14 la promoción se queda en pendiente.
Empieza el closed testing pronto aunque la app esté a medias.
