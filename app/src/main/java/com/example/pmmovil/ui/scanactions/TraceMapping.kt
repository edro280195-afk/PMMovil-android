package com.example.pmmovil.ui.scanactions

import androidx.annotation.StringRes
import com.example.pmmovil.R
import com.example.pmmovil.data.model.TraceKind
import com.example.pmmovil.data.model.TraceResultDto
import com.example.pmmovil.ui.components.SignalKind
import com.example.pmmovil.ui.components.Station
import com.example.pmmovil.ui.components.StationState
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * De los códigos de estado del servidor a las piezas de señalética de la app. Todo lo que el servidor no reconozca esta
 * versión de la app cae en «sin dato»: nunca se inventa un estado.
 */

/** Una estación de La Línea tal como se dibuja: cuál es, cómo va y cuándo pasó (ya formateado). */
internal data class StationRowUi(val station: Station, val state: StationState, val at: String?)

internal fun stationOf(id: String): Station? = when (id) {
    "CASETA" -> Station.Caseta
    "ENTRADA" -> Station.Entrada
    "REVISION" -> Station.Revision
    "RELACION" -> Station.Relacion
    "SALIDA" -> Station.Salida
    else -> null
}

internal fun stationStateOf(state: String): StationState = when (state) {
    "HECHA" -> StationState.Done
    "EN_CURSO" -> StationState.Now
    "PENDIENTE" -> StationState.Todo
    "ATENCION" -> StationState.Warn
    "NO_APLICA" -> StationState.NotApplicable
    else -> StationState.Unknown
}

internal fun signalKindOf(signal: String): SignalKind = when (signal) {
    "ALTO" -> SignalKind.Alto
    "ATENCION" -> SignalKind.Atencion
    "LISTO" -> SignalKind.Listo
    "AVISO" -> SignalKind.Aviso
    "CERRADO" -> SignalKind.Cerrado
    else -> SignalKind.SinDato
}

/** Las cinco estaciones en el orden de La Línea, aunque el servidor mande menos o en otro orden. */
internal fun stationRows(result: TraceResultDto): List<StationRowUi> =
    Station.entries.map { station ->
        val dto = result.stations.firstOrNull { stationOf(it.id) == station }
        StationRowUi(
            station = station,
            state = dto?.let { stationStateOf(it.state) } ?: StationState.Unknown,
            at = formatTraceDate(dto?.at)
        )
    }

@StringRes
internal fun flagTextRes(flag: String): Int? = when (flag) {
    "SIN_ENTRADA_VINCULADA" -> R.string.trace_flag_sin_entrada_vinculada
    "ENTRADA_POR_RELACION" -> R.string.trace_flag_entrada_por_relacion
    "SIN_RELACION" -> R.string.trace_flag_sin_relacion
    "DANADA" -> R.string.trace_flag_danada
    "HAZMAT" -> R.string.trace_flag_hazmat
    "DISCREPANCIA" -> R.string.trace_flag_discrepancia
    "LISTA_RECORTADA" -> R.string.trace_flag_lista_recortada
    "CANCELADA" -> R.string.trace_flag_cancelada
    "RELACION_OCUPADA" -> R.string.trace_flag_relacion_ocupada
    "REGISTRO_ABIERTO" -> R.string.trace_flag_registro_abierto
    "ETIQUETA_NO_REGISTRADA" -> R.string.trace_flag_etiqueta_no_registrada
    // Una bandera que esta versión de la app no conoce se omite: mostrar su código crudo confunde más de lo que ayuda.
    else -> null
}

/** Las banderas que esta versión de la app sabe explicar, en el orden en que llegaron. */
internal fun knownFlags(result: TraceResultDto): List<Int> =
    result.flags.mapNotNull(::flagTextRes).distinct()

@StringRes
internal fun reviewStatusRes(status: String): Int = when (status) {
    "EN_PROGRESO" -> R.string.trace_review_en_progreso
    "COMPLETA" -> R.string.trace_review_completa
    "COMPLETA_CON_DISCREPANCIAS" -> R.string.trace_review_completa_discrepancias
    else -> R.string.trace_review_sin_revisar
}

@StringRes
internal fun preregistroStatusRes(status: String): Int? = when (status) {
    "VIGENTE" -> R.string.trace_prereg_vigente
    "USADO" -> R.string.trace_prereg_usado
    "EXPIRADO" -> R.string.trace_prereg_expirado
    "CANCELADO" -> R.string.trace_prereg_cancelado
    else -> null
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT)

/** `2026-10-20T08:30:00` → `20/10/2026 08:30`; sin fecha o con una que no se entiende, `null` (no se muestra nada). */
internal fun formatTraceDate(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    return runCatching { LocalDateTime.parse(iso).format(DATE_FORMAT) }.getOrNull()
}

