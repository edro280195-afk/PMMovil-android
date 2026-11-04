package com.example.pmmovil.ui.revision

import com.example.pmmovil.data.model.PartidaRevisionDto
import com.example.pmmovil.data.ocr.LabelFieldExtractor.Campo
import com.example.pmmovil.data.ocr.LabelFieldExtractor.CampoLeido
import com.example.pmmovil.data.ocr.LabelFieldExtractor.Origen
import com.example.pmmovil.data.ocr.LabelFieldExtractor.QuantityScope
import com.example.pmmovil.data.ocr.CorroboracionDeLectura
import com.example.pmmovil.data.ocr.PackageId
import com.example.pmmovil.data.ocr.PartidaMatcher
import com.example.pmmovil.data.scanner.LabelDataIdentifiers

/**
 * Cuenta empaques leídos de sus etiquetas y los suma por partida, en piezas o en kilos, sin contar dos veces el mismo.
 *
 * Es lógica pura, igual que [LabelCaptureCotejo]: no toca la pantalla, la red ni el disco. Quien la use escribe el
 * resultado donde corresponda (la cantidad recibida de la partida) y guarda el [Ledger].
 *
 * **Qué es «lo contado».** La suma de los empaques del [Ledger], no el valor que ya tuviera `ncantout`: las partidas
 * llegan con la cantidad recibida precargada con la de la factura (medido: el 100 % de las no revisadas en PZA), y
 * sumarle encima daría 2.1 + 210 en una partida de cientos. Por eso el primer empaque de una partida **reemplaza**
 * ese valor ([Result.Added.previousReceived] guarda el anterior para poder restituirlo) y los siguientes se suman.
 * `ncantout` va en piezas aunque la factura vaya en cientos o millares, y en kilos cuando la partida va en KG: ver
 * [CountUnits]. (Los nombres `pieces` y `expected` de abajo son «la cantidad en la unidad base de la partida».)
 *
 * **Kilos.** Una partida en KG se cuenta sumando la `Q` de cada etiqueta, pero solo si la etiqueta imprime **KG y nada
 * más** ([Label.printedUnits]): una etiqueta que también dice PC, o que dice LB, o que no dice la unidad, no se cuenta
 * sola ([Result.UnitNotConfirmed]). La `Q` de un código sale igual que la de piezas; lo que cambia es que aquí un kilo
 * mal entendido (libras leídas como kilos) no se descubre por el orden de magnitud.
 *
 * **Lo que sí cuenta.** Solo etiquetas cuya clave de parte salió de un código de barras y que trae su cantidad en
 * otro código ([read]): un dato leído por OCR nunca se suma solo. La clave tiene que ser exactamente la de una
 * partida o su alias del cliente (sin los ceros a la izquierda de la etiqueta); las coincidencias aproximadas, la
 * descripción y «es la única partida» no bastan, como en [PartidaMatcher].
 *
 * **Límite conocido.** El [Ledger] vive en el equipo. Si se pierde (otro equipo, datos borrados) y la partida ya
 * traía empaques contados, el siguiente empaque la reiniciaría. Evitarlo del todo pide guardar los empaques en el
 * servidor; mientras tanto, [Result.Added.previousReceived] y el aviso de contenido idéntico lo acotan.
 */
object PackageTally {
    enum class State {
        /** Ningún empaque contado todavía. */
        UNCOUNTED,

        /** Se llevan menos piezas que las que declara la factura. */
        PARTIAL,

        /** Las piezas contadas son las que declara la factura. */
        COMPLETE,

        /** Se llevan más piezas que las que declara la factura. */
        OVER,

        /** La unidad de la factura (litros, metros…) no se cuenta por etiqueta. */
        NOT_COMPARABLE
    }

