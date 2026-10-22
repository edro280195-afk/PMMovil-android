"""
Qué tan arriba o abajo queda el PISO de cada malla `out/acc-*.bin` respecto de lo que ARCore midió (la cruda confiable de los cuadros
de prueba), en centímetros de ALTURA (no a lo largo del rayo: de lejos el piso se ve rasante y un error pequeño de altura se vuelve
grande a lo largo del rayo). Para cada píxel de cruda confiable que cae sobre el piso (±15 cm de la altura estimada) se compara la
altura del punto medido con la del punto de la malla en ese mismo rayo.

Uso: python floor_error.py <carpeta> [cada_n_cuadros=3] [filtro_de_nombre]     (variable PMM_LOG elige la grabación)
"""
import os
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import depthlog as dl
from eval_variants import FLIP, SCALE, read_mesh, render, triangles_idx


def main():
    root = Path(sys.argv[1])
    every = int(sys.argv[2]) if len(sys.argv) > 2 else 3
    name_filter = sys.argv[3] if len(sys.argv) > 3 else ""
    log = sorted(p for p in root.rglob("profundidad-*.bin") if os.environ.get("PMM_LOG", "") in p.name)[-1]
    test_idx = [int(x) for x in (root / "out" / "acc-test-frames.txt").read_text().split(",") if x]
    frames = dl.read_log(str(log))
    use = [frames[i] for i in test_idx[::every]]
    print("%s | cuadros de prueba usados: %d" % (log.name, len(use)))
    for mesh in sorted((root / "out").glob("acc-*.bin")):
        if name_filter and name_filter not in mesh.name:
            continue
        pos, quads = read_mesh(mesh)
        if len(quads) == 0:
            continue
        tris = triangles_idx(quads)
        v0, v1, v2 = pos[tris[:, 0]], pos[tris[:, 1]], pos[tris[:, 2]]
        nrm = np.cross(v1 - v0, v2 - v0)
        ny = np.abs(nrm[:, 1]) / (np.linalg.norm(nrm, axis=1) + 1e-12)
        cy = (v0[:, 1] + v1[:, 1] + v2[:, 1]) / 3
        floor_y = np.percentile(cy[ny > 0.8], 8)
        dh, dist = [], []
        for f in use:
            if f.raw is None or f.conf is None:
                continue
            img = render(f, pos, tris)
            vs, us = np.nonzero((f.raw > 0) & (f.conf >= 91))
            z = f.raw[vs, us] / 1000.0
            keep = (z >= 0.4) & (z <= 6.0)
            vs, us, z = vs[keep], us[keep], z[keep]
            zm = img[vs * SCALE + SCALE // 2, us * SCALE + SCALE // 2]
            ok = zm > 0
            vs, us, z, zm = vs[ok], us[ok], z[ok], zm[ok]
            R = f.r @ FLIP

            def world(depth):
                pc = np.stack([(us + 0.5 - f.cx) / f.fx * depth, (vs + 0.5 - f.cy) / f.fy * depth, depth], axis=1)
                return (R @ pc.T).T + f.t

            pt, pm = world(z), world(zm)
            on = np.abs(pt[:, 1] - floor_y) < 0.15
            dh.extend((pm[on, 1] - pt[on, 1]).tolist())
            dist.extend(np.hypot(pt[on, 0] - f.t[0], pt[on, 2] - f.t[2]).tolist())
        dh = np.array(dh) * 100
        dist = np.array(dist)
        print("%-52s | piso y=%.2f | error de altura (malla − medida): mediana %+5.1f cm, p10 %+5.1f, p90 %+5.1f | ±3 cm: %2.0f %%, ±5 cm: %2.0f %%, >10 cm arriba: %2.0f %%" % (
            mesh.stem[4:][:52], floor_y, np.median(dh), np.percentile(dh, 10), np.percentile(dh, 90), 100 * (np.abs(dh) <= 3).mean(), 100 * (np.abs(dh) <= 5).mean(), 100 * (dh > 10).mean()))
        for lo, hi in ((0, 1.5), (1.5, 2.5), (2.5, 4.0)):
            m = (dist >= lo) & (dist < hi)
            if m.sum() > 100:
                print("     a %.1f–%.1f m: mediana %+5.1f cm, ±5 cm: %3.0f %%, >10 cm arriba: %3.0f %%" % (lo, hi, np.median(dh[m]), 100 * (np.abs(dh[m]) <= 5).mean(), 100 * (dh[m] > 10).mean()))


if __name__ == "__main__":
    main()
