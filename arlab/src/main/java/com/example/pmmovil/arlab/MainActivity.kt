package com.example.pmmovil.arlab

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session

private const val TAG = "ARLAB"

/** Veredicto de cada línea del informe. */
enum class Nivel { OK, NO, AVISO, DATO }

data class Linea(val clave: String, val valor: String, val nivel: Nivel = Nivel.DATO)
data class Bloque(val titulo: String, val lineas: List<Linea>)

/**
 * Prueba 0 de docs/ia-revision-fisica.md: qué trae de verdad el equipo.
 *
 * No dibuja AR: solo pregunta. La idea es tener la tabla de capacidades antes de
 * invertir en las pruebas 1 a 7, y poder correr lo mismo en la tableta de la flota
 * para comparar manzanas con manzanas.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Pantalla() }
    }
}

@Composable
private fun Pantalla() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var bloques by remember { mutableStateOf<List<Bloque>>(emptyList()) }
    var permiso by remember {
        mutableStateOf(
            ContextCompat_checkSelfPermission(ctx, Manifest.permission.CAMERA)
        )
    }

    val pedirPermiso = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        permiso = concedido
        bloques = medirTodo(ctx, concedido)
    }

    LaunchedEffect(Unit) {
        if (!permiso) {
            pedirPermiso.launch(Manifest.permission.CAMERA)
        } else {
            bloques = medirTodo(ctx, true)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF1F5FA)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(18.dp)
        ) {
            Text(
                "Laboratorio AR — Prueba 0",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF0B1220)
            )
            Text(
                "Qué trae de verdad este equipo. Mismo APK para el Pixel y para la tableta.",
                fontSize = 13.sp,
                color = Color(0xFF4A5570)
            )
            Spacer(Modifier.height(16.dp))

            bloques.forEach { bloque ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            bloque.titulo.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF8A94AD)
                        )
                        Spacer(Modifier.height(8.dp))
                        bloque.lineas.forEach { l ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    marca(l.nivel),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = color(l.nivel),
                                    modifier = Modifier.width(22.dp)
                                )
                                Text(
                                    l.clave,
                                    fontSize = 12.5.sp,
                                    color = Color(0xFF4A5570),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    l.valor,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = color(l.nivel),
                                    modifier = Modifier.weight(1.1f)
                                )
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { bloques = medirTodo(ctx, permiso) }) { Text("Volver a medir") }
                OutlinedButton(onClick = { volcarALog(bloques) }) { Text("Mandar a logcat") }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "El informe también se escribe en logcat con la etiqueta ARLAB:\n" +
                    "adb logcat -d -s ARLAB",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF8A94AD)
            )
        }
    }
}

private fun marca(n: Nivel) = when (n) {
    Nivel.OK -> "✓"
    Nivel.NO -> "✗"
    Nivel.AVISO -> "!"
    Nivel.DATO -> "·"
}

private fun color(n: Nivel) = when (n) {
    Nivel.OK -> Color(0xFF15803D)
    Nivel.NO -> Color(0xFFDC2626)
    Nivel.AVISO -> Color(0xFFD97706)
    Nivel.DATO -> Color(0xFF0B1220)
}

private fun ContextCompat_checkSelfPermission(ctx: Context, permiso: String): Boolean =
    ctx.checkSelfPermission(permiso) == PackageManager.PERMISSION_GRANTED

/* ───────────────────────── mediciones ───────────────────────── */

private fun medirTodo(ctx: Context, permisoCamara: Boolean): List<Bloque> {
    val bloques = listOf(
        bloqueSistema(ctx),
        bloqueArCore(ctx, permisoCamara),
        bloqueCamaras(ctx)
    )
    volcarALog(bloques)
    return bloques
}

private fun bloqueSistema(ctx: Context): Bloque {
    val lineas = mutableListOf(
        Linea("Modelo", "${Build.MANUFACTURER} ${Build.MODEL}"),
        Linea("Nombre interno", Build.DEVICE),
        Linea("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"),
        Linea("ABI", Build.SUPPORTED_ABIS.joinToString(", "))
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        lineas += Linea("SoC", "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}")
    }
    lineas += Linea(
        "AICore (Gemini Nano)",
        if (paqueteInstalado(ctx, "com.google.android.aicore")) "instalado" else "ausente",
        if (paqueteInstalado(ctx, "com.google.android.aicore")) Nivel.OK else Nivel.NO
    )
    return Bloque("Sistema", lineas)
}

private fun paqueteInstalado(ctx: Context, nombre: String): Boolean = try {
    ctx.packageManager.getPackageInfo(nombre, 0)
    true
} catch (_: PackageManager.NameNotFoundException) {
    false
}

