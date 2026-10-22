"""
Mide qué tan bien cae cada malla sobre lo que ARCore midió de verdad, con mediciones que la malla NO vio al armarse.

`ReplayTest.exploreAnchoredOnRealDepth` (con PMM_EXPLORE=1 y PMM_REPLAY_DIR) arma varias variantes de la malla con la mitad de
los cuadros de la grabación (bloques de 8 cuadros que alternan entrenamiento y prueba) y deja en `<carpeta>/out/` las mallas
`acc-*.bin` y la lista de cuadros de prueba. Este programa dibuja cada malla desde cada cuadro de prueba como una imagen de
profundidad (algoritmo del pintor con triángulos de profundidad constante, al cuádruple de la resolución de la imagen de
profundidad) y la compara píxel por píxel contra la profundidad CRUDA de ese cuadro con confianza >= TRUTH_CONF (91 por omisión;
con 180 la «verdad» es más estricta):

  bien / delante / detras / hueco : sobre los píxeles de cruda confiable. Tolerancia max(10 cm, 6 %).
                                    «delante» = la malla está más cerca de la cámara que la superficie medida (flota);
                                    «detras»  = la superficie medida queda por delante de la malla; «hueco» = sin malla.
  precision                       : bien / (todo lo que la malla sí cubre)
  cob.vista                       : parte de los píxeles con cualquier profundidad (0.4–6 m) que tienen malla encima
  err.med                         : error mediano (cm) de los que están «bien»

Uso:   python eval_variants.py <carpeta> [cada_n_cuadros=2] [filtro_de_nombre]   (variables: TRUTH_CONF, MIN_ISLAND, PMM_LOG)
Ojo:   la verdad solo existe donde ARCore midió (zonas con textura): una malla puede verse bien en muros lisos sin que esto
       lo note. Sirve para comparar variantes, no como medida absoluta.
"""
import os
import struct
import sys
from pathlib import Path

import cv2
import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import depthlog as dl

FLIP = np.diag([1.0, -1.0, -1.0])
SCALE = 4
BIN = 0.015
TRUTH_CONF = int(os.environ.get("TRUTH_CONF", "91"))
MIN_ISLAND = int(os.environ.get("MIN_ISLAND", "0"))  # quitar islas más chicas que esto antes de medir (0 = no)


def read_mesh(path):
    """Malla PMMH: posiciones (n, 3) y cuadriláteros (q, 4)."""
    data = Path(path).read_bytes()
    _, _, n, q, _ = struct.unpack_from("<iiiif", data, 0)
    pos = np.frombuffer(data, dtype="<f4", count=n * 3, offset=20).reshape(n, 3).astype(np.float64)
    quads = np.frombuffer(data, dtype="<i4", count=q * 4, offset=20 + n * 12 + n * 3).reshape(q, 4)
    return pos, quads


def triangles_idx(quads):
    a = np.concatenate([quads[:, [0, 1, 2]], quads[:, [0, 2, 3]]])
    ok = (a[:, 0] != a[:, 1]) & (a[:, 1] != a[:, 2]) & (a[:, 0] != a[:, 2])
    return a[ok]


def render(f, pos, tris):
    """Imagen de profundidad (z de cámara, 0 = sin malla) de la malla vista desde el cuadro `f`."""
    R = f.r @ FLIP
    pc = (pos - f.t) @ R
    z = pc[:, 2]
    ok = z > 0.05
    zs = np.where(ok, z, 1.0)
    u = (f.fx * pc[:, 0] / zs + f.cx) * SCALE
    v = (f.fy * pc[:, 1] / zs + f.cy) * SCALE
    W, H = f.w * SCALE, f.h * SCALE
    tok = ok[tris].all(axis=1)
    t = tris[tok]
    pu, pv = u[t], v[t]
    inside = (pu.max(1) >= 0) & (pu.min(1) < W) & (pv.max(1) >= 0) & (pv.min(1) < H)
    t, pu, pv = t[inside], pu[inside], pv[inside]
    zt = z[t].mean(axis=1)
    img = np.zeros((H, W), np.float32)
    if len(t) == 0:
        return img
    q = np.floor(zt / BIN).astype(np.int32)
    poly = np.stack([pu, pv], axis=2).astype(np.int32)
    order = np.argsort(-q, kind="stable")
    q = q[order]
    poly = poly[order]
    bounds = np.nonzero(np.diff(q))[0] + 1
    starts = np.concatenate([[0], bounds])
    ends = np.concatenate([bounds, [len(q)]])
    for s, e in zip(starts, ends):
        cv2.fillPoly(img, list(poly[s:e]), float((q[s] + 0.5) * BIN))
    return img


