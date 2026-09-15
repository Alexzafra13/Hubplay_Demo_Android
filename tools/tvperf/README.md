# tvperf — medir en la Mi TV sin adivinar

Scripts usados en la sesión del 2026-09-15 para diagnosticar la ficha.
Todos asumen `adb` en el PATH y la TV en `192.168.1.132:5555` (`D=`).

| Script | Qué hace |
|---|---|
| `dump.sh [focus]` | Vuelca el árbol de accesibilidad (`uiautomator dump`) y lista nodos con texto/desc; con `focus` solo el nodo con foco. Es la única forma fiable de saber en qué pantalla estás y dónde está el foco: `screencap` devuelve frames viejos o blancos mientras hay vídeo por hardware. |
| `fs.py <framestats.txt>` | Desglosa `dumpsys gfxinfo <pkg> framestats` por fases (espera de hilo principal + recomposición, animación, measure/layout/draw, GPU). Usar con `awk '$NF+0 > 20'` para ver solo los frames pesados. |
| `trace.py <trace.bin>` | Parsea un `atrace --async_stop -z` y lista los tramos más largos del hilo principal (`Recomposer:recompose`, `AndroidOwner:measureAndLayout`, `Compose:onForgotten`…). |
| `arttrace2.py <x.trace> <regex-incluir> [regex-excluir] [n]` | Parsea un perfil de muestreo (`am profile start --sampling 500`) y da tiempo inclusivo por método sin contar recursión. Con `'^com\.alex\.hubplay'` dice qué composables pesan. |

Flujo típico:

```bash
D=192.168.1.132:5555
adb -s $D shell dumpsys gfxinfo com.alex.hubplay.debug reset
adb -s $D shell input keyevent KEYCODE_DPAD_CENTER; sleep 4
adb -s $D shell dumpsys gfxinfo com.alex.hubplay.debug framestats > fs.txt
python tools/tvperf/fs.py fs.txt | awk 'NR==1 || $NF+0 > 20'

# perfil de métodos de una acción concreta (en frío: force-stop antes)
adb -s $D shell am profile start --sampling 500 com.alex.hubplay.debug /data/local/tmp/p.trace
adb -s $D shell input keyevent KEYCODE_DPAD_CENTER; sleep 3
adb -s $D shell am profile stop com.alex.hubplay.debug; sleep 2
adb -s $D pull /data/local/tmp/p.trace p.trace
python tools/tvperf/arttrace2.py p.trace '^com\.alex\.hubplay' '' 30
```

Trampas: `input tap` pone la TV en modo táctil y rompe las pruebas de
foco (usar solo D-pad); si el usuario está usando el mando a la vez las
pantallas cambian solas; en Git Bash usar `MSYS_NO_PATHCONV=1` con rutas
`/sdcard/...`.
