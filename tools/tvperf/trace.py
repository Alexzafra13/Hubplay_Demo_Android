r"""Tramos más largos del hilo principal en un atrace (`atrace --async_stop -z`).

Uso: python trace.py trace.bin
Captura: adb shell atrace --async_start -b 32000 -a com.alex.hubplay.debug gfx view input webview am
         ... acción ...
         adb exec-out atrace --async_stop -z -o /dev/stdout > trace.bin
"""
import collections
import re
import sys
import zlib

raw = open(sys.argv[1], "rb").read()
i = raw.index(b"TRACE:\n") + 7
txt = zlib.decompress(raw[i:]).decode("utf-8", "replace")
line_re = re.compile(
    r"^\s*(.*?)-(\d+)\s+\(\s*(\d+|-+)\)\s+\[(\d+)\].*?\s(\d+\.\d+): tracing_mark_write: ([BEC])\|(\d+)\|?(.*)$"
)
stacks = collections.defaultdict(list)
slices = []
for line in txt.splitlines():
    m = line_re.match(line)
    if not m:
        continue
    tname, tid, _, _, ts, kind, ppid, rest = m.groups()
    ts, tid, ppid = float(ts), int(tid), int(ppid)
    if kind == "B":
        stacks[tid].append((rest, ts, len(stacks[tid])))
    elif kind == "E" and stacks[tid]:
        name, t0, depth = stacks[tid].pop()
        slices.append((tid, ppid, tname.strip(), name, t0, ts - t0, depth))
cands = collections.Counter(s[1] for s in slices if s[3].startswith("Choreographer#doFrame") and s[0] == s[1])


def score(p):
    return sum(1 for s in slices if s[1] == p and ("Compose" in s[3] or "AndroidOwner" in s[3]))


app = max(cands, key=score)
t0 = min(s[4] for s in slices)
main = [s for s in slices if s[1] == app and s[0] == app]
print("pid", app, "- tramos más largos del hilo principal:")
for s in sorted(main, key=lambda s: -s[5])[:40]:
    print("  %-70s %7.1f ms d%d t=%.3f" % (s[3][:70], s[5] * 1000, s[6], s[4] - t0))
print("otros hilos:")
for s in sorted([s for s in slices if s[1] == app and s[0] != app], key=lambda s: -s[5])[:10]:
    print("  %-16s %-50s %7.1f ms t=%.3f" % (s[2][:16], s[3][:50], s[5] * 1000, s[4] - t0))
