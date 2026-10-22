"""
Compara los puntos de la fotogrametría (COLMAP, ya alineados a ARCore por `reconstruct_colmap.py`) con la malla en vivo
(un `mesh.bin` del teléfono o una de las mallas que deja la repetición). Son dos mediciones independientes del mismo
lugar: una sale de las fotos y otra de la profundidad estimada de ARCore.

Uso:
    python compare_colmap_live.py <carpeta-del-mapa>/colmap-trabajo <malla-en-vivo.bin>

Imprime qué parte de los puntos de COLMAP cae cerca de la superficie de la malla y qué parte de la malla tiene algún
punto de COLMAP cerca. Los puntos de COLMAP lejanos (> 4 m, con poca separación entre fotos) traen de por sí 10–20 cm
de error de profundidad: no es un veredicto sobre ninguna de las dos mallas, sirve para comparar corridas entre sí.
Solo usa numpy (sin SciPy): la búsqueda del punto más cercano es con una rejilla de 50 cm.
"""
import struct
import sys
from pathlib import Path

import numpy as np

CELL = 0.5
OFFSETS = [(dx, dy, dz) for dx in (-1, 0, 1) for dy in (-1, 0, 1) for dz in (-1, 0, 1)]


def read_mesh(path):
    data = Path(path).read_bytes()
    _, _, n, q, _ = struct.unpack_from("<iiiif", data, 0)
    pos = np.frombuffer(data, dtype="<f4", count=n * 3, offset=20).reshape(n, 3).astype(np.float64)
    quads = np.frombuffer(data, dtype="<i4", count=q * 4, offset=20 + n * 12 + n * 3).reshape(q, 4)
    return pos, quads


def sample_quads(pos, quads, per_m2=400):
    """Puntos repartidos sobre cada cuadrilátero (dos triángulos), para medir la distancia a la superficie y no al vértice."""
    p0, p1, p2, p3 = (pos[quads[:, i]] for i in range(4))
    area = 0.5 * (np.linalg.norm(np.cross(p1 - p0, p2 - p0), axis=1) + np.linalg.norm(np.cross(p2 - p0, p3 - p0), axis=1))
    rng = np.random.default_rng(1)
    counts = np.maximum(1, np.round(area * per_m2).astype(int))
    owner = np.repeat(np.arange(len(quads)), counts)
    u = rng.random((len(owner), 2))
    flip = u.sum(axis=1) > 1
    u[flip] = 1 - u[flip]
    second = rng.random(len(owner)) < 0.5  # segundo triángulo del cuadrilátero
    a = p0[owner]
    b = np.where(second[:, None], p2[owner], p1[owner])
    c = np.where(second[:, None], p3[owner], p2[owner])
    return a + u[:, :1] * (b - a) + u[:, 1:] * (c - a)


def cell_keys(cells):
    off = 1 << 19
    c = cells.astype(np.int64) + off
    return (c[:, 0] << 40) | (c[:, 1] << 20) | c[:, 2]


def nearest(a, b):
    """Distancia de cada punto de `a` al punto más cercano de `b`; infinito si no hay ninguno a menos de 50 cm."""
    cb = np.floor(b / CELL).astype(np.int64)
    order = np.argsort(cell_keys(cb))
    b_sorted = b[order]
    keys_sorted = cell_keys(cb)[order]
    ca = np.floor(a / CELL).astype(np.int64)
    best = np.full(len(a), np.inf)
    for off in OFFSETS:
        qk = cell_keys(ca + np.array(off))
        lo = np.searchsorted(keys_sorted, qk, side="left")
        hi = np.searchsorted(keys_sorted, qk, side="right")
        cnt = hi - lo
        for j in range(int(cnt.max()) if len(cnt) else 0):
            m = np.nonzero(cnt > j)[0]
            d = np.linalg.norm(a[m] - b_sorted[lo[m] + j], axis=1)
            best[m] = np.minimum(best[m], d)
    return best


def main():
    work = Path(sys.argv[1])
    pts = np.load(work / "puntos.npy").astype(np.float64)
    pos, quads = read_mesh(sys.argv[2])
    surf = sample_quads(pos, quads)
    print("malla en vivo: %d vértices, %d cuadriláteros, %d puntos de muestra" % (len(pos), len(quads), len(surf)))
    print("puntos de COLMAP (alineados a ARCore): %d" % len(pts))
    d = nearest(pts, surf)
    for t in (0.05, 0.10, 0.20, 0.50):
        print("  punto de COLMAP a <= %2d cm de la malla en vivo: %5.1f %%" % (t * 100, 100 * (d <= t).mean()))
    capped = np.minimum(d, 0.5)
    print("  mediana %.1f cm | p75 %.1f cm | p90 %.1f cm (los de más de 50 cm cuentan como 50)"
          % (100 * np.median(capped), 100 * np.percentile(capped, 75), 100 * np.percentile(capped, 90)))
    d2 = nearest(surf, pts)
    for t in (0.10, 0.25, 0.50):
        print("  muestra de malla a <= %2d cm de algún punto de COLMAP: %5.1f %%" % (t * 100, 100 * (d2 <= t).mean()))


if __name__ == "__main__":
    main()
