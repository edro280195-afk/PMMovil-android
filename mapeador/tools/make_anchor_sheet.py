#!/usr/bin/env python3
"""Genera docs/banco-anclas.html: las hojas de marcadores para imprimir y probar `AnclasLab` (laboratorio L4).

Cada marcador sale como SVG (vectorial: queda nítido a cualquier tamaño) con su lado EXACTO en milímetros:
  * AprilTag 36h11 de 10, 20 y 30 cm; ArUco 4x4 de 20 cm; QR de 10 y 20 cm.
  * «Lado» = el borde negro exterior del marcador (en el QR, el cuadrado de módulos SIN el margen blanco).
Antes de escribir el HTML, cada marcador se vuelve a LEER con OpenCV (misma familia que usa la app) para comprobar que
lo que se va a imprimir se decodifica; si no, el script se detiene.

Uso:
  python tools/make_anchor_sheet.py [--salida docs/banco-anclas.html] [--no-verificar]
Requiere numpy y OpenCV (cv2) con el módulo aruco.
"""
import argparse
import html
import sys
from pathlib import Path

import cv2
import numpy as np

# (clave, familia visible, id/texto, lado en mm, tamaño de hoja)
SHEETS = [
    ("apriltag", "AprilTag 36h11", 0, 100, "a4"),
    ("apriltag", "AprilTag 36h11", 1, 200, "a3"),
    ("apriltag", "AprilTag 36h11", 2, 300, "a2"),
    ("aruco", "ArUco 4x4", 0, 200, "a3"),
    ("qr", "QR", "ANCLA-0001", 100, "a4"),
    ("qr", "QR", "ANCLA-0002", 200, "a3"),
]
PAPER = {"a4": ("A4 (210 × 297 mm)", 210, 297), "a3": ("A3 (297 × 420 mm)", 297, 420), "a2": ("A2 (420 × 594 mm)", 420, 594)}


def modules_aruco(dictionary_id, marker_id, modules):
    dictionary = cv2.aruco.getPredefinedDictionary(dictionary_id)
    img = cv2.aruco.generateImageMarker(dictionary, marker_id, modules, 1)  # un píxel por módulo, con borde
    return (img < 128).astype(np.uint8)  # 1 = módulo negro


def modules_qr(text):
    raw = cv2.QRCodeEncoder.create().encode(text)
    ys, xs = np.where(raw < 128)
    crop = raw[ys.min():ys.max() + 1, xs.min():xs.max() + 1]  # fuera el margen que agrega el codificador
    return (crop < 128).astype(np.uint8)


def build_modules(kind, ident):
    if kind == "apriltag":
        return modules_aruco(cv2.aruco.DICT_APRILTAG_36h11, ident, 8)
    if kind == "aruco":
        return modules_aruco(cv2.aruco.DICT_4X4_50, ident, 6)
    return modules_qr(ident)


def verify(kind, ident, modules):
    """Dibuja el marcador con margen blanco, lo agranda y lo lee con el mismo detector que la app."""
    n = modules.shape[0]
    scale = max(8, 400 // n)
    quiet = 4 * scale
    img = np.full((n * scale + 2 * quiet,) * 2, 255, np.uint8)
    img[quiet:quiet + n * scale, quiet:quiet + n * scale] = np.kron(1 - modules, np.ones((scale, scale), np.uint8)) * 255
    if kind == "qr":
        ok, texts, _, _ = cv2.QRCodeDetectorAruco().detectAndDecodeMulti(img)
        return bool(ok) and list(texts) == [ident]
    dictionary_id = cv2.aruco.DICT_APRILTAG_36h11 if kind == "apriltag" else cv2.aruco.DICT_4X4_50
    detector = cv2.aruco.ArucoDetector(cv2.aruco.getPredefinedDictionary(dictionary_id), cv2.aruco.DetectorParameters())
    _, ids, _ = detector.detectMarkers(img)
    return ids is not None and [int(i) for i in ids.flatten()] == [ident]


def svg(modules, side_mm):
    n = modules.shape[0]
    rects = []
    for y in range(n):  # tramos horizontales de módulos negros, para no repetir un rectángulo por módulo
        x = 0
        while x < n:
            if modules[y, x]:
                start = x
                while x < n and modules[y, x]:
                    x += 1
                rects.append(f'<rect x="{start}" y="{y}" width="{x - start}" height="1"/>')
            else:
                x += 1
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{side_mm}mm" height="{side_mm}mm" viewBox="0 0 {n} {n}" '
            f'shape-rendering="crispEdges"><rect width="{n}" height="{n}" fill="#fff"/><g fill="#000">{"".join(rects)}</g></svg>')


