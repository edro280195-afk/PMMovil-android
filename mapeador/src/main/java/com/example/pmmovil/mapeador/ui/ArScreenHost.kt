package com.example.pmmovil.mapeador.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.pmmovil.mapeador.ar.ArFrameListener
import com.example.pmmovil.mapeador.ar.ArHandle
import com.example.pmmovil.mapeador.ar.ArStartResult
import com.example.pmmovil.mapeador.ar.ArView
import com.example.pmmovil.mapeador.ar.GlOverlay

private fun Context.findActivity(): Activity {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    error("No hay una actividad detrás de este contexto.")
}

/**
 * Pantalla con el visor de ARCore al fondo: pide el permiso de cámara, maneja el ciclo de vida de
 * la sesión y dibuja `content` encima (HUD, mira, tarjetas) solo cuando ARCore ya arrancó.
 */
@Composable
fun ArScreenHost(
    listener: ArFrameListener,
    handle: ArHandle,
    overlay: GlOverlay? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var startResult by remember { mutableStateOf<ArStartResult?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (!granted) {
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Se necesita la cámara para ver y mapear el espacio.", color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Dar permiso de cámara") }
            }
        } else {
            val owner = LocalLifecycleOwner.current
            val arView = remember {
                ArView(context).also {
                    it.frameListener = listener
                    it.overlay = overlay
                    handle.view = it
                }
            }
            AndroidView(factory = { arView }, modifier = Modifier.fillMaxSize())
            DisposableEffect(owner, arView) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> {
                            startResult = arView.resumeAr(activity)
                            failure = arView.unavailableMessage
                        }
                        Lifecycle.Event.ON_PAUSE -> arView.pauseAr()
                        else -> Unit
                    }
                }
                owner.lifecycle.addObserver(observer)
                onDispose {
                    owner.lifecycle.removeObserver(observer)
                    handle.view = null
                    arView.close()
                }
            }
            when (startResult) {
                ArStartResult.STARTED -> content()
                ArStartResult.INSTALLING -> Notice("Instalando Google Play Services for AR… vuelve a esta pantalla al terminar.")
                ArStartResult.UNAVAILABLE -> Notice(failure ?: "ARCore no está disponible en este equipo.")
                null -> Unit
            }
        }
    }
}

@Composable
private fun BoxScope.Notice(text: String) {
    Text(
        text,
        color = Color.White,
        textAlign = TextAlign.Center,
        modifier = Modifier.align(Alignment.Center).padding(24.dp)
    )
}
