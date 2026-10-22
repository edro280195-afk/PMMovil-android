"""
Reconstrucción con fotogrametría (COLMAP) de un mapa guardado por el mapeador: la «vía 2» (como Scaniverse: se
reconstruye después de la captura, con todas las fotos, no en vivo con la profundidad estimada de cada cuadro).

Usa las fotos de `keyframes/` y las POSES de ARCore de `keyframes.json`:

  1. extrae rasgos SIFT con la cámara conocida (PINHOLE con los intrínsecos de ARCore), con umbrales permisivos porque
     las fotos son de una oficina casi lisa;
  2. empareja solo las fotos que miran a lo mismo según esas poses (en CPU un emparejado exhaustivo tardaría horas);
  3. estructura a partir de las fotos solas (queda en varios fragmentos si las fotos no se enlazan);
  4. alinea cada fragmento a las posiciones de ARCore (le da escala en metros y orientación) y mide cuánto difieren
     las cámaras de COLMAP de las de ARCore;
  5. malla de Delaunay de los puntos de cada fragmento (la reconstrucción densa de COLMAP necesita CUDA: este equipo no
     tiene GPU NVIDIA);
  6. convierte las mallas a `mesh.bin` (formato PMMH) y escribe `views-colmap.json` (tres fotos por fragmento) para
     dibujarlas sobre las fotos con `render_mesh.py`.

Uso:
    python reconstruct_colmap.py <carpeta-del-mapa> [--colmap <colmap.exe>] [--vecinos 25] [--saltar-rasgos]
    python render_mesh.py <carpeta-del-mapa>/colmap-trabajo views-colmap.json      (deja los PNG en colmap-trabajo/png)
    python compare_colmap_live.py <carpeta-del-mapa>/colmap-trabajo <malla-en-vivo.bin>   (puntos de COLMAP vs malla)

Deja todo en <carpeta-del-mapa>/colmap-trabajo/ y un resumen en resumen.txt.
Nota: la triangulación con poses fijas (`point_triangulator`) se cae en COLMAP 4.2.1 con este tipo de modelo (código
0xC0000409 sin mensaje), por eso se usa la estructura a partir de fotos más la alineación a ARCore.
"""
import argparse
import json
import math
import shutil
import struct
import subprocess
import sys
import time
from pathlib import Path

import numpy as np

DEFAULT_COLMAP = "C:/Codigos/herramientas/colmap-4.2.1-nocuda/app/bin/colmap.exe"
FLIP = np.diag([1.0, -1.0, -1.0])


def quat_to_matrix(x, y, z, w):
    n = math.sqrt(x * x + y * y + z * z + w * w)
    x, y, z, w = x / n, y / n, z / n, w / n
    return np.array([
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)],
    ])


class Keyframe:
    def __init__(self, o):
        self.id = o["id"]
        self.file = o["file"]
        self.k = o["k"]
        q = o["q"]
        self.q = q
        self.t = o["t"]
        self.r_wg = quat_to_matrix(q[0], q[1], q[2], q[3])  # worldFromGl (ARCore camera.pose)
        self.c = np.array(o["t"])  # centro de la cámara en el mundo
        self.forward = self.r_wg @ np.array([0.0, 0.0, -1.0])


def run(colmap, args, cwd, log, label):
    t0 = time.time()
    print(f"[{label}] ...", flush=True)
    proc = subprocess.run([colmap] + [str(a) for a in args], cwd=str(cwd), capture_output=True, text=True)
    out = (proc.stdout or "") + (proc.stderr or "")
    with open(log, "a", encoding="utf-8") as f:
        f.write(f"\n===== {label}: colmap {' '.join(str(a) for a in args)}\n{out}\n")
    dt = time.time() - t0
    print(f"[{label}] {'ok' if proc.returncode == 0 else 'FALLÓ (código %d)' % proc.returncode} en {dt:.1f} s", flush=True)
    if proc.returncode != 0:
        print(out[-1500:])
    return proc.returncode, out, dt


def pair_list(keyframes, neighbors, max_dist=5.0, max_angle_deg=50.0):
    """Pares de fotos que miran a lo mismo: cerca y con ejes ópticos parecidos."""
    n = len(keyframes)
    centers = np.array([kf.c for kf in keyframes])
    fwd = np.array([kf.forward for kf in keyframes])
    pairs = set()
    for i in range(n):
        d = np.linalg.norm(centers - centers[i], axis=1)
        ang = np.degrees(np.arccos(np.clip(fwd @ fwd[i], -1, 1)))
        score = d / max_dist + ang / max_angle_deg
        ok = (d < max_dist) & (ang < max_angle_deg)
        ok[i] = False
        cand = np.nonzero(ok)[0]
        cand = cand[np.argsort(score[cand])][:neighbors]
        for j in cand:
            pairs.add((min(i, int(j)), max(i, int(j))))
    for i in range(n - 1):  # siempre las consecutivas
        pairs.add((i, i + 1))
    return sorted(pairs)


