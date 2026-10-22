"""
¿Cuánto se equivoca la profundidad SUAVE de un cuadro A, y de qué depende?
La verdad son los puntos de profundidad CRUDA confiable de OTRO cuadro B (misma escena, otro instante): cada punto se lleva
al cuadro A y se compara con lo que dice la suave de A en ese píxel. Se separa por distancia y por qué tan lejos está el píxel
del dato crudo confiable más cercano de A (si el error crece con esa distancia, conviene pesar la suave según su cercanía).

Uso: python smooth_error_vs_raw.py <registro.bin> [salto_min] [salto_max] [--validos <valid-frames.txt>]
(con --validos solo se usan los cuadros con la pose de su marca de tiempo, ver frame_validity.py)
"""
import sys
from pathlib import Path

import cv2
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
    jmin = int(args[1]) if len(args) > 1 else 4
    jmax = int(args[2]) if len(args) > 2 else 14
    rng = np.random.default_rng(5)
    zb = [(0.5, 1.5), (1.5, 2.5), (2.5, 4.0), (4.0, 6.0)]
    db = [(0, 3), (3, 8), (8, 20), (20, 1e9)]
    rel = {(a, b): [] for a in range(len(zb)) for b in range(len(db))}
    excluded = 0
    total = 0
    n = len(frames)
    for ia in range(0, n, 5):
        if valid is not None and ia not in valid:
            continue
        A = frames[ia]
        if A.smooth is None or (A.smooth > 0).mean() < 0.5:
            continue
        # distancia en píxeles al dato crudo confiable más cercano de A
        mask = np.ones((A.h, A.w), np.uint8)
        if A.raw is not None and A.conf is not None:
            mask[(A.raw > 0) & (A.conf >= 90)] = 0
        if mask.all():
            dist = np.full((A.h, A.w), 999.0, np.float32)
        else:
            dist = cv2.distanceTransform(mask, cv2.DIST_L2, 3)
        RA = A.r @ FLIP
        for off in range(jmin, jmax + 1, 3):
            ib = ia + off
            if ib >= n:
                break
            if valid is not None and ib not in valid:
                continue
            B = frames[ib]
            if B.raw is None or B.conf is None:
                continue
            vs, us = np.nonzero((B.raw > 0) & (B.conf >= 90))
            if len(vs) == 0:
                continue
            sel = rng.permutation(len(vs))[:80]
            vs, us = vs[sel], us[sel]
            z = B.raw[vs, us] / 1000.0
            pc = np.stack([(us + 0.5 - B.cx) / B.fx * z, (vs + 0.5 - B.cy) / B.fy * z, z], axis=1)
            RB = B.r @ FLIP
            pw = (RB @ pc.T).T + B.t
            pa = (pw - A.t) @ RA
            za = pa[:, 2]
            ok = za > 0.4
            ua = np.floor(A.fx * pa[:, 0] / np.where(ok, za, 1) + A.cx).astype(int)
            va = np.floor(A.fy * pa[:, 1] / np.where(ok, za, 1) + A.cy).astype(int)
            ok &= (ua >= 0) & (ua < A.w) & (va >= 0) & (va < A.h)
            for k in np.nonzero(ok)[0]:
                s = A.smooth[va[k], ua[k]] / 1000.0
                if s <= 0:
                    continue
                total += 1
                if s < za[k] * 0.80:  # A ve algo más cerca: probable oclusión, no error de la suave
                    excluded += 1
                    continue
                zi = next((i for i, (lo, hi) in enumerate(zb) if lo <= za[k] < hi), None)
                if zi is None:
                    continue
                d = dist[va[k], ua[k]]
                di = next(i for i, (lo, hi) in enumerate(db) if lo <= d < hi)
                rel[(zi, di)].append(s / za[k] - 1.0)
    print("pares comparados: %d | excluidos por probable oclusión (A más cerca >20 %%): %d" % (total, excluded))
    print("error relativo de la suave (suave/verdad − 1): mediana [p10, p90] y %% con |error| > 10 %%")
    print("%-14s" % "z (m) \\ px al crudo" + "".join("%-34s" % ("%d–%d px" % (lo, min(hi, 99)) if hi < 1e8 else ">%d px" % lo) for lo, hi in db))
    for a, (lo, hi) in enumerate(zb):
        row = "%-14s" % ("%.1f–%.1f" % (lo, hi))
        for b in range(len(db)):
            e = np.array(rel[(a, b)])
            if len(e) < 20:
                row += "%-34s" % ("(n=%d)" % len(e))
            else:
                row += "%-34s" % ("%+5.1f%% [%+5.1f,%+5.1f] >10%%: %2.0f%% n=%d" % (100 * np.median(e), 100 * np.percentile(e, 10), 100 * np.percentile(e, 90), 100 * (np.abs(e) > 0.10).mean(), len(e)))
        print(row)


if __name__ == "__main__":
    main()
