package com.example.pmmovil.data.api

import java.net.URLEncoder

/**
 * Las tres direcciones de una foto del servidor: [mini] para cuadrículas y tiras (~14 KB), [vista] para verla en grande
 * (~130 KB) y [original] (~800 KB) solo para el zoom fuerte. Sin [original] no hay una distinta que traer al acercarse.
 */
data class FotoRemota(val mini: String, val vista: String, val original: String?)

/**
 * Direcciones de las fotos de Revisión y de Entrada Bodega en el API: las que viven en la carpeta del cliente en el 23
 * (`Foto/ver/{id}`, `Foto/entrada/ver/{ref}/{línea}` y `Foto/entrada-danio/ver/{ref}/{línea}`). Mismos tres tamaños y la
 * misma regla de caché que Caseta (ver [CasetaFotoUrls]): con la versión que da la lista, la tableta guarda la foto para
 * siempre y no la vuelve a pedir.
 */
object EvidenciaFotoUrls {

    /** Evidencia de revisión (107) o de discrepancia (103), por su idfotos. */
    fun revision(baseUrl: String, idFotos: Int, tamano: CasetaFotoUrls.Tamano, version: String = ""): String =
        conTamano("${base(baseUrl)}Foto/ver/$idFotos", tamano, version)

    /** Evidencia (100) o daño (103) de Entrada Bodega, por referencia y línea. */
    fun entrada(
        baseUrl: String,
        referencia: String,
        linea: Int,
        danio: Boolean,
        tamano: CasetaFotoUrls.Tamano,
        version: String = ""
    ): String {
        val ruta = if (danio) "Foto/entrada-danio/ver" else "Foto/entrada/ver"
        return conTamano("${base(baseUrl)}$ruta/${referencia.trim()}/$linea", tamano, version)
    }

    /**
     * Las tres direcciones de una foto según su [version] (la que manda la lista). Sin versión el servidor es uno anterior
     * que no sabe de tamaños y mandaría el original a las tres: entonces se usa una sola dirección, la del original, para
     * bajarla una vez y no tres, y sin zoom aparte.
     */
    fun tres(version: String?, url: (CasetaFotoUrls.Tamano, String) -> String): FotoRemota {
        val vigente = version.orEmpty().trim()
        if (vigente.isEmpty()) {
            val original = url(CasetaFotoUrls.Tamano.Original, "")
            return FotoRemota(mini = original, vista = original, original = null)
        }
        return FotoRemota(
            mini = url(CasetaFotoUrls.Tamano.Mini, vigente),
            vista = url(CasetaFotoUrls.Tamano.Vista, vigente),
            original = url(CasetaFotoUrls.Tamano.Original, vigente)
        )
    }

    /** Las tres de una foto de revisión. */
    fun revision(baseUrl: String, idFotos: Int, version: String?): FotoRemota =
        tres(version) { tamano, v -> revision(baseUrl, idFotos, tamano, v) }

    /** Las tres de una foto de Entrada Bodega. */
    fun entrada(baseUrl: String, referencia: String, linea: Int, danio: Boolean, version: String?): FotoRemota =
        tres(version) { tamano, v -> entrada(baseUrl, referencia, linea, danio, tamano, v) }

    private fun base(baseUrl: String): String = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

    private fun conTamano(direccion: String, tamano: CasetaFotoUrls.Tamano, version: String): String {
        val parametros = buildList {
            tamano.parametro?.let { add("tam=$it") }
            version.trim().takeIf(String::isNotEmpty)?.let { add("v=" + URLEncoder.encode(it, "UTF-8")) }
        }
        return if (parametros.isEmpty()) direccion else direccion + "?" + parametros.joinToString("&")
    }
}
