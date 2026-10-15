# Spike de mapa con MapLibre Native

Módulo **aparte** de `:app`: no comparte código con la app de producción y se instala como
`com.example.pmmovil.mapspike`. Sirve para decidir, con datos de un equipo real, si MapLibre Native es
el motor del mapa de bodega. Contexto y criterios: `docs/evaluacion-tecnologia-mapa-bodega.md` en el repo
`PMMovil-migracion`.

## Qué prueba

- Una bodega **de piso** sintética (tarimas GMA 48×40 in en bloques de hasta 3 niveles, tinas IBC, huacales
  a medida, cunas de tubos y áreas de lote; sin racks). Datos de ejemplo, no reales.
- El **mismo `style.json`** que usa el prototipo web (`docs/prototipos/mapa-maplibre`).
- Glifos (letras) y estilo **empaquetados en `assets/`**: el mapa no pide nada a la red. El contador de
  peticiones debe quedar en 0.
- Motor **Vulkan** y **OpenGL ES** en el mismo APK (variante `android-sdk-vulkan-opengl`, solo para comparar).
- Dos pantallas: `MainActivity` (vistas clásicas) y `ComposeMapActivity` (Compose con las mismas versiones
  que `:app`, `MapView` dentro de `AndroidView`).

## Cómo correrlo

Con la tableta conectada por USB (depuración activada):

```powershell
.\gradlew :mapspike:assembleDebug
.\mapspike\scripts\run-benchmark.ps1                 # Vulkan y OpenGL con 1 y 8 copias de la bodega
.\mapspike\scripts\run-benchmark.ps1 -Screen compose # la misma prueba dentro de Compose
```

O a mano: abrir la app **Spike mapa**, pulsar **Bench** (dura unos 40 s) y leer los números en pantalla.
Botones: `2D/3D`, `Zona` (selecciona un bloque y atenúa el resto), `Consulta` (toque bajo el centro),
`Stats` (barra de estadísticas de MapLibre), `Bench`. Extras del intent: `engine` (vulkan|opengl),
`stress` (1–16 copias de la bodega), `autorun`, `exit`.

Los resultados quedan en `mapspike\results\*.json` y en logcat (`adb logcat -s MapSpike`).

## Cómo leer los resultados

| Fase | Qué mide | Criterio sugerido |
|---|---|---|
| `idle_steady` | Cuadros dibujados sin interacción | ≈ 0 (no repinta si nada cambia) |
| `pan_zoom_2d`, `tilt_rotate_3d`, `overview_all_features` | Movimiento de cámara | ≥ 55 fps sostenidos |
| `source_update_30hz` | Actualizar una fuente GeoJSON 30 veces por segundo (punto en movimiento) | ≥ 55 fps |
| `paint_update_per_frame` | Cambiar filtro y color en cada cuadro (selección) | ≥ 55 fps |
| `query_stress` | 300 consultas de toque (`queryRenderedFeatures`) | promedio < 50 ms |

Además: `PSS MB` (memoria), `irreg. %` (cuadros de la interfaz más lentos que 1.5× la mediana) y el
tiempo hasta el primer dibujo.

## Qué mandar de vuelta

El o los JSON de `mapspike\results\` y el modelo del equipo. Si algo falla con Vulkan, repetir con
`-Engines opengl`: algunos equipos industriales traen controladores Vulkan irregulares.
