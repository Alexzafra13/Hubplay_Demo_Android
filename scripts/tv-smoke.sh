#!/usr/bin/env bash
#
# tv-smoke.sh — drive HubPlay on a connected Android TV emulator/device via
# adb, capture a numbered screenshot at every step, dump logcat, and flag
# crashes/ANRs. A cheap "did the flow break?" pass you can eyeball afterwards.
#
# It does NOT assert UX quality — it proves the app launches, survives a
# D-pad tour without crashing, and gives you a visual + log record to review.
# Pair it with a REAL HubPlay server already paired on the device (or pair
# interactively when the script pauses) to exercise Home/Player.
#
# Usage:
#   scripts/tv-smoke.sh                 # drive the already-installed app
#   scripts/tv-smoke.sh --install       # assembleDebug + install first
#   scripts/tv-smoke.sh --serial <id>   # target a specific adb device
#
# Requirements: adb on PATH, exactly one device (or --serial), app installed
# (or --install). Output lands in build/tv-smoke/<timestamp>/.

set -uo pipefail

PKG="com.alex.hubplay"
ACTIVITY="$PKG/.MainActivity"
SERIAL=""
DO_INSTALL=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --install) DO_INSTALL=1; shift ;;
    --serial)  SERIAL="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,22p' "$0"; exit 0 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done

ADB=(adb)
[[ -n "$SERIAL" ]] && ADB=(adb -s "$SERIAL")

# ── preflight ────────────────────────────────────────────────────────────────
command -v adb >/dev/null || { echo "adb not found on PATH" >&2; exit 1; }
DEV_COUNT=$(adb devices | grep -cE "\bdevice$")
if [[ -z "$SERIAL" && "$DEV_COUNT" -ne 1 ]]; then
  echo "Expected exactly 1 adb device (found $DEV_COUNT). Use --serial <id>." >&2
  adb devices >&2
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TS="$(date +%Y%m%d-%H%M%S)"
OUT="$ROOT/build/tv-smoke/$TS"
mkdir -p "$OUT"
echo "▶ output: $OUT"

if [[ "$DO_INSTALL" -eq 1 ]]; then
  echo "▶ building + installing debug APK…"
  (cd "$ROOT" && ./gradlew :app:installDebug) || { echo "install failed" >&2; exit 1; }
fi

STEP=0
shot() {        # shot <label>
  STEP=$((STEP + 1))
  local name; name=$(printf "%02d_%s" "$STEP" "$1")
  "${ADB[@]}" exec-out screencap -p > "$OUT/$name.png" 2>/dev/null \
    && echo "  📸 $name.png" \
    || echo "  ⚠ screencap failed at $name"
}
key()  { "${ADB[@]}" shell input keyevent "$1" >/dev/null 2>&1; }  # key <KEYCODE>
pause() { sleep "${1:-1.5}"; }

# D-pad keycodes
UP=19; DOWN=20; LEFT=21; RIGHT=22; CENTER=23; BACK=4; ENTER=66

alive() { [[ -n "$("${ADB[@]}" shell pidof "$PKG" 2>/dev/null | tr -d '\r')" ]]; }

# ── launch ───────────────────────────────────────────────────────────────────
echo "▶ clearing logcat + launching $ACTIVITY"
"${ADB[@]}" logcat -c >/dev/null 2>&1 || true
"${ADB[@]}" shell am force-stop "$PKG" >/dev/null 2>&1 || true
"${ADB[@]}" shell am start -n "$ACTIVITY" >/dev/null 2>&1 \
  || "${ADB[@]}" shell monkey -p "$PKG" -c android.intent.category.LEANBACK_LAUNCHER 1 >/dev/null 2>&1
pause 4
shot "launch"

# ── D-pad tour (edit freely for your screens) ─────────────────────────────────
# Generic exploration: works whether the app opens on Login or Home. The
# point is a visual record + a crash check, not a fixed script.
echo "▶ D-pad tour"
for _ in 1 2; do key "$DOWN"; pause; shot "down"; done
for _ in 1 2; do key "$RIGHT"; pause; shot "right"; done
key "$CENTER"; pause 2.5; shot "center-open"   # open focused item / play
pause 2
shot "after-open"
key "$BACK"; pause; shot "back"
key "$LEFT";  pause; shot "left-sidebar"        # Prime-style sidebar reveal

# ── verdict ──────────────────────────────────────────────────────────────────
echo "▶ collecting logs"
"${ADB[@]}" logcat -d -v time           > "$OUT/logcat-full.txt" 2>/dev/null || true
"${ADB[@]}" logcat -d -b crash -v time  > "$OUT/logcat-crash.txt" 2>/dev/null || true

# App-tagged lines (its own Log.w/e) — handy signal of recoverable errors.
grep -iE " (HubplayApp|HomeRepository|PlayerVM|LoginVM|MeEventsStream|LanDiscovery|CrashLogger):" \
  "$OUT/logcat-full.txt" > "$OUT/logcat-app.txt" 2>/dev/null || true

# grep -c prints "0" and exits 1 when there are no matches; capture the
# number directly (no `|| echo` — that would append a second 0).
CRASHES=$(grep -cE "FATAL EXCEPTION|AndroidRuntime: |ANR in $PKG" "$OUT/logcat-full.txt" 2>/dev/null)
CRASHES=${CRASHES:-0}

echo
echo "──────── verdict ────────"
if alive; then
  echo "✅ process alive after tour"
else
  echo "❌ process DIED during tour (likely crash) — see logcat-crash.txt"
fi
if [[ "$CRASHES" -gt 0 ]]; then
  echo "❌ $CRASHES crash/ANR marker(s) in logcat:"
  grep -nE "FATAL EXCEPTION|AndroidRuntime: |ANR in $PKG" "$OUT/logcat-full.txt" | head -20
else
  echo "✅ no FATAL/ANR markers in logcat"
fi
echo
echo "Review: open the PNGs in $OUT (in order) and skim logcat-app.txt."
echo "Screens captured: $STEP"
