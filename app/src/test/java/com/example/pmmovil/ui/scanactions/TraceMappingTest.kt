package com.example.pmmovil.ui.scanactions

import com.example.pmmovil.R
import com.example.pmmovil.data.model.TraceBultoDto
import com.example.pmmovil.data.model.TraceCajaDto
import com.example.pmmovil.data.model.TraceEntradaDto
import com.example.pmmovil.data.model.TraceKind
import com.example.pmmovil.data.model.TracePreregistroDto
import com.example.pmmovil.data.model.TraceRelacionDto
import com.example.pmmovil.data.model.TraceResultDto
import com.example.pmmovil.data.model.TraceStationDto
import com.example.pmmovil.ui.components.SignalKind
import com.example.pmmovil.ui.components.Station
import com.example.pmmovil.ui.components.StationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceMappingTest {

    // Resuelve los textos sin Android: el id y sus argumentos bastan para comprobar qué se mostraría.
    private val text: (Int, List<Any>) -> String = { id, args -> "$id" + if (args.isEmpty()) "" else args.joinToString(prefix = "(", postfix = ")") }

    @Test
    fun `server states become the signage states and anything new reads as unknown`() {
        assertEquals(StationState.Done, stationStateOf("HECHA"))
        assertEquals(StationState.Now, stationStateOf("EN_CURSO"))
        assertEquals(StationState.Todo, stationStateOf("PENDIENTE"))
        assertEquals(StationState.Warn, stationStateOf("ATENCION"))
        assertEquals(StationState.NotApplicable, stationStateOf("NO_APLICA"))
        assertEquals(StationState.Unknown, stationStateOf("SIN_DATO"))
        assertEquals(StationState.Unknown, stationStateOf("ALGO_NUEVO"))
    }

    @Test
    fun `server signals become the signage signals and anything new reads as no data`() {
        assertEquals(SignalKind.Alto, signalKindOf("ALTO"))
        assertEquals(SignalKind.Atencion, signalKindOf("ATENCION"))
        assertEquals(SignalKind.Listo, signalKindOf("LISTO"))
        assertEquals(SignalKind.Aviso, signalKindOf("AVISO"))
        assertEquals(SignalKind.Cerrado, signalKindOf("CERRADO"))
        assertEquals(SignalKind.SinDato, signalKindOf("SIN_DATO"))
        assertEquals(SignalKind.SinDato, signalKindOf(""))
    }

    @Test
    fun `the line always has the five stations in order even if the server sends fewer or shuffled`() {
        val result = TraceResultDto(
            stations = listOf(
                TraceStationDto("SALIDA", "PENDIENTE"),
                TraceStationDto("CASETA", "HECHA", "2026-10-20T08:30:00"),
                TraceStationDto("ESTACION_NUEVA", "HECHA")
            )
        )

        val rows = stationRows(result)

        assertEquals(Station.entries, rows.map { it.station })
        assertEquals(StationState.Done, rows[0].state)
        assertEquals("20/10/2026 08:30", rows[0].at)
        assertEquals(StationState.Unknown, rows[1].state)
        assertEquals(StationState.Unknown, rows[2].state)
        assertEquals(StationState.Todo, rows[4].state)
    }

    @Test
    fun `dates are shown day first and an unreadable one is hidden`() {
        assertEquals("20/10/2026 08:30", formatTraceDate("2026-10-20T08:30:00"))
        assertNull(formatTraceDate(null))
        assertNull(formatTraceDate(""))
        assertNull(formatTraceDate("no es fecha"))
    }

    @Test
    fun `only the flags this version can explain are shown`() {
        val result = TraceResultDto(flags = listOf("DANADA", "BANDERA_FUTURA", "SIN_RELACION", "DANADA"))

        assertEquals(
            listOf(R.string.trace_flag_danada, R.string.trace_flag_sin_relacion),
            knownFlags(result)
        )
    }

    @Test
    fun `every flag the server can send has a text`() {
        listOf(
            "SIN_ENTRADA_VINCULADA", "ENTRADA_POR_RELACION", "SIN_RELACION", "DANADA", "HAZMAT", "DISCREPANCIA",
            "LISTA_RECORTADA", "CANCELADA", "RELACION_OCUPADA", "REGISTRO_ABIERTO", "ETIQUETA_NO_REGISTRADA"
        ).forEach { flag -> assertTrue("Falta el texto de $flag", flagTextRes(flag) != null) }
    }

    @Test
    fun `a box lists its release and its entries as related, never itself`() {
        val result = TraceResultDto(
            kind = TraceKind.CAJA,
            caja = TraceCajaDto(casetaId = "E-17840/26"),
            relacion = TraceRelacionDto(relno = "01I26104549", estatusCarga = "AUTORIZADA"),
            entradas = listOf(
                TraceEntradaDto(reference = "01C263582800000", reviewStatus = "COMPLETA"),
                TraceEntradaDto(reference = "01C263582810000", reviewStatus = "EN_PROGRESO")
            )
        )

        val rows = relatedRows(result)

        assertEquals(listOf("01I26104549", "01C263582800000", "01C263582810000"), rows.map { it.code })
        assertEquals(R.string.trace_related_relacion, rows[0].typeRes)
        assertEquals("AUTORIZADA", rows[0].statusText)
        assertEquals(R.string.trace_related_entrada, rows[1].typeRes)
        assertEquals(R.string.trace_review_completa, rows[1].statusRes)
    }

    @Test
    fun `a release lists its box and entries but not itself`() {
        val result = TraceResultDto(
            kind = TraceKind.RELACION,
            caja = TraceCajaDto(casetaId = "E-17840/26", estatus = "ASIGNADA"),
            relacion = TraceRelacionDto(relno = "01I26104549"),
            entradas = listOf(TraceEntradaDto(reference = "01C263582800000"))
        )

        assertEquals(listOf("E-17840/26", "01C263582800000"), relatedRows(result).map { it.code })
    }

    @Test
    fun `a folder is the entry itself so only its box and release are related`() {
        val result = TraceResultDto(
            kind = TraceKind.CARPETA,
            caja = TraceCajaDto(casetaId = "E-17840/26"),
            relacion = TraceRelacionDto(relno = "01I26104549"),
            entradas = listOf(TraceEntradaDto(reference = "01C263582800000"))
        )

        assertEquals(listOf("E-17840/26", "01I26104549"), relatedRows(result).map { it.code })
    }

    @Test
    fun `a box shows its customer carrier number plant and operation and skips the empty ones`() {
        val result = TraceResultDto(
            kind = TraceKind.CAJA,
            caja = TraceCajaDto(cliente = "SKF", linea = "TRANSPORTES X", numCaja = "522365", planta = "", tipoOp = "IMPO", arrivalAt = "2026-10-20T08:30:00")
        )

        val fields = traceFields(result, text)

        assertEquals(
            listOf(
                R.string.trace_field_cliente, R.string.trace_field_linea, R.string.trace_field_caja,
                R.string.trace_field_operacion, R.string.trace_field_arrival
            ),
            fields.map { it.label }
        )
        assertEquals("20/10/2026 08:30", fields.last().value)
    }

    @Test
    fun `a package shows its position and the review progress of its entry`() {
        val result = TraceResultDto(
            kind = TraceKind.BULTO,
            bulto = TraceBultoDto(ordinal = 3, total = 12, registered = true),
            entradas = listOf(
                TraceEntradaDto(
                    reference = "01C263582800000", cliente = "SKF", reviewStatus = "EN_PROGRESO",
                    totalInvoices = 2, completedInvoices = 1, totalLines = 14, reviewedLines = 5
                )
            )
        )

        val fields = traceFields(result, text).associate { it.label to it.value }

        assertEquals("${R.string.trace_label_position}(3, 12)", fields[R.string.trace_field_label])
        assertEquals("${R.string.trace_review_en_progreso} · ${R.string.trace_review_progress}(1, 2, 5, 14)", fields[R.string.trace_field_review])
    }

    @Test
    fun `an entry without invoices shows only the review status`() {
        val result = TraceResultDto(
            kind = TraceKind.CARPETA,
            entradas = listOf(TraceEntradaDto(reference = "01C263582800000", reviewStatus = "SIN_REVISAR"))
        )

        val review = traceFields(result, text).single { it.label == R.string.trace_field_review }

        assertEquals("${R.string.trace_review_sin_revisar}", review.value)
    }

    @Test
    fun `a pre registration shows its state in words`() {
        val result = TraceResultDto(
            kind = TraceKind.PREREGISTRO,
            preregistro = TracePreregistroDto(folio = "7K2Q-4481", status = "VIGENTE", linea = "TRANSPORTES X", unidad = "T-88", expiresAt = "2026-10-26T23:00:00")
        )

        val fields = traceFields(result, text).associate { it.label to it.value }

        assertEquals("${R.string.trace_prereg_vigente}", fields[R.string.trace_field_status])
        assertEquals("TRANSPORTES X", fields[R.string.trace_field_linea])
        assertEquals("26/10/2026 23:00", fields[R.string.trace_field_expires])
    }
}
