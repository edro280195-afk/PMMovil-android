# mapeador — Prototipo de oficina del mapeador de bodega

Módulo **aparte de `:app`**, igual que `:arlab` y `:mapspike`: no comparte código ni dependencias con
la app de producción. Se instala como `com.example.pmmovil.mapeador` («PMM Mapeador») y convive con
PMMovil en el mismo teléfono.

Contexto, decisiones y plan: [`docs/mapeador-bodega.md`](../docs/mapeador-bodega.md).

**Estado (22-oct-2026):** **v9 instalada y probada en el Pixel 9 Pro XL**, con el veredicto de Eduardo: «sí, se corrigió muy bien esa
malla». Arregla lo que él señaló —«la malla no se reconstruye correctamente después del primer reconocimiento»—: el vértice de cada
celda se fijaba con los dos primeros cuadros que veían el lugar y ya no se movía. 94 pruebas JVM en verde; en el aparato el costo por
cuadro no subió (`integra` mediana 63 ms) y no agrega retiro de cuadriláteros. Causa, arreglo y cifras:
[`docs/mapeador-bodega.md` §8.6](../docs/mapeador-bodega.md); dos hilos abiertos que NO son de la malla (Ubicar y los mapas que
desaparecen del teléfono) en §8.7. El módulo ya está **versionado** en la rama `idiomas` (commit del 22-oct), todavía **sin empujar**.

## Para qué es

Ver **cómo funcionaría** un mapa propio de la nave antes de construirlo para la nave: se camina un
lugar con el teléfono, se marcan ubicaciones y, después, la cámara se ubica en ese mapa y muestra
flotando las tarjetas de lo que el sistema dice que hay en cada ubicación.

| Modo | Qué hace |
|---|---|
| **Mapear** | ARCore da el movimiento. **La geometría se ve cubriéndose en vivo:** la profundidad de ARCore se integra en un volumen TSDF (voxeles de 10 cm, en bloques de 8×8×8) y de él sale una malla delgada que se pinta sobre la cámara con relleno translúcido y cuadrícula (piso azul, muros turquesa, techo violeta; lo recién escaneado brilla en blanco), con prueba de profundidad y, opcional, **oclusión** con la profundidad actual (apagada por omisión). El panel cuenta los m² de **piso** (no los de muros y muebles). Además se guardan fotogramas clave con su pose, se extraen rasgos ORB y se triangulan los **puntos de anclaje** (amarillos) con las poses de ARCore. **Marcar ubicaciones** con la mira central. Interruptores: Superficies, Anclas, *Planos de ARCore* (piso, muros y techo que detecta ARCore), Oclusión, fuente de profundidad (mixta anclada / cruda / suave sin anclar) y *Mapa de prof.* (mapa de calor para comprobar la alineación). Si ARCore deja de dar profundidad (parado, girando sin avanzar, muy rápido, o frente a un muro liso) el panel lo dice y por qué. |
| **Ubicar** | Carga un mapa, compara los rasgos del cuadro con el mapa (ORB + `solvePnPRansac`), fusiona con el movimiento de ARCore y pinta una **tarjeta** sobre cada ubicación. **Llévame aquí** (flecha y distancia) y **auditar** (✓ / ✗ / +). |
| **Exportar** | Escribe las poses de ARCore en el formato de texto de COLMAP para usarlas como punto de partida en la PC (Etapa 0 de la nave). |

Las tarjetas usan entradas de **demostración** (`DemoData`, marcadas «DEMO») con la misma forma que
`OccupancyEntry` del API de ocupación, para sustituirlas por la ocupación real sin rehacer nada.

## Correrlo

