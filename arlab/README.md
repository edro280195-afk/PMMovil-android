# arlab — Laboratorio de realidad aumentada

Módulo **aparte de `:app`**, igual que `:mapspike`. No comparte código ni dependencias con la app de
producción, así que no la afecta ni le sube el peso. Se instala como `com.example.pmmovil.arlab` y
convive con PMMovil en el mismo teléfono.

Contexto y plan completo: [`docs/ia-revision-fisica.md`](../docs/ia-revision-fisica.md), sección 12.

## Para qué es

Medir **el techo real** de un equipo con buenos sensores (Pixel 9 Pro XL) y poder comparar contra la
flota actual (Ulefone Armor Pad Pro) corriendo **el mismo APK en los dos**. La decisión de cambiar
equipos se toma con esa tabla, no con suposiciones.

Hoy implementa la **Prueba 0**: qué trae de verdad el equipo.

- Sistema: modelo, Android, ABI, SoC, si AICore (Gemini Nano) está instalado
- ARCore: disponibilidad, versión de Play Services for AR, **Depth API**, **Raw Depth**, **Geospatial**
- Cámaras: por lente, focal · **distancia mínima de enfoque** · resolución máxima

La distancia mínima de enfoque es el dato que decide si el equipo puede leer un número de lote impreso
chico. Camera2 la entrega en dioptrías (1/m): 50 dioptrías = 2 cm.

## Correrlo

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :arlab:assembleDebug
adb install -r arlab/build/outputs/apk/debug/arlab-debug.apk
adb shell pm grant com.example.pmmovil.arlab android.permission.CAMERA
adb shell am start -n com.example.pmmovil.arlab/.MainActivity
adb logcat -d -s ARLAB:I
```

El informe sale en pantalla **y** en logcat con la etiqueta `ARLAB`, para poder capturarlo sin fotos.

## Resultado en Pixel 9 Pro XL (14-oct-2026)

```
--- Sistema ---
· Modelo: Google Pixel 9 Pro XL        · Android: 17 (API 37)
· SoC: Google Tensor G4                · ABI: arm64-v8a
✓ AICore (Gemini Nano): instalado
--- ARCore ---
✓ Disponibilidad: SUPPORTED_INSTALLED  ✓ Play Services for AR: 1.56.262080393
✓ Depth API (AUTOMATIC): sí            ✓ Raw Depth: sí
✓ Geospatial / VPS: sí                 ! Scene Semantics: solo exteriores — descartada
--- Cámaras ---
· Lente 2/5 (trasera): 6.9 mm  · 11 cm · 13 MP
· Lente 3/9 (trasera): 2.0 mm  ·  2 cm · 12 MP   <- macro
· Lente 4/6 (trasera): 17.9 mm · 80 cm · 12 MP   <- tele
```

**Falta correr el mismo APK en la Armor Pad Pro.** Se espera `UNSUPPORTED_DEVICE_NOT_CAPABLE` en
ARCore; lo que interesa de verdad de ese lado es la fila de cámaras.

## Qué falta implementar (pruebas 1 a 7)

| # | Prueba | Estado |
|---|---|---|
| 0 | Capacidades del equipo | **hecha** |
| 1 | Anclas que sobreviven (Cloud Anchors, deriva a 1 h / 8 h / 7 días) | pendiente |
| 2 | `checkVpsAvailabilityAsync` en las coordenadas de la bodega | pendiente |
| 3 | Depth API midiendo tarimas contra flexómetro | pendiente |
| 4 | 30 etiquetas difíciles: Pixel vs Armor Pad Pro | pendiente |
| 5 | Arranque y sostenimiento de cámara (fps real, temperatura) | pendiente |
| 6 | Gemini Nano describiendo daño (`genai-image-description:1.0.0-beta1`) | pendiente |
| 7 | Demostración: etiquetas ancladas frente a un rack | pendiente |

## Notas

- **`AR Required`** en el manifiesto solo filtra en Play Store. Como distribuimos por APK, no estorba;
  lo que sí manda es que ARCore no corre en equipos no certificados.
- ARCore **1.56.0** es la misma versión que ya trae el Pixel instalada como Play Services for AR.
- Crear una `Session` exige permiso de cámara **concedido**, no solo declarado.
- APK de ~24 MB en debug. No tocar `:app` hasta que las pruebas 1 a 7 digan si vale la pena.
