"""
Cuánta «confeti» tiene una malla: islas de pocos cuadriláteros que quedan sueltas (flotando), contra láminas grandes y
conectadas que sí son superficie. Dos cuadriláteros están conectados si comparten un vértice.

Uso: python mesh_islands.py <malla.bin>… [--minimo 20]
Imprime, por malla: cuadriláteros, componentes, y qué parte de los cuadriláteros está en islas de menos de `minimo`.
"""
import struct
import sys
from pathlib import Path

import numpy as np


def read_quads(path):
    data = Path(path).read_bytes()
    _, _, n, q, _ = struct.unpack_from("<iiiif", data, 0)
    return n, np.frombuffer(data, dtype="<i4", count=q * 4, offset=20 + n * 12 + n * 3).reshape(q, 4)


def components(n, quads):
    """Unión-búsqueda sobre los vértices; devuelve el tamaño (en cuadriláteros) del componente de cada cuadrilátero."""
    parent = np.arange(n)

    def find(x):
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    for a, b, c, d in quads:
        ra = find(a)
        for v in (b, c, d):
            rv = find(v)
            if rv != ra:
                parent[rv] = ra
    roots = np.array([find(q[0]) for q in quads])
    _, inverse, counts = np.unique(roots, return_inverse=True, return_counts=True)
    return counts[inverse], len(counts)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    minimo = 20
    if "--minimo" in sys.argv:
        minimo = int(sys.argv[sys.argv.index("--minimo") + 1])
        args = [a for a in args if a != str(minimo)]
    print("%-52s %8s %8s | %12s %10s" % ("malla", "quads", "islas", "en islas<%d" % minimo, "mayor"))
    for p in args:
        n, quads = read_quads(p)
        if len(quads) == 0:
            continue
        size, comps = components(n, quads)
        small = (size < minimo).mean() * 100
        print("%-52s %8d %8d | %11.1f%% %9.1f%%" % (Path(p).stem[:52], len(quads), comps, small, 100 * size.max() / len(quads)))


if __name__ == "__main__":
    main()