def read_ply(path):
    with open(path, "rb") as f:
        header = []
        while True:
            line = f.readline().decode("ascii", errors="replace").strip()
            header.append(line)
            if line == "end_header":
                break
        fmt = next(h for h in header if h.startswith("format")).split()[1]
        elements = []
        cur = None
        for h in header:
            p = h.split()
            if p and p[0] == "element":
                cur = {"name": p[1], "count": int(p[2]), "props": []}
                elements.append(cur)
            elif p and p[0] == "property" and cur is not None:
                cur["props"].append(p[1:])
        types = {"char": "i1", "uchar": "u1", "short": "i2", "ushort": "u2", "int": "i4", "uint": "u4",
                 "float": "f4", "float32": "f4", "double": "f8", "int8": "i1", "uint8": "u1", "int32": "i4", "uint32": "u4"}
        data = {}
        if fmt == "ascii":
            tokens = f.read().decode("ascii", errors="replace").split()
            pos = 0
            for el in elements:
                rows = []
                for _ in range(el["count"]):
                    row = []
                    for pr in el["props"]:
                        if pr[0] == "list":
                            cnt = int(tokens[pos])
                            pos += 1
                            row.append([float(x) for x in tokens[pos:pos + cnt]])
                            pos += cnt
                        else:
                            row.append(float(tokens[pos]))
                            pos += 1
                    rows.append(row)
                names = [pr[-1] for pr in el["props"]]
                if all(pr[0] != "list" for pr in el["props"]):
                    arr = np.array(rows, dtype=np.float64).reshape(-1, len(names))
                    data[el["name"]] = {nm: arr[:, i] for i, nm in enumerate(names)}
                else:
                    data[el["name"]] = rows
        else:
            endian = "<" if "little" in fmt else ">"
            buf = f.read()
            pos = 0
            for el in elements:
                if any(pr[0] == "list" for pr in el["props"]):
                    rows = []
                    for _ in range(el["count"]):
                        row = []
                        for pr in el["props"]:
                            if pr[0] == "list":
                                ct, it = types[pr[1]], types[pr[2]]
                                cnt = np.frombuffer(buf, dtype=endian + ct, count=1, offset=pos)[0]
                                pos += np.dtype(ct).itemsize
                                idx = np.frombuffer(buf, dtype=endian + it, count=int(cnt), offset=pos)
                                pos += np.dtype(it).itemsize * int(cnt)
                                row.append(idx.tolist())
                            else:
                                t = types[pr[0]]
                                row.append(float(np.frombuffer(buf, dtype=endian + t, count=1, offset=pos)[0]))
                                pos += np.dtype(t).itemsize
                        rows.append(row)
                    data[el["name"]] = rows
                else:
                    dt = np.dtype([(pr[1], endian + types[pr[0]]) for pr in el["props"]])
                    arr = np.frombuffer(buf, dtype=dt, count=el["count"], offset=pos)
                    pos += dt.itemsize * el["count"]
                    data[el["name"]] = {nm: arr[nm] for nm in dt.names}
        return data


def ply_to_pmmh(ply_path, out_path):
    """Convierte una malla PLY de triángulos a mesh.bin (formato PMMH): cada triángulo es un cuadrilátero degenerado."""
    data = read_ply(ply_path)
    v = data["vertex"]
    pos = np.stack([v["x"], v["y"], v["z"]], axis=1).astype(np.float32)
    faces = data["face"]
    tris = np.array([r[0][:3] for r in faces if len(r[0]) >= 3], dtype=np.int32)
    if len(tris) == 0:
        return len(pos), 0
    nor = np.zeros((len(pos), 3), np.float64)
    a, b, c = pos[tris[:, 0]].astype(np.float64), pos[tris[:, 1]].astype(np.float64), pos[tris[:, 2]].astype(np.float64)
    fn = np.cross(b - a, c - a)
    for k in range(3):
        np.add.at(nor, tris[:, k], fn)
    ln = np.linalg.norm(nor, axis=1, keepdims=True)
    nor = np.where(ln > 1e-12, nor / np.maximum(ln, 1e-12), np.array([0.0, 1.0, 0.0]))
    quads = np.stack([tris[:, 0], tris[:, 1], tris[:, 2], tris[:, 2]], axis=1).astype(np.int32)
    with open(out_path, "wb") as f:
        f.write(struct.pack("<iiiif", 0x484D4D50, 1, len(pos), len(quads), 0.0))
        f.write(pos.astype("<f4").tobytes())
        f.write(np.round(np.clip(nor, -1, 1) * 127).astype(np.int8).tobytes())
        f.write(quads.astype("<i4").tobytes())
    return len(pos), len(quads)