CSS = """
@page a4 { size: 210mm 297mm; margin: 0 }
@page a3 { size: 297mm 420mm; margin: 0 }
@page a2 { size: 420mm 594mm; margin: 0 }
* { box-sizing: border-box }
body { margin: 0; background: #d9d9d9; font-family: Arial, Helvetica, sans-serif; color: #111 }
.hoja { background: #fff; margin: 0 auto 10mm; position: relative; overflow: hidden; display: flex;
        flex-direction: column; justify-content: space-between; break-after: page }
.a4 { width: 210mm; height: 297mm; page: a4 }
.a3 { width: 297mm; height: 420mm; page: a3 }
.a2 { width: 420mm; height: 594mm; page: a2 }
.cab, .pie { padding: 12mm 15mm }
.cab { font-size: 5mm }
.cab small { color: #444 }
.centro { flex: 1; display: flex; align-items: center; justify-content: center }
.pie { font-size: 3.6mm; color: #333; display: flex; gap: 10mm; align-items: flex-end }
.regla { width: 100mm; height: 5mm; border: .3mm solid #000; background:
         repeating-linear-gradient(90deg, #000 0 .3mm, transparent .3mm 10mm); flex: none }
.instrucciones { width: 210mm; height: 297mm; page: a4; padding: 15mm 18mm; font-size: 3.9mm; line-height: 1.45 }
.instrucciones h1 { font-size: 7mm; margin: 0 0 3mm }
.instrucciones h2 { font-size: 4.8mm; margin: 5mm 0 1.5mm }
.instrucciones table { border-collapse: collapse; width: 100%; font-size: 3.2mm }
.instrucciones th, .instrucciones td { border: .25mm solid #666; padding: 1.6mm 1mm; text-align: center }
.instrucciones th:first-child, .instrucciones td:first-child { width: 20mm }
@media screen { .hoja:not(.instrucciones) { zoom: .35 } }
@media print { body { background: #fff } .hoja { margin: 0; zoom: 1 } }
"""


