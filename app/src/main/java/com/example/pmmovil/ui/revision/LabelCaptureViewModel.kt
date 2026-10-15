package com.example.pmmovil.ui.revision

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.data.model.PaisDto
import com.example.pmmovil.data.ocr.CountryCatalogMatcher
import com.example.pmmovil.data.ocr.LabelFieldExtractor
import com.example.pmmovil.data.ocr.LocalTextOcr
import com.example.pmmovil.data.ocr.LocalTextOcrResult
import com.example.pmmovil.data.ocr.PaddleOcrEngine
import com.example.pmmovil.data.ocr.PhotoBarcodeScanner
import com.example.pmmovil.data.repository.RevisionRepository
import com.example.pmmovil.ui.scanner.BarcodeFormatSets
import com.example.pmmovil.data.ocr.mergeLocalResults
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Lee una foto de etiqueta y propone los campos de la partida.
 *
 * Vive aparte de [DetalleRevisionViewModel] a proposito: aquel ya carga facturas,
 * partidas, paises, fotos, hazmat y permisos. Esto es una sola cosa -- foto entra,
 * campos salen -- y asi se puede reusar tal cual en Entrada Bodega o en el censo
 * de rack sin arrastrar la revision completa.
 *
 * Ver docs/ia-revision-fisica.md (secciones 6 y 11).
 */
