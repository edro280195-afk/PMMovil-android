package com.example.pmmovil.data.ocr

/**
 * Saca campos estructurados del texto que ya leyo el OCR de una etiqueta.
 *
 * El hallazgo que justifica esta clase: al revisar 119 fotos de evidencia de 15
 * clientes, TODAS las etiquetas traen anclas explicitas -- `LOT #:`, `Lot Number`,
 * `Batch/Lot#`, `PART #`, `COO:`, `Qty:`, `Packing Group:` -- y en 10 de 13
 * revisiones habia ademas un codigo de barras legible en la propia foto. O sea: el
 * dato ya viene impreso y hoy se teclea a mano. Ver docs/ia-revision-fisica.md.
 *
 * Dos reglas que no se negocian:
 *
 *  1. **No se inventa nada.** Si el ancla no aparece, el campo no sale. Mismo
 *     criterio que PlateOcrRanker con las placas: nunca fabricar guiones ni
 *     completar formatos. Un lote mal "corregido" es peor que un lote vacio.
 *  2. **El OCR propone, el revisador dispone.** Cada campo sale con su ancla y su
 *     confianza para que la pantalla pueda mostrar de donde salio y el humano
 *     confirme.
 *
 * No depende de Android: se prueba con JUnit sobre texto plano.
 */
object LabelFieldExtractor {

    enum class Campo {
        LOTE,
        NUMERO_PARTE,
        NUMERO_SERIE,
        CANTIDAD,
        PIEZAS_POR_EMPAQUE,
        PAIS_ORIGEN,
        CADUCIDAD,
        UN_HAZMAT,
        CLASE_HAZMAT,
        GRUPO_EMBALAJE
    }

    /** De donde salio el valor. Importa para la confianza y para explicarlo en pantalla. */
    enum class Origen {
        /** Ancla de texto en la misma linea: `LOT #: 2504298-135`. */
        ANCLA_MISMA_LINEA,

        /** Ancla al final de la linea y valor en la siguiente. */
        ANCLA_LINEA_SIGUIENTE,

        /** Identificador de aplicacion GS1 dentro de un codigo: `(10)2625100027`. */
        GS1,

        /** Patron propio del rombo de mercancia peligrosa: `UN1866`. */
        ROMBO_HAZMAT
    }

    data class CampoLeido(
        val campo: Campo,
        val valor: String,
        val origen: Origen,
        /** Lo que se vio en la etiqueta, tal cual, para poder mostrarlo. */
        val ancla: String,
        val confianza: Int
    )

    /**
     * Anclas por campo, en orden de preferencia. Todas salieron de etiquetas
     * reales; el comentario dice de cual, para que nadie las borre pensando que
     * son inventadas.
     */
    private val ANCLAS: Map<Campo, List<String>> = mapOf(
        Campo.LOTE to listOf(
            "BATCH/LOT#", "BATCH/LOT",      // ADM Wild Flavors
            "LOT NUMBER",                   // allnex
            "LOT NO", "LOT #", "LOT#",      // PATCO, Brikksen
            "NO. DE LOTE", "NUM. LOTE", "NUMERO DE LOTE", "LOTE",
            "BATCH", "LOT"
        ),
        Campo.NUMERO_PARTE to listOf(
            "PART NO", "PART #", "PART#",   // SKF, Brikksen
            "PRODUCT CODE",                 // allnex
            "ITEM NO", "ITEM #", "ITEM#", "ITEM",  // VORTEX, PATCO
            "NO. PARTE", "NUM. PARTE", "NO PARTE", "P/N"
        ),
        Campo.NUMERO_SERIE to listOf(
            "SERIAL NO", "SERIAL #", "SERIAL", "S/N",
            "NO. SERIE", "NUM. SERIE", "NO SERIE"
        ),
        Campo.CANTIDAD to listOf(
            "QUANTITY", "QTY",              // ENERPAC, VORTEX, SKF
            "CANTIDAD", "CANT"
        ),
        Campo.PIEZAS_POR_EMPAQUE to listOf(
            "UNITS/PALLET", "UNITS PER PALLET",  // PATCO
            "PER BAG", "PER BOX", "PZS/CAJA", "PIEZAS POR"
        ),
        Campo.PAIS_ORIGEN to listOf(
            "COO",                          // Brikksen
            "COUNTRY OF ORIGIN", "MADE IN", "HECHO EN", "PAIS DE ORIGEN", "ORIGEN"
        ),
        Campo.CADUCIDAD to listOf(
            "EXP DATE", "EXP. DATE", "EXPIRY DATE", "EXPIRY", "EXP",
            "CADUCIDAD", "FECHA DE CADUCIDAD"
        ),
        Campo.CLASE_HAZMAT to listOf("HAZARD CLASS", "CLASE DE PELIGRO", "CLASE"),
        Campo.GRUPO_EMBALAJE to listOf("PACKING GROUP", "GRUPO DE EMBALAJE", "GRUPO EMBALAJE")
    )

