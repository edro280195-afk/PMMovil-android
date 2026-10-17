package com.example.pmmovil.ui.revision

import com.example.pmmovil.data.model.ConfirmLabelCaptureRequest
import com.example.pmmovil.data.model.LabelCaptureFields
import com.example.pmmovil.data.model.LabelCaptureHazmat
import com.example.pmmovil.data.model.LabelCaptureObservation
import com.example.pmmovil.data.model.LabelCaptureSnapshot
import com.example.pmmovil.data.model.PartidaRevisionDto
import com.example.pmmovil.data.ocr.LabelFieldExtractor
import com.example.pmmovil.data.ocr.LabelFieldExtractor.Campo
import com.example.pmmovil.ui.revision.LabelCaptureViewModel.CampoPropuesto
import java.text.Normalizer
import java.util.Locale

/**
 * Compara lo que dice la etiqueta contra lo que la partida ya declara.
 *
 * Es lógica pura: la tarjeta de un toque solo dibuja lo que devuelve [comparar] y el
 * ViewModel solo envía lo que [Fila.seGuarda] permite. Un dato que no cambia nunca se manda
 * (ausente significa conservar) y la cantidad de la etiqueta jamás pisa la de la factura.
 */
object LabelCaptureCotejo {
    enum class Tipo {
        /** Lo leído y lo declarado dicen lo mismo, sin importar mayúsculas, espacios ni signos. */
        COINCIDE,

        /** La partida no tenía el dato (o, en lotes, es un lote que todavía no está): se agrega. */
        NUEVO,

        /** Hay un dato declarado distinto: el revisador decide con cuál se queda. */
        DIFERENTE,

        /** La foto trae lecturas contradictorias del mismo campo: hay que elegir una. */
        CONFLICTO,

        /** Se leyó algo, pero no sirve para guardarse (país fuera del catálogo, UN mal formado). */
        NO_VALIDO,

        /** La etiqueta no trajo el dato, o el revisador lo quitó. */
        SIN_LECTURA,

        /** Un empaque con menos que el total de la partida: es lo normal cuando llega en varios. */
        PARCIAL
    }

    /** Qué tan lejos o cerca está la cantidad de la etiqueta de la de la factura, en la misma unidad. */
    enum class ResultadoCantidad { IGUAL, PARCIAL, MAYOR, NO_COMPARABLE }

    /**
     * Cantidad de la etiqueta frente a la de la factura, ya expresadas en la misma unidad. Solo informa:
     * ninguna cantidad de la etiqueta se escribe en la partida.
     */
    data class Cantidad(
        val etiqueta: Double,
        val declaradaFactura: Double,
        val unidadFactura: String,
        /** «pzas» cuando la factura va en cientos o millares; si no, la propia unidad de la factura. */
        val unidadBase: String,
        val declaradaEnBase: Double,
        /** Falso cuando la etiqueta trae otra unidad (kilos contra piezas) y comparar engañaría. */
        val comparable: Boolean
    ) {
        val resultado: ResultadoCantidad
            get() = when {
                !comparable -> ResultadoCantidad.NO_COMPARABLE
                kotlin.math.abs(etiqueta - declaradaEnBase) <= TOLERANCIA + declaradaEnBase * 1e-9 -> ResultadoCantidad.IGUAL
                etiqueta < declaradaEnBase -> ResultadoCantidad.PARCIAL
                else -> ResultadoCantidad.MAYOR
            }
    }

    enum class Decision { CONSERVAR, USAR }

    data class Fila(
        val campo: Campo,
        val tipo: Tipo,
        /** Lo que la partida tiene hoy. Vacío si no tiene nada. */
        val declarado: String,
        /** Lo que se escribiría. Vacío si no se leyó nada utilizable. */
        val leido: String,
        /** Frase corta para acompañar la fila (por ejemplo, país con nombre). */
        val detalle: String? = null,
        /** Falso para datos que solo sirven de referencia, como el número de parte. */
        val escribible: Boolean = true,
        val decision: Decision? = null,
        /** Lecturas entre las que hay que elegir cuando [tipo] es [Tipo.CONFLICTO]. */
        val opciones: List<String> = emptyList(),
        /** Solo lotes: el lote leído se agrega a los ya declarados, no los reemplaza. */
        val suma: Boolean = false,
        /** Solo la fila de cantidad: la comparación ya convertida a una misma unidad. */
        val cantidad: Cantidad? = null
    ) {
        val pendiente: Boolean
            get() = escribible && (tipo == Tipo.CONFLICTO || tipo == Tipo.DIFERENTE && decision == null)
        val seGuarda: Boolean
            get() = escribible && (tipo == Tipo.NUEVO || tipo == Tipo.DIFERENTE && decision == Decision.USAR)
        val seConserva: Boolean
            get() = escribible && (tipo == Tipo.COINCIDE || tipo == Tipo.DIFERENTE && decision == Decision.CONSERVAR)

        /** Lo que quedará en la partida para este dato una vez que se confirme. */
        val resultante: String
            get() = when {
                seGuarda -> leido
                else -> declarado
            }
    }

