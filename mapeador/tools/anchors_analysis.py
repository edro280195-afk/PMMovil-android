#!/usr/bin/env python3
"""Laboratorio L4 (docs/mapeador-bodega.md, sección 12.9): resume la bitácora que graba `AnclasLab` (anclas-*.csv).

Agrupa las lecturas por (familia, lado, distancia real, etiqueta) y calcula, por grupo:
  * EXACTITUD (solo si se tecleó la distancia real medida con láser): error de la distancia leída contra la real
    (sesgo con signo, mediana y percentil 90 del error absoluto, en cm);
  * PRECISIÓN (no necesita láser): dispersión de la posición del marcador en el mundo de ARCore, en cm, sobre las
    lecturas del grupo. Ojo: si ARCore se desvió durante la prueba, esa deriva entra aquí;
  * qué tan «clara» fue cada lectura: ángulo de vista, tamaño en píxeles, error de reproyección y ambigüedad medianos.
La app solo escribe una fila cuando LEE el marcador, así que la tasa de lectura no sale del CSV: se anota a mano
(o con el contador de la pantalla) y se compara con la tabla de la simulación (docs §12.11).

Uso:
  python tools/anchors_analysis.py <anclas-AAAAMMDD-HHMMSS.csv> [--min-n 5]
  python tools/anchors_analysis.py --autoprueba
Solo numpy.
"""
import argparse
import csv
import math
import sys
import tempfile
from collections import defaultdict
from pathlib import Path

import numpy as np

FLOAT_FIELDS = ["lado_cm", "dist_m", "z_m", "angulo_deg", "tam_px", "reproj_px", "mundo_x", "mundo_y", "mundo_z"]


def number(text):
    try:
        return float(text)
    except (TypeError, ValueError):
        return None


def load(path: Path):
    rows = []
    with open(path, newline="", encoding="utf-8") as f:
        for r in csv.DictReader(f):
            row = {"familia": r["familia"], "id": r["id"], "etiqueta": r.get("etiqueta", ""),
                   "real_m": number(r.get("real_m")), "ambiguedad": number(r.get("ambiguedad"))}
            for field in FLOAT_FIELDS:
                row[field] = number(r.get(field))
            if any(row[f] is None for f in ("dist_m", "mundo_x", "mundo_y", "mundo_z", "lado_cm")):
                continue
            rows.append(row)
    return rows


def summarise(rows):
    groups = defaultdict(list)
    for r in rows:
        groups[(r["familia"], r["lado_cm"], r["real_m"], r["etiqueta"])].append(r)
    out = []
    for key, items in groups.items():
        dist = np.array([r["dist_m"] for r in items])
        world = np.array([[r["mundo_x"], r["mundo_y"], r["mundo_z"]] for r in items])
        scatter_cm = float(np.sqrt(((world - world.mean(axis=0)) ** 2).sum(axis=1).mean()) * 100.0) if len(items) > 1 else None
        real = key[2]
        err = (dist - real) * 100.0 if real else None
        amb = [r["ambiguedad"] for r in items if r["ambiguedad"] is not None]
        out.append({
            "familia": key[0], "lado_cm": key[1], "real_m": real, "etiqueta": key[3], "n": len(items),
            "dist_med_m": float(np.median(dist)),
            "bias_cm": float(np.median(err)) if err is not None else None,
            "abs_med_cm": float(np.median(np.abs(err))) if err is not None else None,
            "abs_p90_cm": float(np.percentile(np.abs(err), 90)) if err is not None else None,
            "scatter_cm": scatter_cm,
            "ang_med": float(np.median([r["angulo_deg"] for r in items if r["angulo_deg"] is not None] or [float("nan")])),
            "px_med": float(np.median([r["tam_px"] for r in items if r["tam_px"] is not None] or [float("nan")])),
            "reproj_med": float(np.median([r["reproj_px"] for r in items if r["reproj_px"] is not None] or [float("nan")])),
            "amb_med": float(np.median(amb)) if amb else None,
        })
    out.sort(key=lambda g: (g["familia"], g["lado_cm"], g["real_m"] if g["real_m"] is not None else -1, g["etiqueta"]))
    return out