private fun bloqueArCore(ctx: Context, permisoCamara: Boolean): Bloque {
    val lineas = mutableListOf<Linea>()

    val disponibilidad = try {
        ArCoreApk.getInstance().checkAvailability(ctx)
    } catch (e: Exception) {
        lineas += Linea("checkAvailability", "error: ${e.javaClass.simpleName}", Nivel.NO)
        null
    }
    if (disponibilidad != null) {
        lineas += Linea(
            "Disponibilidad",
            disponibilidad.name,
            if (disponibilidad.isSupported) Nivel.OK else Nivel.NO
        )
    }

    lineas += Linea(
        "Play Services for AR",
        versionPaquete(ctx, "com.google.ar.core") ?: "no instalado",
        if (versionPaquete(ctx, "com.google.ar.core") != null) Nivel.OK else Nivel.NO
    )

    if (!permisoCamara) {
        lineas += Linea("Sesión ARCore", "falta permiso de cámara", Nivel.AVISO)
        return Bloque("ARCore", lineas)
    }
    if (disponibilidad?.isSupported != true) {
        lineas += Linea("Sesión ARCore", "equipo no compatible", Nivel.NO)
        return Bloque("ARCore", lineas)
    }

    var sesion: Session? = null
    try {
        sesion = Session(ctx)
        val auto = sesion.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
        val cruda = sesion.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY)
        val geo = sesion.isGeospatialModeSupported(Config.GeospatialMode.ENABLED)

        lineas += Linea("Depth API (AUTOMATIC)", si(auto), nivel(auto))
        lineas += Linea("Raw Depth", si(cruda), nivel(cruda))
        lineas += Linea("Geospatial / VPS", si(geo), nivel(geo))
        lineas += Linea(
            "Scene Semantics",
            "solo exteriores — descartada",
            Nivel.AVISO
        )
    } catch (e: Exception) {
        lineas += Linea("Sesión ARCore", "falló: ${e.javaClass.simpleName}", Nivel.NO)
    } finally {
        try {
            sesion?.close()
        } catch (_: Exception) {
        }
    }
    return Bloque("ARCore", lineas)
}

private fun si(b: Boolean) = if (b) "sí" else "no"
private fun nivel(b: Boolean) = if (b) Nivel.OK else Nivel.NO

private fun versionPaquete(ctx: Context, nombre: String): String? = try {
    ctx.packageManager.getPackageInfo(nombre, 0).versionName
} catch (_: PackageManager.NameNotFoundException) {
    null
}

/**
 * Distancia mínima de enfoque por lente. Es el dato que decide si el equipo puede
 * leer un número de lote impreso chico sin pelearse con el autofoco: Camera2 la
 * entrega en dioptrías (1/m), así que 50 dioptrías = 2 cm.
 */
private fun bloqueCamaras(ctx: Context): Bloque {
    val lineas = mutableListOf<Linea>()
    try {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        // Un mismo lente fisico se expone bajo varias camaras logicas; agrupamos por
        // resumen para no repetir la misma optica tres veces en el informe.
        val vistos = LinkedHashMap<String, MutableList<String>>()
        for (id in cm.cameraIdList) {
            val c = cm.getCameraCharacteristics(id)
            val cara = when (c.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_BACK -> "trasera"
                CameraCharacteristics.LENS_FACING_FRONT -> "frontal"
                else -> "externa"
            }
            val fisicas = c.physicalCameraIds
            val ids = if (fisicas.isEmpty()) listOf(id) else fisicas.toList()
            for (fid in ids) {
                val ch = if (fid == id) c else cm.getCameraCharacteristics(fid)
                val clave = cara + "|" + resumenLente(ch)
                vistos.getOrPut(clave) { mutableListOf() }.add(fid)
            }
        }
        vistos.forEach { (clave, ids) ->
            val cara = clave.substringBefore('|')
            val resumen = clave.substringAfter('|')
            lineas += Linea("Lente " + ids.joinToString("/") + " (" + cara + ")", resumen)
        }
    } catch (e: Exception) {
        lineas += Linea("Cámaras", "error: ${e.javaClass.simpleName}", Nivel.NO)
    }
    return Bloque("Cámaras", lineas)
}

private fun resumenLente(ch: CameraCharacteristics): String {
    val focal = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
    val dioptrias = ch.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
    val cm = if (dioptrias != null && dioptrias > 0f) "%.0f cm".format(100f / dioptrias) else "fijo"
    val mp = try {
        val mapa = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val mayor = mapa?.getOutputSizes(ImageFormat.JPEG)?.maxByOrNull { it.width.toLong() * it.height }
        if (mayor != null) "%.0f MP".format(mayor.width.toLong() * mayor.height / 1_000_000f) else "?"
    } catch (_: Exception) {
        "?"
    }
    return "${focal?.let { "%.1f mm".format(it) } ?: "?"} · $cm · $mp"
}

private fun volcarALog(bloques: List<Bloque>) {
    if (bloques.isEmpty()) return
    Log.i(TAG, "================ INFORME ARLAB ================")
    bloques.forEach { b ->
        Log.i(TAG, "--- ${b.titulo} ---")
        b.lineas.forEach { l -> Log.i(TAG, "${marca(l.nivel)} ${l.clave}: ${l.valor}") }
    }
    Log.i(TAG, "===============================================")
}
