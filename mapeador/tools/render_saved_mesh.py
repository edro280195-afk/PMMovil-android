"""
Dibuja la malla que GUARDÓ el teléfono (`mesh.bin` del mapa) sobre los fotogramas clave del mismo mapa: es lo que Eduardo vio.

Uso:  python render_saved_mesh.py <carpeta-del-mapa> [cuántas_vistas=12]
Escribe <carpeta-del-mapa>/vistas.json y las imágenes en <carpeta-del-mapa>/png/ (con render_mesh.py).
"""
import json
import subprocess
import sys
from pathlib import Path


def main():
    mapdir = Path(sys.argv[1])
    count = int(sys.argv[2]) if len(sys.argv) > 2 else 12
    kfs = json.loads((mapdir / "keyframes.json").read_text(encoding="utf-8"))
    step = max(1, len(kfs) // count)
    views = []
    for i in range(0, len(kfs), step):
        k = kfs[i]
        views.append({
            "name": "guardada-%s" % Path(k["file"]).stem, "mesh": "mesh.bin", "q": k["q"], "t": k["t"], "k": k["k"],
            "image": str(mapdir / "keyframes" / k["file"]),
        })
    (mapdir / "vistas.json").write_text(json.dumps(views, indent=1), encoding="utf-8")
    subprocess.run([sys.executable, str(Path(__file__).resolve().parent / "render_mesh.py"), str(mapdir), "vistas.json"], check=True)


if __name__ == "__main__":
    main()
