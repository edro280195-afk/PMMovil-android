package com.example.pmmovil.ui.pdf

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Ruta de compatibilidad para enlaces antiguos de PDF.
 * El flujo real usa el mismo espacio de revisión con AhmerPdfium y PDFBox.
 */
@Composable
fun PdfViewerScreen(
    creferencia: String,
    onClose: () -> Unit
) {
    InlinePdfWorkspace(
        crefAgrupada = creferencia,
        initialReference = creferencia,
        onClose = onClose,
        modifier = Modifier.fillMaxSize()
    )
}