    /** Qué tanto respalda la clave impresa en la etiqueta que sea la partida elegida. */
    enum class Identidad {
        /** La clave de la etiqueta es la de la partida (o su alias del cliente). */
        CLAVE_EXACTA,

        /** La etiqueta no trae una clave legible: solo una elección humana puede dar la identidad por buena. */
        SIN_CLAVE,

        /** La etiqueta trae una clave y no es la de esta partida. */
        CLAVE_DISTINTA
    }

    /** Lo que trae la etiqueta sobre cantidad. Solo informa: no se escribe en ningún lado. */
    data class CantidadEtiqueta(
        val texto: String,
        /** Si la etiqueta numera el bulto («3-10»), el número de este bulto. */
        val bulto: String? = null,
        val bultoTotal: String? = null
    )

    private val ORDEN = listOf(
        Campo.LOTE, Campo.NUMERO_SERIE, Campo.PAIS_ORIGEN, Campo.MARCA, Campo.MODELO,
        Campo.UN_HAZMAT, Campo.CLASE_HAZMAT, Campo.GRUPO_EMBALAJE
    )

    fun comparar(estado: LabelCaptureViewModel.Estado): List<Fila> {
        val snapshot = estado.snapshot ?: return emptyList()
        return comparar(
            campos = estado.campos,
            snapshot = snapshot,
            partida = estado.selectedPartida,
            conflictos = estado.conflicts,
            resueltos = estado.resolvedConflicts,
            decisiones = estado.decisiones
        )
    }

    fun comparar(
        campos: List<CampoPropuesto>,
        snapshot: LabelCaptureSnapshot,
        partida: PartidaRevisionDto?,
        conflictos: List<LabelFieldExtractor.FieldConflict>,
        resueltos: Set<Campo>,
        decisiones: Map<Campo, Decision>
    ): List<Fila> {
        val porCampo = campos.associateBy { it.leido.campo }
        val sinResolver = conflictos.filter { it.field !in resueltos }.associateBy { it.field }
        val filas = mutableListOf(filaParte(porCampo[Campo.NUMERO_PARTE], partida))
        filaCantidad(porCampo[Campo.CANTIDAD], partida)?.let { filas += it }
        for (campo in ORDEN) {
            val declarado = declaradoDe(snapshot, campo)
            val conflicto = sinResolver[campo]
            val propuesto = porCampo[campo]
            when {
                conflicto != null -> filas += Fila(
                    campo = campo, tipo = Tipo.CONFLICTO, declarado = declarado, leido = "",
                    opciones = conflicto.observations.map { it.valor }.distinct()
                )
                propuesto != null -> filas += filaDe(campo, propuesto, declarado, decisiones[campo])
            }
        }
        return filas
    }

