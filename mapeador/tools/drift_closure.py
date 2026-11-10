#!/usr/bin/env python3
"""Laboratorio L2 (docs/mapeador-bodega.md, sección 12.6): mide la deriva de ARCore.

Lee los fotogramas clave de un mapa (keyframes.json: pose de ARCore de cada uno) y calcula:
  * cuánto se caminó (longitud de la trayectoria) y a qué ritmo se guardaron fotogramas;
  * el ERROR DE CIERRE: se empieza con el teléfono apoyado en una marca fija y se termina
    apoyándolo en la MISMA marca con la misma orientación. En el mundo real el primer y el último
    fotograma están en el mismo lugar, así que lo que los separa en el mapa es deriva pura;
  * con --real-m, el error de ESCALA (ver abajo).

Lo que el cierre NO ve: un error de escala parejo (si ARCore mide 1 % de más en todo, el circuito
sigue cerrando). Para la escala hay que dar una distancia real:
  * circuito cerrado:  --real-m <perímetro medido>   compara contra la longitud de la trayectoria;
  * línea recta:       --recta --real-m <distancia medida entre las dos marcas>   compara contra
                       el desplazamiento entre el primer y el último fotograma.

Uso:
  python tools/drift_closure.py <carpeta-del-mapa | keyframes.json> [--real-m 120] [--recta]
         [--desde ID] [--hasta ID]
  python tools/drift_closure.py --autoprueba
Sin dependencias fuera de numpy. Mundo de ARCore: Y hacia arriba.
"""
import argparse
import json
import math
import sys
from pathlib import Path

import numpy as np


def load_keyframes(path: Path):
    """Devuelve (ids, segundos, cuaterniones x,y,z,w, posiciones) ordenados por marca de tiempo."""
    file = path / "keyframes.json" if path.is_dir() else path
    data = json.loads(file.read_text(encoding="utf-8"))
    if not data:
        raise SystemExit("keyframes.json está vacío")
    data.sort(key=lambda k: k["timestampNs"])
    ids = np.array([k["id"] for k in data])
    seconds = np.array([k["timestampNs"] for k in data], dtype=np.float64) / 1e9
    quats = np.array([k["q"] for k in data], dtype=np.float64)
    positions = np.array([k["t"] for k in data], dtype=np.float64)
    return ids, seconds, quats, positions


def quat_conj(q):
    return np.array([-q[0], -q[1], -q[2], q[3]])


def quat_mul(a, b):
    ax, ay, az, aw = a
    bx, by, bz, bw = b
    return np.array([
        aw * bx + ax * bw + ay * bz - az * by,
        aw * by - ax * bz + ay * bw + az * bx,
        aw * bz + ax * by - ay * bx + az * bw,
        aw * bw - ax * bx - ay * by - az * bz,
    ])


def relative_rotation(qa, qb):
    """Rotación en el marco del mundo que lleva la orientación A a la B."""
    q = quat_mul(qb / np.linalg.norm(qb), quat_conj(qa / np.linalg.norm(qa)))
    return q if q[3] >= 0 else -q


def total_angle_deg(q_rel):
    return math.degrees(2.0 * math.acos(min(1.0, abs(q_rel[3]))))


def yaw_deg(q_rel):
    """Parte de la rotación alrededor de la vertical del mundo (Y), en grados con signo (-180..180)."""
    angle = math.degrees(2.0 * math.atan2(q_rel[1], q_rel[3]))
    return (angle + 180.0) % 360.0 - 180.0


def path_length(positions, step=1):
    pts = positions[::step]
    if len(pts) < 2:
        return 0.0
    return float(np.linalg.norm(np.diff(pts, axis=0), axis=1).sum())


