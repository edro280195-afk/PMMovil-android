package com.example.pmmovil.data.ocr

import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Empareja cada valor impreso con el rótulo que lo nombra, usando solo la geometría de los renglones.
 *
 * En una etiqueta de códigos por campo (la bolsa de Freudenberg) cada código de barras trae su valor
 * impreso al lado y, junto a él, un rótulo chico («QTY», «Batch», «Part Number»). Los lectores de texto
 * entregan esos renglones desordenados y giradas las columnas, así que «el renglón de abajo» no sirve:
 * en la bolsa real el rótulo «QTY» quedaba seguido del PO. En cambio la distancia sí es confiable:
 * el valor siempre queda pegado a su rótulo, del mismo lado y alineado con él.
 *
 * Todo se mide en el marco de cada renglón (hacia dónde corre y hacia dónde queda «arriba»), de modo que
 * funciona igual con la etiqueta derecha, girada o de cabeza. Como cada plantilla pone el valor encima o
 * debajo de su rótulo, se prueban los dos lados y gana el que empareja más rótulos.
 *
 * Solo se toman como valores los renglones cuyo texto es **exactamente** un código de barras leído: un
 * número que el OCR confundió no se empareja. Un rótulo sin valor cerca se queda sin valor.
 */
object LabelCaptionPairing {
    enum class Rotulo { PO, PARTE, CANTIDAD, PROVEEDOR, CURADO, ORIGEN, LOTE, DESCRIPCION }

    data class Par(val rotulo: Rotulo, val textoDelRotulo: String, val valor: String)

    private val ROTULOS = listOf(
        Rotulo.PO to Regex("""^P\.?\s?O\.?\s*(NUMBER|NO\.?|#)?$|^PURCHASE ORDER$""", RegexOption.IGNORE_CASE),
        Rotulo.PARTE to Regex("""^PART\s*(NUMBER|NO\.?|#)$""", RegexOption.IGNORE_CASE),
        Rotulo.CANTIDAD to Regex("""^(QTY|QUANTITY|CANTIDAD)\.?$""", RegexOption.IGNORE_CASE),
        Rotulo.PROVEEDOR to Regex("""^VENDOR(\s*(NUMBER|NO\.?|#))?$""", RegexOption.IGNORE_CASE),
        Rotulo.CURADO to Regex("""^CURE\s*DATE$""", RegexOption.IGNORE_CASE),
        Rotulo.ORIGEN to Regex("""^(ORIGIN|COUNTRY OF ORIGIN|COO)$""", RegexOption.IGNORE_CASE),
        Rotulo.LOTE to Regex("""^(BATCH|LOT)(\s*(NUMBER|NO\.?|#))?$""", RegexOption.IGNORE_CASE),
        Rotulo.DESCRIPCION to Regex("""^DESCRIPTION$""", RegexOption.IGNORE_CASE)
    )

    /** Qué tan lejos puede quedar el valor de su rótulo, en alturas de renglón. En la bolsa real son ~3. */
    private const val SEPARACION_MAXIMA = 5.0

    /** Cuánto se permite que el valor se desplace a lo largo del renglón respecto al rótulo (en largos de renglón). */
    private const val DESFASE_MAXIMO = 0.65

    /** Dos renglones del mismo bloque de la etiqueta corren casi en paralelo. */
    private const val PARALELISMO_MINIMO = 0.9

    /**
     * @param lineas renglones de texto con sus esquinas.
     * @param valores los códigos de barras leídos en la foto.
     */
    fun emparejar(lineas: List<LineaDeTexto>, valores: Collection<String>): List<Par> {
        val porNormalizado = valores.filter(String::isNotBlank).associateBy(::normalizar)
        if (porNormalizado.isEmpty()) return emptyList()
        val cajas = lineas.mapNotNull(Caja::de)
        val rotulos = cajas.mapNotNull { caja -> rotuloDe(caja.texto)?.let { caja to it } }
        val renglonesDeValor = cajas.filter { normalizar(it.texto) in porNormalizado }
        if (rotulos.isEmpty() || renglonesDeValor.isEmpty()) return emptyList()

        return listOf(1, -1)
            .map { sentido -> asignar(rotulos, renglonesDeValor, sentido, porNormalizado) }
            .maxByOrNull { pares -> pares.count { it.rotulo != Rotulo.DESCRIPCION } }
            .orEmpty()
    }

