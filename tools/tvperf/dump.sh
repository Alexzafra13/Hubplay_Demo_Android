#!/usr/bin/env bash
# Árbol de accesibilidad de la TV: nodos con texto/desc, o solo el foco.
# Uso: tools/tvperf/dump.sh [focus]
D="${D:-192.168.1.132:5555}"
OUT="${TMPDIR:-/tmp}/hubplay-ui.xml"
MSYS_NO_PATHCONV=1 adb -s "$D" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
MSYS_NO_PATHCONV=1 adb -s "$D" pull /sdcard/ui.xml "$OUT" >/dev/null 2>&1
python "$(dirname "$0")/dump.py" "$OUT" "$@"