    /** Identificadores de aplicacion GS1 que de verdad aparecen en estas etiquetas. */
    private val GS1_AI: Map<String, Campo> = mapOf(
        "10" to Campo.LOTE,
        "21" to Campo.NUMERO_SERIE,
        "30" to Campo.CANTIDAD,
        "17" to Campo.CADUCIDAD,
        "37" to Campo.PIEZAS_POR_EMPAQUE
    )

    private val UN_ROMBO = Regex("""\bUN\s?(\d{4})\b""", RegexOption.IGNORE_CASE)
    private val GS1_PARENTESIS = Regex("""\((\d{2,4})\)\s*([A-Za-z0-9\-_/.]+)""")

    /** `Qty: 1 of 2` -> cantidad 1, de un total de 2. Visto en VORTEX. */
    private val N_DE_M = Regex("""^(\d+(?:[.,]\d+)?)\s*(?:of|de)\s*(\d+(?:[.,]\d+)?)$""", RegexOption.IGNORE_CASE)

    /** `10 PCS`, `10 PIEZAS`. Visto en Brikksen. */
    private val PIEZAS_SUELTAS = Regex("""^(\d{1,5})\s*(PCS|PZS|PIEZAS|PIECES)\b""", RegexOption.IGNORE_CASE)

    /**
     * @param textoOcr el `rawText` que entrega [LocalTextOcrResult].
     * @param codigos los codigos de barras leidos en la misma captura. Un codigo
     *        siempre le gana al texto: es exacto.
     */
    fun extraer(textoOcr: String, codigos: List<String> = emptyList()): List<CampoLeido> {
        val encontrados = LinkedHashMap<Campo, CampoLeido>()

        // 1. Codigos primero: si el dato viene en GS1 no hay nada que adivinar.
        codigos.forEach { codigo ->
            leerGs1(codigo).forEach { campo ->
                encontrados.putIfAbsent(campo.campo, campo)
            }
        }

        val lineas = textoOcr.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // 2. Anclas de texto.
        lineas.forEachIndexed { i, linea ->
            val siguiente = lineas.getOrNull(i + 1)
            ANCLAS.forEach { (campo, anclas) ->
                if (encontrados.containsKey(campo)) return@forEach
                buscarPorAncla(campo, anclas, linea, siguiente)?.let { encontrados[campo] = it }
            }
        }

        // 3. Patrones sin ancla que solo valen en contextos muy concretos.
        lineas.forEach { linea ->
            if (!encontrados.containsKey(Campo.UN_HAZMAT)) {
                UN_ROMBO.find(linea)?.let {
                    encontrados[Campo.UN_HAZMAT] = CampoLeido(
                        campo = Campo.UN_HAZMAT,
                        valor = it.groupValues[1],
                        origen = Origen.ROMBO_HAZMAT,
                        ancla = it.value,
                        confianza = 95
                    )
                }
            }
            if (!encontrados.containsKey(Campo.PIEZAS_POR_EMPAQUE)) {
                PIEZAS_SUELTAS.find(linea)?.let {
                    encontrados[Campo.PIEZAS_POR_EMPAQUE] = CampoLeido(
                        campo = Campo.PIEZAS_POR_EMPAQUE,
                        valor = it.groupValues[1],
                        origen = Origen.ANCLA_MISMA_LINEA,
                        ancla = it.value,
                        confianza = 80
                    )
                }
            }
        }

        return encontrados.values.toList()
    }

