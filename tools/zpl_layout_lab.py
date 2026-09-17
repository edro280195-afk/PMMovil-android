#!/usr/bin/env python3
"""Banco de pruebas para disenar la etiqueta ZPL en un 'marco de lectura'.

Se disena en orientacion de lectura (landscape 1035 x 830) y se convierte a
coordenadas de impresion (portrait 830 x 1035) que es como se envia a la Zebra.
La impresion se ve correcta al girar la etiqueta 90 grados.

Mapeo print<->reading (verificado empiricamente con el ZPL existente):
    print -> reading (girar la imagen impresa 90 horario, PIL rotate(-90)):
        xr = 1034 - yz ; yr = xz
    reading -> print (inverso):
        xz = yr ; yz = 1034 - xr

Para texto horizontal en lectura usamos ^A0B (lee de abajo hacia arriba en
print) y el ^FO se calcula desde la esquina sup-izq del texto en lectura.
"""
from __future__ import annotations
import os, re, urllib.request
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GFA = open(os.path.join(ROOT, "artifacts", "imex_logo_gfa.txt"), encoding="ascii").read().strip()

PW, LL = 830, 1035          # print (portrait)
WR, HR = 1035, 830          # reading (landscape)

def sani(x, n=200):
    return re.sub(r"\s+", " ", "".join(" " if (c in "^~" or ord(c) < 32) else c for c in x)).strip()[:n]

def r2p_point(xr, yr):
    return yr, 1034 - xr      # xz, yz

def text(xr, yr, h, w, s, fb_len, just="L", lines=1):
    """Texto horizontal en lectura: (xr,yr)=esquina sup-izq, h=alto fuente,
    w=ancho fuente, fb_len=largo de bloque (en lectura-x), just=L/R/C."""
    # esquina sup-izq de lectura -> top-left del campo en print
    xz = yr
    yz = 1034 - xr - fb_len
    return f"^FO{xz},{yz}^A0B,{h},{w}^FB{fb_len},{lines},0,{just},0^FD{sani(s)}^FS"

def line_h(xr, yr, length, thick=3):
    """Linea horizontal (subrayado) en lectura de largo 'length' a partir de (xr,yr)."""
    # una linea horizontal en lectura es vertical en print
    xz = yr
    yz = 1034 - xr - length
    return f"^FO{xz},{yz}^GB{thick},{length},{thick}^FS"

LOGO_W = 460   # ancho del logo en lectura (debe coincidir con png_to_zpl_logo.py)

def logo(xr, yr):
    """Coloca el grafico del logo (LOGO_W de ancho en lectura) con esquina sup-izq (xr,yr)."""
    xz = yr
    yz = 1034 - xr - LOGO_W
    return f"^FO{xz},{yz}{GFA}^FS"

def qr(xr_tr, yr_tr, mag=7):
    """QR cuya esquina superior-derecha en lectura es (xr_tr, yr_tr).
    El QR crece hacia abajo-izquierda en lectura."""
    xz = yr_tr
    yz = 1034 - xr_tr
    return f"^FO{xz},{yz}^BQN,2,{mag}^FDLA,{sani('01C26355910-00-00 5-6',700)}^FS"

def build():
    parts = ["^XA", "^CI28", f"^PW{PW}", f"^LL{LL}", "^LH0,0", "^PR4", "^MD18"]
    # --- Columna superior izquierda: LOGO grande, F. ARRIBO, NUMERO grande ---
    parts.append("^FWB")
    parts.append(logo(28, 14))
    parts.append("^FWN")
    parts.append(text(28, 214, 30, 28, "F. ARRIBO:", 210, "L"))
    parts.append(text(245, 214, 32, 30, "06/02/2026", 230, "L"))
    parts.append(line_h(240, 252, 230))
    # Numero grande con guion manual (la fuente ^A0 deja hueco enorme en "-").
    consec, suf = "355910", "00"
    nx = 40
    end = nx + len(consec) * 38          # avance ~38 dots/digito a ^A0B,138,80
    parts.append(text(nx, 268, 138, 80, consec, 470, "L"))
    parts.append(line_h(end + 8, 326, 38, 18))                  # guion solido
    parts.append(text(end + 54, 268, 138, 80, suf, 200, "L"))   # sufijo
    # --- QR GRANDE arriba-derecha (esquina sup-der en lectura) ---
    parts.append(qr(1016, 12, 19))
    # --- CLIENTE: fila completa (pegada debajo del QR) ---
    parts.append(text(28, 430, 32, 30, "CLIENTE:", 210, "L"))
    parts.append(text(238, 433, 28, 18, "ALLNEX MEXICO, S. DE R.L. DE C.V. CL7267", 770, "L"))
    parts.append(line_h(233, 469, 770))
    # --- CAJA (izq) | GUIA (der) en la misma fila ---
    parts.append(text(28, 512, 32, 30, "CAJA:", 210, "L"))
    parts.append(text(150, 512, 30, 28, "", 290, "L"))
    parts.append(line_h(145, 548, 290))
    parts.append(text(540, 512, 32, 30, "GUIA:", 210, "L"))
    parts.append(text(680, 512, 30, 28, "472430009", 300, "L"))
    parts.append(line_h(675, 548, 300))
    # --- UBICA debajo de GUIA ---
    parts.append(text(540, 592, 32, 30, "UBICA:", 210, "L"))
    parts.append(text(680, 592, 30, 28, "O3", 250, "L"))
    parts.append(line_h(675, 628, 250))
    # --- CONTADOR grande debajo de UBICA, esquina inferior derecha ---
    parts.append(text(790, 680, 140, 134, "5/6", 250, "R"))
    parts.append("^PQ1")
    parts.append("^XZ")
    return "\n".join(parts)

def render(zpl, name):
    req = urllib.request.Request("https://api.labelary.com/v1/printers/8dpmm/labels/4.09x5.1/0/",
                                 data=zpl.encode(), headers={"Accept": "image/png"})
    try:
        png = urllib.request.urlopen(req, timeout=40).read()
    except urllib.error.HTTPError as e:
        print("HTTP", e.code, e.read().decode("utf-8", "ignore")[:400]); return
    p = os.path.join(ROOT, "artifacts", name)
    open(p, "wb").write(png)
    img = Image.open(p)
    img.rotate(-90, expand=True).save(os.path.join(ROOT, "artifacts", name.replace(".png", "_reading.png")))
    print("OK", name, img.size)

def compare():
    """Apila mi render (lectura) sobre la foto de referencia para comparar."""
    mine = Image.open(os.path.join(ROOT, "artifacts", "_lab_reading.png")).convert("RGB")
    ref_path = os.path.join(ROOT, "artifacts", "_ref_reading.png")
    if not os.path.exists(ref_path):
        return
    ref = Image.open(ref_path).convert("RGB")
    w = 1000
    mine2 = mine.resize((w, round(w * mine.height / mine.width)))
    ref2 = ref.resize((w, round(w * ref.height / ref.width)))
    canvas = Image.new("RGB", (w, mine2.height + ref2.height + 20), (255, 255, 255))
    canvas.paste(mine2, (0, 0))
    canvas.paste(ref2, (0, mine2.height + 20))
    canvas.save(os.path.join(ROOT, "artifacts", "_compare.png"))
    print("comparacion -> _compare.png")


if __name__ == "__main__":
    z = build()
    open(os.path.join(ROOT, "artifacts", "_lab.zpl"), "w", encoding="ascii").write(z)
    render(z, "_lab.png")
    compare()
