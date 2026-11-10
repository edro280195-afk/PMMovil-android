#!/usr/bin/env python3
"""Laboratorio L4 (docs/mapeador-bodega.md, sección 12.9): simula qué tan bien se puede sacar la POSE de un
marcador cuadrado (AprilTag 36h11) con la cámara del Pixel, ANTES de salir a medir.

Hace lo mismo que `AnclasLab` en el teléfono: detecta con ArucoDetector (esquinas SUBPIX), resuelve la pose con
solvePnPGeneric(IPPE_SQUARE) y compara contra la pose verdadera con la que se dibujó el marcador. Sirve para:
  1) comprobar el orden de las esquinas y los convenios de ejes (si estuvieran mal, el error sale enorme);
  2) tener un TECHO TEÓRICO: cámara ideal (solo desenfoque leve y ruido de sensor). La realidad es peor:
     autofocus, movimiento, luz mala, lente real. Lo medido en la bodega manda.

Uso:
  python tools/anchors_simulation.py              tabla completa (tarda ~1 min)
  python tools/anchors_simulation.py --rapida     una sola celda para comprobar la geometría
Requiere numpy y OpenCV (cv2) con el módulo aruco.
"""
import argparse
import math
import sys

import cv2
import numpy as np

# Intrínsecos reales de la imagen de CPU del Pixel 9 Pro XL (keyframes.json de los mapas del 21/22-oct).
FX, FY, CX, CY, W, H = 921.2120, 922.0795, 641.2795, 359.5580, 1280, 720
K = np.array([[FX, 0, CX], [0, FY, CY], [0, 0, 1]], dtype=np.float64)

MARKER_ID = 7
MODULES = 8  # 36h11: cuadrícula de 6×6 + 1 módulo de borde por lado
rng = np.random.default_rng(20261110)


def object_points(side):
    """Esquinas del marcador en su marco: X a la derecha, Y hacia arriba, Z hacia quien lo mira (orden de OpenCV)."""
    h = side / 2.0
    return np.array([[-h, h, 0], [h, h, 0], [h, -h, 0], [-h, -h, 0]], dtype=np.float64)


