"""
Qué ve quien mira la pantalla: para cada fotograma clave, los puntos de anclaje que ESE fotograma vio (no están tapados) se
comparan con la primera superficie de la malla a lo largo del rayo cámara→ancla. Es una prueba INDEPENDIENTE de la profundidad
de ARCore: los anclas salen de la imagen (ORB triangulado), la malla sale de la profundidad.

  bien     : la malla está a ±10 cm (o ±6 %) del ancla
  delante  : la malla está más cerca de la cámara que el ancla  → superficie que «flota» delante de lo real
  detras   : la malla está más lejos que el ancla               → el muro real queda por delante de la malla
  hueco    : el rayo no cruza ninguna malla

Uso: python anchor_view_error.py <carpeta-del-mapa> <malla.bin> [kf_max]

  <carpeta-del-mapa> trae keyframes.json y landmarks.bin (maps/<id>/ en los datos del teléfono).
  <malla.bin>        es el mesh.bin que guardó el teléfono o una malla de la repetición (out/acc-*.bin).
  kf_max             opcional: solo los fotogramas clave hasta ese índice (para comparar con una malla armada con menos cuadros).

Hay que usar la malla que corresponde a ese mapa (las anclas son de las fotos de ESE mapa). Resultado del 21-oct: la malla guardada
de la v8 coincide con el 55 % y el 57 % de las anclas (la v7: 31–40 %); delante 23–25 %, detrás 15–21 %.
"""
import json
import struct
import sys
from pathlib import Path

import numpy as np


def read_mesh(path):  # noqa: E302
    data = Path(path).read_bytes()
    _, _, n, q, _ = struct.unpack_from("<iiiif", data, 0)
    pos = np.frombuffer(data, dtype="<f4", count=n * 3, offset=20).reshape(n, 3).astype(np.float64)
    quads = np.frombuffer(data, dtype="<i4", count=q * 4, offset=20 + n * 12 + n * 3).reshape(q, 4)
    return pos, quads


def read_landmarks(path):
    buf = Path(path).read_bytes()
    magic, version, p, e, desc = struct.unpack_from("<iiiii", buf, 0)
    off = 20
    pos = np.frombuffer(buf, dtype="<f4", count=p * 3, offset=off).reshape(p, 3).astype(np.float64)
    off += p * 12 + p * 4 + e * desc
    entry_point = np.frombuffer(buf, dtype="<i4", count=e, offset=off)
    off += e * 4
    entry_kf = np.frombuffer(buf, dtype="<i4", count=e, offset=off)
    return pos, entry_point, entry_kf


def triangles(pos, quads):
    a = np.concatenate([quads[:, [0, 1, 2]], quads[:, [0, 2, 3]]])
    ok = (a[:, 0] != a[:, 1]) & (a[:, 1] != a[:, 2]) & (a[:, 0] != a[:, 2])
    a = a[ok]
    return pos[a[:, 0]], pos[a[:, 1]], pos[a[:, 2]]


def first_hit(c, u, v0, e1, e2, cen, rad):
    """Distancia a la primera intersección del rayo (c, u) con los triángulos; inf si no hay."""
    w = cen - c
    t_along = w @ u
    perp = np.linalg.norm(w - t_along[:, None] * u[None, :], axis=1)
    cand = np.nonzero((t_along > 0.05) & (perp < rad + 0.02))[0]
    if len(cand) == 0:
        return np.inf
    p = np.cross(u[None, :], e2[cand])
    det = np.einsum("ij,ij->i", e1[cand], p)
    good = np.abs(det) > 1e-12
    inv = np.where(good, 1.0 / np.where(good, det, 1.0), 0.0)
    tv = c - v0[cand]
    uu = np.einsum("ij,ij->i", tv, p) * inv
    q = np.cross(tv, e1[cand])
    vv = (q @ u) * inv
    tt = np.einsum("ij,ij->i", e2[cand], q) * inv
    hit = good & (uu >= -0.02) & (vv >= -0.02) & (uu + vv <= 1.02) & (tt > 0.05)
    return float(tt[hit].min()) if hit.any() else np.inf


def main():
    mapdir = Path(sys.argv[1])
    mesh = sys.argv[2]
    kf_max = int(sys.argv[3]) if len(sys.argv) > 3 else 10 ** 9
    kfs = json.loads((mapdir / "keyframes.json").read_text(encoding="utf-8"))
    lpos, e_pt, e_kf = read_landmarks(mapdir / "landmarks.bin")
    pos, quads = read_mesh(mesh)
    v0, v1, v2 = triangles(pos, quads)
    e1, e2 = v1 - v0, v2 - v0
    cen = (v0 + v1 + v2) / 3.0
    rad = np.maximum(np.linalg.norm(v0 - cen, axis=1), np.maximum(np.linalg.norm(v1 - cen, axis=1), np.linalg.norm(v2 - cen, axis=1)))
    # radio común (más rápido): el mayor de los triángulos pequeños
    rad = np.minimum(rad, 0.5)

    by_kf = {}
    for e in range(len(e_pt)):
        by_kf.setdefault(int(e_kf[e]), set()).add(int(e_pt[e]))

    bins = [(0.0, 2.0), (2.0, 3.5), (3.5, 8.0)]
    tot = {b: dict(bien=0, delante=0, detras=0, hueco=0) for b in bins}
    errs = {b: [] for b in bins}
    for k, pts in sorted(by_kf.items()):
        if k >= len(kfs) or k > kf_max:
            continue
        c = np.array(kfs[k]["t"])
        for pi in pts:
            a = lpos[pi]
            d = np.linalg.norm(a - c)
            if d < 0.4 or d > 8.0:
                continue
            u = (a - c) / d
            h = first_hit(c, u, v0, e1, e2, cen, rad)
            b = next(bb for bb in bins if bb[0] <= d < bb[1])
            if not np.isfinite(h):
                tot[b]["hueco"] += 1
                continue
            e = h - d
            errs[b].append(e)
            tol = max(0.10, 0.06 * d)
            if abs(e) <= tol:
                tot[b]["bien"] += 1
            elif e < 0:
                tot[b]["delante"] += 1
            else:
                tot[b]["detras"] += 1
    print("malla: %s | triángulos %d | anclas vistas por fotograma (hasta kf %s)" % (Path(mesh).name, len(v0), kf_max if kf_max < 10 ** 8 else "todos"))
    allc = dict(bien=0, delante=0, detras=0, hueco=0)
    for b in bins:
        n = sum(tot[b].values())
        for k in allc:
            allc[k] += tot[b][k]
        if n == 0:
            continue
        ee = np.array(errs[b]) if errs[b] else np.array([0.0])
        print("  %.1f–%.1f m: %5d anclas | bien %4.1f %% | delante %4.1f %% | detrás %4.1f %% | hueco %4.1f %% | error mediano %+5.1f cm (p10 %+5.1f, p90 %+5.1f)" % (
            b[0], b[1], n, *(100 * tot[b][k] / n for k in ("bien", "delante", "detras", "hueco")),
            100 * np.median(ee), 100 * np.percentile(ee, 10), 100 * np.percentile(ee, 90)))
    n = sum(allc.values())
    print("  TODAS:     %5d anclas | bien %4.1f %% | delante %4.1f %% | detrás %4.1f %% | hueco %4.1f %%" % (
        n, *(100 * allc[k] / n for k in ("bien", "delante", "detras", "hueco"))))


if __name__ == "__main__":
    main()