    private fun buscarPorAncla(
        campo: Campo,
        anclas: List<String>,
        linea: String,
        siguiente: String?
    ): CampoLeido? {
        val mayus = linea.uppercase()
        for (ancla in anclas) {
            val pos = mayus.indexOf(ancla)
            if (pos < 0) continue

            // El ancla tiene que empezar en frontera de palabra: asi "LOT" no
            // pega dentro de "PILOT" ni "CANT" dentro de "DESCANT".
            if (pos > 0 && mayus[pos - 1].isLetterOrDigit()) continue

            val resto = linea.substring(pos + ancla.length)
            val mismaLinea = limpiarValor(resto)
            if (esValorPlausible(campo, mismaLinea)) {
                return CampoLeido(
                    campo = campo,
                    valor = normalizar(campo, mismaLinea),
                    origen = Origen.ANCLA_MISMA_LINEA,
                    ancla = linea.substring(pos, pos + ancla.length),
                    confianza = 90
                )
            }

            // El ancla quedo sola al final del renglon: el valor va abajo.
            if (mismaLinea.isEmpty() && siguiente != null) {
                val abajo = limpiarValor(siguiente)
                if (esValorPlausible(campo, abajo)) {
                    return CampoLeido(
                        campo = campo,
                        valor = normalizar(campo, abajo),
                        origen = Origen.ANCLA_LINEA_SIGUIENTE,
                        ancla = linea.substring(pos, pos + ancla.length),
                        confianza = 70
                    )
                }
            }
        }
        return null
    }

    /**
     * Quita lo que separa el ancla del valor y la basura de orilla, pero **no**
     * toca el interior: un lote puede traer guiones, puntos o diagonales y no nos
     * toca decidir cuales son correctos.
     */
    private fun limpiarValor(bruto: String): String {
        var v = bruto.trim()
        v = v.trimStart(':', '#', '-', '=', '.', ' ', ' ')
        // Si quedan dos o mas campos en la misma linea ("Order #: VL1 Rev: 1"),
        // nos quedamos con el primer token.
        v = v.trim().split(Regex("""\s{2,}""")).firstOrNull()?.trim() ?: v
        return v.trimEnd(':', ',', ';', '.', ' ')
    }

    private fun esValorPlausible(campo: Campo, valor: String): Boolean {
        if (valor.isEmpty() || valor.length > 60) return false
        // Un valor que es puro signo no es un valor.
        if (valor.none { it.isLetterOrDigit() }) return false
        return when (campo) {
            Campo.CANTIDAD, Campo.PIEZAS_POR_EMPAQUE ->
                valor.firstOrNull()?.isDigit() == true
            Campo.CLASE_HAZMAT, Campo.GRUPO_EMBALAJE ->
                valor.length <= 6
            Campo.PAIS_ORIGEN ->
                valor.length in 2..40 && valor.any { it.isLetter() }
            Campo.LOTE, Campo.NUMERO_PARTE, Campo.NUMERO_SERIE ->
                valor.length >= 3 && valor.any { it.isDigit() }
            else -> true
        }
    }

    /** Lo unico que se normaliza: `Qty: 1 of 2` deja la cantidad, no el texto. */
    private fun normalizar(campo: Campo, valor: String): String {
        if (campo == Campo.CANTIDAD) {
            N_DE_M.find(valor)?.let { return it.groupValues[1] }
        }
        return valor
    }

    /**
     * Lee identificadores GS1 en su forma con parentesis, que es como los imprimen
     * estas etiquetas (`LOT NO (10) 2625100027`, PATCO).
     */
    private fun leerGs1(codigo: String): List<CampoLeido> =
        GS1_PARENTESIS.findAll(codigo).mapNotNull { m ->
            val campo = GS1_AI[m.groupValues[1]] ?: return@mapNotNull null
            val valor = m.groupValues[2].trim()
            if (valor.isEmpty()) return@mapNotNull null
            CampoLeido(
                campo = campo,
                valor = valor,
                origen = Origen.GS1,
                ancla = "(${m.groupValues[1]})",
                confianza = 99
            )
        }.toList()
}
