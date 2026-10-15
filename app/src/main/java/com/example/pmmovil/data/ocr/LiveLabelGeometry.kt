package com.example.pmmovil.data.ocr

/**
 * Donde esta cada cosa que el analisis en vivo ve, para poder dibujarlo encima
 * de la preview.
 *
 * Por que hacia falta: [LocalTextOcrResult] entrega **solo texto** -- rawText,
 * candidatos y codigos -- porque el unico consumidor que existia (la captura de
 * placa y caja en Entrada Caseta) nada mas necesita el valor. Sin coordenadas no
 * se puede marcar la etiqueta en pantalla, y sin marcas el revisador dispara a
 * ciegas: toma la foto sin saber si encuadro el lote o el nombre del producto.
 *
 * Las cajas vienen en el sistema de coordenadas de la imagen **ya enderezada**
 * (ML Kit rota el boundingBox cuando se le pasa `rotationDegrees`), por eso
 * [anchoImagen] y [altoImagen] tambien van enderezados: con rotacion de 90 o
 * 270 grados estan intercambiados respecto a la imagen del sensor. Quien dibuje
 * necesita las dos cosas para escalar bien, y usar las dimensiones del sensor en
 * vez de estas es justo el error que corre las marcas de lugar.
 */
data class CajaTexto(
    val texto: String,
    val izquierda: Float,
    val arriba: Float,
    val derecha: Float,
    val abajo: Float,
    /** Un codigo de barras o QR decodificado, no texto leido por OCR. */
    val esCodigo: Boolean = false
) {
    val ancho: Float get() = derecha - izquierda
    val alto: Float get() = abajo - arriba
}

data class GeometriaEnVivo(
    val anchoImagen: Int,
    val altoImagen: Int,
    val cajas: List<CajaTexto>
) {
    val valida: Boolean get() = anchoImagen > 0 && altoImagen > 0

    val codigos: List<CajaTexto> get() = cajas.filter { it.esCodigo }

    companion object {
        val VACIA = GeometriaEnVivo(0, 0, emptyList())
    }
}
