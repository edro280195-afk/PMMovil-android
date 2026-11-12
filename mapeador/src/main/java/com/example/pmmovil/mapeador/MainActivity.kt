package com.example.pmmovil.mapeador

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.pmmovil.mapeador.core.MapStore
import com.example.pmmovil.mapeador.ui.AnclasLabScreen
import com.example.pmmovil.mapeador.ui.CaptureScreen
import com.example.pmmovil.mapeador.ui.HomeScreen
import com.example.pmmovil.mapeador.ui.LocateScreen
import com.example.pmmovil.mapeador.ui.MapeadorTheme
import java.io.File

private sealed interface Screen {
    data object Home : Screen
    data object Capture : Screen
    data class Locate(val mapId: String) : Screen

    /** Laboratorio de anclas (AprilTag / ArUco / QR): no guarda mapas. */
    data object Anclas : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Mapear y ubicar son sesiones largas con la cámara: que la pantalla no se apague sola.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Carpeta accesible por adb (adb pull /sdcard/Android/data/com.example.pmmovil.mapeador/files/maps).
        val store = MapStore(File(getExternalFilesDir(null) ?: filesDir, "maps"))
        setContent {
            MapeadorTheme { MapeadorApp(store) }
        }
    }
}

@Composable
private fun MapeadorApp(store: MapStore) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    when (val s = screen) {
        Screen.Home -> HomeScreen(
            store = store,
            onCapture = { screen = Screen.Capture },
            onLocate = { screen = Screen.Locate(it) },
            onAnchors = { screen = Screen.Anclas }
        )
        Screen.Anclas -> AnclasLabScreen(onExit = { screen = Screen.Home })
        Screen.Capture -> CaptureScreen(store = store, onExit = { screen = Screen.Home })
        is Screen.Locate -> LocateScreen(store = store, mapId = s.mapId, onExit = { screen = Screen.Home })
    }
}
