package com.example.pmmovil.data.ocr

import com.example.pmmovil.data.ocr.LabelFieldExtractor.Campo
import com.example.pmmovil.data.ocr.LabelFieldExtractor.CampoLeido
import com.example.pmmovil.data.ocr.LabelFieldExtractor.Origen
import com.example.pmmovil.data.scanner.BarcodeValueNormalizer
import com.example.pmmovil.data.scanner.LabelDataIdentifiers
import java.util.Locale

/**
 * Entiende las etiquetas cuyos códigos de barras son simples y sin nombre de campo, como la bolsa de
 * Freudenberg: ocho Code 128 que traen solo el valor (PO, parte, cantidad, ID del empaque, proveedor,
 * curado, origen y lote). El código no dice qué es; las claves de las partidas de la entrada sí ayudan:
 * un código igual a la clave de una partida es el número de parte, y uno que es un país es el origen.
 *
 * Es deliberadamente conservador. Si no está seguro de qué campo es un valor, no lo asigna, porque un
 * lote o una cantidad equivocados son peor que ninguno. El **orden** del texto del OCR no sirve para
 * asignarlos: las columnas salen giradas y los renglones no emparejan cada valor con su rótulo (en la
 * bolsa real propuso «lote = 200» y «cantidad = el PO»). Lo que sí sirve es la **geometría**: el valor
 * impreso queda pegado a su rótulo, ver [LabelCaptionPairing]. Sin geometría, o si no empareja con
 * certeza, el lote y la cantidad se quedan sin asignar.
 *
 * Los códigos estructurados (`+30S…+Q…+H…`, GS1) no pasan por aquí: ya los lee [LabelFieldExtractor].
 */
object LabelBarcodeInterpreter {
    data class Interpretacion(
        val campos: List<CampoLeido>,
        /** Campos en los que el texto no debe opinar para esta etiqueta. */
        val camposSinOcr: Set<Campo>
    ) {
        companion object {
            val VACIA = Interpretacion(emptyList(), emptySet())
        }
    }

    /** Con menos códigos simples que esto no se trata como una etiqueta de códigos por campo. */
    private const val MINIMO_CODIGOS_POR_CAMPO = 4

    /**
     * @param claves claves de las partidas de la entrada (número de parte y alias del cliente).
     * @param esPais dice si un valor es un país del catálogo, por nombre o por código.
     */
    fun interpretar(
        codigos: List<String>,
        claves: Collection<String>,
        esPais: (String) -> Boolean = { false },
        lineas: List<LineaDeTexto> = emptyList()
    ): Interpretacion {
        val catalogo = claves.map(::clave).filter(String::isNotEmpty).toSet()

        // Si cada código trae su identificador de dato adentro, la etiqueta se explica sola y no hace
        // falta adivinar nada por geometría ni por el texto. Se intenta antes que todo lo demás.
        porIdentificadores(codigos, catalogo)?.let { return it }

        val simples = codigos.map(BarcodeValueNormalizer::normalizeLabel).distinct().filter(::esCodigoSimple)
        if (simples.isEmpty() || claves.isEmpty()) return Interpretacion.VACIA
        val parte = simples.firstOrNull { valor ->
            val k = clave(valor)
            k in catalogo || sinCeros(k)?.let { it in catalogo } == true
        } ?: return Interpretacion.VACIA

        val campos = mutableListOf(
            CampoLeido(Campo.NUMERO_PARTE, parte, Origen.CODIGO_PLANO, "código de barras", 99, rawCode = parte)
        )
        val porCampo = simples.size >= MINIMO_CODIGOS_POR_CAMPO
        val paises = if (porCampo) simples.filter { it != parte && esPais(it) } else emptyList()
        paises.firstOrNull()?.let { pais ->
            campos += CampoLeido(Campo.PAIS_ORIGEN, pais, Origen.CODIGO_PLANO, "código de barras", 95, rawCode = pais)
        }
        // El lote y la cantidad solo se asignan cuando la geometría del texto demuestra a qué rótulo pertenecen:
        // 200, 667 y los dos números de diez dígitos no se distinguen por su forma.
        if (porCampo && lineas.isNotEmpty()) {
            val pares = LabelCaptionPairing.emparejar(lineas, simples)
            pares.firstOrNull { it.rotulo == LabelCaptionPairing.Rotulo.LOTE }
                ?.takeIf { it.valor != parte }
                ?.let { campos += CampoLeido(Campo.LOTE, it.valor, Origen.CODIGO_PLANO, it.textoDelRotulo, 95, rawCode = it.valor) }
            pares.firstOrNull { it.rotulo == LabelCaptionPairing.Rotulo.CANTIDAD }
                ?.takeIf { par -> par.valor.length <= 9 && par.valor.all { it in '0'..'9' } }
                ?.let { campos += CampoLeido(Campo.CANTIDAD, it.valor, Origen.CODIGO_PLANO, it.textoDelRotulo, 95, rawCode = it.valor) }
        }

        val sinOcr = if (porCampo) {
            buildSet {
                add(Campo.NUMERO_PARTE); add(Campo.LOTE); add(Campo.CANTIDAD)
                if (paises.isNotEmpty()) add(Campo.PAIS_ORIGEN)
            }
        } else emptySet()
        return Interpretacion(campos, sinOcr)
    }