    /**
     * Arma lo que viaja al servidor, o nada si todavía no se puede guardar. Solo van los datos que el cotejo
     * manda guardar: lo que ya coincide, lo que el revisador decidió conservar y lo que no se leyó va
     * ausente, y ausente significa conservar. La petición ni siquiera tiene dónde poner la cantidad de la
     * etiqueta, así que no puede tocar la cantidad de la factura.
     */
    fun armarPeticion(
        estado: LabelCaptureViewModel.Estado,
        crefAgrupada: String,
        markReviewed: Boolean
    ): ConfirmLabelCaptureRequest? {
        if (!estado.canSave || markReviewed && !estado.canComplete) return null
        val partida = estado.selectedPartida ?: return null
        val captureId = estado.captureId ?: return null
        val snapshot = estado.snapshot ?: return null
        val filas = comparar(estado)
        fun escribir(campo: Campo): String? = filas.firstOrNull { it.campo == campo && it.seGuarda }?.leido
        val hazmat = LabelCaptureHazmat(escribir(Campo.UN_HAZMAT), escribir(Campo.CLASE_HAZMAT), escribir(Campo.GRUPO_EMBALAJE))
        return ConfirmLabelCaptureRequest(
            captureId = captureId, crefAgrupada = crefAgrupada, idRevdet = partida.IdRevdet, expectedSnapshot = snapshot,
            fields = LabelCaptureFields(
                numLote = escribir(Campo.LOTE), numSer = escribir(Campo.NUMERO_SERIE), marca = escribir(Campo.MARCA),
                modelo = escribir(Campo.MODELO), cPaisOrigen = escribir(Campo.PAIS_ORIGEN)
            ),
            hazmat = hazmat.takeIf { it.un != null || it.clase != null || it.grupo != null },
            markReviewed = markReviewed,
            // Tocar «Confirmar y marcar revisada» es la declaración de revisión física que exige el servidor.
            physicalReviewConfirmed = markReviewed,
            observations = estado.observations.take(64).map { lectura ->
                val fila = filas.firstOrNull { it.campo == lectura.campo }
                LabelCaptureObservation(
                    field = lectura.campo.name,
                    value = lectura.valor,
                    rawValue = lectura.rawValue,
                    source = lectura.origen.name,
                    accepted = fila != null && (fila.seGuarda || fila.tipo == Tipo.COINCIDE) &&
                        fila.leido.equals(lectura.valor, ignoreCase = true)
                )
            },
            rawText = estado.textoCrudo.take(16000),
            rawCodes = estado.rawCodes.take(64).map { it.take(1024) }
        )
    }

    fun identidad(campos: List<CampoPropuesto>, partida: PartidaRevisionDto?): Identidad =
        when (filaParte(campos.firstOrNull { it.leido.campo == Campo.NUMERO_PARTE }, partida).tipo) {
            Tipo.COINCIDE -> Identidad.CLAVE_EXACTA
            Tipo.DIFERENTE -> Identidad.CLAVE_DISTINTA
            else -> Identidad.SIN_CLAVE
        }

    fun pendientes(filas: List<Fila>): Int = filas.count { it.pendiente }

    /** Datos que se escribirán, en el orden en que se muestran. */
    fun aGuardar(filas: List<Fila>): List<Campo> = filas.filter { it.seGuarda }.map { it.campo }

    /** Datos que la partida ya tiene bien y que no se tocan. */
    fun sinCambio(filas: List<Fila>): List<Campo> = filas.filter { it.seConserva }.map { it.campo }

    fun cantidadEtiqueta(campos: List<CampoPropuesto>): CantidadEtiqueta? {
        val lectura = campos.firstOrNull { it.leido.campo == Campo.CANTIDAD }?.leido
            ?: campos.firstOrNull { it.leido.campo == Campo.PIEZAS_POR_EMPAQUE }?.leido
            ?: return null
        return if (lectura.quantityScope == LabelFieldExtractor.QuantityScope.PACKAGE_ORDINAL) {
            CantidadEtiqueta(lectura.rawValue, bulto = lectura.valor, bultoTotal = lectura.packageTotal)
        } else {
            CantidadEtiqueta(lectura.rawValue)
        }
    }

    private const val TOLERANCIA = 0.0005

    /**
     * Factor para pasar la unidad de la factura a piezas. En `unidades`, CNT es CIENTOS y MLL es MILLAR: una
     * partida de «2.100 CNT» son 210 piezas, que es lo que dice la bolsa. Las demás unidades (kilos, litros,
     * metros…) se comparan tal cual, sin factor.
     */
    private val FACTOR_A_PIEZAS = mapOf("PZA" to 1.0, "CNT" to 100.0, "MLL" to 1000.0)

