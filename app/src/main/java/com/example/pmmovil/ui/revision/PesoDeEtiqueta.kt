package com.example.pmmovil.ui.revision

import com.example.pmmovil.R
import com.example.pmmovil.data.ocr.LabelFieldExtractor.Campo
import com.example.pmmovil.data.ocr.LabelFieldExtractor.CampoLeido

/**
 * Qué hacer con el peso que trae una etiqueta.
 *
 * **Lo decidió Eduardo el 19-oct-2026:** el peso leído va al apartado de **Inspección física**, al campo
 * «Peso kg» o «Peso lb» **según la unidad que traiga la etiqueta** — sin convertir, al que corresponde.
 * Esos campos son de la factura (`fact.npesokgs` / `fact.npesolbs`), no de la partida.
 *
 * Dos cosas que esta lógica **no** decide, a propósito:
 *
 *  1. **Cuál peso, cuando la etiqueta trae dos.** La de envío VDA trae `NET WT KG 8` y `GROSS WT KG 9`.
 *     La base no distingue neto de bruto en ninguna columna y la pantalla rotula «Peso kg» a secas, así
 *     que elegir uno sería inventarlo. Con dos pesos se devuelve [Resultado.Ambiguo] y el revisador
 *     teclea el que corresponde, viendo los dos.
 *  2. **Pisar lo ya capturado.** Si el campo destino ya trae un peso distinto de cero no se toca: puede
 *     ser el peso del documento o algo que el revisador ya escribió. Se devuelve [Resultado.YaCapturado]
 *     para poder decirlo en pantalla en vez de sobreescribir en silencio.
 *
 * El recálculo entre libras y kilos no se hace aquí: ya lo aplica el servidor al guardar
 * (`GuardarCampoFacturaAsync`, misma regla que el VFP — libras recalcula kilos siempre, kilos recalcula
 * libras solo si libras seguía en cero).
 */
object PesoDeEtiqueta {

    const val CAMPO_KILOS = "npesokgs"
    const val CAMPO_LIBRAS = "npesolbs"

    data class Peso(val valor: Double, val unidad: String, val esBruto: Boolean) {
        /** El campo de Inspección física que le toca por su unidad. */
        val campo: String get() = if (unidad == "LB") CAMPO_LIBRAS else CAMPO_KILOS
    }

    sealed interface Resultado {
        /** La etiqueta no trae peso legible, o lo trae sin unidad. */
        data object Ninguno : Resultado

        /** Un solo peso: se puede guardar tal cual en su campo. */
        data class Unico(val peso: Peso) : Resultado

        /** Neto y bruto a la vez: lo elige el revisador. */
        data class Ambiguo(val neto: Peso, val bruto: Peso) : Resultado

        /** Hay peso en la etiqueta pero el campo destino ya tiene un valor capturado. */
        data class YaCapturado(val peso: Peso, val actual: Double) : Resultado
    }

    /**
     * El aviso que se le muestra al revisador al guardar una etiqueta, o null si no hay nada que
     * decir. **No escribe nada todavía**, por lo medido el 19-oct-2026:
     *
     *  - `revdet.npeso` guarda el peso **total de la partida**, no el de un empaque: la partida 3055981
     *    pesa 534.392 lb (242.4 kg) y su etiqueta dice 8 kg, porque son 30 empaques (600 000 piezas
     *    entre 20 000 por etiqueta, y 30 × 8 = 240 kg).
     *  - Ese peso **ya viene lleno** en el 91.7 % de las partidas de Freudenberg sin revisar (89.5 % en
     *    todos los clientes del último año): lo precarga clasificación, igual que `ncantout`.
     *
     * Así que copiar el peso de la etiqueta lo dejaría treinta veces por debajo, y llenar un hueco casi
     * nunca es lo que hace falta. Lo útil es **cotejar**, y si hay que sumar empaques eso es una
     * decisión de negocio que no se toma aquí.
     *
     * @param pesoDeclaradoLibras lo que la partida ya declara en «Peso lb» (`revdet.npeso`).
     * @param texto resuelve un recurso de cadena con sus argumentos.
     */
    fun aviso(
        observaciones: List<CampoLeido>,
        pesoDeclaradoLibras: Double,
        texto: (Int, Array<Any>) -> String
    ): String? {
        val neto = pesoDe(observaciones, Campo.PESO_NETO, esBruto = false)
        val bruto = pesoDe(observaciones, Campo.PESO_BRUTO, esBruto = true)
        val peso = neto ?: bruto ?: return null

        val declaradoKg = RevisionWeightRules.kilosFromLibras(pesoDeclaradoLibras)
        val etiquetaEnKg = if (peso.unidad == "LB") RevisionWeightRules.kilosFromLibras(peso.valor) else peso.valor

        if (pesoDeclaradoLibras <= 0.0) return null
        return texto(
            R.string.label_capture_weight_compared,
            arrayOf(formatear(etiquetaEnKg), formatear(declaradoKg))
        )
    }

