package com.example.pmmovil.data.api

import java.net.URLEncoder

/**
 * Direcciones de las fotos de caseta en el API (`Foto/caseta/ver`).
 *
 * El servidor sirve tres tamaños de la misma foto: la miniatura (~14 KB) para cuadrículas y tiras, la vista (~130 KB)
 * para verla en pantalla y el original (~800 KB) solo para el zoom fuerte. Medido con fotos reales de caseta el
 * 18-nov-2026: las 7 de una entrada pesan 5.5 MB en original y 105 KB en miniatura.
 *
 * Con la versión que manda el detalle ([com.example.pmmovil.data.model.CasetaEntradaFotoInfo.version]) la tableta guarda
 * la foto para siempre y no la vuelve a pedir; sin ella (fotos de llegada en la salida) pregunta si cambió y el servidor
 * contesta con unos cuantos bytes si no. Un servidor anterior ignora `tam` y `v` y manda el original: la app sigue
 * funcionando, solo que más pesada.
 */
object CasetaFotoUrls {

    enum class Tamano(val parametro: String?) {
        Mini("mini"),
        Vista("vista"),
        Original(null)
    }

    fun de(baseUrl: String, casetaId: String, indice: Int, tamano: Tamano, version: String = ""): String = buildString {
        append(baseUrl)
        if (!baseUrl.endsWith("/")) append('/')
        append("Foto/caseta/ver?casetaId=")
        append(URLEncoder.encode(casetaId.trim(), "UTF-8"))
        append("&indice=").append(indice)
        tamano.parametro?.let { append("&tam=").append(it) }
        version.trim().takeIf(String::isNotEmpty)?.let { append("&v=").append(URLEncoder.encode(it, "UTF-8")) }
    }
}
