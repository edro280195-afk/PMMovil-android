"""
¿Qué cuadros de la grabación de profundidad se integraron con la pose EQUIVOCADA?

Cuando ARCore se atasca devuelve la última imagen de profundidad con su marca de tiempo vieja. Las versiones hasta la v7 no lo
revisaban: la pose de esa marca ya había salido del historial y la imagen se pegaba donde estaba la cámara AHORA, así que la
misma geometría se «arrastraba» por el recorrido (la malla flotante). Aquí se detecta por consistencia: la pose guardada con cada
cuadro debe coincidir con la trayectoria de la cámara (sacada de los fotogramas clave del mapa, que traen su marca de tiempo) en
la marca de tiempo de la propia imagen de profundidad.

Uso: python frame_validity.py <carpeta> [tolerancia_cm=10]   (con varias grabaciones: PMM_LOG=<fragmento> PMM_MAP=<fragmento>)
Escribe <carpeta>/out/valid-frames.txt (índices de los cuadros buenos, separados por coma; `ReplayTest.exploreAnchoredOnRealDepth`
los usa) e imprime cuántos son y cómo se reparte el problema. Los cuadros fuera del tiempo que cubren los fotogramas clave se
dan por buenos (no hay con qué compararlos).
"""
import json
import os
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import depthlog as dl


def main():
    root = Path(sys.argv[1])
    tol = float(sys.argv[2]) / 100.0 if len(sys.argv) > 2 else 0.10
    # PMM_LOG y PMM_MAP (fragmentos del nombre) eligen la grabación y el mapa cuando hay varios.
    log = sorted(p for p in root.rglob("profundidad-*.bin") if os.environ.get("PMM_LOG", "") in p.name)[-1]
    kf_json = sorted((p for p in root.rglob("keyframes.json") if os.environ.get("PMM_MAP", "") in p.parent.name), key=lambda p: p.parent.name)[-1]
    frames = dl.read_log(str(log))
    kfs = json.loads(kf_json.read_text(encoding="utf-8"))
    kt = np.array([k["timestampNs"] for k in kfs], dtype=np.float64) / 1e9
    kp = np.array([k["t"] for k in kfs])
    order = np.argsort(kt)
    kt, kp = kt[order], kp[order]
    valid = []
    bad_runs = []
    run_start = None
    for i, f in enumerate(frames):
        s = f.ts / 1e9
        ok = True
        if kt[0] + 0.3 < s < kt[-1] - 0.3:
            expected = np.array([np.interp(s, kt, kp[:, a]) for a in range(3)])
            ok = float(np.linalg.norm(expected - f.t)) < tol
        if ok:
            valid.append(i)
            if run_start is not None:
                bad_runs.append((run_start, i - 1))
                run_start = None
        elif run_start is None:
            run_start = i
    if run_start is not None:
        bad_runs.append((run_start, len(frames) - 1))
    out = root / "out"
    out.mkdir(exist_ok=True)
    (out / "valid-frames.txt").write_text(",".join(str(i) for i in valid), encoding="utf-8")
    n = len(frames)
    print("%s | %d cuadros, %d con la pose de su marca de tiempo (%.0f %%), %d con pose equivocada" % (log.name, n, len(valid), 100 * len(valid) / n, n - len(valid)))
    long_runs = [(a, b) for a, b in bad_runs if b - a >= 4]
    print("tramos largos de cuadros con pose equivocada (cuadro inicial–final):", ", ".join("%d–%d" % r for r in long_runs) or "ninguno")


if __name__ == "__main__":
    main()