    /**
     * La etiqueta cuyos códigos traen el identificador de dato adentro: el PDF417 del compuesto químico
     * (`+30S…+Q…+H…`) y el envío VDA de Freudenberg-NOK (un Code 39 por campo, `P…`, `Q…`, `H…`).
     *
     * Devuelve null si no es una de esas, para que siga el camino de siempre. Solo se acepta cuando la
     * clave leída es la de una partida de la entrada: sin esa comprobación, un conjunto de códigos
     * cualquiera que empezara con las letras correctas se leería como una etiqueta.
     */
    private fun porIdentificadores(codigos: List<String>, catalogo: Set<String>): Interpretacion? {
        val leido = LabelDataIdentifiers.leer(codigos)
        if (leido.vacia) return null

        val parte = leido.clave ?: return null
        val normalizada = clave(parte)
        val enCatalogo = normalizada in catalogo ||
            clave(LabelDataIdentifiers.sinCerosALaIzquierda(normalizada)) in catalogo
        if (!enCatalogo) return null

        val fuente = if (leido.forma == LabelDataIdentifiers.Forma.CONCATENADA) {
            "código estructurado"
        } else {
            "código con nombre de campo"
        }
        val campos = mutableListOf(
            CampoLeido(Campo.NUMERO_PARTE, parte, Origen.STRUCTURED_BARCODE, fuente, 99, rawCode = parte)
        )
        leido[LabelDataIdentifiers.Dato.CANTIDAD]?.let {
            campos += CampoLeido(Campo.CANTIDAD, it, Origen.STRUCTURED_BARCODE, "$fuente · Q", 99, rawCode = it)
        }
        leido[LabelDataIdentifiers.Dato.LOTE]?.let {
            campos += CampoLeido(Campo.LOTE, it, Origen.STRUCTURED_BARCODE, "$fuente · H", 99, rawCode = it)
        }
        leido[LabelDataIdentifiers.Dato.FECHA]?.let {
            campos += CampoLeido(Campo.CADUCIDAD, fechaImpresa(it), Origen.STRUCTURED_BARCODE, "$fuente · 2D", 99, rawValue = it, rawCode = it)
        }

        // El texto no opina de lo que el código ya dijo: ahí es donde salían «LOTE = NO. (H) 5004029781»
        // y la cantidad tomada del peso del renglón de abajo. El país sí, porque no viaja en el código.
        return Interpretacion(
            campos,
            setOf(Campo.NUMERO_PARTE, Campo.CANTIDAD, Campo.LOTE, Campo.CADUCIDAD)
        )
    }

    /** `092036` es septiembre de 2036; `10102027` es el 10 de octubre de 2027. */
    private fun fechaImpresa(valor: String): String = when {
        valor.length == 6 && valor.all { it in '0'..'9' } -> "${valor.substring(0, 2)}/${valor.substring(2)}"
        valor.length == 8 && valor.all { it in '0'..'9' } -> "${valor.substring(0, 2)}/${valor.substring(2, 4)}/${valor.substring(4)}"
        else -> valor
    }

    /** Un valor suelto, no uno estructurado (`+…`), GS1 con paréntesis ni con identificador AIM (`]C1`). */
    private fun esCodigoSimple(valor: String): Boolean =
        valor.isNotBlank() && !valor.startsWith("+") && !valor.startsWith("(") && !valor.startsWith("]") &&
            '\u001D' !in valor

    private fun clave(valor: String): String = valor.trim().uppercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    private fun sinCeros(clave: String): String? =
        if (clave.length > 1 && clave[0] == '0' && clave.all { it in '0'..'9' }) clave.trimStart('0').ifEmpty { "0" } else null
}
