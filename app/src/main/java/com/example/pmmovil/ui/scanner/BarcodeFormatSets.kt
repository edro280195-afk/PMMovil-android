package com.example.pmmovil.ui.scanner

import com.google.mlkit.vision.barcode.common.Barcode

object BarcodeFormatSets {
    /**
     * Etiquetas impresas por PMM y códigos de producto básicos.
     * Incluye DataMatrix para leer etiquetas 2D de clientes (ej. Sigma-Aldrich, Merck).
     */
    const val PMM_LABELS: Int =
        Barcode.FORMAT_QR_CODE or
            Barcode.FORMAT_CODE_128 or
            Barcode.FORMAT_DATA_MATRIX

    /**
     * Mercancia de cliente, que puede traer cualquier simbologia comercial.
     * Solo hace falta donde se leen productos, no etiquetas propias.
     */
    const val WAREHOUSE_PRODUCT_LABELS: Int =
        Barcode.FORMAT_CODE_128 or
            Barcode.FORMAT_CODE_39 or
            Barcode.FORMAT_CODE_93 or
            Barcode.FORMAT_EAN_13 or
            Barcode.FORMAT_EAN_8 or
            Barcode.FORMAT_UPC_A or
            Barcode.FORMAT_UPC_E or
            Barcode.FORMAT_ITF or
            Barcode.FORMAT_QR_CODE or
            Barcode.FORMAT_DATA_MATRIX or
            Barcode.FORMAT_PDF417
}

/**
 * Que se espera leer en cada pantalla.
 *
 * Restringir las simbologias es la optimizacion que mas recomienda Google
 * para el escaneo en vivo: cada detector activo cuesta tiempo por frame y,
 * con `enableAllPotentialBarcodes()`, es una fuente mas de falsos positivos
 * sobre texturas del fondo (rejas, persianas, teclados).
 */
enum class ScanProfile(val formats: Int, val subtitle: String) {
    /** Referencias y cajas: lo que imprime la propia empresa. */
    PmmLabel(
        formats = BarcodeFormatSets.PMM_LABELS,
        subtitle = "Enfoca el QR de la etiqueta dentro del visor"
    ),

    /** Mercancia de cliente durante la revision de partidas. */
    WarehouseProduct(
        formats = BarcodeFormatSets.WAREHOUSE_PRODUCT_LABELS,
        subtitle = "Enfoca el codigo del producto dentro del visor"
    ),

    /**
     * Entrada de bodega: las etiquetas propias son siempre QR.
     * Limitar a un solo formato hace que ML Kit detecte potenciales
     * a mayor distancia porque no divide ciclos entre 11 simbologias.
     */
    EntradaBodegaQR(
        formats = Barcode.FORMAT_QR_CODE,
        subtitle = "Enfoca el QR de la caja dentro del visor"
    ),

    /**
     * QR-only generico para flujos que solo deben reaccionar al QR propio de
     * PMM (entrada de caseta, busqueda de entrada en bodega, relacion). Sin
     * esto ML Kit tambien decodifica el Code 128 del producto del cliente que
     * suele estar pegado justo al lado, y se lee el codigo equivocado.
     */
    QrOnly(
        formats = Barcode.FORMAT_QR_CODE,
        subtitle = "Enfoca el código QR dentro del visor"
    )
}