def instructions():
    rows = "".join(f"<tr><td><b>{d} m</b></td>{'<td></td>' * 9}</tr>" for d in (2, 4, 6, 8, 10, 12, 15))
    return f"""
<section class="hoja instrucciones">
<h1>Laboratorio de anclas · instrucciones</h1>
<div>Para medir hasta dónde y con qué precisión lee la app los marcadores (<code>AnclasLab</code>, módulo <code>:mapeador</code>).
Esta hoja y las siguientes se imprimen <b>al 100 %</b> (sin «ajustar a la página»): comprueba que la barra de cada hoja mida 100 mm.</div>
<h2>Cómo imprimir</h2>
<ul>
<li><b>10 cm</b> (AprilTag id 0 y QR 0001): cabe en carta o A4.</li>
<li><b>20 cm</b> (AprilTag id 1, ArUco 0 y QR 0002): necesita A3 o tabloide (11 × 17 in), con margen blanco alrededor.</li>
<li><b>30 cm</b> (AprilTag id 2): necesita A2, es decir, plóter o rotulación.</li>
<li><b>Papel mate</b> (nada brillante: el reflejo borra el marcador). Pegado plano sobre cartón, foam o PVC, sin arrugas.</li>
</ul>
<h2>Cómo medir</h2>
<ol>
<li>Pega el marcador <b>a la altura de la cámara</b> (≈ 1.4 m), en un muro sin brillo, con luz normal de la bodega.</li>
<li>En el teléfono: <b>Laboratorio de anclas</b> → Ajustes: elige la familia y el lado (cm) del marcador que estás viendo.</li>
<li>Con el <b>medidor láser</b> mide la distancia del teléfono (parte de atrás, donde está la cámara) al centro del marcador: <b>2, 4, 6, 8, 10, 12 y 15 m</b>.</li>
<li>Teclea esa distancia en «Distancia real», pon una etiqueta (por ejemplo <i>30grados</i>), apunta al marcador, aguanta quieto unos 5 s y toca <b>Grabar</b>. Para el ángulo, sitúate a 0°, 30° y 60° respecto a la perpendicular del marcador.</li>
<li>La app <b>solo graba cuando lee</b>: anota aquí si <b>no</b> lo logró (✗) y cuántos intentos de 10 lo logró. Eso no sale del CSV.</li>
<li>Al terminar, avisa: el archivo es <code>files/logs/anclas-AAAAMMDD-HHMMSS.csv</code> y se analiza con <code>tools/anchors_analysis.py</code>.</li>
</ol>
<h2>¿Lo leyó? (✓ / ✗ / n de 10)</h2>
<table>
<tr><th rowspan="2">Distancia</th><th colspan="3">0° (de frente)</th><th colspan="3">30°</th><th colspan="3">60°</th></tr>
<tr>{'<th>10 cm</th><th>20 cm</th><th>30 cm</th>' * 3}</tr>
{rows}
</table>
<h2>Qué se espera (simulación, cámara ideal)</h2>
<div>Legible con ≥ ~18 px de lado: unos 5 m con 10 cm, 10 m con 20 cm y 15 m con 30 cm, de frente. La <b>distancia</b> es lo que se equivoca; la posición lateral casi no.
Lo medido aquí manda: el autofocus, el movimiento y la luz de la bodega lo empeoran. Detalle en <code>docs/mapeador-bodega.md</code> §12.11.</div>
</section>
"""


def sheet(kind, label, ident, side_mm, paper):
    modules = build_modules(kind, ident)
    name = f"{label} · {'id ' + str(ident) if kind != 'qr' else html.escape(str(ident))}"
    paper_label = PAPER[paper][0]
    return f"""
<section class="hoja {paper}">
<div class="cab"><b>{name}</b> · lado = <b>{side_mm} mm</b> <small>(borde negro exterior{'; en QR, el cuadrado de módulos sin margen' if kind == 'qr' else ''})</small></div>
<div class="centro">{svg(modules, side_mm)}</div>
<div class="pie"><div class="regla"></div><div>Esta barra debe medir <b>100 mm</b>. Si no, imprime al 100 % (sin «ajustar a la página»).<br>
Hoja: {paper_label} · papel mate · pegar plano.</div></div>
</section>
"""


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--salida", default=str(Path(__file__).resolve().parents[2] / "docs" / "banco-anclas.html"))
    ap.add_argument("--no-verificar", action="store_true")
    args = ap.parse_args()

    body = [instructions()]
    for kind, label, ident, side_mm, paper in SHEETS:
        modules = build_modules(kind, ident)
        if not args.no_verificar:
            if not verify(kind, ident, modules):
                raise SystemExit(f"El marcador {label} {ident} NO se decodifica: no se genera la hoja.")
            print(f"  OK  {label:<15} {str(ident):<11} {side_mm:3d} mm  ({modules.shape[0]}×{modules.shape[0]} módulos) → {paper.upper()}")
        body.append(sheet(kind, label, ident, side_mm, paper))

    page = (f'<!doctype html><html lang="es"><head><meta charset="utf-8"><title>Banco de anclas · laboratorio L4</title>'
            f'<meta name="viewport" content="width=device-width, initial-scale=1"><style>{CSS}</style></head><body>{"".join(body)}</body></html>')
    out = Path(args.salida)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(page, encoding="utf-8")
    print(f"Escrito {out} ({out.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    sys.exit(main())