def analyse(ids, seconds, quats, positions, real_m=None, straight=False, start_id=None, end_id=None):
    ia = 0 if start_id is None else int(np.where(ids == start_id)[0][0])
    ib = len(ids) - 1 if end_id is None else int(np.where(ids == end_id)[0][0])
    if ib <= ia:
        raise SystemExit("--hasta debe ser un fotograma posterior a --desde")
    sl = slice(ia, ib + 1)
    pos, secs, q = positions[sl], seconds[sl], quats[sl]
    duration = float(secs[-1] - secs[0])
    length_all = path_length(pos)
    length_smooth = path_length(pos, step=4)  # con menos puntos el temblor de la mano pesa menos
    span = pos.max(axis=0) - pos.min(axis=0)

    delta = pos[-1] - pos[0]
    horizontal = float(math.hypot(delta[0], delta[2]))
    vertical = float(abs(delta[1]))
    closure = float(np.linalg.norm(delta))
    q_rel = relative_rotation(q[0], q[-1])

    out = {
        "keyframes": int(len(pos)),
        "duration_s": duration,
        "length_m": length_all,
        "length_smooth_m": length_smooth,
        "kf_per_s": len(pos) / duration if duration > 0 else float("nan"),
        "kf_per_m": len(pos) / length_all if length_all > 0 else float("nan"),
        "mean_speed_mps": length_all / duration if duration > 0 else float("nan"),
        "extent_xyz_m": [float(v) for v in span],
        "closure_m": closure,
        "closure_horizontal_m": horizontal,
        "closure_vertical_m": vertical,
        "closure_pct_of_path": 100.0 * closure / length_all if length_all > 0 else float("nan"),
        "rotation_total_deg": total_angle_deg(q_rel),
        "rotation_yaw_deg": yaw_deg(q_rel),
    }
    if real_m:
        if straight:
            out["scale_error_pct"] = 100.0 * (closure - real_m) / real_m
            out["scale_basis"] = "desplazamiento primer→último fotograma contra la distancia real"
        else:
            out["scale_error_pct"] = 100.0 * (length_smooth - real_m) / real_m
            out["scale_basis"] = "longitud de la trayectoria (suavizada) contra el perímetro real"
    return out


def print_report(r, straight):
    print(f"Fotogramas clave: {r['keyframes']}   duración {r['duration_s']:.1f} s   "
          f"velocidad media {r['mean_speed_mps']:.2f} m/s")
    print(f"Trayectoria: {r['length_m']:.1f} m (suavizada {r['length_smooth_m']:.1f} m)   "
          f"extensión x/y/z: {r['extent_xyz_m'][0]:.1f} / {r['extent_xyz_m'][1]:.1f} / {r['extent_xyz_m'][2]:.1f} m")
    print(f"Densidad de fotogramas: {r['kf_per_s']:.2f} por segundo, {r['kf_per_m']:.2f} por metro")
    label = "Distancia primer→último" if straight else "ERROR DE CIERRE"
    print(f"{label}: {r['closure_m']:.2f} m  (horizontal {r['closure_horizontal_m']:.2f} m, "
          f"vertical {r['closure_vertical_m']:.2f} m)  = {r['closure_pct_of_path']:.2f} % de lo caminado")
    print(f"Giro entre primer y último: {r['rotation_total_deg']:.1f}° en total, "
          f"{r['rotation_yaw_deg']:+.1f}° alrededor de la vertical")
    if "scale_error_pct" in r:
        print(f"Error de escala: {r['scale_error_pct']:+.2f} %  ({r['scale_basis']})")
    if not straight:
        print("Recuerda: el cierre NO detecta un error de escala parejo; para eso usa --real-m.")