/** Una fila de «Relacionado»: a qué código lleva, de qué tipo es y cómo va. */
internal data class RelatedRowUi(
    @param:StringRes val typeRes: Int,
    val code: String,
    /** Estatus ya traducido o `null`. Se resuelve en pantalla con [statusRes]/[statusText]. */
    @param:StringRes val statusRes: Int?,
    val statusText: String?
)

/**
 * Las piezas que se relacionan con el código leído, para saltar a su propia ficha: la caja, la relación y las entradas
 * que no son el propio código. Una carpeta o un bulto *son* la entrada, así que no se repiten.
 */
internal fun relatedRows(result: TraceResultDto): List<RelatedRowUi> {
    val rows = mutableListOf<RelatedRowUi>()

    result.caja?.takeIf { result.kind != TraceKind.CAJA && it.casetaId.isNotBlank() }?.let { caja ->
        rows += RelatedRowUi(R.string.trace_related_caja, caja.casetaId, null, caja.estatus.takeIf { it.isNotBlank() })
    }

    result.relacion?.takeIf { result.kind != TraceKind.RELACION && it.relno.isNotBlank() }?.let { relacion ->
        rows += RelatedRowUi(R.string.trace_related_relacion, relacion.relno, null, relacion.estatusCarga.takeIf { it.isNotBlank() })
    }

    if (result.kind != TraceKind.CARPETA && result.kind != TraceKind.BULTO) {
        result.entradas.filter { it.reference.isNotBlank() }.forEach { entrada ->
            rows += RelatedRowUi(R.string.trace_related_entrada, entrada.reference, reviewStatusRes(entrada.reviewStatus), null)
        }
    }

    return rows
}

/** Un dato de la etiqueta: su rótulo y su valor ya listo para mostrar. */
internal data class TraceField(@param:StringRes val label: Int, val value: String)

/**
 * Los datos que lleva la etiqueta según lo que se leyó (hasta seis). Los vacíos no se muestran.
 *
 * @param text resuelve un texto con argumentos; se recibe como función para que esto se pruebe sin Android.
 */
internal fun traceFields(result: TraceResultDto, text: (Int, List<Any>) -> String): List<TraceField> {
    val fields = mutableListOf<TraceField>()
    fun add(@StringRes label: Int, value: String?) {
        if (!value.isNullOrBlank()) fields += TraceField(label, value)
    }

    when (result.kind) {
        TraceKind.CAJA -> result.caja?.let { caja ->
            add(R.string.trace_field_cliente, caja.cliente)
            add(R.string.trace_field_linea, caja.linea)
            add(R.string.trace_field_caja, caja.numCaja)
            add(R.string.trace_field_planta, caja.planta)
            add(R.string.trace_field_operacion, caja.tipoOp)
            add(R.string.trace_field_arrival, formatTraceDate(caja.arrivalAt))
        }

        TraceKind.RELACION -> result.relacion?.let { relacion ->
            add(R.string.trace_field_status, relacion.estatusCarga)
            add(R.string.trace_field_transfer, relacion.transfer)
            add(R.string.trace_field_caja, relacion.caja)
            add(R.string.trace_field_authorized, formatTraceDate(relacion.authorizedAt))
            add(R.string.trace_field_departure, formatTraceDate(relacion.departureAt))
        }

        TraceKind.CARPETA, TraceKind.BULTO -> {
            result.bulto?.let { bulto ->
                add(R.string.trace_field_label, text(R.string.trace_label_position, listOf(bulto.ordinal, bulto.total)))
            }
            result.entradas.firstOrNull()?.let { entrada ->
                add(R.string.trace_field_cliente, entrada.cliente)
                add(R.string.trace_field_arrival, formatTraceDate(entrada.arrivalAt))
                add(R.string.trace_field_review, reviewText(entrada.reviewStatus, entrada.totalInvoices, entrada.completedInvoices, entrada.totalLines, entrada.reviewedLines, text))
            }
        }

        TraceKind.PREREGISTRO -> result.preregistro?.let { pre ->
            preregistroStatusRes(pre.status)?.let { add(R.string.trace_field_status, text(it, emptyList())) }
            add(R.string.trace_field_linea, pre.linea)
            add(R.string.trace_field_unit, pre.unidad)
            add(R.string.trace_field_expires, formatTraceDate(pre.expiresAt))
            add(R.string.trace_field_used, formatTraceDate(pre.usedAt))
        }
    }
    return fields
}

/** «Completa · 2/2 facturas · 14/14 partidas»; sin facturas, solo el estatus. */
private fun reviewText(
    status: String,
    totalInvoices: Int,
    completedInvoices: Int,
    totalLines: Int,
    reviewedLines: Int,
    text: (Int, List<Any>) -> String
): String {
    val state = text(reviewStatusRes(status), emptyList())
    if (totalInvoices <= 0) return state
    return state + " · " + text(R.string.trace_review_progress, listOf(completedInvoices, totalInvoices, reviewedLines, totalLines))
}