@HiltViewModel
class LabelCaptureViewModel @Inject constructor(
    private val paddleOcrEngine: PaddleOcrEngine,
    private val localTextOcr: LocalTextOcr,
    private val photoBarcodeScanner: PhotoBarcodeScanner,
    private val repository: RevisionRepository
) : ViewModel() {

    /** A donde va cada campo cuando el revisador lo acepta. */
    enum class Destino {
        /** Columna de la partida, via DetalleRevisionViewModel.actualizarDatoPartida. */
        PARTIDA,

        /** traf3, que es por referencia y no por partida: UN, clase y grupo. */
        HAZMAT,

        /** Solo se muestra. */
        NINGUNO
    }

    /**
     * Un campo que la etiqueta entrego. [escribible] dice si el revisador puede
     * mandarlo a la partida con un toque, o si solo es para que lo vea.
     */
    data class CampoPropuesto(
        val leido: LabelFieldExtractor.CampoLeido,
        val escribible: Boolean,
        val aceptado: Boolean,
        val destino: Destino = Destino.NINGUNO,
        /**
         * Lo que de verdad se guarda. Casi siempre es [leido].valor, pero el pais
         * se escribe como codigo de 3 letras aunque la etiqueta diga "THAILAND".
         */
        val valorAEscribir: String = leido.valor,
        /** Aclaracion para la pantalla, p. ej. el nombre del pais resuelto. */
        val detalle: String? = null
    )

    data class Estado(
        val analizando: Boolean = false,
        /**
         * Ya hay campos en pantalla (salieron del codigo, que es exacto) pero el
         * OCR sigue corriendo por si la etiqueta trae mas datos en texto. Sirve
         * para no hacer esperar al revisador por lo que ya se sabe.
         */
        val refinando: Boolean = false,
        val fotoPath: String? = null,
        val campos: List<CampoPropuesto> = emptyList(),
        val textoCrudo: String = "",
        val error: String? = null
    ) {
        val huboLectura: Boolean get() = campos.isNotEmpty()
        val escribibles: List<CampoPropuesto> get() = campos.filter { it.escribible }
        val aceptados: List<CampoPropuesto> get() = campos.filter { it.escribible && it.aceptado }
    }

    private val _estado = MutableStateFlow(Estado())
    val estado: StateFlow<Estado> = _estado.asStateFlow()

    /**
     * @param codigosEnVivo codigos que el lector ya decodifico apuntando a la
     *        misma etiqueta. Un codigo le gana al texto porque es exacto.
     */
    fun analizar(
        path: String,
        codigosEnVivo: List<String> = emptyList(),
        catalogoPaises: List<PaisDto> = emptyList()
    ) {
        _estado.value = Estado(analizando = true, fotoPath = path)
        viewModelScope.launch {
            val arranque = SystemClock.elapsedRealtime()

            // ── 1. Codigo y texto, A LA VEZ ─────────────────────────────────
            // Son motores distintos y no compiten por lo mismo; el analizador
            // en vivo ya los corre en paralelo sobre cada cuadro. En serie se
            // sumaban: 495 ms de codigo + 604 ms de texto = 1,102 ms medidos,
            // cuando el codigo casi siempre vuelve con las manos vacias. En
            // paralelo el total es el del mas lento, no la suma.
            //
            // El codigo se busca en la FOTO y no solo en lo que vio el video: el
            // cuadro en vivo va a 640x480 y ahi un DataMatrix de etiqueta no
            // tiene pixeles para decodificarse; la foto son 4080x3072.
            val tEtapa1 = SystemClock.elapsedRealtime()
            val (deLaFoto, mlKit) = coroutineScope {
                val tareaCodigos = async {
                    photoBarcodeScanner
                        .leer(path, BarcodeFormatSets.WAREHOUSE_PRODUCT_LABELS)
                        .getOrDefault(emptyList())
                }
                val tareaTexto = async { localTextOcr.readFromFile(path).getOrNull() }
                tareaCodigos.await() to tareaTexto.await()
            }
            val codigosTotales = (codigosEnVivo + deLaFoto).distinct()
            Log.d(
                "LABEL_CAPTURE",
                "etapa1 ${SystemClock.elapsedRealtime() - tEtapa1}ms | " +
                    "codigos vivo=${codigosEnVivo.size} foto=${deLaFoto.size} " +
                    // El contenido importa tanto como la cantidad: si el codigo
                    // no viene en formato GS1 (identificadores (10), (21), (30))
                    // el extractor no puede mapearlo a un campo.
                    "valores=${codigosTotales.joinToString(" | ") { it.take(60) }} | " +
                    "texto=${mlKit?.rawText?.length ?: 0} chars"
            )

            // ── 2. ¿Con esto alcanza? ───────────────────────────────────────
            val porCodigo = LabelFieldExtractor.extraer("", codigosTotales)
            val camposDeTexto = mlKit?.let { LabelFieldExtractor.extraer(it.rawText) }.orEmpty()

            val resultado = if (porCodigo.isNotEmpty() || camposDeTexto.isNotEmpty()) {
                mlKit
            } else {
                // Ni el codigo ni el texto rapido sacaron nada: ahora si vale la
                // pena pagar Paddle.
                conPaddleDeRespaldo(path, mlKit)
            }
            if (resultado == null) {
                // Si el codigo ya habia dado campos, se conservan: que el OCR no
                // encuentre texto no invalida lo que el codigo dijo.
                if (porCodigo.isEmpty()) {
                    _estado.value = Estado(
                        fotoPath = path,
                        error = "No se alcanzo a leer la etiqueta. Acercate o cambia el angulo para quitar el reflejo."
                    )
                } else {
                    _estado.value = _estado.value.copy(refinando = false)
                }
                return@launch
            }

            val codigos = (codigosTotales + resultado.barcodes).distinct()
            val campos = LabelFieldExtractor.extraer(resultado.rawText, codigos)
            Log.d(
                "LABEL_CAPTURE",
                "campos=${campos.size} codigos=${codigos.size} " +
                    "texto=${resultado.rawText.length} chars " +
                    "total=${SystemClock.elapsedRealtime() - arranque}ms"
            )

            _estado.value = Estado(
                fotoPath = path,
                textoCrudo = resultado.rawText,
                campos = campos.map { leido -> aPropuesto(leido, catalogoPaises) },
                error = if (campos.isEmpty()) {
                    "Se leyo la etiqueta pero no trae ninguna de las anclas conocidas " +
                        "(LOT, PART, Qty...). Captura el dato a mano."
                } else {
                    null
                }
            )
        }
    }

    /**
     * Decide si un campo leido se puede escribir y con que valor.
     *
     * El pais es el unico que necesita traduccion: `cpaisorigen` es varchar(3) y
     * el catalogo esta en espanol, asi que "THAILAND" tiene que volverse "THA"
     * antes de guardarse. Si no se puede resolver con certeza, el campo baja a
     * solo referencia en vez de escribir algo a medias.
     */
    private fun aPropuesto(
        leido: LabelFieldExtractor.CampoLeido,
        catalogoPaises: List<PaisDto>
    ): CampoPropuesto {
        if (leido.campo == LabelFieldExtractor.Campo.PAIS_ORIGEN) {
            val catalogo = catalogoPaises.mapNotNull { pais ->
                val codigo = pais.codigo?.trim().orEmpty()
                val nombre = (pais.descrip ?: pais.cnombre).orEmpty()
                if (codigo.isEmpty()) null else CountryCatalogMatcher.PaisCatalogo(codigo, nombre)
            }
            val resuelto = CountryCatalogMatcher.emparejar(leido.valor, catalogo)
            return CampoPropuesto(
                leido = leido,
                escribible = resuelto != null,
                aceptado = resuelto != null,
                destino = if (resuelto != null) Destino.PARTIDA else Destino.NINGUNO,
                valorAEscribir = resuelto?.codigo ?: leido.valor,
                detalle = resuelto?.let { "${it.codigo} · ${it.nombre}" }
                    ?: "No esta en el catalogo: captúralo a mano"
            )
        }
        if (leido.campo in CAMPOS_HAZMAT) {
            return CampoPropuesto(
                leido = leido,
                escribible = true,
                aceptado = true,
                destino = Destino.HAZMAT,
                detalle = "Se guarda en traf3, para toda la referencia"
            )
        }
        val aPartida = CAMPOS_ESCRIBIBLES[leido.campo]
        return CampoPropuesto(
            leido = leido,
            escribible = aPartida != null,
            // Se aceptan por omision; el revisador desmarca lo que no cuadre.
            aceptado = aPartida != null,
            destino = if (aPartida != null) Destino.PARTIDA else Destino.NINGUNO
        )
    }

    fun alternar(campo: LabelFieldExtractor.Campo) {
        _estado.value = _estado.value.copy(
            campos = _estado.value.campos.map {
                if (it.leido.campo == campo && it.escribible) it.copy(aceptado = !it.aceptado) else it
            }
        )
    }

    /**
     * Manda los datos del rombo a traf3. Van juntos en una sola llamada porque
     * son un solo hecho: este embarque es material peligroso tal y tal.
     */
    fun guardarHazmat(
        crefAgrupada: String,
        campos: List<CampoPropuesto>,
        onResultado: (Boolean) -> Unit = {}
    ) {
        val delRombo = campos.filter { it.destino == Destino.HAZMAT }
        if (crefAgrupada.isBlank() || delRombo.isEmpty()) {
            onResultado(false)
            return
        }
        fun valor(campo: LabelFieldExtractor.Campo) =
            delRombo.firstOrNull { it.leido.campo == campo }?.valorAEscribir

        viewModelScope.launch {
            val r = repository.guardarHazmatRevision(
                crefAgrupada = crefAgrupada,
                un = valor(LabelFieldExtractor.Campo.UN_HAZMAT),
                // La clase se lee como "3 · LIQUIDO"; a la columna va solo el codigo.
                clase = valor(LabelFieldExtractor.Campo.CLASE_HAZMAT)?.substringBefore(' ')?.trim(),
                grupo = valor(LabelFieldExtractor.Campo.GRUPO_EMBALAJE)
            )
            onResultado(r.isSuccess)
        }
    }

    fun limpiar() {
        _estado.value = Estado()
    }

    /**
     * Reconocimiento de texto en cascada: **el motor barato primero**.
     *
     * Medido en el Pixel con la etiqueta de allnex SETAL impresa en papel:
     *
     * ```
     * OCR_PADDLE: lineas=45 det=511ms rec=4677ms total=5220ms
     * LABEL_CAPTURE: campos=2 total=6034ms
     * ```
     *
     * El costo no esta en la deteccion sino en el **reconocimiento renglon por
     * renglon**: 45 renglones, y casi todos son el parrafo de advertencias de
     * riesgo, que no aporta ningun campo. En pantalla eran 23 renglones y 1,113
     * ms; impresa y bien enfocada la etiqueta entrega el doble de renglones y
     * el tiempo se cuadruplica. Mejor foto, mas lento: al reves de lo deseable.
     *
     * ML Kit sobre la misma foto a resolucion completa tarda ~600 ms y en las
     * pruebas entrego 111 candidatos. Para una etiqueta **impresa, enfocada y
     * bien iluminada** alcanza de sobra, asi que va primero y Paddle queda de
     * red de seguridad.
     *
     * **Esto invierte la regla general del proyecto** ("Paddle es el motor
     * primario para fotos estaticas"), y a proposito: esa regla se fijo para
     * placas y numeros de caja -- texto industrial golpeado, con estarcido y
     * poco contraste -- donde Paddle gana claro. Una etiqueta de producto
     * recien impresa es el caso facil. Si en bodega aparecen etiquetas sucias o
     * desgastadas donde ML Kit falle, la vuelta atras es cambiar el orden de
     * estas dos llamadas.
     *
     * Secuencial a proposito: correr los dos motores a la vez duplica el pico
     * de CPU y deja al HAL de la camara sin buffers.
     */
    private suspend fun conPaddleDeRespaldo(
        path: String,
        mlKit: LocalTextOcrResult?
    ): LocalTextOcrResult? {
        val tPaddle = SystemClock.elapsedRealtime()
        val paddle = paddleOcrEngine.readFromFile(path).getOrNull()
        Log.d(
            "LABEL_CAPTURE",
            "paddle (red de seguridad): texto=${paddle?.rawText?.length ?: 0} chars " +
                "en ${SystemClock.elapsedRealtime() - tPaddle}ms"
        )

        val fusionado = mergeLocalResults(
            paddle ?: LocalTextOcrResult("", emptyList()),
            mlKit ?: LocalTextOcrResult("", emptyList())
        )
        return fusionado.takeIf { it.rawText.isNotBlank() || it.candidates.isNotEmpty() }
    }

    companion object {
        /**
         * Campos que SI se pueden mandar a la partida, con el nombre que espera
         * `DetalleRevisionViewModel.actualizarDatoPartida`.
         *
         * Los que no estan aqui se muestran pero no se escriben, y cada ausencia
         * tiene razon:
         *
         *  - **Numero de parte**: `actualizarDatoPartida` no lo maneja; viene del
         *    pedimento y no se edita en revision.
         *  - **Cantidad y piezas por empaque**: la etiqueta dice piezas POR EMPAQUE
         *    ("10 PCS", "Qty: 1 of 2"). El campo de la partida es el TOTAL de
         *    piezas. Meter uno en el otro es justo el error silencioso que
         *    queremos quitar, asi que lo decide el revisador.
         *  - **UN, clase y grupo de embalaje**: no van a la partida sino a `traf3`,
         *    que es por referencia. Se escriben con [guardarHazmat] y por eso no
         *    estan en este mapa, pero si son escribibles (ver [CAMPOS_HAZMAT]).
         */
        val CAMPOS_ESCRIBIBLES: Map<LabelFieldExtractor.Campo, String> = mapOf(
            LabelFieldExtractor.Campo.LOTE to "numLote",
            LabelFieldExtractor.Campo.NUMERO_SERIE to "numSer",
            // Se escribe el CODIGO que resolvio CountryCatalogMatcher, no el
            // texto de la etiqueta: la columna es varchar(3).
            LabelFieldExtractor.Campo.PAIS_ORIGEN to "cPaisOrigen"
        )

        /** Los tres datos del rombo. No van a la partida: van a traf3. */
        val CAMPOS_HAZMAT = setOf(
            LabelFieldExtractor.Campo.UN_HAZMAT,
            LabelFieldExtractor.Campo.CLASE_HAZMAT,
            LabelFieldExtractor.Campo.GRUPO_EMBALAJE
        )
    }
}