def synthetic_loop(drift_deg_per_m=0.0, scale=1.0, step=0.25):
    """Circuito rectangular de 40×20 m (perímetro 120 m) caminado a 1 m/s, con deriva de rumbo
    y/o error de escala inyectados. Devuelve (ids, segundos, cuaterniones, posiciones, metros)."""
    corners = [(0, 0), (40, 0), (40, 20), (0, 20), (0, 0)]
    # El primer fotograma se toma con el teléfono apoyado en la marca de salida.
    start_heading = math.atan2(corners[1][0] - corners[0][0], corners[1][1] - corners[0][1])
    pts = [[0.0, 1.4, 0.0]]
    quats = [[0.0, math.sin(start_heading / 2), 0.0, math.cos(start_heading / 2)]]
    secs = [0.0]
    x = z = walked = t = 0.0
    for (x0, z0), (x1, z1) in zip(corners[:-1], corners[1:]):
        leg = math.hypot(x1 - x0, z1 - z0)
        true_heading = math.atan2(x1 - x0, z1 - z0)
        for _ in range(int(round(leg / step))):
            heading = true_heading + math.radians(drift_deg_per_m * walked)
            x += step * scale * math.sin(heading)
            z += step * scale * math.cos(heading)
            walked += step
            t += step
            pts.append([x, 1.4, z])
            quats.append([0.0, math.sin(heading / 2), 0.0, math.cos(heading / 2)])
            secs.append(t)
    # Como en el protocolo: al llegar a la marca se gira en el lugar hasta quedar mirando como al
    # inicio. El giro real se deshace por completo; lo que sobra es solo la deriva acumulada.
    first_heading = math.atan2(corners[1][0] - corners[0][0], corners[1][1] - corners[0][1])
    final_heading = first_heading + math.radians(drift_deg_per_m * walked)
    pts.append([x, 1.4, z])
    quats.append([0.0, math.sin(final_heading / 2), 0.0, math.cos(final_heading / 2)])
    secs.append(t + 1.0)
    ids = np.arange(1, len(pts) + 1)
    return ids, np.array(secs), np.array(quats), np.array(pts), walked


def self_test():
    """Comprueba la matemática: recupera una deriva de rumbo inyectada y confirma que un error de
    escala parejo NO abre el circuito (por eso el cierre solo no basta)."""
    # 1) Deriva de rumbo de 0.05°/m: tras 120 m el giro acumulado es 6° y el circuito queda abierto.
    ids, secs, quats, pts, walked = synthetic_loop(drift_deg_per_m=0.05)
    r = analyse(ids, secs, quats, pts, real_m=120.0)
    expected_yaw = 0.05 * walked
    assert abs(abs(r["rotation_yaw_deg"]) - expected_yaw) < 0.5, (r["rotation_yaw_deg"], expected_yaw)
    assert r["closure_m"] > 1.0, "con deriva inyectada el circuito debe quedar abierto"
    # 2) Sin deriva y con 2 % de escala de más: cierra casi perfecto, pero la escala sí se detecta.
    ids, secs, quats, pts, walked = synthetic_loop(scale=1.02)
    s = analyse(ids, secs, quats, pts, real_m=120.0)
    assert s["closure_m"] < 0.05, s["closure_m"]
    assert abs(s["scale_error_pct"] - 2.0) < 0.3, s["scale_error_pct"]
    print(f"autoprueba OK: giro inyectado {expected_yaw:.1f}° recuperado {r['rotation_yaw_deg']:+.1f}° "
          f"(cierre {r['closure_m']:.2f} m en {r['length_m']:.0f} m); "
          f"escala +2 % detectada como {s['scale_error_pct']:+.2f} % con cierre {s['closure_m']:.3f} m")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("mapa", nargs="?", help="carpeta del mapa o keyframes.json")
    ap.add_argument("--real-m", type=float, help="perímetro real del circuito (o, con --recta, distancia real)")
    ap.add_argument("--recta", action="store_true", help="la caminata fue una línea recta entre dos marcas")
    ap.add_argument("--desde", type=int, help="id del fotograma donde empieza la parte a medir")
    ap.add_argument("--hasta", type=int, help="id del fotograma donde termina la parte a medir")
    ap.add_argument("--autoprueba", action="store_true", help="comprueba la matemática con un circuito sintético")
    args = ap.parse_args()
    if args.autoprueba:
        self_test()
        return
    if not args.mapa:
        ap.error("falta la carpeta del mapa")
    ids, secs, quats, pos = load_keyframes(Path(args.mapa))
    report = analyse(ids, secs, quats, pos, args.real_m, args.recta, args.desde, args.hasta)
    print_report(report, args.recta)


if __name__ == "__main__":
    sys.exit(main())
