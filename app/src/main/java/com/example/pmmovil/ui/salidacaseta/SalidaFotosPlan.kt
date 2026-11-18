package com.example.pmmovil.ui.salidacaseta

/**
 * Cómo se parten en lotes las fotos de una salida y cuáles faltan por mandar.
 *
 * Antes las 8 o 10 fotos (8 a 12 MB) viajaban en una sola petición: con la red lenta de la caseta el guardia veía un
 * spinner sin avance durante 20 minutos y, si algo fallaba, todo volvía a empezar. En lotes de pocas fotos se ve el
 * avance, un fallo solo repite un lote y lo que ya llegó no se vuelve a mandar. Es lo mismo que ya hace Entrada.
 */
object SalidaFotosPlan {

    /**
     * Fotos por envío: las mismas 4 que manda Entrada (10 fotos subieron en 3 s en la tableta de caseta), unos 3 MB con
     * las fotos de ~780 KB que se miden en producción. Pocas para que cada envío termine (o falle) pronto aun con la red
     * mala, y para que el avance se mueva seguido; el servidor acepta hasta 6 por envío.
     */
    const val FOTOS_POR_LOTE = 4

    /**
     * Un lote: las fotos [desde] hasta [hastaExclusivo] de la lista, que en el servidor ocupan las posiciones
     * [indiceInicial] (desde 1) en adelante.
     */
    data class Lote(val desde: Int, val hastaExclusivo: Int) {
        val indiceInicial: Int get() = desde + 1
        val cantidad: Int get() = hastaExclusivo - desde
    }

    fun lotes(total: Int, porLote: Int = FOTOS_POR_LOTE): List<Lote> {
        if (total <= 0) return emptyList()
        val tamano = porLote.coerceAtLeast(1)
        return (0 until total step tamano).map { desde -> Lote(desde, minOf(desde + tamano, total)) }
    }

    /**
     * Los lotes que todavía hay que mandar. Una foto ya está en el servidor cuando se mandó en esa misma posición;
     * si el guardia quitó una del medio, las que se recorrieron cambian de posición y se mandan otra vez.
     *
     * Solo van las que faltan, juntando las que quedan seguidas en lotes de hasta [porLote]: con las fotos subiendo en
     * segundo plano mientras el guardia toma más (18-nov-2026), un lote de 3 que ya llegó no se vuelve a mandar entero
     * cuando aparece la cuarta. El servidor acepta cualquier posición inicial y reemplaza la foto de cada posición.
     */
    fun pendientes(fotos: List<String>, subidas: Map<String, Int>, porLote: Int = FOTOS_POR_LOTE): List<Lote> {
        val tamano = porLote.coerceAtLeast(1)
        val faltan = fotos.indices.filter { i -> subidas[fotos[i]] != i + 1 }
        val pendientes = mutableListOf<Lote>()
        var i = 0
        while (i < faltan.size) {
            val desde = faltan[i]
            var hasta = desde + 1
            i++
            while (i < faltan.size && faltan[i] == hasta && hasta - desde < tamano) {
                hasta++
                i++
            }
            pendientes += Lote(desde, hasta)
        }
        return pendientes
    }

    fun textoAvance(enviadas: Int, total: Int): String = "Enviando fotos: $enviadas de $total"
}
