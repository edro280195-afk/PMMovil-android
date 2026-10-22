"""
Vista de planta (desde arriba) de una malla guardada: cada celda de 10 cm se pinta según la altura de lo que hay encima (azul = piso,
verde/amarillo = muebles y muros bajos, rojo = lo alto) y se dibuja el recorrido de la cámara con los fotogramas clave. Sirve para
juzgar la cobertura de un cuarto y ver si hay malla suelta fuera de él.

Uso: python plan_view.py <carpeta-del-mapa> [salida.png]
Imprime también la huella (m²) y las dimensiones de lo cubierto.
"""
import json
import struct
import sys
from pathlib import Path

import cv2
import numpy as np

CELL = 0.10


def read_mesh(path):
    data = Path(path).read_bytes()
    _, _, n, q, _ = struct.unpack_from("<iiiif", data, 0)
    pos = np.frombuffer(data, dtype="<f4", count=n * 3, offset=20).reshape(n, 3).astype(np.float64)
    quads = np.frombuffer(data, dtype="<i4", count=q * 4, offset=20 + n * 12 + n * 3).reshape(q, 4)
    return pos, quads


def main():
    mapdir = Path(sys.argv[1])
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else mapdir / "planta.png"
    pos, quads = read_mesh(mapdir / "mesh.bin")
    kfs = json.loads((mapdir / "keyframes.json").read_text(encoding="utf-8"))
    cam = np.array([k["t"] for k in kfs])
    center = pos[quads].mean(axis=1)  # centro de cada cuadrilátero
    x, y, z = center[:, 0], center[:, 1], center[:, 2]
    # suelo = la altura más baja frecuente: el percentil 3 de y
    floor_y = np.percentile(y, 3)
    h = y - floor_y
    lo_x, hi_x = min(x.min(), cam[:, 0].min()) - 0.5, max(x.max(), cam[:, 0].max()) + 0.5
    lo_z, hi_z = min(z.min(), cam[:, 2].min()) - 0.5, max(z.max(), cam[:, 2].max()) + 0.5
    W = int((hi_x - lo_x) / CELL) + 1
    H = int((hi_z - lo_z) / CELL) + 1
    scale = max(1, int(900 / max(W, H)))
    top = np.full((H, W), -9.0)
    ix = ((x - lo_x) / CELL).astype(int)
    iz = ((z - lo_z) / CELL).astype(int)
    np.maximum.at(top, (iz, ix), h)
    img = np.full((H, W, 3), 255, np.uint8)
    covered = top > -9
    t = np.clip(top / 2.6, 0, 1)
    hsv = np.zeros((H, W, 3), np.uint8)
    hsv[..., 0] = (120 - 120 * t).astype(np.uint8)  # azul (piso) → rojo (alto)
    hsv[..., 1] = 200
    hsv[..., 2] = 230
    rgb = cv2.cvtColor(hsv, cv2.COLOR_HSV2BGR)
    img[covered] = rgb[covered]
    img = cv2.resize(img, (W * scale, H * scale), interpolation=cv2.INTER_NEAREST)
    pts = [(int((c[0] - lo_x) / CELL * scale), int((c[2] - lo_z) / CELL * scale)) for c in cam]
    for a, b in zip(pts, pts[1:]):
        cv2.line(img, a, b, (40, 40, 40), 1, cv2.LINE_AA)
    cv2.circle(img, pts[0], 6, (0, 160, 0), -1)
    cv2.circle(img, pts[-1], 6, (0, 0, 200), -1)
    # regla de 1 m
    cv2.line(img, (10, img.shape[0] - 14), (10 + int(1.0 / CELL * scale), img.shape[0] - 14), (0, 0, 0), 2)
    cv2.putText(img, "1 m", (14, img.shape[0] - 22), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 0, 0), 1, cv2.LINE_AA)
    cv2.imwrite(str(out), img)
    floor_cells = covered.sum()
    low = ((top > -9) & (top < 0.35)).sum()
    print("%s: %d cuadriláteros | huella %.0f m² (celdas con algo encima: %d de 10 cm) | de ellas con solo piso/bajo: %.0f m²" % (
        mapdir.name, len(quads), floor_cells * CELL * CELL, floor_cells, low * CELL * CELL))
    print("   extensión x %.1f m, z %.1f m | recorrido de la cámara: x %.1f m, z %.1f m | piso a y=%.2f" % (
        x.max() - x.min(), z.max() - z.min(), np.ptp(cam[:, 0]), np.ptp(cam[:, 2]), floor_y))


if __name__ == "__main__":
    main()