def fmt(value, spec, missing="—"):
    return missing if value is None or (isinstance(value, float) and math.isnan(value)) else format(value, spec)


def print_table(groups, min_n):
    print("familia          lado  real     n  dist(med)  sesgo   |err| med / p90      dispersión   ángulo  tamaño  reproj  ambig  etiqueta")
    for g in groups:
        flag = "  (pocas lecturas)" if g["n"] < min_n else ""
        print(f"{g['familia']:<16} {g['lado_cm']:4.0f}  {fmt(g['real_m'], '5.2f'):>5}  {g['n']:4d}  {g['dist_med_m']:7.2f} m  "
              f"{fmt(g['bias_cm'], '+6.1f'):>6}  {fmt(g['abs_med_cm'], '5.1f'):>5} / {fmt(g['abs_p90_cm'], '5.1f'):>5} cm   "
              f"{fmt(g['scatter_cm'], '6.1f'):>6} cm   {fmt(g['ang_med'], '5.0f'):>4}°  {fmt(g['px_med'], '5.0f'):>4}px  "
              f"{fmt(g['reproj_med'], '5.2f'):>5}  {fmt(g['amb_med'], '5.1f'):>5}  {g['etiqueta']}{flag}")


def self_test():
    """Escribe un CSV falso con sesgo +1 cm y ruido de 3 cm a 4 m, y comprueba que el resumen lo recupera."""
    rng = np.random.default_rng(3)
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / "anclas-prueba.csv"
        header = "hora,ts_ns,familia,id,lado_cm,dist_m,z_m,angulo_deg,tam_px,reproj_px,ambiguedad,mundo_x,mundo_y,mundo_z,cam_x,cam_y,cam_z,real_m,etiqueta"
        lines = [header]
        for i in range(400):
            dist = 4.0 + 0.01 + rng.normal(0, 0.03)
            wx, wy, wz = 1.0 + rng.normal(0, 0.02), 1.4 + rng.normal(0, 0.02), -4.0 + rng.normal(0, 0.02)
            lines.append(f'00:00:{i % 60:02d}.000,{i},APRILTAG_36H11,"7",20.0,{dist:.4f},{dist:.4f},5.00,46.0,0.300,1.50,'
                         f'{wx:.4f},{wy:.4f},{wz:.4f},0,1.5,0,4.000,"frontal"')
        path.write_text("\n".join(lines) + "\n", encoding="utf-8")
        groups = summarise(load(path))
    assert len(groups) == 1, groups
    g = groups[0]
    assert g["n"] == 400
    assert abs(g["bias_cm"] - 1.0) < 0.5, g["bias_cm"]
    assert 1.5 < g["abs_med_cm"] < 3.0, g["abs_med_cm"]
    assert 2.5 < g["scatter_cm"] < 4.5, g["scatter_cm"]  # raíz de tres desviaciones de 2 cm ≈ 3.5 cm
    print(f"autoprueba OK: sesgo {g['bias_cm']:+.1f} cm, |error| mediano {g['abs_med_cm']:.1f} cm, dispersión {g['scatter_cm']:.1f} cm")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("csv", nargs="?", help="anclas-*.csv que dejó la app en files/logs/")
    ap.add_argument("--min-n", type=int, default=5, help="avisa de los grupos con menos lecturas")
    ap.add_argument("--autoprueba", action="store_true")
    args = ap.parse_args()
    if args.autoprueba:
        self_test()
        return
    if not args.csv:
        ap.error("falta el archivo CSV")
    rows = load(Path(args.csv))
    if not rows:
        raise SystemExit("El CSV no trae lecturas.")
    print_table(summarise(rows), args.min_n)


if __name__ == "__main__":
    sys.exit(main())
