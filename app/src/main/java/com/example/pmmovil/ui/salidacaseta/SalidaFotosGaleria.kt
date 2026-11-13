package com.example.pmmovil.ui.salidacaseta

import com.example.pmmovil.ui.components.EvidenceGalleryItem
import java.io.File

/**
 * Las fotos de una salida como las pide el visor grande ([com.example.pmmovil.ui.components.EvidenceGalleryDialog]).
 * Se arman aquí, sin Compose, para poder probar que cada foto conserva su identidad y su número al tocarla, al
 * deslizar y al borrar una del medio.
 */
object SalidaFotosGaleria {

    /** Fotos que el guardia tomó con la cámara: viven en la tableta y su id es la ruta del archivo. */
    fun deSalida(rutas: List<String>): List<EvidenceGalleryItem> =
        rutas.mapIndexed { i, ruta ->
            EvidenceGalleryItem(id = ruta, model = File(ruta), label = "Foto ${i + 1}")
        }

    /**
     * Fotos que se tomaron al llegar la caja: viven en el servidor, así que el visor las pide por URL. [urlDe] arma
     * la dirección de cada índice (el mismo que ya usan las miniaturas).
     */
    fun deLlegada(indices: List<Int>, urlDe: (Int) -> String): List<EvidenceGalleryItem> =
        indices.mapIndexed { i, indice ->
            EvidenceGalleryItem(id = "llegada-$indice", model = urlDe(indice), label = "Llegada ${i + 1}")
        }
}
