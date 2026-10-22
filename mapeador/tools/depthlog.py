"""Lector en Python del registro de profundidad (formato de core/DepthLog.kt)."""
import struct
import zlib

import numpy as np


def quat_to_matrix(x, y, z, w):
    n = (x * x + y * y + z * z + w * w) ** 0.5
    x, y, z, w = x / n, y / n, z / n, w / n
    return np.array([
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)],
    ])


class Frame:
    pass


def read_log(path, limit=None):
    data = open(path, "rb").read()
    magic, version = struct.unpack_from(">ii", data, 0)
    assert magic == 0x504D4D44, hex(magic)
    off = 8
    frames = []
    while off + 8 <= len(data):
        size, total = struct.unpack_from(">ii", data, off)
        off += 8
        if off + total > len(data):
            break
        payload = zlib.decompress(data[off:off + total])
        off += total
        f = Frame()
        o = 0
        f.ts = struct.unpack_from(">q", payload, o)[0]; o += 8
        vals = struct.unpack_from(">7d", payload, o); o += 56
        f.t = np.array(vals[0:3])
        f.q = vals[3:7]
        f.r = quat_to_matrix(*f.q)
        f.fx, f.fy, f.cx, f.cy = struct.unpack_from(">4d", payload, o); o += 32
        f.w, f.h = struct.unpack_from(">ii", payload, o); o += 8
        flags = payload[o]; o += 1
        n = f.w * f.h
        f.raw = f.conf = f.smooth = None
        if flags & 1:
            f.raw = np.frombuffer(payload, dtype=">u2", count=n, offset=o).astype(np.float64).reshape(f.h, f.w); o += n * 2
        if flags & 2:
            f.conf = np.frombuffer(payload, dtype=np.uint8, count=n, offset=o).reshape(f.h, f.w); o += n
        if flags & 4:
            f.smooth = np.frombuffer(payload, dtype=">u2", count=n, offset=o).astype(np.float64).reshape(f.h, f.w); o += n * 2
        frames.append(f)
        if limit and len(frames) >= limit:
            break
    return frames


def backproject(f, depth_mm):
    """Puntos del mundo (N, 3) de una imagen de profundidad en mm, y los índices (v, u) de cada uno."""
    v, u = np.nonzero(depth_mm > 0)
    z = depth_mm[v, u] / 1000.0
    x = (u + 0.5 - f.cx) / f.fx * z
    y = (v + 0.5 - f.cy) / f.fy * z
    pc = np.stack([x, y, z], axis=1)
    flip = np.diag([1.0, -1.0, -1.0])
    pw = (f.r @ flip @ pc.T).T + f.t
    return pw, v, u, z
