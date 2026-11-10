#!/usr/bin/env python3
"""Laboratorio L5 (docs/mapeador-bodega.md, sección 12.6): anota los signos vitales del teléfono
durante una sesión larga de mapeo (batería, temperatura, estado térmico, memoria de la app).

Se corre en la PC, con el teléfono conectado por adb (USB o Wi-Fi), y escribe una fila por muestra
en un CSV. SOLO LEE (dumpsys / pidof): no toca la pantalla, no manda toques, no instala nada.
Sirve para un teléfono personal. Para parar: Ctrl+C (o --minutos).

Uso:
  python tools/vitals_logger.py --serial 47091FDAS009UK --cada 30 --minutos 70 --salida vitales.csv
  python tools/vitals_logger.py --serial 47091FDAS009UK --una-vez          (prueba rápida)
Columnas: hora, minuto, bateria_pct, bateria_c, estado_termico (0 normal … 6 apagado de emergencia),
temp_max_c, pss_mb (memoria de la app), vive (la app tiene proceso), al_frente (la app está en pantalla).
"""
import argparse
import csv
import os
import re
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path

PACKAGE = "com.example.pmmovil.mapeador"
DEFAULT_ADB = Path(os.environ.get("LOCALAPPDATA", "")) / "Android" / "Sdk" / "platform-tools" / "adb.exe"


def adb(adb_path, serial, *args, timeout=20):
    cmd = [str(adb_path)]
    if serial:
        cmd += ["-s", serial]
    cmd += list(args)
    try:
        done = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout, encoding="utf-8", errors="replace")
        return done.stdout
    except (subprocess.TimeoutExpired, OSError):
        return ""


def sample(adb_path, serial, package=PACKAGE):
    battery = adb(adb_path, serial, "shell", "dumpsys", "battery")
    level = re.search(r"^\s*level:\s*(\d+)", battery, re.M)
    temp = re.search(r"^\s*temperature:\s*(\d+)", battery, re.M)

    thermal = adb(adb_path, serial, "shell", "dumpsys", "thermalservice")
    status = re.search(r"Thermal Status:\s*(\d+)", thermal)
    temps = [float(v) for v in re.findall(r"mValue=(-?[\d.]+),", thermal) if float(v) > -50]

    mem = adb(adb_path, serial, "shell", "dumpsys", "meminfo", package)
    pss = re.search(r"TOTAL PSS:\s*(\d+)", mem) or re.search(r"^\s*TOTAL\s+(\d+)", mem, re.M)

    alive = bool(adb(adb_path, serial, "shell", "pidof", package).strip())
    top = adb(adb_path, serial, "shell", "dumpsys activity activities")
    front = re.search(r"topResumedActivity=.*?\s([\w.]+)/", top)

    return {
        "bateria_pct": int(level.group(1)) if level else "",
        "bateria_c": round(int(temp.group(1)) / 10.0, 1) if temp else "",
        "estado_termico": int(status.group(1)) if status else "",
        "temp_max_c": round(max(temps), 1) if temps else "",
        "pss_mb": round(int(pss.group(1)) / 1024.0, 1) if pss else "",
        "vive": int(alive),
        "al_frente": int(bool(front and front.group(1) == package)),
    }


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--serial", help="serie del teléfono (adb devices); con USB y Wi-Fi a la vez, elegir uno")
    ap.add_argument("--adb", default=str(DEFAULT_ADB), help="ruta de adb.exe")
    ap.add_argument("--cada", type=int, default=30, help="segundos entre muestras (30 por omisión)")
    ap.add_argument("--minutos", type=float, default=0, help="parar solo a los N minutos (0 = hasta Ctrl+C)")
    ap.add_argument("--salida", default="vitales.csv", help="archivo CSV de salida")
    ap.add_argument("--una-vez", action="store_true", help="una muestra, se imprime y termina")
    ap.add_argument("--paquete", default=PACKAGE, help="app a vigilar (por omisión el mapeador)")
    args = ap.parse_args()

    if args.una_vez:
        for key, value in sample(args.adb, args.serial, args.paquete).items():
            print(f"{key}: {value}")
        return

    fields = ["hora", "minuto", "bateria_pct", "bateria_c", "estado_termico", "temp_max_c", "pss_mb", "vive", "al_frente"]
    started = time.time()
    new_file = not Path(args.salida).exists()
    with open(args.salida, "a", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fields)
        if new_file:
            writer.writeheader()
        print(f"Anotando cada {args.cada} s en {args.salida} (Ctrl+C para parar)")
        try:
            while True:
                row = {"hora": datetime.now().strftime("%H:%M:%S"), "minuto": round((time.time() - started) / 60.0, 1)}
                row.update(sample(args.adb, args.serial, args.paquete))
                writer.writerow(row)
                f.flush()
                print(f"{row['hora']}  min {row['minuto']:>5}  bat {row['bateria_pct']}% {row['bateria_c']}°C  "
                      f"térmico {row['estado_termico']}  mem {row['pss_mb']} MB  "
                      f"{'vive' if row['vive'] else 'SIN PROCESO'}  {'al frente' if row['al_frente'] else 'fuera de pantalla'}")
                if args.minutos and (time.time() - started) / 60.0 >= args.minutos:
                    break
                time.sleep(args.cada)
        except KeyboardInterrupt:
            print("Parado por el usuario.")


if __name__ == "__main__":
    sys.exit(main())
