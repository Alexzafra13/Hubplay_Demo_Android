"""Desglosa `dumpsys gfxinfo <pkg> framestats` por fases (ms).

Uso: python fs.py framestats.txt
Columnas: t desde el primer frame, flags (1 = ventana secundaria, p. ej. un
Dialog), espera del hilo principal + recomposición, animación, traversal,
measure/layout/draw (Compose mide y dibuja dentro de dispatchDraw), GPU y
total. Filtrar los pesados: `| awk 'NR==1 || $NF+0 > 20'`.
"""
import sys

rows = []
started = False
for line in open(sys.argv[1]):
    if line.startswith("Flags,"):
        started = True
        continue
    if started and line.strip() and line[0].isdigit() and "," in line and "percentile" not in line:
        f = [int(x) for x in line.strip().strip(",").split(",")]
        if len(f) < 14:
            continue
        vs, anim, trav, draw, sync, issue, swap, done = f[1], f[6], f[7], f[8], f[10], f[11], f[12], f[13]
        rows.append((f[0], (anim - vs) / 1e6, (trav - anim) / 1e6, (draw - trav) / 1e6,
                     (sync - draw) / 1e6, (swap - issue) / 1e6, (done - vs) / 1e6, vs))
t0 = rows[0][7] if rows else 0
print("  t(ms) flags input+recomp  anim  trav  draw(meas/lay/draw)  gpu  total")
for r in rows:
    print("%6.0f %5d %9.1f %8.1f %5.1f %10.1f %14.1f %6.1f" % ((r[7] - t0) / 1e6, r[0], r[1], r[2], r[3], r[4], r[5], r[6]))
