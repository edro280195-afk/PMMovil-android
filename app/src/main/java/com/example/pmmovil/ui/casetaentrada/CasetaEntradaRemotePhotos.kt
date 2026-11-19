package com.example.pmmovil.ui.casetaentrada

import com.example.pmmovil.data.api.CasetaFotoUrls
import com.example.pmmovil.data.model.CasetaEntradaFotoInfo

/**
 * Fotos ya guardadas de una entrada a caseta, listas para pintarse sin bajar los originales.
 *
 * Cada foto son tres direcciones de la misma imagen en el servidor: la miniatura para la cuadrícula y la tira del visor,
 * la vista para verla en grande y el original solo cuando alguien hace zoom fuerte. Coil las guarda en la tableta; con la
 * versión del detalle, para siempre.
 *
 * Una foto sin versión viene de un servidor anterior (que no sabe de tamaños y mandaría el original a las tres
 * direcciones) o no tiene fila en la base: se usa una sola dirección, la del original, para bajarla una vez y no tres.
 */
internal object CasetaEntradaRemotePhotos {

    fun desdeServidor(
        baseUrl: String,
        casetaId: String,
        fotos: List<CasetaEntradaFotoInfo>,
        maximo: Int
    ): List<CasetaEntradaRemotePhoto> =
        fotos.take(maximo).mapIndexed { posicion, info ->
            val version = info.version.orEmpty().trim()
            fun url(tamano: CasetaFotoUrls.Tamano) = CasetaFotoUrls.de(baseUrl, casetaId, info.indice, tamano, version)
            val key = "remote-${info.indice}-$posicion"
            val label = info.nombreArchivo.ifBlank { "Guardada ${info.indice}" }
            if (version.isEmpty()) {
                CasetaEntradaRemotePhoto(
                    key = key,
                    label = label,
                    model = url(CasetaFotoUrls.Tamano.Original),
                    sourceIndex = info.indice
                )
            } else {
                CasetaEntradaRemotePhoto(
                    key = key,
                    label = label,
                    model = url(CasetaFotoUrls.Tamano.Mini),
                    sourceIndex = info.indice,
                    viewerModel = url(CasetaFotoUrls.Tamano.Vista),
                    zoomModel = url(CasetaFotoUrls.Tamano.Original)
                )
            }
        }
}
