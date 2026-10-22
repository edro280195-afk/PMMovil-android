"""Detecta los atascos de la profundidad de ARCore (imagen suave vacía y marca de tiempo congelada) y los compara con el movimiento."""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import depthlog as dl

for p in sys.argv[1:]:
    frames = dl.read_log(p)
    n = len(frames)
    smooth_valid = np.array([0.0 if f.smooth is None else float((f.smooth > 0).mean()) for f in frames])
    ts = np.array([f.ts for f in frames], dtype=np.float64)
    stalled = smooth_valid < 0.01
    stale = np.zeros(n, bool)
    stale[1:] = ts[1:] == ts[:-1]
    steps = np.zeros(n)
    rots = np.zeros(n)
    for i in range(1, n):
        steps[i] = np.linalg.norm(frames[i].t - frames[i - 1].t) * 100
        cosang = (np.trace(frames[i - 1].r.T @ frames[i].r) - 1) / 2
        rots[i] = np.degrees(np.arccos(np.clip(cosang, -1, 1)))
    print("\n===", Path(p).name, "| cuadros grabados:", n)
    print("  con imagen suave vacía (<1 %% válido): %d (%.0f %%) | con marca de tiempo repetida: %d (%.0f %%)" % (stalled.sum(), 100 * stalled.mean(), stale.sum(), 100 * stale.mean()))
    # tramos
    runs = []
    i = 0
    while i < n:
        if stalled[i]:
            j = i
            while j < n and stalled[j]:
                j += 1
            runs.append((i, j))
            i = j
        else:
            i += 1
    print("  tramos de atasco (cuadros de inicio–fin, largo, paso medio cm/cuadro, giro medio °/cuadro):")
    for a, b in runs:
        print("    %4d–%4d  largo %3d | paso %.1f | giro %.1f" % (a, b - 1, b - a, steps[a:b].mean(), rots[a:b].mean()))
    ok = ~stalled
    print("  movimiento con profundidad OK: paso medio %.1f cm/cuadro, giro medio %.1f °/cuadro (p90 giro %.1f)" % (steps[ok].mean(), rots[ok].mean(), np.percentile(rots[ok], 90)))
    if stalled.any():
        print("  movimiento durante atascos:    paso medio %.1f cm/cuadro, giro medio %.1f °/cuadro (p90 giro %.1f)" % (steps[stalled].mean(), rots[stalled].mean(), np.percentile(rots[stalled], 90)))
