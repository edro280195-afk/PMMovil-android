package com.example.pmmovil.ui.revision

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.data.model.PaisDto
import com.example.pmmovil.data.ocr.CountryCatalogMatcher
import com.example.pmmovil.data.ocr.LabelFieldExtractor
import com.example.pmmovil.data.ocr.LocalTextOcr
import com.example.pmmovil.data.ocr.LocalTextOcrResult
import com.example.pmmovil.data.ocr.PaddleOcrEngine
import com.example.pmmovil.data.ocr.mergeLocalResults
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val localTextOcr: LocalTextOcr
) : ViewModel() {

    /**
     * Un campo que la etiqueta entrego. [escribible] dice si el revisador puede
     * mandarlo a la partida con un toque, o si solo es para que lo vea.
     */
    data class CampoPropuesto(
        val leido: LabelFieldExtractor.CampoLeido,
        val escribible: Boolean,
        val aceptado: Boolean,
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
            val lectura = leerConPaddleYFallback(path)
            val resultado = lectura.getOrNull()
            if (resultado == null) {
                _estado.value = Estado(
                    fotoPath = path,
                    error = "No se alcanzo a leer la etiqueta. Acercate o cambia el angulo para quitar el reflejo."
                )
                return@launch
            }

            val codigos = (codigosEnVivo + resultado.barcodes).distinct()
            val campos = LabelFieldExtractor.extraer(resultado.rawText, codigos)
            Log.d(
                "LABEL_CAPTURE",
                "campos=${campos.size} codigos=${codigos.size} texto=${resultado.rawText.length} chars"
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
                valorAEscribir = resuelto?.codigo ?: leido.valor,
                detalle = resuelto?.let { "${it.codigo} · ${it.nombre}" }
                    ?: "No esta en el catalogo: captúralo a mano"
            )
        }
        val destino = CAMPOS_ESCRIBIBLES[leido.campo]
        return CampoPropuesto(
            leido = leido,
            escribible = destino != null,
            // Se aceptan por omision; el revisador desmarca lo que no cuadre.
            aceptado = destino != null
        )
    }

    fun alternar(campo: LabelFieldExtractor.Campo) {
        _estado.value = _estado.value.copy(
            campos = _estado.value.campos.map {
                if (it.leido.campo == campo && it.escribible) it.copy(aceptado = !it.aceptado) else it
            }
        )
    }

    fun limpiar() {
        _estado.value = Estado()
    }

    /**
     * Mismo criterio que Entrada Caseta: Paddle primero, ML Kit de red, y se
     * fusionan. **Secuencial a proposito** -- correr los dos motores a la vez
     * duplicaba el pico de CPU y dejaba al HAL de la camara sin buffers.
     */
    private suspend fun leerConPaddleYFallback(path: String): Result<LocalTextOcrResult> {
        val paddle = paddleOcrEngine.readFromFile(path).getOrNull()
        val mlKit = localTextOcr.readFromFile(path).getOrNull()
        val fusionado = mergeLocalResults(
            paddle ?: LocalTextOcrResult("", emptyList()),
            mlKit ?: LocalTextOcrResult("", emptyList())
        )
        return if (fusionado.rawText.isNotBlank() || fusionado.candidates.isNotEmpty()) {
            Result.success(fusionado)
        } else {
            Result.failure(IllegalStateException("No se detecto texto en la imagen."))
        }
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
         *  - **UN, clase y grupo de embalaje**: hoy no hay setter de partida para
         *    hazmat. Van a `traf3`, que es otro endpoint.
         */
        val CAMPOS_ESCRIBIBLES: Map<LabelFieldExtractor.Campo, String> = mapOf(
            LabelFieldExtractor.Campo.LOTE to "numLote",
            LabelFieldExtractor.Campo.NUMERO_SERIE to "numSer",
            // Se escribe el CODIGO que resolvio CountryCatalogMatcher, no el
            // texto de la etiqueta: la columna es varchar(3).
            LabelFieldExtractor.Campo.PAIS_ORIGEN to "cPaisOrigen"
        )
    }
}