    private fun asignar(
        rotulos: List<Pair<Caja, Rotulo>>,
        renglonesDeValor: List<Caja>,
        sentido: Int,
        porNormalizado: Map<String, String>
    ): List<Par> {
        class Candidato(val rotulo: Caja, val tipo: Rotulo, val valor: Caja, val distancia: Double)

        val candidatos = rotulos.mapNotNull { (rotulo, tipo) ->
            renglonesDeValor.mapNotNull { valor ->
                val dx = valor.cx - rotulo.cx
                val dy = valor.cy - rotulo.cy
                val arriba = (dx * rotulo.ux + dy * rotulo.uy) * sentido
                val alLargo = dx * rotulo.bx + dy * rotulo.by
                val alto = maxOf(rotulo.alto, valor.alto)
                val paralelo = abs(rotulo.bx * valor.bx + rotulo.by * valor.by)
                val cerca = arriba > 0.5 * alto && arriba <= SEPARACION_MAXIMA * alto
                val alineado = abs(alLargo) <= DESFASE_MAXIMO * maxOf(rotulo.largo, valor.largo) + alto
                if (cerca && alineado && paralelo >= PARALELISMO_MINIMO) Candidato(rotulo, tipo, valor, arriba) else null
            }.minByOrNull { it.distancia }
        }

        // Un valor pertenece a un solo rótulo: si dos lo quieren, se lo queda el más cercano y el otro queda sin valor.
        val elegidos = candidatos.sortedBy { it.distancia }.distinctBy { it.valor }
        return elegidos
            .distinctBy { it.tipo }
            .map { Par(it.tipo, it.rotulo.texto, porNormalizado.getValue(normalizar(it.valor.texto))) }
    }

    private fun rotuloDe(texto: String): Rotulo? {
        val limpio = texto.trim()
        return ROTULOS.firstOrNull { (_, patron) -> patron.matches(limpio) }?.first
    }

    /** Mayúsculas sin espacios ni signos: «1Y-0000005 0337428» y «1Y-00000050337428» son el mismo valor. */
    private fun normalizar(valor: String): String =
        valor.uppercase(Locale.ROOT).filter(Char::isLetterOrDigit)

    /** Un renglón con su marco: hacia dónde corre (`b`) y hacia dónde queda «arriba» para él (`u`). */
    private class Caja(
        val texto: String,
        val cx: Double, val cy: Double,
        val largo: Double, val alto: Double,
        val bx: Double, val by: Double,
        val ux: Double, val uy: Double
    ) {
        companion object {
            fun de(linea: LineaDeTexto): Caja? {
                val e = linea.esquinas
                if (e.size != 4) return null
                val (tl, tr, _, bl) = e
                val largo = hypot((tr.x - tl.x).toDouble(), (tr.y - tl.y).toDouble())
                val alto = hypot((tl.x - bl.x).toDouble(), (tl.y - bl.y).toDouble())
                if (largo <= 0.0 || alto <= 0.0) return null
                return Caja(
                    texto = linea.texto,
                    cx = e.sumOf { it.x.toDouble() } / 4, cy = e.sumOf { it.y.toDouble() } / 4,
                    largo = largo, alto = alto,
                    bx = (tr.x - tl.x) / largo, by = (tr.y - tl.y) / largo,
                    ux = (tl.x - bl.x) / alto, uy = (tl.y - bl.y) / alto
                )
            }
        }
    }
}