    /** La unidad que la etiqueta imprime pegada al número («200 KG NET» → KG, «10 PCS PER BAG» → PZA). */
    private val UNIDADES_IMPRESAS = mapOf(
        "PC" to "PZA", "PCS" to "PZA", "PZ" to "PZA", "PZS" to "PZA", "PZA" to "PZA", "PIECES" to "PZA", "PIEZAS" to "PZA", "EA" to "PZA",
        "KG" to "KG", "KGS" to "KG", "KGM" to "KG", "LB" to "LB", "LBS" to "LB",
        "L" to "L", "LT" to "L", "LTR" to "L", "GAL" to "GAL", "M" to "M", "MTR" to "M", "T" to "T"
    )

    private fun unidadImpresa(crudo: String): String? {
        val token = Regex("""^\s*[\d.,]+\s*([A-Za-z]{1,6})\b""").find(crudo)?.groupValues?.get(1) ?: return null
        return UNIDADES_IMPRESAS[token.uppercase(Locale.ROOT)]
    }

    /**
     * La cantidad total de la etiqueta contra la de la factura. Un número de bulto («3 de 10») no es cantidad y
     * «10 PCS PER BAG» es el contenido de un empaque, no el total: ninguno de los dos genera esta fila.
     */
    private fun filaCantidad(propuesto: CampoPropuesto?, partida: PartidaRevisionDto?): Fila? {
        val lectura = propuesto?.leido ?: return null
        if (partida == null || lectura.quantityScope == LabelFieldExtractor.QuantityScope.PACKAGE_ORDINAL) return null
        val etiqueta = lectura.valor.trim().replace(',', '.').toDoubleOrNull() ?: return null
        val unidad = partida.UnidadFactura?.trim()?.uppercase(Locale.ROOT).orEmpty()
        if (unidad.isEmpty()) return null
        val impresa = unidadImpresa(lectura.rawValue)
        val factor = FACTOR_A_PIEZAS[unidad]
        val cantidad = if (factor != null) {
            Cantidad(etiqueta, partida.nCantFactura, unidad, "pzas", partida.nCantFactura * factor, impresa == null || impresa == "PZA")
        } else {
            Cantidad(etiqueta, partida.nCantFactura, unidad, unidad, partida.nCantFactura, impresa == null || impresa == unidad)
        }
        val tipo = when (cantidad.resultado) {
            ResultadoCantidad.IGUAL -> Tipo.COINCIDE
            ResultadoCantidad.PARCIAL -> Tipo.PARCIAL
            ResultadoCantidad.MAYOR -> Tipo.DIFERENTE
            ResultadoCantidad.NO_COMPARABLE -> Tipo.SIN_LECTURA
        }
        return Fila(
            Campo.CANTIDAD, tipo, declarado = numero(cantidad.declaradaEnBase), leido = numero(etiqueta),
            escribible = false, cantidad = cantidad
        )
    }

    /** 210.0 se escribe «210» y 2.1 se escribe «2.1»: sin ceros sobrantes. */
    fun numero(valor: Double): String =
        if (valor % 1.0 == 0.0 && kotlin.math.abs(valor) < 1e15) valor.toLong().toString()
        else String.format(Locale.US, "%.3f", valor).trimEnd('0').trimEnd('.')

    /** La clave sin ceros a la izquierda, o null si no los tiene o no es puramente numérica. */
    private fun sinCeros(clave: String): String? =
        if (clave.length > 1 && clave[0] == '0' && clave.all { it in '0'..'9' }) clave.trimStart('0').ifEmpty { "0" } else null

    private fun filaParte(propuesto: CampoPropuesto?, partida: PartidaRevisionDto?): Fila {
        val clave = partida?.CnoParte.orEmpty().trim()
        val alias = partida?.ParteCte.orEmpty().trim()
        val leido = propuesto?.leido?.valor?.trim().orEmpty()
        if (leido.isEmpty()) {
            return Fila(Campo.NUMERO_PARTE, Tipo.SIN_LECTURA, declarado = clave, leido = "", escribible = false)
        }
        // Los ceros a la izquierda de la etiqueta son relleno del proveedor; el catálogo no los guarda.
        val leidoSinCeros = sinCeros(claveExacta(leido))
        fun coincide(catalogo: String) = catalogo.isNotEmpty() &&
            (claveExacta(catalogo) == claveExacta(leido) || claveExacta(catalogo) == leidoSinCeros)
        val coincideConAlias = coincide(alias)
        val coincideConClave = coincide(clave)
        val soloRelleno = leidoSinCeros != null && !(clave.isNotEmpty() && claveExacta(clave) == claveExacta(leido))
        return when {
            coincideConClave -> Fila(
                Campo.NUMERO_PARTE, Tipo.COINCIDE, clave, leido,
                detalle = if (soloRelleno) "Sin los ceros de la etiqueta" else null, escribible = false
            )
            coincideConAlias -> Fila(Campo.NUMERO_PARTE, Tipo.COINCIDE, alias, leido, detalle = "Alias del cliente", escribible = false)
            else -> {
                val soloSeparadores = listOf(clave, alias).any { it.isNotEmpty() && sinSeparadores(it) == sinSeparadores(leido) }
                Fila(
                    Campo.NUMERO_PARTE, Tipo.DIFERENTE, clave, leido,
                    detalle = if (soloSeparadores) "Solo coincide al quitar separadores" else null,
                    escribible = false
                )
            }
        }
    }

