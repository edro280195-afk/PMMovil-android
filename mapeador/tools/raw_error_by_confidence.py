"""
¿Qué tan fiel es la profundidad CRUDA según su confianza? Los puntos de cruda con confianza >= 150 de un cuadro B (otro
instante) son la verdad; se llevan al cuadro A y se comparan con la cruda de A en ese píxel, separada por la confianza de A.
Sirve para decidir si bajar el umbral de confianza (90) suma anclas útiles.

Uso: python raw_error_by_confidence.py <registro.bin> [--validos <valid-frames.txt>]
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import depthlog as dl

FLIP = np.diag([1.0, -1.0, -1.0])


def main():
    args = [a for a in sys.argv[1:]]
    valid = None
    if "--validos" in args:
        k = args.index("--validos")
        valid = set(int(x) for x in Path(args[k + 1]).read_text().split(",") if x)
        del args[k:k + 2]
    frames = dl.read_log(args[0])
    rng = np.random.default_rng(7)
    cb = [(1, 20), (20, 50), (50, 90), (90, 150), (150, 256)]
    rel = {i: [] for i in range(len(cb))}
    n = len(frames)
    for ia in range(0, n, 4):
        if valid is not None and ia not in valid:
            continue
        A = frames[ia]
        if A.raw is None or A.conf is None:
            continue
        RA = A.r @ FLIP
        for off in (4, 7, 10):
            ib = ia + off
            if ib >= n:
                break
            if valid is not None and ib not in valid:
                continue
            B = frames[ib]
            if B.raw is None or B.conf is None:
                continue
            vs, us = np.nonzero((B.raw > 0) & (B.conf >= 150))
            if len(vs) == 0:
                continue
            sel = rng.permutation(len(vs))[:100]
            vs, us = vs[sel], us[sel]
            z = B.raw[vs, us] / 1000.0
            pc = np.stack([(us + 0.5 - B.cx) / B.fx * z, (vs + 0.5 - B.cy) / B.fy * z, z], axis=1)
            pw = ((B.r @ FLIP) @ pc.T).T + B.t
            pa = (pw - A.t) @ RA
            za = pa[:, 2]
            ok = za > 0.5
            ua = np.floor(A.fx * pa[:, 0] / np.where(ok, za, 1) + A.cx).astype(int)
            va = np.floor(A.fy * pa[:, 1] / np.where(ok, za, 1) + A.cy).astype(int)
            ok &= (ua >= 0) & (ua < A.w) & (va >= 0) & (va < A.h) & (za < 6)
            for k in np.nonzero(ok)[0]:
                r = A.raw[va[k], ua[k]]
                c = int(A.conf[va[k], ua[k]])
                if r <= 0 or c < 1:
                    continue
                rz = r / 1000.0
                if rz < za[k] * 0.8:
                    continue  # probable oclusión
                bi = next(i for i, (lo, hi) in enumerate(cb) if lo <= c < hi)
                rel[bi].append(rz / za[k] - 1.0)
    print("error relativo de la cruda de A frente a la cruda confiable (>=150) de B, por confianza de A")
    for i, (lo, hi) in enumerate(cb):
        e = np.array(rel[i])
        if len(e) < 20:
            print("  conf %3d–%3d: n=%d" % (lo, hi - 1, len(e)))
            continue
        print("  conf %3d–%3d: n=%5d | mediana %+5.1f %% | |error| mediano %4.1f %% | >10 %%: %2.0f %% | >25 %%: %2.0f %%" % (
            lo, hi - 1, len(e), 100 * np.median(e), 100 * np.median(np.abs(e)), 100 * (np.abs(e) > 0.10).mean(), 100 * (np.abs(e) > 0.25).mean()))


if __name__ == "__main__":
    main()