    /** Lo que el conteo necesita saber de una etiqueta leída. */
    data class Label(
        /** El número de parte tal como se leyó, con los ceros a la izquierda si los trae. */
        val key: String,
        /** Las piezas que trae este empaque, o null si la etiqueta no las dice en un código. */
        val pieces: Double?,
        /** El identificador único del empaque, si lo imprime. */
        val packageId: String?,
        /** Un resumen del contenido, para avisar cuando dos empaques sin identificador dicen exactamente lo mismo. */
        val fingerprint: String,
        /**
         * El peso de este empaque **en kilos**, o null si la etiqueta no lo trae con su unidad.
         *
         * Se normaliza a kilos porque la etiqueta los da así y `revdet.ncanttarifa` también; el peso
         * declarado de la partida vive en libras (`npeso`) y se convierte para comparar.
         */
        val weightKg: Double? = null,
        /**
         * Las unidades que el texto impreso de la etiqueta nombra. Solo importa para partidas en kilos: la etiqueta
         * tiene que decir KG y ninguna otra, porque la `Q` del código no trae su unidad.
         */
        val printedUnits: Set<CountUnits.PrintedUnit> = emptySet()
    )

    sealed interface Reading {
        data class Ready(val label: Label) : Reading

        /** No hay un número de parte leído de un código de barras: esta etiqueta no se cuenta sola. */
        data object NoKey : Reading

        /** Hay más de una etiqueta en el cuadro; no se sabe de cuál es cada dato. */
        data class SeveralLabels(val ids: List<String>) : Reading

        /**
         * La cantidad salió de un código pero **no aparece impresa** en la etiqueta. El decodificador
         * se equivoca sin avisar (medido: `Q2000000` donde la hoja dice 20000), así que un dato que
         * nadie corrobora no se cuenta solo: lo confirma el revisador.
         */
        data class QuantityNotCorroborated(val fromBarcode: String) : Reading
    }

    /** Un empaque ya contado, ligado a la partida a la que se sumó. */
    data class CountedPackage(
        val partidaId: Int,
        val packageId: String?,
        val pieces: Double,
        val fingerprint: String,
        /** Lo que pesaba este empaque, en kilos. Cero cuando la etiqueta no lo traía. */
        val weightKg: Double = 0.0
    )

    /** Los empaques contados de una entrada, en el orden en que se leyeron. */
    data class Ledger(val packages: List<CountedPackage> = emptyList()) {
        val last: CountedPackage? get() = packages.lastOrNull()

        fun total(partidaId: Int): Double = packages.filter { it.partidaId == partidaId }.sumOf { it.pieces }

        /** Los kilos que suman los empaques contados de la partida. */
        fun totalWeightKg(partidaId: Int): Double =
            packages.filter { it.partidaId == partidaId }.sumOf { it.weightKg }

        /** Cuántos de esos empaques traían peso: sin todos, el acumulado todavía no se puede comparar. */
        fun weighedCount(partidaId: Int): Int =
            packages.count { it.partidaId == partidaId && it.weightKg > 0.0 }

        fun ofPartida(partidaId: Int): List<CountedPackage> = packages.filter { it.partidaId == partidaId }

        fun byId(packageId: String): CountedPackage? = packages.firstOrNull { it.packageId == packageId }

        fun plus(counted: CountedPackage): Ledger = copy(packages = packages + counted)

        /** Quita el último empaque igual a [counted] (el que se acaba de leer, si se deshace). */
        fun minus(counted: CountedPackage): Ledger {
            val index = packages.lastIndexOf(counted)
            return if (index < 0) this else copy(packages = packages.filterIndexed { i, _ -> i != index })
        }
    }

    sealed interface Result {
        data class Added(
            val partida: PartidaRevisionDto,
            val counted: CountedPackage,
            val totalBefore: Double,
            val totalAfter: Double,
            /** Las piezas que declara la factura para esta partida. */
            val expected: Double,
            val state: State,
            /** La cantidad recibida que tenía la partida antes de este conteo; sirve para restituirla si se deshace el primer empaque. */
            val previousReceived: Double?,
            /** Sin identificador, y el contenido es idéntico al del empaque anterior de la misma partida: se cuenta, pero se avisa. */
            val sameAsPrevious: Boolean,
            /** Los kilos que suman los empaques de esta partida con este incluido. Cero si ninguno traía peso. */
            val totalWeightKg: Double = 0.0,
            /**
             * Los kilos que declara la partida (`npeso`, que está en libras, convertido), o null si no
             * declara ninguno. **No se clasifica si el peso «cuadra»:** los pesos no cierran exactos
             * —30 empaques de 8 kg dan 240 contra los 242.4 declarados de la partida 3055981— y poner
             * una tolerancia sería inventarla. Se muestran los dos números y juzga el revisador.
             */
            val expectedWeightKg: Double? = null,
            /** En qué se contó: piezas o kilos. Decide las unidades de los avisos. */
            val dimension: CountUnits.Dimension = CountUnits.Dimension.PIECES
        ) : Result