Por Wi-Fi para poder caminar con el teléfono mientras se lee el log: con el teléfono por USB,
`adb tcpip 5555` y luego `adb connect <ip-del-teléfono>:5555` (la IP sale de `adb shell ip -f inet addr show wlan0`).

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :mapeador:assembleDebug        # APK solo arm64 (~49 MB)
adb -s <serie> install -r mapeador/build/outputs/apk/debug/mapeador-debug.apk
adb -s <serie> shell pm grant com.example.pmmovil.mapeador android.permission.CAMERA
adb -s <serie> shell am start -n com.example.pmmovil.mapeador/.MainActivity
```

Con el Pixel y otro equipo conectados, **siempre** `-s <serie>`. No reinstalar mientras alguien prueba:
mata el proceso y se pierde el mapa en construcción.

## Pruebas

```bash
./gradlew :mapeador:testDebugUnitTest                 # 93 pruebas JVM (geometría, TSDF y malla, mapa, ubicación, fusión, guardado); 6 se omiten solas sin datos reales
./gradlew :mapeador:assembleDebugAndroidTest          # APK de pruebas con OpenCV real
adb -s <serie> install -r mapeador/build/outputs/apk/androidTest/debug/mapeador-debug-androidTest.apk
adb -s <serie> shell am instrument -w com.example.pmmovil.mapeador.test/androidx.test.runner.AndroidJUnitRunner   # 6 pruebas
```

- La prueba **`exactObservationsGiveExactPoints`** triangula con observaciones sin ruido: si los ejes de
  ARCore y OpenCV o algún signo estuvieran mal, los puntos saldrían a metros. Cuadran al milímetro.
- La prueba **`fullPipelineLocalizesAQueryAndRecoversTheSessionOffset`** arma un mapa, ubica una cámara
  desde otro «marco de sesión» y recupera la transformación mapa←sesión: es la cadena que usa «Ubicar».

## Dónde quedan los datos

`/sdcard/Android/data/com.example.pmmovil.mapeador/files/` (se saca con `adb pull`):

| Ruta | Contenido |
|---|---|
| `maps/<id>/meta.json`, `locations.json`, `keyframes.json`, `landmarks.bin`, `mesh.bin`, `keyframes/*.jpg` | Un mapa (`mesh.bin` es la malla de superficies; los mapas hechos antes de la v3 no la traen). `colmap/` aparece al exportar. |
| `logs/ubicar-<fecha>.csv` | Un renglón por intento de ubicación: rasgos, coincidencias, inliers, error de reproyección, tiempos, cambio de la fijación. |
| `logs/auditoria.csv` | Cada ✓ / ✗ / + que se toca en una tarjeta. |
| `logs/profundidad-<fecha>.bin` | Grabación de la profundidad real de ARCore al mapear (cruda, confianza, suave, pose e intrínsecos de cada imagen; deflate, tope de 150 MB). Es la entrada de la herramienta de repetición. |
| `logs/planos-<fecha>.jsonl` | Los planos que tiene ARCore cada 0.5 s, con la misma marca de sesión que la profundidad: para evaluar en la PC si fijar la malla a los planos la mejora. Una línea por muestra, con la forma `{"t": ns, "planos": [{"id", "k", "q", "p", "ex", "ez", "poly"}]}`: `t` es la marca del cuadro en ns (la misma `timestampNs` de la profundidad), `k` es FLOOR / WALL / CEILING, `q` (x,y,z,w) y `p` (x,y,z) son la pose del centro del plano, `ex` y `ez` su extensión y `poly` su polígono `[x,z,…]` en coordenadas locales del plano. Solo planos en seguimiento y no absorbidos por otro; se graba siempre, esté o no encendido el chip. Existe desde la v8.1. |

Logcat: `adb logcat -s MAPEADOR`. Al mapear, cada 2 s sale una línea `geometria:` (voxeles, bloques y MB, vértices, cuadriláteros, área, ms por cuadro, % de profundidad cruda confiable / de relleno / vacía, giro) y, con la profundidad disponible, una línea `oclusion:` que compara la profundidad del centro de la pantalla con el hit-test de ARCore (si dice «coincide», la textura de profundidad está alineada).

## Repetir la reconstrucción en la PC

Con la profundidad grabada se ajusta la malla sin volver a caminar:

```bash
adb -s <serie> pull /sdcard/Android/data/com.example.pmmovil.mapeador/files/ <carpeta>
export PMM_REPLAY_DIR=<carpeta>
./gradlew :mapeador:testDebugUnitTest --offline --console=plain -i --tests "*ReplayTest*"
```

`ReplayTest` imprime las estadísticas de la profundidad (qué parte es cruda confiable, relleno o vacío; cociente suave÷cruda; velocidad y giro), una tabla que compara siete configuraciones de la malla (bloques, MB, voxeles, cuadriláteros, área, ms por cuadro) y el **acuerdo entre los puntos de anclaje (que salen de la imagen) y la malla (que sale de la profundidad)**. Deja en `<carpeta>/out/` las mallas, `views.json` y las imágenes de `png/` que dibuja `tools/render_mesh.py` (Python con numpy y OpenCV) sobre los fotogramas clave del mapa guardado. Sin `PMM_REPLAY_DIR` la prueba se omite. Para probar la herramienta sin datos reales: `PMM_SYNTH_OUT=<carpeta>` con `--tests "*RoomScanTest.writeSyntheticLogWhenAsked"` escribe un recorrido sintético de un cuarto.

Variables de entorno que leen las pruebas de `ReplayTest`:

| Variable | Para qué |
|---|---|
| `PMM_REPLAY_DIR` | Carpeta con el `files/` sacado del teléfono (`logs/profundidad-*.bin`, `maps/<id>/…`). Sin ella todo se omite. |
| `PMM_LOG` | Texto del nombre de la grabación `profundidad-*.bin` que se usa (por omisión, la más reciente). |
| `PMM_MAP` | Texto del nombre del mapa (`maps/<id>`) del que salen los puntos de anclaje y los fotogramas (por omisión, el más reciente). **Si eliges otra grabación con `PMM_LOG`, elige también su mapa**: la grabación `…181859` va con `map-…182041` y la `…182259` con `map-…182327`; si no, los puntos de anclaje y los fotogramas serían de otra corrida. |
| `PMM_EXPLORE=1` | Enciende las pruebas de exploración (`exploreStabilityOnRealDepth`, `exploreAnchoredOnRealDepth`, `exploreBandOnRealDepth`…): no son pruebas, son experimentos que imprimen tablas y escriben mallas. |
| `PMM_BATCH` | Lote de variantes de `exploreAnchoredOnRealDepth`: 1 y 2 (umbral de la cruda, pesos, banda, voxel), 3 (alrededor de «cruda ×2, votos libres 0.5»), 4 (limpieza visual), 5 (v7 contra v8 con cuadros buenos), 6 (cuánto anclar), **7 (la v7 tal cual la corría el teléfono contra la v8: la comparación de §8.4)** y **8 (vértice congelado contra recalculado, con y sin voxel fino: la comparación de §8.6)**. Escribe `out/acc-*.bin` y `out/acc-test-frames.txt`, la entrada de `tools/eval_variants.py`. **El nombre de una variante no puede traer `:` ni `.`:** en Windows el archivo no se escribe y la variante desaparece de la tabla sin dar error. |

`exploreAnchoredOnRealDepth` usa `out/valid-frames.txt` (lo escribe `tools/frame_validity.py`) para armar la malla solo con cuadros de pose
buena; sin ese archivo usa todos. Los cuadros se reparten en bloques de 8 que alternan **entrenamiento** (arman la malla) y **prueba**
(su cruda confiable es la verdad con la que se compara).

## Medir la malla contra lo que ARCore midió (sin adivinar)

«La malla se ve mal» no se arregla ajustando a ojo: con la profundidad grabada se mide. Todas estas herramientas (Python con
numpy y OpenCV, en `tools/`) leen el `profundidad-*.bin` con `depthlog.py`:

| Herramienta | Qué mide |
|---|---|
| `depth_stalls.py <registro.bin>…` | Cuándo ARCore **dejó de dar profundidad** (imagen suave vacía y marca de tiempo congelada) y qué hacía la cámara entonces (avance en cm por cuadro y giro en °). |
| `smooth_error_vs_raw.py <registro.bin>` | Cuánto se equivoca la profundidad **suave** de un cuadro, según la distancia y según qué tan lejos está el píxel de un dato crudo confiable (la «verdad» son los puntos crudos de otro cuadro llevados a este). |
| `raw_error_by_confidence.py <registro.bin>` | Cuánto se equivoca la profundidad **cruda** según su confianza (decide desde qué confianza vale la pena usarla). |
| `frame_validity.py <carpeta> [tolerancia_cm=10]` (con varias grabaciones o mapas: `PMM_LOG` y `PMM_MAP`) | Qué cuadros de la grabación se integraron con la pose EQUIVOCADA (imagen atascada con la pose de ahora): compara la pose guardada con la trayectoria de los fotogramas clave en la marca de tiempo de la imagen. Escribe `out/valid-frames.txt`, que usan el resto de las herramientas (`--validos`) y `ReplayTest.exploreAnchoredOnRealDepth`. **Correrla antes de sacar cualquier estadística de una grabación.** |
| `render_saved_mesh.py <carpeta-del-mapa>` | Dibuja la malla que GUARDÓ el teléfono (`mesh.bin`) sobre los fotogramas clave del mismo mapa: lo que vio la persona. |
| `plan_view.py <carpeta-del-mapa>` | Vista de planta (desde arriba) de la malla guardada, coloreada por altura, con el recorrido de la cámara: cobertura del cuarto y malla suelta fuera de él. |
| `mesh_islands.py <malla.bin>…` | Cuánta «confeti» tiene una malla: parte de los cuadriláteros en islas de menos de N, y qué tan grande es la lámina mayor. |
| `eval_variants.py <carpeta>` | Dibuja cada malla `out/acc-*.bin` (las deja `ReplayTest.exploreAnchoredOnRealDepth` con `PMM_EXPLORE=1`; `PMM_BATCH` elige el lote de variantes) desde los cuadros de **prueba** (los que la malla no vio) y la compara con la cruda: bien / delante (flota) / detrás / hueco, precisión y cobertura de la vista. Uso: `eval_variants.py <carpeta> [cada_n_cuadros=2] [filtro_de_nombre]`; variables: `TRUTH_CONF` (confianza mínima de la «verdad», 91 por omisión), `MIN_ISLAND` (quita las islas más chicas antes de medir; 0 = no) y `PMM_LOG`. **Es la medida que se usa para decidir si un cambio mejora lo que se ve.** |
| `floor_error.py <carpeta> [cada_n_cuadros=3] [filtro]` | Cuánto queda arriba o abajo el PISO de cada malla `out/acc-*.bin` respecto de lo medido, en cm de ALTURA y por distancia (de lejos el piso se ve rasante y un error pequeño de altura se vuelve grande a lo largo del rayo). |
| `anchor_view_error.py <carpeta-del-mapa> <malla.bin>` | Prueba **independiente de la profundidad**: para cada fotograma, los puntos de anclaje que ese fotograma vio (salen de la imagen) contra la primera superficie de la malla a lo largo del rayo cámara→ancla: bien / delante / detrás / hueco por distancia. Sirve con el `mesh.bin` que guardó el teléfono. |
| `depthlog.py` | Lector de `profundidad-*.bin` en Python; lo importan las demás. |

Resultados del 21-oct con el Pixel 9 Pro XL (cifras y razones en `docs/mapeador-bodega.md` §8.4): ARCore pierde la profundidad si
la persona se queda parada, gira sin avanzar o camina a más de ~1 m/s; la suave solo es confiable a ≤ 3 px de un píxel de cruda
confiable (lejos de ellos se equivoca > 10 % en 50–70 % de los píxeles); y la cruda de confianza baja sirve (error mediano 2–7 %).

## Reconstruir con fotogrametría en la PC (vía 2)

Lo que hace Scaniverse: reconstruir **después** de la captura con todas las fotos, no pintar en vivo la profundidad
estimada de cada cuadro. El mapa guardado ya trae lo necesario: las fotos de `keyframes/` y la pose de ARCore de cada una
en `keyframes.json`. COLMAP no viene en el repositorio: se baja aparte (`colmap-x64-windows-nocuda.zip` de
github.com/colmap/colmap/releases) y se pasa su ruta con `--colmap`.

```bash
python mapeador/tools/reconstruct_colmap.py <carpeta>/files/maps/<mapa> [--colmap <ruta>/colmap.exe] [--vecinos 25]
python mapeador/tools/render_mesh.py <carpeta>/files/maps/<mapa>/colmap-trabajo views-colmap.json
python mapeador/tools/compare_colmap_live.py <carpeta>/files/maps/<mapa>/colmap-trabajo <malla-en-vivo.bin>
```

`reconstruct_colmap.py` extrae rasgos SIFT con la cámara conocida (umbrales permisivos: las oficinas son casi lisas), empareja
solo las fotos que miran a lo mismo según las poses de ARCore, arma la estructura, **alinea cada fragmento a las posiciones
de ARCore** (escala en metros) y saca una malla de Delaunay de cada uno. Deja en `colmap-trabajo/`: `resumen.txt` (fotos,
puntos, reproyección y cuánto difieren las cámaras de COLMAP de las de ARCore), `puntos-fragmento-N.ply`,
`malla-fragmento-N.ply` y `.bin`, `puntos.npy` y `views-colmap.json` (tres fotos por fragmento para `render_mesh.py`).
`compare_colmap_live.py` mide qué tan cerca caen los puntos de COLMAP de la malla en vivo.

**Límites:** la reconstrucción densa de COLMAP (`patch_match_stereo`) necesita CUDA; en un equipo sin GPU NVIDIA solo hay nube
dispersa y su malla de Delaunay, que **no es una superficie**. Con las fotos que guarda hoy la app (grises, 1280×720, oficina
lisa) salen 6 fragmentos y ~13 mil puntos: no mejora la malla en vivo. Para denso en CPU existe OpenMVS (aparte, AGPL).

## Cómo está armado

```text
core/     Kotlin puro (se prueba en la JVM): Se3, CamPose, Triangulator, LandmarkSet, MapBuilder,
          Relocalizer, PoseFusion, KeyframePolicy, Projection, MapStore, DemoData
vision/   OpenCV: ORB, emparejador de Hamming, PnP, JPEG
ar/       ArView (ARCore + fondo de cámara en GL), ArFrameInfo, mediciones con la mira
session/  MapCaptureController, LocateController (un hilo de visión cada uno)
ui/       Compose: inicio, mapear, ubicar, tarjetas
```

- **Geometría:** `TsdfMesher` integra cada imagen de profundidad (160×90) en un volumen de distancia con signo
  al plano local (voxeles de 10 cm, banda de ±20 cm desde la v8; la v4 usaba 5 cm y ±10 cm) y la PROMEDIA entre cuadros,
  así el ruido no deja varias capas. Los voxeles viven en **bloques densos de 8×8×8** (~7 bytes por voxel; un cuarto de 8×6 m recorrido un
  minuto cabe en ~10 MB). La superficie sale con «surface nets» (vértice por celda cruzada, cuadrilátero por arista
  cruzada), solo para voxeles vistos en ≥ 2 cuadros; el área es la suma real de los cuadriláteros. `ScanOverlay`
  sube la malla a la GPU por tramos (contexto de GL ES 3, índices de 32 bits, 24 bits de profundidad) y la dibuja
  con pre-pase de profundidad, relleno translúcido y cuadrícula; la **oclusión** compara cada fragmento con la
  profundidad actual de ARCore (textura RG8). Fuentes de profundidad (`DepthMode`): mixta, cruda y suave
  (`DepthMerge`). Se guarda en `mesh.bin` y en «Ubicar» se superpone transformada con mapa←sesión.
  (Una primera versión con un punto por voxel se descartó: el ruido dejaba varias capas del mismo muro; y el
  almacén con mapa de hash de la v3 se llenaba en ~30 s en una oficina: ~49 bytes por voxel.)
- **Votos de espacio libre:** cada imagen de profundidad revisa los voxeles ya reservados que caen en su campo de
  visión: si todos los píxeles de su vecindario de 3×3 ven más lejos (más que la banda y que el 12 % de la distancia),
  el voxel recibe un voto de «libre» con el peso de todos los rayos de un cuadro. Así se borran los fantasmas de la
  profundidad suave y lo que ya se movió. Los cuadriláteros que dejan de cruzar la superficie se **quitan** de la malla
  (`MeshBuffer.removeQuad`; en la GPU apuntan a un vértice «muerto» y se refrescan por tramos); al guardar solo se
  escriben los vigentes (`compacted()`). La oclusión con la profundidad actual queda apagada por omisión: con la
  profundidad suave escondía la mitad de la malla.
- **Peso por rayos (v7, `normalizeByRays`):** cada cuadro aporta ~1 de peso por voxel sin importar cuántos píxeles lo
  cruzan (antes ~10 rayos por cuadro saturaban el peso en 6 cuadros y el promedio dejaba de moverse); un voxel necesita
  `minWeight` para dibujarse (0.3 en la v7, 0.1 desde la v8), y un cuadrilátero solo se quita tras `removeAfterMisses` 2
  contradicciones seguidas (histéresis). La profundidad suave sola entra con confianza de «relleno», no como cruda.
- **Imágenes de profundidad que no se integran (v8):** ARCore actualiza la profundidad a ~5 Hz y, si se atasca, sigue devolviendo la
  última imagen con su marca de tiempo VIEJA. Hasta la v7 esa imagen se pegaba con la pose de ese instante si seguía en el historial
  (24 cuadros) o, peor, con la de AHORA si ya había salido: la misma geometría se arrastraba por el recorrido (capas fantasma y malla
  flotante; solo ~52 % de los cuadros grabados tenía la pose de su marca). Ahora no se integran las repetidas (misma marca), las
  viejas (más de 0.6 s detrás del cuadro; cada imagen, cruda y suave, se revisa por separado) ni las vacías (< 1 % de píxeles), y si lleva
  más de 4 s sin profundidad nueva se intenta destrabar ARCore apagando y prendiendo su modo de profundidad.
- **Anclaje a lo medido (v8):** la cruda de ARCore entra desde confianza 1 (error mediano 2–6 %) con peso ×3 que crece con la
  confianza (`rawWeight`, `rawLowWeight`); la suave («relleno») solo es confiable junto a un píxel de cruda, así que pesa
  `exp(−d²/2σ²)` con `d` = píxeles al dato crudo más cercano de ese cuadro (`anchorSigmaPx` 10, piso `anchorFloor` 0.15). Se
  integra un píxel por bloque de 2×2, el de cruda si lo hay. Voxel de 10 cm con banda de 20 cm. El modo «Prof. suave (sin anclar)»
  del chip apaga el anclaje (`TsdfMesher.anchoringEnabled`) para comparar.
- **Aviso de ritmo y reinicio de la profundidad (v8, `MapCaptureController.updatePace`):** si ARCore lleva > 1.5 s sin dar profundidad
  nueva, el panel dice la causa probable (más despacio, giras sin avanzar, parado, o «ARCore no mide aquí: apunta a muebles, puertas
  o cuadros»); tras 4 s intenta destrabarlo apagando y prendiendo su modo de profundidad (`ArHandle.restartDepth`, como mucho
  cada 15 s). El log dirá `intento de reinicio`; **nunca se ha disparado en el teléfono**, así que no se sabe si sirve.
- **Piso a su nivel (v8.1):** el número del panel es `MeshBuffer.floorCoverageM2`: celdas de 25 cm con superficies que miran hacia
  arriba (normal y ≥ 0.8) entre 15 cm abajo y 20 cm arriba del piso estimado (percentil 8 de esas alturas, cubetas de 5 cm). Contar
  cualquier celda con malla encima inflaba el número 2–3× (84 y 59 m² contra ~41 y ~23 m²).
- **Planos de ARCore (v8.1):** `ArView.updatePlanes` lee `session.getAllTrackables(Plane)` cada 250 ms (`PlaneGeometry`: tipo y
  polígono en el mundo, 5 floats por vértice). `ScanOverlay.drawPlanes` los dibuja con cuadrícula de 25 cm, sin escribir profundidad,
  si el chip «Planos de ARCore» está encendido (apagado por omisión). `MapCaptureController` guarda `planos-<fecha>.jsonl` (una
  línea cada 0.5 s) con la misma marca de sesión que la profundidad, y el log escribe cada 2 s `planos de ARCore: N (piso/mesa,
  muros, techo) … | estado térmico`. En la oficina ARCore ve hasta 14–15 planos (4–6 verticales). **Aún no se usan para construir la
  malla**: esa es la idea del paso 2 de `docs/mapeador-bodega.md` §8.5.
- **Mapa:** *puntos* (posición promediada de varias triangulaciones) con una o varias *entradas*
  (descriptores ORB desde distintos ángulos). Los puntos quedan en el marco de ARCore de la sesión de
  mapeo, que es el marco del mapa (+Y es arriba).
- **Ubicar:** cada ~0.4 s se intenta una fijación visual. `PoseFusion` guarda mapa←sesión, la suaviza y
  solo cambia de opinión cuando varias fijaciones coinciden entre sí.
- **Autoprueba (al mapear):** cada 3 fotogramas intenta ubicar el nuevo contra el mapa que ya existe y
  compara con la pose real de ARCore. Es el termómetro de la calidad del mapa.

### Los ejes, que es lo que más cuesta

- `camera.pose` de ARCore está **orientada al sensor** (coincide con la imagen de CPU y sus intrínsecos),
  no a la pantalla. Para dibujar se usan las matrices de vista y proyección, que sí van con la pantalla.
- ARCore/OpenGL: X derecha, Y arriba, −Z adelante. OpenCV: X derecha, Y abajo, +Z adelante.
  `CameraConventions.FLIP_YZ` pasa de una a otra y es su propia inversa.
- Nombres de transformaciones: `aFromB` lleva coordenadas de B a A.

## Límites a propósito

- El mapa se arma con las poses de ARCore: sirve para una oficina, **no para la nave** (la deriva se
  acumula; para la nave va COLMAP).
- Un solo piso, sin servidor: todo queda en el teléfono.
- ARCore solo corre en equipos certificados (Pixel sí; Ulefone no está en la lista).

## Trampas ya encontradas

- En Kotlin los comentarios de bloque **se anidan**: `keyframes/*.jpg` dentro de un `/** … */` abre otro
  comentario y rompe la compilación («Unclosed comment»).
- Con un punto nuevo por cada par de fotogramas, el mismo lugar queda repetido y la prueba de razón del
  emparejado lo descarta (su «segundo mejor» es otra copia del mismo punto). Por eso hay puntos con varias
  vistas y la razón ignora vecinos del mismo lugar.
- Una captura con `adb exec-out screencap -p > archivo` desde PowerShell corrompe el PNG; hacerlo en Bash.
- Los tests unitarios de Android compilan contra `android.jar` y **no ven `java.awt`/`ImageIO`**: por eso el dibujo para
  revisar la malla es un script de Python y no código de la prueba.
- Con un TSDF de mapa de hash, ~8,000 voxeles por m² de superficie real (banda ancha + jitter de poses) llenan cualquier
  tope razonable: usar bloques densos y banda delgada (≈ 2 voxeles por lado).
- COLMAP 4.x: los modelos de texto necesitan además `rigs.txt` y `frames.txt`; `point_triangulator` con poses conocidas se
  **cae** en 4.2.1 (código 0xC0000409, sin mensaje) aunque el modelo se lea bien (se usa `mapper` + `model_aligner`);
  `--FeatureExtraction.use_gpu 0` y `--FeatureMatching.use_gpu 0` son obligatorios sin GPU; para emparejar solo pares
  elegidos: `matches_importer --match_type pairs`.
- Con los umbrales de SIFT por omisión, las fotos de una oficina lisa quedan en 5 fragmentos de 10–27 fotos; con
  `peak_threshold` 0.0025, `edge_threshold` 15 y razón 0.9 se registran casi todas, pero siguen sin enlazarse.
- **No dar por buena una grabación de profundidad sin filtrar por la pose** (`tools/frame_validity.py`): con las versiones hasta la v7, casi la mitad de los cuadros traían una imagen atascada con la pose equivocada y contaminaban cualquier estadística (fantasmas, ruido entre cuadros, error de la suave).
- **Medido y descartado: no repetir sin una razón nueva** (±1 punto en cuatro grabaciones): corregir el desfase de ~100 ms entre las
  marcas de la cruda y la suave, corregir el sesgo de la suave por distancia, quitar las islas sueltas (2–3 % de los cuadriláteros)
  y llevar a su plano lo medido dentro de la banda del piso. Detalle en `docs/mapeador-bodega.md` §8.4. Lo que sí movió la aguja fue
  no integrar cuadros malos y pesar distinto lo medido que lo inventado.
- `planos-*.jsonl` existe solo desde la v8.1: las grabaciones anteriores (todas las de `C:\Codigos\mapeo-oficina\`) no traen los planos
  de ARCore; para evaluarlos hace falta una corrida nueva.
- Al compilar las pruebas salen dos avisos de Kotlin en `ReplayTest.kt:215` (`File?`): son avisos, no fallas.
- En Git Bash, `adb pull /sdcard/...` convierte la ruta (`C:/Program Files/Git/sdcard/...`): `export MSYS_NO_PATHCONV=1`. Y un `cd` dentro de un comando cambia el directorio de toda la sesión: usar `( cd dir && … )` o rutas absolutas.
- Python con `-I` (modo aislado) no ve los paquetes del usuario (`cv2`): las herramientas de `tools/` se corren sin `-I`
  porque solo leen datos del propio mapa.