def ply_points(ply_path):
    data = read_ply(ply_path)
    v = data["vertex"]
    return np.stack([v["x"], v["y"], v["z"]], axis=1)


def read_images_txt(path):
    poses = {}
    lines = [l for l in Path(path).read_text(encoding="utf-8").splitlines() if l and not l.startswith("#")]
    for i in range(0, len(lines), 2):
        p = lines[i].split()
        w, x, y, z = [float(v) for v in p[1:5]]
        t = np.array([float(v) for v in p[5:8]])
        poses[p[9]] = (quat_to_matrix(x, y, z, w), t)
    return poses


def parse_analyzer(out):
    info = {}
    for line in out.splitlines():
        t = line.split("]")[-1].strip()
        for key, name in (("Registered images", "imagenes"), ("Points", "puntos"), ("Mean track length", "track"), ("Mean reprojection error", "reproy")):
            if t.startswith(key + ":"):
                info[name] = float(t.split(":")[1].strip().replace("px", ""))
    return info


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("mapa")
    ap.add_argument("--colmap", default=DEFAULT_COLMAP)
    ap.add_argument("--vecinos", type=int, default=25)
    ap.add_argument("--saltar-rasgos", action="store_true", help="reusa la base de datos si ya existe")
    ap.add_argument("--min-fotos", type=int, default=8, help="los fragmentos con menos fotos se ignoran")
    a = ap.parse_args()

    mapdir = Path(a.mapa)
    kfs = [Keyframe(o) for o in json.loads((mapdir / "keyframes.json").read_text(encoding="utf-8"))]
    kfs = [kf for kf in kfs if (mapdir / "keyframes" / kf.file).exists()]
    by_name = {kf.file: kf for kf in kfs}
    work = mapdir / "colmap-trabajo"
    work.mkdir(exist_ok=True)
    log = work / "registro.txt"
    summary = []

    def say(msg):
        print(msg, flush=True)
        summary.append(msg)

    say(f"{len(kfs)} fotos con pose de ARCore; trabajo en {work}")
    db = work / "database.db"
    images = mapdir / "keyframes"
    k0 = kfs[0].k

    if not (a.saltar_rasgos and db.exists()):
        if db.exists():
            db.unlink()
        rc, out, dt = run(a.colmap, [
            "feature_extractor", "--database_path", db, "--image_path", images,
            "--ImageReader.camera_model", "PINHOLE", "--ImageReader.single_camera", 1,
            "--ImageReader.camera_params", "%f,%f,%f,%f" % (k0["fx"], k0["fy"], k0["cx"], k0["cy"]),
            "--FeatureExtraction.use_gpu", 0, "--SiftExtraction.max_num_features", 16384,
            "--SiftExtraction.peak_threshold", 0.0025, "--SiftExtraction.edge_threshold", 15,
        ], work, log, "rasgos SIFT")
        say(f"rasgos SIFT (permisivos): {dt:.0f} s")
        if rc != 0:
            sys.exit(1)
        pairs = pair_list(kfs, a.vecinos)
        (work / "pares.txt").write_text("\n".join(f"{kfs[i].file} {kfs[j].file}" for i, j in pairs) + "\n", encoding="utf-8")
        say(f"pares a emparejar (según las poses de ARCore): {len(pairs)} de {len(kfs) * (len(kfs) - 1) // 2} posibles")
        rc, out, dt = run(a.colmap, [
            "matches_importer", "--database_path", db, "--match_list_path", work / "pares.txt", "--match_type", "pairs",
            "--FeatureMatching.use_gpu", 0, "--SiftMatching.max_ratio", 0.9, "--SiftMatching.max_distance", 0.8,
        ], work, log, "emparejado")
        say(f"emparejado: {dt:.0f} s")
        if rc != 0:
            sys.exit(1)

    sparse = work / "sparse"
    if sparse.exists():
        shutil.rmtree(sparse)
    sparse.mkdir()
    rc, out, dt = run(a.colmap, ["mapper", "--database_path", db, "--image_path", images, "--output_path", sparse], work, log, "estructura (mapper)")
    say(f"estructura a partir de las fotos solas: {dt:.0f} s")
    if rc != 0:
        sys.exit(1)

    # posiciones de ARCore de cada foto, para darle escala en metros y orientación a cada fragmento
    refs = work / "referencias-arcore.txt"
    refs.write_text("\n".join("%s %.6f %.6f %.6f" % (kf.file, kf.c[0], kf.c[1], kf.c[2]) for kf in kfs) + "\n", encoding="utf-8")

    aligned_root = work / "alineado"
    if aligned_root.exists():
        shutil.rmtree(aligned_root)
    aligned_root.mkdir()
    fragments = []
    views = []
    for model in sorted([d for d in sparse.iterdir() if d.is_dir()], key=lambda d: int(d.name)):
        rc, out, _ = run(a.colmap, ["model_analyzer", "--path", model], work, log, f"análisis del fragmento {model.name}")
        info = parse_analyzer(out)
        if info.get("imagenes", 0) < a.min_fotos:
            say(f"fragmento {model.name}: {int(info.get('imagenes', 0))} fotos (se ignora)")
            continue
        dest = aligned_root / model.name
        dest.mkdir()
        rc, out, _ = run(a.colmap, [
            "model_aligner", "--input_path", model, "--output_path", dest, "--ref_images_path", refs,
            "--ref_is_gps", 0, "--alignment_type", "custom", "--alignment_max_error", 0.5,
        ], work, log, f"alineación del fragmento {model.name} a las posiciones de ARCore")
        if rc != 0:
            say(f"fragmento {model.name}: no se pudo alinear a ARCore")
            continue
        txt = dest / "txt"
        txt.mkdir()
        run(a.colmap, ["model_converter", "--input_path", dest, "--output_path", txt, "--output_type", "TXT"], work, log, f"fragmento {model.name} a TXT")
        poses = read_images_txt(txt / "images.txt")
        errs = []
        for name, (r, t) in poses.items():
            if name in by_name:
                c = -r.T @ t
                errs.append(np.linalg.norm(c - by_name[name].c))
        ply = work / f"puntos-fragmento-{model.name}.ply"
        run(a.colmap, ["model_converter", "--input_path", dest, "--output_path", ply, "--output_type", "PLY"], work, log, f"fragmento {model.name} a PLY")
        mesh_ply = work / f"malla-fragmento-{model.name}.ply"
        rcm, outm, dtm = run(a.colmap, ["delaunay_mesher", "--input_path", dest, "--input_type", "sparse", "--output_path", mesh_ply], work, log, f"malla de Delaunay del fragmento {model.name}")
        nv = nq = 0
        if rcm == 0 and mesh_ply.exists():
            nv, nq = ply_to_pmmh(mesh_ply, work / f"malla-fragmento-{model.name}.bin")
        e = np.array(errs) if errs else np.array([float("nan")])
        say("fragmento %s: %d fotos, %d puntos, reproy. %.2f px | tras alinear a ARCore, la cámara de COLMAP difiere de la de ARCore: mediana %.1f cm, p90 %.1f cm | malla: %d vért., %d triáng."
            % (model.name, info["imagenes"], info["puntos"], info.get("reproy", float("nan")), np.median(e) * 100, np.percentile(e, 90) * 100, nv, nq))
        if nq > 0:
            names = sorted(n for n in poses if n in by_name)
            for n in ([names[len(names) // 4], names[len(names) // 2], names[3 * len(names) // 4]] if names else []):
                kf = by_name[n]
                views.append({"name": f"colmap-frag{model.name}-{Path(n).stem}", "mesh": f"malla-fragmento-{model.name}.bin",
                              "q": kf.q, "t": kf.t, "k": kf.k, "image": str(images / n)})
        fragments.append((model.name, info, ply))
    (work / "views-colmap.json").write_text(json.dumps(views, indent=1), encoding="utf-8")

    allpts = []
    for name, info, ply in fragments:
        try:
            allpts.append(ply_points(ply))
        except Exception as ex:  # noqa: BLE001
            say(f"no se pudo leer {ply.name}: {ex}")
    if allpts:
        pts = np.concatenate(allpts)
        np.save(work / "puntos.npy", pts)
        say("puntos alineados de todos los fragmentos: %d; extensión x/y/z (m): %.1f / %.1f / %.1f" % ((len(pts),) + tuple(np.ptp(pts, axis=0))))
    (work / "resumen.txt").write_text("\n".join(summary) + "\n", encoding="utf-8")
    print("Listo. Resumen en", work / "resumen.txt")


if __name__ == "__main__":
    main()