        /** Este empaque ya estaba en el [Ledger]; [where] es la partida a la que se sumó entonces. */
        data class AlreadyCounted(val counted: CountedPackage, val where: PartidaRevisionDto?) : Result

        /** Se reconoció la partida pero la etiqueta no trae la cantidad en un código: hay que capturarla o tomar la foto. */
        data class MissingQuantity(val partida: PartidaRevisionDto) : Result

        /** La partida ya está revisada; contar encima cambiaría un dato cerrado. */
        data class AlreadyReviewed(val partida: PartidaRevisionDto) : Result

        /** La misma clave está en más de una partida de la entrada: lo decide el revisador. */
        data class SeveralPartidas(val candidates: List<PartidaRevisionDto>) : Result

        /** Ninguna partida de esta entrada tiene esa clave (la etiqueta puede ser de otra entrada). */
        data class NotInEntry(val key: String) : Result

        /** La factura de esta partida va en litros, metros… y esta cuenta no sabe de esa unidad. */
        data class UnsupportedUnit(val partida: PartidaRevisionDto, val unit: String) : Result

        /**
         * La partida va en kilos pero la etiqueta no dice **solo** KG: no dice ninguna unidad, también dice piezas, o dice
         * libras. La `Q` del código no trae su unidad y contarla sería suponerla; lo decide el revisador.
         */
        data class UnitNotConfirmed(
            val partida: PartidaRevisionDto,
            val printed: Set<CountUnits.PrintedUnit>
        ) : Result

        /**
         * Un solo empaque trae más piezas que toda la partida: casi seguro el decodificador se
         * equivocó, no que llegara mercancía de más.
         *
         * Medido en el Pixel el 20-oct-2026: leyendo la etiqueta de envío VDA, la **foto** decodificó
         * `Q20000000` donde el valor impreso es `Q20000` — mil veces más, en una partida que declara
         * 600 000 piezas. No es un tropiezo del video: salió del mismo código de la foto, que se creía
         * confiable. Contarlo habría escrito veinte millones en la cantidad recibida.
         *
         * Un sobrante real lo captura el revisador a mano; una lectura así no se cuenta sola.
         */
        data class QuantityOutOfRange(
            val partida: PartidaRevisionDto,
            val perPackage: Double,
            val expected: Double,
            val dimension: CountUnits.Dimension = CountUnits.Dimension.PIECES
        ) : Result
    }

    /** Cuántas partidas hay en cada situación. [reviewed] ya no entra en las demás. */
    data class Progress(
        val total: Int,
        val reviewed: Int,
        val uncounted: Int,
        val partial: Int,
        val complete: Int,
        val over: Int,
        val notComparable: Int
    )

    private val BARCODE_ORIGINS = setOf(Origen.CODIGO_PLANO, Origen.STRUCTURED_BARCODE, Origen.GS1)

