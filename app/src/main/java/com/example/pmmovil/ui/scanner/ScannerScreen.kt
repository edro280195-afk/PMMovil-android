package com.example.pmmovil.ui.scanner

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize

@Composable
fun ScannerScreen(
    onBarcodeScanned: (String) -> Unit,
    onClose: () -> Unit,
    profile: ScanProfile = ScanProfile.PmmLabel,
    startZoomRatio: Float = 1f
) {
    SmartBarcodeCameraView(
        onBarcodeScanned = onBarcodeScanned,
        title = "Escanear codigo",
        subtitle = profile.subtitle,
        barcodeFormats = profile.formats,
        continuous = false,
        // Esta lectura dispara una busqueda en el backend y un cambio de
        // pantalla, asi que se exige ver el mismo valor en dos frames
        // seguidos. Cuesta ~un frame y evita actuar sobre una lectura sucia.
        minConsecutiveReads = 2,
        showClose = true,
        onClose = onClose,
        compact = false,
        startZoomRatio = startZoomRatio,
        scanDistance = ScanDistance.Far,
        modifier = Modifier.fillMaxSize()
    )
}
