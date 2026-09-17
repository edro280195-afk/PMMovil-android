#!/usr/bin/env python3
"""Convierte el logo de marca (imex_logo.png) a un campo grafico ZPL (^GFA).

El logo se imprime en una etiqueta Zebra ZT411 (203 dpi) en orientacion portrait
(830 x 1035 dots) que el operador lee girando la etiqueta 90 grados. Por eso el
bitmap se almacena rotado: en pantalla/lectura mide ~288 x 118 dots y se guarda
girado 90 grados a ~120 x 288 dots (15 bytes/fila x 288 filas = 4320 bytes), que
es exactamente el formato que ya espera ZebraZplBuilder en el ^FO650,34.

Uso:
    python tools/png_to_zpl_logo.py

Genera:
    artifacts/imex_logo_gfa.txt        -> string ^GFA,... listo para pegar
    artifacts/imex_logo_reading.png    -> preview en orientacion de lectura
    artifacts/imex_logo_stored.png     -> preview del bitmap tal cual va al ZPL

Requiere Pillow (pip install Pillow).
"""
from __future__ import annotations

import os
from PIL import Image

# --- Parametros (ajustables) -------------------------------------------------
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app", "src", "main", "res", "drawable", "imex_logo.png")
OUT_DIR = os.path.join(ROOT, "artifacts")

READING_WIDTH = 460          # ancho del logo cuando se lee la etiqueta (dots)
STORED_WIDTH_BYTES = 24      # 24 bytes -> 192 dots de ancho almacenado (mult. de 8)
THRESHOLD = 160              # < THRESHOLD -> negro. Sube para engrosar el trazo.
ROTATE_CCW = True            # True: rota 90 antihorario para almacenar


def load_flattened(path: str) -> Image.Image:
    """Carga el PNG, aplana la transparencia sobre blanco y pasa a escala de grises."""
    img = Image.open(path).convert("RGBA")
    white = Image.new("RGBA", img.size, (255, 255, 255, 255))
    flat = Image.alpha_composite(white, img)
    return flat.convert("L")


def to_reading_bitmap(gray: Image.Image) -> Image.Image:
    """Reescala a orientacion de lectura y umbraliza a 1-bit (negro/blanco)."""
    w = READING_WIDTH
    h = max(1, round(w * gray.height / gray.width))
    resized = gray.resize((w, h), Image.LANCZOS)
    # '1' con dither=NONE da blanco/negro limpio segun THRESHOLD
    bw = resized.point(lambda p: 0 if p < THRESHOLD else 255, mode="L")
    return bw.convert("1", dither=Image.NONE)


def to_stored_bitmap(reading: Image.Image) -> Image.Image:
    """Rota a orientacion de almacenamiento y rellena el ancho a multiplo de 8."""
    angle = 90 if ROTATE_CCW else -90
    rotated = reading.rotate(angle, expand=True)          # PIL: positivo = antihorario
    target_w = STORED_WIDTH_BYTES * 8                     # 120 dots
    if rotated.width > target_w:
        rotated = rotated.crop((0, 0, target_w, rotated.height))
    if rotated.width < target_w:
        # rellena a la derecha con blanco (255) para no agregar puntos negros
        canvas = Image.new("1", (target_w, rotated.height), 1)
        canvas.paste(rotated, (0, 0))
        rotated = canvas
    return rotated


def to_gfa(stored: Image.Image) -> str:
    """Empaqueta el bitmap 1-bit a hex (MSB primero) y arma el comando ^GFA."""
    width, height = stored.width, stored.height
    bytes_per_row = width // 8
    px = stored.load()
    hex_chars = []
    for y in range(height):
        for bx in range(bytes_per_row):
            byte = 0
            for bit in range(8):
                x = bx * 8 + bit
                # en modo '1' de PIL: 0 = negro, 255 = blanco. ZPL: 1 = punto negro.
                if px[x, y] == 0:
                    byte |= (1 << (7 - bit))
            hex_chars.append(f"{byte:02X}")
    data = "".join(hex_chars)
    total = bytes_per_row * height
    return f"^GFA,{total},{total},{bytes_per_row},{data}"


def main() -> None:
    os.makedirs(OUT_DIR, exist_ok=True)
    gray = load_flattened(SRC)
    reading = to_reading_bitmap(gray)
    stored = to_stored_bitmap(reading)

    reading.save(os.path.join(OUT_DIR, "imex_logo_reading.png"))
    stored.save(os.path.join(OUT_DIR, "imex_logo_stored.png"))

    gfa = to_gfa(stored)
    out_txt = os.path.join(OUT_DIR, "imex_logo_gfa.txt")
    with open(out_txt, "w", encoding="ascii") as fh:
        fh.write(gfa)

    print(f"Origen        : {SRC}")
    print(f"Lectura       : {reading.width} x {reading.height} dots")
    print(f"Almacenado    : {stored.width} x {stored.height} dots "
          f"({stored.width // 8} bytes/fila)")
    print(f"^GFA bytes    : {(stored.width // 8) * stored.height}")
    print(f"GFA escrito en: {out_txt}")
    print(f"Previews      : imex_logo_reading.png / imex_logo_stored.png")


if __name__ == "__main__":
    main()