    /**
     * Reduce lo que salió de una etiqueta a lo que el conteo necesita. [fields] son los datos ya extraídos (sin
     * contradicciones) y [codes] todos los códigos decodificados, para buscar el identificador del empaque.
     */
    /**
     * @param textoImpreso lo que el lector de texto sacó de la etiqueta. Sirve para **corroborar** que
     *   la cantidad del código esté también escrita en la hoja. Si llega vacío no se corrobora nada y
     *   la cantidad no se da por buena: con cero margen de error, no saber es igual que no coincidir.
     */
    fun read(fields: List<CampoLeido>, codes: List<String>, textoImpreso: String = ""): Reading {
        // Dos PDF417 distintos en la misma foto son dos etiquetas: la cantidad de una y el lote de la otra se
        // mezclarían en una sola lectura (medido en el Pixel el 27-oct: 78.7 kg con el lote de la caja de 77.6).
        val productCodes = codes.map(String::trim).filter(::isProductCode).distinct()
        if (productCodes.size > 1) return Reading.SeveralLabels(productCodes)
        val packageId = when (val reading = PackageId.read(codes)) {
            is PackageId.Reading.Multiple -> return Reading.SeveralLabels(reading.values)
            is PackageId.Reading.Single -> reading.value
            PackageId.Reading.None -> null
        }
        val fromBarcode = fields.filter { it.origen in BARCODE_ORIGINS }
        val key = fromBarcode.firstOrNull { it.campo == Campo.NUMERO_PARTE }?.valor?.trim()
            ?.takeIf(String::isNotEmpty) ?: return Reading.NoKey
        val cantidadLeida = (fromBarcode.firstOrNull { it.campo == Campo.CANTIDAD && it.quantityScope != QuantityScope.PACKAGE_ORDINAL }
            ?: fromBarcode.firstOrNull { it.campo == Campo.PIEZAS_POR_EMPAQUE && it.quantityScope != QuantityScope.PACKAGE_ORDINAL })
            ?.valor?.trim()
        // La cantidad es el único dato que se escribe sin que nadie lo mire, así que es el que más
        // exige: tiene que estar además impresa en la etiqueta.
        if (cantidadLeida != null && !CorroboracionDeLectura.cantidadApareceImpresa(cantidadLeida, textoImpreso)) {
            return Reading.QuantityNotCorroborated(cantidadLeida)
        }
        val pieces = cantidadLeida?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
        val batch = fromBarcode.firstOrNull { it.campo == Campo.LOTE }?.valor?.trim().orEmpty()
        val expiry = fromBarcode.firstOrNull { it.campo == Campo.CADUCIDAD }?.valor?.trim().orEmpty()
        val fingerprint = listOf(withoutLeadingZeros(key), pieces?.let(LabelCaptureCotejo::numero).orEmpty(), batch, expiry)
            .joinToString("|")
        return Reading.Ready(
            Label(key, pieces, packageId, fingerprint, weightKg(fields), CountUnits.printedUnits(textoImpreso))
        )
    }

    /**
     * El peso de un empaque, en kilos. A diferencia de la cantidad, el peso **sí** se toma del texto
     * impreso: ninguna de las etiquetas conocidas lo mete en un código de barras, y el rótulo
     * («NET WT KG») dice sin ambigüedad qué es y en qué unidad.
     *
     * **Se prefiere el neto cuando la etiqueta trae los dos**, por lo medido en la partida 3055981: sus
     * 30 empaques suman 240 kg con el neto (8 kg cada uno) contra los 242.4 kg declarados — 1 % de
     * diferencia —, mientras que con el bruto (9 kg) darían 270, un 11 % arriba. Es **una sola muestra**;
     * con más etiquetas reales se confirma o se cambia.
     */
    private fun weightKg(fields: List<CampoLeido>): Double? {
        val leido = fields.firstOrNull { it.campo == Campo.PESO_NETO }
            ?: fields.firstOrNull { it.campo == Campo.PESO_BRUTO }
            ?: return null
        val unidad = leido.unidad?.uppercase() ?: return null
        val valor = leido.valor.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        return when (unidad) {
            "KG" -> valor
            "LB" -> RevisionWeightRules.kilosFromLibras(valor)
            else -> null
        }
    }

    /** Lo que la partida declara que pesa, en kilos. `npeso` vive en libras. */
    fun expectedWeightKg(partida: PartidaRevisionDto): Double? =
        partida.nPeso?.takeIf { it > 0.0 }?.let(RevisionWeightRules::kilosFromLibras)

    /** Lo que declara la factura de la partida, en la unidad base de su cuenta (piezas o kilos); null si no se cuenta. */
    fun expectedQuantity(partida: PartidaRevisionDto): Double? =
        CountUnits.countable(partida.UnidadFactura)?.let { partida.nCantFactura * it.factor }

