package com.example.pmmovil.ui.revision

import com.example.pmmovil.data.model.ProductoResumen
import com.example.pmmovil.data.model.PendingBarcodeScan
import com.example.pmmovil.data.scanner.BarcodeValueNormalizer

data class ManualBarcodeInputStatus(
    val cleanValue: String,
    val length: Int,
    val expectedLength: Int,
    val canSubmit: Boolean,
    val helperText: String,
    val isExactLength: Boolean,
    val isOverLength: Boolean
)

const val BARCODE_DUPLICATE_WINDOW_MILLIS = 750L
private const val BARCODE_GLOBAL_DUPLICATE_WINDOW_MILLIS = 60L

fun isBarcodeDuplicateTooSoon(
    normalizedCode: String,
    nowMillis: Long,
    previousSameCodeAtMillis: Long?,
    lastAcceptedAtMillis: Long,
    duplicateWindowMillis: Long = BARCODE_DUPLICATE_WINDOW_MILLIS
): Boolean {
    val sameCodeTooSoon = previousSameCodeAtMillis != null &&
        nowMillis - previousSameCodeAtMillis < duplicateWindowMillis
    val anyCodeTooSoon = nowMillis - lastAcceptedAtMillis <
        BARCODE_GLOBAL_DUPLICATE_WINDOW_MILLIS
    return normalizedCode.isNotBlank() && (sameCodeTooSoon || anyCodeTooSoon)
}

fun barcodeManualInputStatus(
    rawValue: String,
    expectedLength: Int
): ManualBarcodeInputStatus {
    val clean = BarcodeValueNormalizer.normalize(rawValue)
    val length = clean.length
    val expected = expectedLength.coerceAtLeast(0)
    val isExact = expected > 0 && length == expected
    val isOver = expected > 0 && length > expected
    val canSubmit = clean.isNotEmpty() && (expected == 0 || length >= expected)
    val helper = when {
        clean.isEmpty() && expected > 0 -> "Esperando $expected caracteres."
        clean.isEmpty() -> "Captura el código y presiona agregar."
        expected == 0 -> "$length caracteres listos para validar."
        isExact -> "$length/$expected caracteres. Listo para agregar."
        isOver -> "$length/$expected caracteres. Se validará el código completo."
        else -> "$length/$expected caracteres. Faltan ${expected - length}."
    }

    return ManualBarcodeInputStatus(
        cleanValue = clean,
        length = length,
        expectedLength = expected,
        canSubmit = canSubmit,
        helperText = helper,
        isExactLength = isExact,
        isOverLength = isOver
    )
}

fun filterBarcodeProducts(
    products: List<ProductoResumen>,
    filter: BarcodeProductFilter
): List<ProductoResumen> = when (filter) {
    BarcodeProductFilter.ALL -> products
    BarcodeProductFilter.PENDING -> products.filter { it.pendientes > 0 }
    BarcodeProductFilter.RECOGNIZED -> products.filter(ProductoResumen::existe)
    BarcodeProductFilter.UNKNOWN -> products.filter {
        !it.existe && it.pendientes < it.cantidad
    }
}

fun pendingScansForSession(
    pendingScans: List<PendingBarcodeScan>,
    sessionId: Int
): List<PendingBarcodeScan> = pendingScans.filter {
    it.sessionId == sessionId
}

fun barcodeFinalizeBlocker(
    totalProducts: Int,
    pendingCount: Int,
    isLoading: Boolean,
    isFinalizing: Boolean,
    hasError: Boolean
): String? = when {
    isFinalizing -> "Finalizando sesión..."
    isLoading -> "Preparando la sesión..."
    hasError -> "Corrige el error antes de finalizar."
    totalProducts <= 0 -> "Escanea al menos un producto."
    pendingCount > 0 -> "Espera $pendingCount lectura(s) por sincronizar."
    else -> null
}