    /**
     * El aviso del peso **acumulado** de una partida: lo que suman los empaques leídos contra lo que la
     * partida declara. Es lo que ve el revisador al contar la entrada etiqueta por etiqueta.
     *
     * No dice si «cuadra»: los pesos no cierran exactos (30 empaques de 8 kg dan 240 contra 242.4
     * declarados) y poner una tolerancia sería inventarla. Enseña los dos números y el conteo.
     */
    fun avisoAcumulado(
        empaquesPesados: Int,
        empaquesContados: Int,
        pesoAcumuladoKg: Double,
        pesoDeclaradoKg: Double?,
        texto: (Int, Array<Any>) -> String
    ): String? = avisoAcumuladoRecurso(empaquesPesados, empaquesContados, pesoAcumuladoKg, pesoDeclaradoKg)
        ?.let { (id, args) -> texto(id, args) }

    /**
     * Lo mismo que [avisoAcumulado] pero sin resolver el texto: devuelve el recurso y sus argumentos,
     * para que quien llame arme su propio `UiText` sin que esta lógica dependa de Android.
     */
    fun avisoAcumuladoRecurso(
        empaquesPesados: Int,
        empaquesContados: Int,
        pesoAcumuladoKg: Double,
        pesoDeclaradoKg: Double?
    ): Pair<Int, Array<Any>>? {
        if (empaquesPesados <= 0 || pesoAcumuladoKg <= 0.0) return null
        if (pesoDeclaradoKg == null || pesoDeclaradoKg <= 0.0) {
            return R.string.label_capture_weight_sum_only to
                arrayOf(formatear(pesoAcumuladoKg), empaquesPesados)
        }
        // Si algún empaque no traía peso, el acumulado todavía no es comparable: se dice cuántos sí.
        val id = if (empaquesPesados < empaquesContados) {
            R.string.label_capture_weight_sum_partial
        } else {
            R.string.label_capture_weight_sum_compared
        }
        return id to arrayOf(
            formatear(pesoAcumuladoKg), formatear(pesoDeclaradoKg),
            empaquesPesados, empaquesContados
        )
    }

    /**
     * @param observaciones lo que el lector sacó de la etiqueta.
     * @param pesoLbsActual lo que la factura ya tiene en «Peso lb».
     * @param pesoKgsActual lo que la factura ya tiene en «Peso kg».
     */
    fun resolver(
        observaciones: List<CampoLeido>,
        pesoLbsActual: Double,
        pesoKgsActual: Double
    ): Resultado {
        val neto = pesoDe(observaciones, Campo.PESO_NETO, esBruto = false)
        val bruto = pesoDe(observaciones, Campo.PESO_BRUTO, esBruto = true)

        if (neto != null && bruto != null) return Resultado.Ambiguo(neto, bruto)
        val peso = neto ?: bruto ?: return Resultado.Ninguno

        val actual = if (peso.campo == CAMPO_LIBRAS) pesoLbsActual else pesoKgsActual
        if (actual > 0.0) return Resultado.YaCapturado(peso, actual)
        return Resultado.Unico(peso)
    }

    /**
     * Sin unidad no se guarda nada: la etiqueta de la bolsa de Taiwán imprime números sueltos, y meter
     * kilos en el campo de libras cambia la cifra por 2.2.
     */
    /**
     * «8», «12.5», «242.4»: sin ceros de relleno, que en un peso se leen como error de captura.
     *
     * Redondea a dos decimales porque la conversión entre libras y kilos arrastra el error del punto
     * flotante — 534.392 × 0.4536 da 242.40021120000003 — y un aviso con esa cola no se lee como un
     * peso, se lee como una falla. Dos decimales es lo que ya usan los espejos de la pantalla.
     */
    fun formatear(valor: Double): String =
        valor.toBigDecimal()
            .setScale(2, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()

    private fun pesoDe(observaciones: List<CampoLeido>, campo: Campo, esBruto: Boolean): Peso? {
        val leido = observaciones.firstOrNull { it.campo == campo } ?: return null
        val unidad = leido.unidad?.uppercase() ?: return null
        if (unidad != "KG" && unidad != "LB") return null
        val valor = leido.valor.replace(',', '.').toDoubleOrNull() ?: return null
        if (!valor.isFinite() || valor <= 0.0) return null
        return Peso(valor, unidad, esBruto)
    }
}