    fun stateOf(counted: Double, expected: Double?): State = when {
        expected == null -> State.NOT_COMPARABLE
        counted <= 0.0 -> State.UNCOUNTED
        CountUnits.areEqual(counted, expected) -> State.COMPLETE
        counted < expected -> State.PARTIAL
        else -> State.OVER
    }

    fun stateOf(partida: PartidaRevisionDto, ledger: Ledger): State =
        stateOf(ledger.total(partida.IdRevdet), expectedQuantity(partida))

    /**
     * Si contar este empaque deja la cuenta **abierta**: se cuenta, pero la partida todavía no llega a lo que
     * declara la factura (o se pasó). Con la cuenta abierta la tarjeta no debe marcar la partida como revisada:
     * medido en el Pixel el 27-oct-2026, guardar la primera de cinco cajas la dejaba «revisada» con el 20 % contado y
     * las otras cuatro ya no se contaban («la partida ya está revisada»).
     *
     * Solo una cuenta que **cierra exacta** deja marcar la revisión. Lo que no se puede contar ([Result.Added] es
     * lo único que cuenta) sigue como siempre: el revisador decide con la tarjeta.
     */
    fun leavesCountOpen(result: Result?): Boolean = result is Result.Added && result.state != State.COMPLETE

    /**
     * Suma el empaque de [label] a su partida. No cambia nada por sí sola: devuelve qué pasó y, si [Result.Added],
     * el empaque que hay que agregar al [Ledger] y el total que hay que escribir como cantidad recibida.
     *
     * @param multiple cuántos empaques idénticos representa la lectura (una caja leída y «son 50» en el teclado).
     */
    fun process(partidas: List<PartidaRevisionDto>, ledger: Ledger, label: Label, multiple: Int = 1): Result {
        require(multiple >= 1) { "multiple debe ser al menos 1" }
        val partida = identify(partidas, label.key).let { found ->
            when {
                found.isEmpty() -> return Result.NotInEntry(label.key)
                found.size > 1 -> return Result.SeveralPartidas(found)
                else -> found.single()
            }
        }
        label.packageId?.let { id ->
            ledger.byId(id)?.let { counted ->
                return Result.AlreadyCounted(counted, partidas.firstOrNull { it.IdRevdet == counted.partidaId })
            }
        }
        if (partida.Revisada) return Result.AlreadyReviewed(partida)
        val countable = CountUnits.countable(partida.UnidadFactura)
            ?: return Result.UnsupportedUnit(partida, partida.UnidadFactura.orEmpty())
        val expected = partida.nCantFactura * countable.factor
        val perPackage = label.pieces ?: return Result.MissingQuantity(partida)
        // La `Q` de un código no trae su unidad: en kilos la etiqueta tiene que decir KG y nada más.
        if (countable.dimension == CountUnits.Dimension.KILOS && label.printedUnits != setOf(CountUnits.PrintedUnit.KILOS)) {
            return Result.UnitNotConfirmed(partida, label.printedUnits)
        }
        // Un empaque que por sí solo trae más que toda la partida es una lectura mala, no un sobrante.
        if (expected > 0.0 && perPackage * multiple > expected) {
            return Result.QuantityOutOfRange(partida, perPackage * multiple, expected, countable.dimension)
        }

        val counted = CountedPackage(
            partida.IdRevdet, label.packageId, perPackage * multiple, label.fingerprint,
            weightKg = (label.weightKg ?: 0.0) * multiple
        )
        val before = ledger.total(partida.IdRevdet)
        val after = before + counted.pieces
        val sameAsPrevious = label.packageId == null && ledger.last?.let {
            it.partidaId == partida.IdRevdet && it.packageId == null && it.fingerprint == label.fingerprint
        } == true
        return Result.Added(
            partida = partida,
            counted = counted,
            totalBefore = before,
            totalAfter = after,
            expected = expected,
            state = stateOf(after, expected),
            previousReceived = partida.nCantOut,
            sameAsPrevious = sameAsPrevious,
            totalWeightKg = ledger.totalWeightKg(partida.IdRevdet) + counted.weightKg,
            expectedWeightKg = expectedWeightKg(partida),
            dimension = countable.dimension
        )
    }