def main():
    root = Path(sys.argv[1])
    every = int(sys.argv[2]) if len(sys.argv) > 2 else 2
    name_filter = sys.argv[3] if len(sys.argv) > 3 else ""
    log = sorted(p for p in root.rglob("profundidad-*.bin") if os.environ.get("PMM_LOG", "") in p.name)[-1]
    out = root / "out"
    test_idx = [int(x) for x in (out / "acc-test-frames.txt").read_text().split(",") if x]
    frames = dl.read_log(str(log))
    use = [frames[i] for i in test_idx[::every]]
    print("%s | cuadros de prueba usados: %d | verdad: cruda con confianza >= %d" % (log.name, len(use), TRUTH_CONF))
    print("%-58s %7s | %6s %7s %7s %6s | %9s | %9s | %7s" % ("malla", "quads", "bien", "delante", "detras", "hueco", "precision", "cob.vista", "err.med"))
    for mesh in sorted(out.glob("acc-*.bin")):
        if name_filter and name_filter not in mesh.name:
            continue
        pos, quads = read_mesh(mesh)
        if MIN_ISLAND > 0 and len(quads) > 0:
            # Quita las islas de menos de MIN_ISLAND cuadriláteros (confeti suelto) antes de medir.
            from mesh_islands import components
            size, _ = components(len(pos), quads)
            quads = quads[size >= MIN_ISLAND]
        if len(quads) == 0:
            print("%-58s %7d | (vacía)" % (mesh.stem[4:][:58], 0))
            continue
        tris = triangles_idx(quads)
        cnt = dict(bien=0, delante=0, detras=0, hueco=0)
        errs = []
        cov_hit = cov_tot = 0
        for f in use:
            img = render(f, pos, tris)
            if f.raw is not None and f.conf is not None:
                vs, us = np.nonzero((f.raw > 0) & (f.conf >= TRUTH_CONF))
                z = f.raw[vs, us] / 1000.0
                keep = (z >= 0.4) & (z <= 6.0)
                vs, us, z = vs[keep], us[keep], z[keep]
                zm = img[vs * SCALE + SCALE // 2, us * SCALE + SCALE // 2]
                miss = zm <= 0
                cnt["hueco"] += int(miss.sum())
                e = (zm - z)[~miss]
                zz = z[~miss]
                good = np.abs(e) <= np.maximum(0.10, 0.06 * zz)
                cnt["bien"] += int(good.sum())
                cnt["delante"] += int(((e < 0) & ~good).sum())
                cnt["detras"] += int(((e > 0) & ~good).sum())
                errs.extend(e[good].tolist())
            sm = f.smooth if f.smooth is not None else f.raw
            if sm is not None:
                vs, us = np.mgrid[0:f.h:2, 0:f.w:2]
                vs, us = vs.ravel(), us.ravel()
                zc = sm[vs, us] / 1000.0
                keep = (zc >= 0.4) & (zc <= 6.0)
                vs, us = vs[keep], us[keep]
                zm = img[vs * SCALE + SCALE // 2, us * SCALE + SCALE // 2]
                cov_hit += int((zm > 0).sum())
                cov_tot += len(vs)
        tot = max(sum(cnt.values()), 1)
        hit = tot - cnt["hueco"]
        print("%-58s %7d | %5.1f%% %6.1f%% %6.1f%% %5.1f%% | %8.1f%% | %8.1f%% | %+6.1f" % (
            mesh.stem[4:][:58], len(quads), *(100 * cnt[k] / tot for k in ("bien", "delante", "detras", "hueco")),
            100 * cnt["bien"] / max(hit, 1), 100 * cov_hit / max(cov_tot, 1), 100 * float(np.median(errs)) if errs else 0.0))


if __name__ == "__main__":
    main()
