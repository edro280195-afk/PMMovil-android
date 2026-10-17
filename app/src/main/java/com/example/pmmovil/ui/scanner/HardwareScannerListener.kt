package com.example.pmmovil.ui.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.pmmovil.data.scanner.DataWedgeProfileManager
import com.example.pmmovil.data.scanner.DataWedgeProfileStatus
import com.example.pmmovil.data.scanner.HardwareScanEvent
import com.example.pmmovil.data.scanner.HardwareScannerIntents

private const val HARDWARE_SCANNER_LOG_TAG = "PMM-HARDWARE-SCAN"

/**
 * Receptor compartido para lectores fisicos Zebra, Honeywell y lectores HID
 * que MainActivity convierte al intent interno com.pmmovil.SCAN.
 */
@Composable
fun HardwareScannerListener(onBarcodeScanned: (String) -> Unit) {
    HardwareScannerListenerWithDiagnostics(
        onBarcodeScanned = onBarcodeScanned
    )
}

@Composable
fun HardwareScannerListenerWithDiagnostics(
    onBarcodeScanned: (String) -> Unit,
    onScanEvent: ((HardwareScanEvent) -> Unit)? = null,
    onDataWedgeStatus: ((DataWedgeProfileStatus) -> Unit)? = null,
    configureDataWedge: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnScanned = rememberUpdatedState(onBarcodeScanned)
    val currentOnScanEvent = rememberUpdatedState(onScanEvent)
    val currentOnDataWedgeStatus = rememberUpdatedState(onDataWedgeStatus)

    DisposableEffect(context, lifecycleOwner) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val dataWedgeStatus = DataWedgeProfileManager.parseResult(intent)
                if (dataWedgeStatus != null) {
                    currentOnDataWedgeStatus.value?.invoke(dataWedgeStatus)
                    return
                }

                val scanEvent = HardwareScannerIntents.parseScanEvent(intent)
                if (scanEvent != null) {
                    Log.i(
                        HARDWARE_SCANNER_LOG_TAG,
                        "Lectura recibida source=${scanEvent.source} action=${scanEvent.intentAction} " +
                            "extra=${scanEvent.dataExtraKey} length=${scanEvent.value.length}"
                    )
                    currentOnScanEvent.value?.invoke(scanEvent)
                        ?: currentOnScanned.value(scanEvent.value)
                } else if (intent?.action in HardwareScannerIntents.scannerActions) {
                    val extraNames = intent?.extras?.keySet()?.joinToString().orEmpty()
                    Log.w(
                        HARDWARE_SCANNER_LOG_TAG,
                        "Evento de lector sin dato reconocido action=${intent?.action} extras=$extraNames"
                    )
                }
            }
        }

        val filter = IntentFilter().apply {
            HardwareScannerIntents.scannerActions.forEach(::addAction)
            addAction(DataWedgeProfileManager.ACTION_RESULT)
            addCategory(Intent.CATEGORY_DEFAULT)
        }

        var registered = false

        fun register() {
            if (registered) return
            if (configureDataWedge) {
                DataWedgeProfileManager.configure(context)
            }
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            registered = true
        }

        fun unregister() {
            if (!registered) return
            runCatching { context.unregisterReceiver(receiver) }
            registered = false
        }

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> register()
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_DESTROY -> unregister()
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            register()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            unregister()
        }
    }
}