def rodrigues_yaw(deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def render(dictionary, side_m, rot, t):
    """Imagen 1280×720 con el marcador (impreso sobre una hoja blanca) visto desde la cámara en el origen."""
    px = 400  # resolución del marcador fuente
    marker = cv2.aruco.generateImageMarker(dictionary, MARKER_ID, px, 1)
    pad = int(px * 0.5)  # margen blanco alrededor, como una hoja impresa
    sheet = np.full((px + 2 * pad, px + 2 * pad), 255, np.uint8)
    sheet[pad:pad + px, pad:pad + px] = marker
    scale = side_m / px  # metros por píxel de la hoja

    # Píxel de la hoja (u, v) -> punto del marco del marcador (x = (u-c)*scale, y = -(v-c)*scale, 0)
    c = (px + 2 * pad) / 2.0
    to_plane = np.array([[scale, 0, -c * scale], [0, -scale, c * scale], [0, 0, 1]])
    plane_to_img = K @ np.column_stack([rot[:, 0], rot[:, 1], t])
    homography = plane_to_img @ to_plane
    img = np.full((H, W), 110, np.uint8)
    warped = cv2.warpPerspective(sheet, homography, (W, H), flags=cv2.INTER_AREA, borderMode=cv2.BORDER_TRANSPARENT,
                                 dst=img)
    img = cv2.GaussianBlur(img, (0, 0), 0.7)
    noisy = img.astype(np.float32) + rng.normal(0, 2.0, img.shape)
    return np.clip(noisy, 0, 255).astype(np.uint8)


def detect_and_solve(detector, img, side_m):
    corners, ids, _ = detector.detectMarkers(img)
    if ids is None or MARKER_ID not in ids.flatten():
        return None
    quad = corners[list(ids.flatten()).index(MARKER_ID)].reshape(4, 2).astype(np.float64)
    ok, rvecs, tvecs, errs = cv2.solvePnPGeneric(object_points(side_m), quad, K, None, flags=cv2.SOLVEPNP_IPPE_SQUARE)
    if not ok or len(rvecs) == 0:
        return None
    rot, _ = cv2.Rodrigues(rvecs[0])
    errs = np.array(errs).flatten()
    ambiguity = float(errs[1] / errs[0]) if len(errs) > 1 and errs[0] > 1e-9 else float("inf")
    size_px = float(np.mean([np.linalg.norm(quad[i] - quad[(i + 1) % 4]) for i in range(4)]))
    return rot, tvecs[0].reshape(3), float(errs[0]), ambiguity, size_px


def view_angle_deg(rot, t):
    """Ángulo entre la normal del marcador (hacia quien lo mira) y la línea que va del marcador a la cámara."""
    normal = rot @ np.array([0.0, 0.0, 1.0])
    to_cam = -t / np.linalg.norm(t)
    return math.degrees(math.acos(float(np.clip(normal @ to_cam, -1, 1))))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--rapida", action="store_true", help="una sola celda (4 m, 20 cm, de frente) para comprobar la geometría")
    ap.add_argument("--pruebas", type=int, default=12, help="repeticiones por celda")
    args = ap.parse_args()

    dictionary = cv2.aruco.getPredefinedDictionary(cv2.aruco.DICT_APRILTAG_36h11)
    params = cv2.aruco.DetectorParameters()
    params.cornerRefinementMethod = cv2.aruco.CORNER_REFINE_SUBPIX
    detector = cv2.aruco.ArucoDetector(dictionary, params)

    distances = [4.0] if args.rapida else [2.0, 4.0, 6.0, 8.0, 10.0, 12.0, 15.0]
    sides = [0.20] if args.rapida else [0.10, 0.20, 0.30]
    angles = [0.0] if args.rapida else [0.0, 30.0, 60.0]
    trials = 4 if args.rapida else args.pruebas

    print("Marcador AprilTag 36h11 (1280×720, f≈921 px). Error = distancia entre la posición resuelta y la verdadera.")
    print("lado  dist  ángulo | lectura  tamaño   error 3D mediana / p90   error en distancia   error lateral   ángulo medido")
    for side in sides:
        for angle in angles:
            for d in distances:
                found, err3d, err_range, err_lat, size_px, view = 0, [], [], [], [], []
                for _ in range(trials):
                    # Posición casi centrada con un poco de desplazamiento lateral; el marcador gira sobre la vertical.
                    x = rng.uniform(-0.15, 0.15) * d
                    y = rng.uniform(-0.05, 0.05) * d
                    t_true = np.array([x, y, d])
                    # Un marcador que mira de frente a la cámara (X a la derecha, Y arriba, Z hacia la cámara) tiene, en el
                    # marco de OpenCV (Y abajo, Z hacia adelante), una rotación de 180° sobre X. Después gira sobre la vertical.
                    rot_true = rodrigues_yaw(angle) @ np.diag([1.0, -1.0, -1.0])
                    img = render(dictionary, side, rot_true, t_true)
                    got = detect_and_solve(detector, img, side)
                    if got is None:
                        continue
                    rot, t, _, _, spx = got
                    found += 1
                    err3d.append(np.linalg.norm(t - t_true))
                    ray = t_true / np.linalg.norm(t_true)
                    along = float((t - t_true) @ ray)
                    err_range.append(abs(along))
                    err_lat.append(float(np.linalg.norm((t - t_true) - along * ray)))
                    size_px.append(spx)
                    view.append(view_angle_deg(rot, t))
                if found == 0:
                    print(f"{side*100:4.0f}  {d:4.0f}  {angle:5.0f}° |  0/{trials}     --")
                    continue
                print(f"{side*100:4.0f}  {d:4.0f}  {angle:5.0f}° | {found:2d}/{trials:<2d}   {np.median(size_px):5.1f}px   "
                      f"{np.median(err3d)*100:6.1f} / {np.percentile(err3d, 90)*100:6.1f} cm        "
                      f"{np.median(err_range)*100:6.1f} cm          {np.median(err_lat)*100:5.1f} cm      {np.median(view):5.1f}°")


if __name__ == "__main__":
    sys.exit(main())