    /**
     * Qué cantidad recibida dejar en la partida al quitar [counted] de [ledgerWithout] (el libro ya sin ese
     * empaque): lo que suman los que quedan, o lo que la partida traía si era el único.
     */
    fun receivedAfterUndo(ledgerWithout: Ledger, counted: CountedPackage, previousReceived: Double?): Double =
        if (ledgerWithout.ofPartida(counted.partidaId).isEmpty()) previousReceived ?: 0.0
        else ledgerWithout.total(counted.partidaId)

    fun progress(partidas: List<PartidaRevisionDto>, ledger: Ledger): Progress {
        var reviewed = 0
        val byState = mutableMapOf<State, Int>()
        partidas.forEach { partida ->
            if (partida.Revisada) reviewed++ else byState.merge(stateOf(partida, ledger), 1, Int::plus)
        }
        return Progress(
            total = partidas.size,
            reviewed = reviewed,
            uncounted = byState[State.UNCOUNTED] ?: 0,
            partial = byState[State.PARTIAL] ?: 0,
            complete = byState[State.COMPLETE] ?: 0,
            over = byState[State.OVER] ?: 0,
            notComparable = byState[State.NOT_COMPARABLE] ?: 0
        )
    }

    /** Las partidas cuya clave o alias del cliente es exactamente [key] (sin los ceros a la izquierda de la etiqueta). */
    private fun identify(partidas: List<PartidaRevisionDto>, key: String): List<PartidaRevisionDto> {
        val exact = setOf(PartidaMatcher.MatchKind.EXACT_PART_NUMBER, PartidaMatcher.MatchKind.EXACT_CUSTOMER_ALIAS)
        return PartidaMatcher
            .match(partidas, listOf(CampoLeido(Campo.NUMERO_PARTE, key, Origen.CODIGO_PLANO, "", 99)))
            .candidates
            .filter { it.matchKind in exact }
            .map { it.partida }
    }

    /** Un código que, él solo, ya trae la clave de un producto: el PDF417 de una etiqueta («+30S0049118845+Q…»). */
    private fun isProductCode(code: String): Boolean =
        code.startsWith("+") && LabelDataIdentifiers.leer(listOf(code)).clave != null

    private fun withoutLeadingZeros(key: String): String =
        if (key.length > 1 && key.all { it in '0'..'9' }) key.trimStart('0').ifEmpty { "0" } else key
}

/**
 * Convierte el [PackageTally.Ledger] en texto para guardarlo en el equipo y de vuelta. Un renglón por empaque,
 * separado por tabuladores, sin JSON ni reflexión: R8 no puede renombrarle nada y un renglón dañado se ignora en
 * vez de tirar el libro completo.
 */
object PackageLedgerCodec {
    fun encode(ledger: PackageTally.Ledger): String = ledger.packages.joinToString("\n") { p ->
        listOf(
            p.partidaId.toString(), p.pieces.toString(), clean(p.packageId.orEmpty()), clean(p.fingerprint),
            p.weightKg.toString()
        ).joinToString("\t")
    }

    /**
     * El peso se agregó como **quinta** columna para que un libro guardado antes —con cuatro— se siga
     * leyendo entero, solo que sin pesos. `clean` ya quita los tabuladores de los valores, así que
     * partir sin límite es seguro.
     */
    fun decode(text: String?): PackageTally.Ledger {
        if (text.isNullOrBlank()) return PackageTally.Ledger()
        val packages = text.lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 4) return@mapNotNull null
            val partidaId = parts[0].toIntOrNull() ?: return@mapNotNull null
            val pieces = parts[1].toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: return@mapNotNull null
            val weightKg = parts.getOrNull(4)?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
            PackageTally.CountedPackage(partidaId, parts[2].ifEmpty { null }, pieces, parts[3], weightKg)
        }.toList()
        return PackageTally.Ledger(packages)
    }

    private fun clean(value: String): String = value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
}
