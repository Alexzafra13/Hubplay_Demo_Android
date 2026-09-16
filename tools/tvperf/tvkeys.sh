#!/usr/bin/env bash
# Teclas al mando SOLO si HubPlay está en primer plano. Uso: source tvkeys.sh; k KEYCODE_X [sleep]
D="${D:-192.168.1.132:5555}"
front() { adb -s "$D" shell dumpsys window | grep -o "mCurrentFocus=.*" | grep -c "com.alex.hubplay"; }
k() {
  if [ "$(front)" = "0" ]; then
    echo "!! HubPlay NO está en primer plano; tecla $1 NO enviada"; adb -s "$D" shell dumpsys window | grep -o "mCurrentFocus=.*" | cut -c1-90; return 1
  fi
  adb -s "$D" shell input keyevent "$1"; sleep "${2:-1.5}"
}
launch() {
  adb -s "$D" shell am force-stop com.alex.hubplay.debug
  adb -s "$D" shell am start -n com.alex.hubplay.debug/com.alex.hubplay.MainActivity >/dev/null 2>&1
  for i in $(seq 1 12); do sleep 1; [ "$(front)" != "0" ] && { sleep 9; echo "HubPlay en primer plano"; return 0; }; done
  echo "!! HubPlay no llegó al primer plano"; return 1
}