    private fun filaDe(campo: Campo, propuesto: CampoPropuesto, declarado: String, decision: Decision?): Fila {
        val leido = propuesto.valorAEscribir.trim()
        if (leido.isEmpty()) return Fila(campo, Tipo.SIN_LECTURA, declarado, leido = "")
        val mostrado = if (campo == Campo.PAIS_ORIGEN) propuesto.detalle?.takeIf { propuesto.escribible } else null
        if (!propuesto.escribible) {
            return Fila(campo, Tipo.NO_VALIDO, declarado, leido = propuesto.leido.rawValue.ifBlank { leido }, detalle = propuesto.detalle)
        }
        if (declarado.isBlank()) return Fila(campo, Tipo.NUEVO, declarado, leido, detalle = mostrado)
        if (campo == Campo.LOTE || campo == Campo.NUMERO_SERIE) {
            // Una partida puede traer varios lotes o series: lo leído coincide si ya está en la lista declarada.
            val yaDeclarados = elementos(declarado).map(::clave).toSet()
            return when {
                elementos(leido).all { clave(it) in yaDeclarados } -> Fila(campo, Tipo.COINCIDE, declarado, leido)
                // El servidor suma los lotes a los declarados, pero reemplaza la serie: la serie sí pide decisión.
                campo == Campo.LOTE -> Fila(campo, Tipo.NUEVO, declarado, leido, suma = true)
                else -> Fila(campo, Tipo.DIFERENTE, declarado, leido, decision = decision)
            }
        }
        return if (clave(campo, declarado) == clave(campo, leido)) {
            Fila(campo, Tipo.COINCIDE, declarado, leido, detalle = mostrado)
        } else {
            Fila(campo, Tipo.DIFERENTE, declarado, leido, detalle = mostrado, decision = decision)
        }
    }

    private fun declaradoDe(snapshot: LabelCaptureSnapshot, campo: Campo): String = when (campo) {
        Campo.LOTE -> snapshot.numLote
        Campo.NUMERO_SERIE -> snapshot.numSer
        Campo.MARCA -> snapshot.marca
        Campo.MODELO -> snapshot.modelo
        Campo.PAIS_ORIGEN -> snapshot.cPaisOrigen
        Campo.UN_HAZMAT -> snapshot.hazmatUn
        Campo.CLASE_HAZMAT -> snapshot.hazmatClase
        Campo.GRUPO_EMBALAJE -> snapshot.hazmatGrupo
        else -> null
    }?.trim().orEmpty()

    /** El servidor separa los lotes con coma, punto y coma o salto de línea; las series se leen igual. */
    private fun elementos(valor: String): List<String> =
        valor.split(Regex("[,;\\r\\n]+")).map(String::trim).filter(String::isNotEmpty)

    private fun clave(campo: Campo, valor: String): String =
        if (campo == Campo.UN_HAZMAT) clave(valor.trim().uppercase(Locale.ROOT).removePrefix("UN")) else clave(valor)

    /** Mayúsculas, sin acentos, sin espacios ni signos: «Demo-Tec» y «DEMOTEC» son lo mismo. */
    internal fun clave(valor: String): String =
        Normalizer.normalize(valor, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .uppercase(Locale.ROOT)
            .filter(Char::isLetterOrDigit)

    private fun claveExacta(valor: String): String = valor.trim().uppercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    private fun sinSeparadores(valor: String): String = claveExacta(valor).replace(Regex("[\\s./_-]"), "")
}
