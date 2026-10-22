"""
Dibuja la malla escaneada vista desde una cámara, sobre las fotos reales del mapa, para revisar en la PC cómo se
ve y si cuadra con el mundo. Imita el sombreador del teléfono: la superficie más cercana tapa a la lejana, relleno
translúcido por orientación (piso azul, muro turquesa, techo violeta), cuadrícula y desvanecido con la distancia.

Lo llama `ReplayTest` (o se corre a mano):

    python render_mesh.py <carpeta-con-views.json> [archivo-de-vistas.json]

`views.json` es una lista de vistas {name, mesh, q:[x,y,z,w], t:[x,y,z], k:{fx,fy,cx,cy,width,height}, image|null}
y cada `mesh` es un `mesh.bin` (formato PMMH de MeshIO). Las imágenes salen en <carpeta>/png/.
"""
import json
import math
import struct
import sys
from pathlib import Path

import cv2
import numpy as np

FLOOR = np.array([1.00, 0.56, 0.22])  # BGR
WALL = np.array([0.88, 0.90, 0.10])
CEIL = np.array([1.00, 0.48, 0.70])


def read_mesh(path: Path):
    data = path.read_bytes()
    magic, version, n, q, voxel = struct.unpack_from("<iiiif", data, 0)
    if magic != 0x484D4D50:
        raise ValueError(f"{path} no es una malla de este programa")
    off = 20
    pos = np.frombuffer(data, dtype="<f4", count=n * 3, offset=off).reshape(n, 3).astype(np.float64)
    off += n * 12
    nor = np.frombuffer(data, dtype=np.int8, count=n * 3, offset=off).reshape(n, 3).astype(np.float64) / 127.0
    off += n * 3
    quads = np.frombuffer(data, dtype="<i4", count=q * 4, offset=off).reshape(q, 4)
    return pos, nor, quads


def quat_to_matrix(x, y, z, w):
    n = math.sqrt(x * x + y * y + z * z + w * w)
    x, y, z, w = x / n, y / n, z / n, w / n
    return np.array([
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)],
    ])


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def render(mesh, view, out_w=960, fill_alpha=0.32, line_alpha=0.85):
    pos, nor, quads = mesh
    k = view["k"]
    bg = None
    if view.get("image"):
        bg = cv2.imread(view["image"], cv2.IMREAD_COLOR)
    h_img = k["height"]
    w_img = k["width"]
    out_h = int(round(out_w * h_img / w_img))
    if bg is None:
        bg = np.full((out_h, out_w, 3), 46, np.uint8)
    else:
        bg = cv2.resize(bg, (out_w, out_h), interpolation=cv2.INTER_AREA)

    s = out_w / w_img
    fx, fy, cx, cy = k["fx"] * s, k["fy"] * s, k["cx"] * s, k["cy"] * s
    r_gl = quat_to_matrix(*view["q"])
    t = np.array(view["t"])
    flip = np.diag([1.0, -1.0, -1.0])
    cam = (flip @ r_gl.T @ (pos - t).T).T  # cuerpo OpenCV: x derecha, y abajo, z adelante
    z = cam[:, 2]
    with np.errstate(divide="ignore", invalid="ignore"):
        u = fx * cam[:, 0] / z + cx
        v = fy * cam[:, 1] / z + cy
    ok = z > 0.05
    dist = np.linalg.norm(pos - t, axis=1)

    # Cuadriláteros visibles: sus 4 vértices delante de la cámara y la cara a menos de 6 m.
    qa = quads
    good = ok[qa].all(axis=1)
    qdist = dist[qa].mean(axis=1)
    good &= qdist < 6.0
    idx = np.nonzero(good)[0]
    order = idx[np.argsort(-qdist[idx])]  # de lejos a cerca (algoritmo del pintor)

    # Lienzos de 8 bits (así OpenCV dibuja con suavizado): color y opacidad; lo más cercano se pinta al final.
    color = np.zeros((out_h, out_w, 3), np.uint8)
    alpha = np.zeros((out_h, out_w), np.uint8)
    for qi in order:
        vs = qa[qi]
        n = nor[vs].mean(axis=0)
        nl = np.linalg.norm(n)
        if nl < 1e-9:
            continue
        n = n / nl
        tc = t - pos[vs[0]]
        tl = max(np.linalg.norm(tc), 0.05)
        ndv = abs(float(np.dot(n, tc)) / tl)
        light = 0.55 + 0.45 * ndv
        if n[1] > 0:
            base = WALL + (FLOOR - WALL) * float(smoothstep(0.25, 0.8, n[1]))
        else:
            base = WALL + (CEIL - WALL) * float(smoothstep(0.25, 0.8, -n[1]))
        base = base * light
        fade = 1.0 - float(smoothstep(3.2, 5.5, tl))
        if fade <= 0.01:
            continue
        pts = np.stack([u[vs], v[vs]], axis=1)
        if not np.isfinite(pts).all() or np.abs(pts).max() > 20000:
            continue
        poly = np.round(pts * 16).astype(np.int32)  # 4 bits de subpíxel
        fill_color = tuple(int(min(c, 1.0) * 255) for c in base)
        cv2.fillConvexPoly(color, poly, fill_color, lineType=cv2.LINE_AA, shift=4)
        cv2.fillConvexPoly(alpha, poly, int(round(255 * fill_alpha * fade)), lineType=cv2.LINE_AA, shift=4)
        line_color = tuple(int(min(c * 1.25, 1.0) * 255) for c in base)
        for a, b in ((0, 1), (1, 2), (2, 3), (3, 0)):
            p0 = tuple(int(round(c * 16)) for c in pts[a])
            p1 = tuple(int(round(c * 16)) for c in pts[b])
            cv2.line(color, p0, p1, line_color, 1, cv2.LINE_AA, 4)
            cv2.line(alpha, p0, p1, int(round(255 * line_alpha * fade)), 1, cv2.LINE_AA, 4)

    a3 = alpha.astype(np.float32)[:, :, None] / 255.0
    result = bg.astype(np.float32) * (1 - a3) + color.astype(np.float32) * a3
    return np.clip(result, 0, 255).astype(np.uint8)


def main():
    folder = Path(sys.argv[1])
    views_file = sys.argv[2] if len(sys.argv) > 2 else "views.json"
    views = json.loads((folder / views_file).read_text(encoding="utf-8"))
    png = folder / "png"
    png.mkdir(exist_ok=True)
    meshes = {}
    for view in views:
        name = view["mesh"]
        if name not in meshes:
            meshes[name] = read_mesh(folder / name)
        img = render(meshes[name], view)
        cv2.imwrite(str(png / f"{view['name']}.png"), img)
        print(f"{view['name']}.png  ({name}, {len(meshes[name][2])} cuadriláteros)")


if __name__ == "__main__":
    main()
