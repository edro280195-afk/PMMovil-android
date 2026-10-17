package com.example.pmmovil.ui.revision

import com.example.pmmovil.data.model.LabelCaptureFields
import com.example.pmmovil.data.model.LabelCaptureSnapshot
import com.example.pmmovil.data.model.PartidaRevisionDto
import com.example.pmmovil.data.ocr.LabelFieldExtractor
import com.example.pmmovil.data.ocr.LabelFieldExtractor.Campo
import com.example.pmmovil.ui.revision.LabelCaptureCotejo.Decision
import com.example.pmmovil.ui.revision.LabelCaptureCotejo.Fila
import com.example.pmmovil.ui.revision.LabelCaptureCotejo.Tipo
import com.example.pmmovil.ui.revision.LabelCaptureViewModel.CampoPropuesto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelCaptureCotejoTest {
    // ---- Los cuatro casos del mockup aprobado ----

    @Test
    fun `todo coincide - lo nuevo se guarda y lo igual no se toca`() {
        val filas = comparar(
            campos = listOf(
                propuesta(Campo.NUMERO_PARTE, "HX-4410-B"), propuesta(Campo.LOTE, "2604117-A"),
                propuesta(Campo.PAIS_ORIGEN, "THA", detalle = "THA · Tailandia"), propuesta(Campo.MARCA, "DEMOTEC")
            ),
            snapshot = LabelCaptureSnapshot(marca = "DEMOTEC"),
            partida = partida(parte = "HX-4410-B")
        )

        assertEquals(Tipo.COINCIDE, fila(filas, Campo.NUMERO_PARTE).tipo)
        assertEquals(Tipo.NUEVO, fila(filas, Campo.LOTE).tipo)
        assertEquals(Tipo.NUEVO, fila(filas, Campo.PAIS_ORIGEN).tipo)
        assertEquals(Tipo.COINCIDE, fila(filas, Campo.MARCA).tipo)
        assertEquals(0, LabelCaptureCotejo.pendientes(filas))
        assertEquals(listOf(Campo.LOTE, Campo.PAIS_ORIGEN), LabelCaptureCotejo.aGuardar(filas))
        assertEquals(listOf(Campo.MARCA), LabelCaptureCotejo.sinCambio(filas))
    }

    @Test
    fun `una diferencia obliga a decidir y cada decision cambia lo que se guarda`() {
        val campos = listOf(propuesta(Campo.NUMERO_PARTE, "HX-4410-B"), propuesta(Campo.MARCA, "DEMOTEC"))
        val snapshot = LabelCaptureSnapshot(marca = "ALFAPARTS")

        val sinDecidir = comparar(campos, snapshot, partida(parte = "HX-4410-B"))
        assertEquals(Tipo.DIFERENTE, fila(sinDecidir, Campo.MARCA).tipo)
        assertEquals(1, LabelCaptureCotejo.pendientes(sinDecidir))
        assertTrue(LabelCaptureCotejo.aGuardar(sinDecidir).isEmpty())

        val usar = comparar(campos, snapshot, partida(parte = "HX-4410-B"), decisiones = mapOf(Campo.MARCA to Decision.USAR))
        assertEquals(0, LabelCaptureCotejo.pendientes(usar))
        assertEquals(listOf(Campo.MARCA), LabelCaptureCotejo.aGuardar(usar))
        assertEquals("DEMOTEC", fila(usar, Campo.MARCA).resultante)

        val conservar = comparar(campos, snapshot, partida(parte = "HX-4410-B"), decisiones = mapOf(Campo.MARCA to Decision.CONSERVAR))
        assertEquals(0, LabelCaptureCotejo.pendientes(conservar))
        assertTrue(LabelCaptureCotejo.aGuardar(conservar).isEmpty())
        assertEquals(listOf(Campo.MARCA), LabelCaptureCotejo.sinCambio(conservar))
        assertEquals("ALFAPARTS", fila(conservar, Campo.MARCA).resultante)
    }

    @Test
    fun `sin clave leida el numero de parte queda sin lectura pero el resto se coteja`() {
        val filas = comparar(
            campos = listOf(propuesta(Campo.LOTE, "K2610-44"), propuesta(Campo.MARCA, "FLOWTEC")),
            snapshot = LabelCaptureSnapshot(),
            partida = partida(parte = "VB-0750")
        )

        assertEquals(Tipo.SIN_LECTURA, fila(filas, Campo.NUMERO_PARTE).tipo)
        assertEquals("VB-0750", fila(filas, Campo.NUMERO_PARTE).declarado)
        assertEquals(listOf(Campo.LOTE, Campo.MARCA), LabelCaptureCotejo.aGuardar(filas))
    }

    @Test
    fun `el rombo peligroso compara UN clase y grupo contra el hazmat de la referencia`() {
        val filas = comparar(
            campos = listOf(
                propuesta(Campo.NUMERO_PARTE, "RA-27-1316"), propuesta(Campo.UN_HAZMAT, "1866"),
                propuesta(Campo.CLASE_HAZMAT, "3"), propuesta(Campo.GRUPO_EMBALAJE, "III")
            ),
            snapshot = LabelCaptureSnapshot(hazmatUn = "UN 1866"),
            partida = partida(parte = "RA-27-1316")
        )

        assertEquals(Tipo.COINCIDE, fila(filas, Campo.UN_HAZMAT).tipo)
        assertEquals(Tipo.NUEVO, fila(filas, Campo.CLASE_HAZMAT).tipo)
        assertEquals(Tipo.NUEVO, fila(filas, Campo.GRUPO_EMBALAJE).tipo)
        assertEquals(listOf(Campo.CLASE_HAZMAT, Campo.GRUPO_EMBALAJE), LabelCaptureCotejo.aGuardar(filas))
    }

    @Test
    fun `un hazmat distinto al vigente no se pisa sin decision`() {
        val filas = comparar(
            campos = listOf(propuesta(Campo.CLASE_HAZMAT, "3")),
            snapshot = LabelCaptureSnapshot(hazmatClase = "8"),
            partida = partida(parte = "X")
        )

        assertEquals(Tipo.DIFERENTE, fila(filas, Campo.CLASE_HAZMAT).tipo)
        assertTrue(fila(filas, Campo.CLASE_HAZMAT).pendiente)
        assertTrue(LabelCaptureCotejo.aGuardar(filas).isEmpty())
    }

    // ---- Qué cuenta como «igual» ----

    @Test
    fun `mayusculas espacios signos y acentos no crean diferencias`() {
        val filas = comparar(
            campos = listOf(propuesta(Campo.MARCA, "Demo-Tec"), propuesta(Campo.MODELO, "Perez 10"), propuesta(Campo.NUMERO_SERIE, "sn 0042")),
            snapshot = LabelCaptureSnapshot(marca = "DEMOTEC", modelo = "PÉREZ-10", numSer = "SN0042"),
            partida = partida(parte = "X")
        )

        listOf(Campo.MARCA, Campo.MODELO, Campo.NUMERO_SERIE).forEach { assertEquals(it.name, Tipo.COINCIDE, fila(filas, it).tipo) }
        assertTrue(LabelCaptureCotejo.aGuardar(filas).isEmpty())
    }

    @Test
    fun `lo que ya coincide nunca se reescribe aunque se vea distinto`() {
        val filas = comparar(listOf(propuesta(Campo.MARCA, "demo tec")), LabelCaptureSnapshot(marca = "DEMOTEC"), partida(parte = "X"))

        assertFalse(fila(filas, Campo.MARCA).seGuarda)
        assertEquals("DEMOTEC", fila(filas, Campo.MARCA).resultante)
    }

    @Test
    fun `una letra distinta sigue siendo diferencia`() {
        val filas = comparar(listOf(propuesta(Campo.MODELO, "XT-200")), LabelCaptureSnapshot(modelo = "XT-2000"), partida(parte = "X"))

        assertEquals(Tipo.DIFERENTE, fila(filas, Campo.MODELO).tipo)
    }

    // ---- Lotes: el servidor los suma, nunca los reemplaza ----

    @Test
    fun `un lote ya declarado coincide sin importar mayusculas ni orden`() {
        val filas = comparar(listOf(propuesta(Campo.LOTE, "b2")), LabelCaptureSnapshot(numLote = "A1, B2"), partida(parte = "X"))

        assertEquals(Tipo.COINCIDE, fila(filas, Campo.LOTE).tipo)
        assertTrue(LabelCaptureCotejo.aGuardar(filas).isEmpty())
    }

    @Test
    fun `un lote nuevo se suma a los declarados y no exige decision`() {
        val filas = comparar(listOf(propuesta(Campo.LOTE, "C3")), LabelCaptureSnapshot(numLote = "A1, B2"), partida(parte = "X"))

        val lote = fila(filas, Campo.LOTE)
        assertEquals(Tipo.NUEVO, lote.tipo)
        assertTrue(lote.suma)
        assertFalse(lote.pendiente)
        assertEquals(listOf(Campo.LOTE), LabelCaptureCotejo.aGuardar(filas))
    }

    @Test
    fun `el primer lote de una partida sin lotes no es una suma`() {
        val filas = comparar(listOf(propuesta(Campo.LOTE, "A1")), LabelCaptureSnapshot(), partida(parte = "X"))

        assertFalse(fila(filas, Campo.LOTE).suma)
        assertEquals(Tipo.NUEVO, fila(filas, Campo.LOTE).tipo)
    }

    @Test
    fun `varios lotes leidos solo suman cuando alguno falta`() {
        val completo = comparar(listOf(propuesta(Campo.LOTE, "A1; B2")), LabelCaptureSnapshot(numLote = "A1,B2"), partida(parte = "X"))
        val parcial = comparar(listOf(propuesta(Campo.LOTE, "A1; B2")), LabelCaptureSnapshot(numLote = "A1"), partida(parte = "X"))

        assertEquals(Tipo.COINCIDE, fila(completo, Campo.LOTE).tipo)
        assertEquals(Tipo.NUEVO, fila(parcial, Campo.LOTE).tipo)
    }

    @Test
    fun `una serie ya declarada coincide y una serie nueva reemplazaria asi que pide decision`() {
        val yaEsta = comparar(listOf(propuesta(Campo.NUMERO_SERIE, "sn-2")), LabelCaptureSnapshot(numSer = "SN-1, SN-2"), partida(parte = "X"))
        val nueva = comparar(listOf(propuesta(Campo.NUMERO_SERIE, "SN-3")), LabelCaptureSnapshot(numSer = "SN-1, SN-2"), partida(parte = "X"))

        assertEquals(Tipo.COINCIDE, fila(yaEsta, Campo.NUMERO_SERIE).tipo)
        assertEquals(Tipo.DIFERENTE, fila(nueva, Campo.NUMERO_SERIE).tipo)
        assertTrue(fila(nueva, Campo.NUMERO_SERIE).pendiente)
        assertFalse(fila(nueva, Campo.NUMERO_SERIE).suma)
    }

    // ---- País, UN ----

    @Test
    fun `el pais muestra el nombre del catalogo y se compara por codigo`() {
        val filas = comparar(
            campos = listOf(propuesta(Campo.PAIS_ORIGEN, "THAILAND", escribirComo = "THA", detalle = "THA · Tailandia")),
            snapshot = LabelCaptureSnapshot(cPaisOrigen = "MEX"),
            partida = partida(parte = "X")
        )

        val pais = fila(filas, Campo.PAIS_ORIGEN)
        assertEquals(Tipo.DIFERENTE, pais.tipo)
        assertEquals("THA", pais.leido)
        assertEquals("THA · Tailandia", pais.detalle)
    }

    @Test
    fun `un pais fuera del catalogo se avisa pero no se guarda ni bloquea`() {
        val filas = comparar(
            campos = listOf(propuesta(Campo.PAIS_ORIGEN, "ATLANTIS", escribible = false, detalle = "Elige un país válido del catálogo.")),
            snapshot = LabelCaptureSnapshot(),
            partida = partida(parte = "X")
        )

        val pais = fila(filas, Campo.PAIS_ORIGEN)
        assertEquals(Tipo.NO_VALIDO, pais.tipo)
        assertEquals("ATLANTIS", pais.leido)
        assertFalse(pais.pendiente)
        assertFalse(pais.seGuarda)
    }

    @Test
    fun `el prefijo UN no crea diferencia`() {
        val filas = comparar(listOf(propuesta(Campo.UN_HAZMAT, "UN1866")), LabelCaptureSnapshot(hazmatUn = "1866"), partida(parte = "X"))

        assertEquals(Tipo.COINCIDE, fila(filas, Campo.UN_HAZMAT).tipo)
    }

    // ---- Lecturas contradictorias y datos quitados ----

    @Test
    fun `lecturas contradictorias bloquean hasta elegir una y luego se comparan normal`() {
        val conflicto = LabelFieldExtractor.FieldConflict(Campo.LOTE, listOf(leida(Campo.LOTE, "A100"), leida(Campo.LOTE, "A1O0"), leida(Campo.LOTE, "A100")))
        val propuesto = listOf(propuesta(Campo.LOTE, "A100"))

        val sinResolver = comparar(propuesto, LabelCaptureSnapshot(), partida(parte = "X"), conflictos = listOf(conflicto))
        val lote = fila(sinResolver, Campo.LOTE)
        assertEquals(Tipo.CONFLICTO, lote.tipo)
        assertEquals(listOf("A100", "A1O0"), lote.opciones)
        assertTrue(lote.pendiente)
        assertEquals(1, LabelCaptureCotejo.pendientes(sinResolver))
        assertTrue(LabelCaptureCotejo.aGuardar(sinResolver).isEmpty())

        val resuelto = comparar(propuesto, LabelCaptureSnapshot(), partida(parte = "X"), conflictos = listOf(conflicto), resueltos = setOf(Campo.LOTE))
        assertEquals(Tipo.NUEVO, fila(resuelto, Campo.LOTE).tipo)
        assertEquals(0, LabelCaptureCotejo.pendientes(resuelto))
    }

    @Test
    fun `un dato que el revisador dejo vacio ya no se guarda`() {
        val vacio = CampoPropuesto(leida(Campo.MARCA, "DEMOTEC"), escribible = false, aceptado = false,
            destino = LabelCaptureViewModel.Destino.NINGUNO, valorAEscribir = "")
        val filas = comparar(listOf(vacio), LabelCaptureSnapshot(), partida(parte = "X"))

        assertEquals(Tipo.SIN_LECTURA, fila(filas, Campo.MARCA).tipo)
        assertTrue(LabelCaptureCotejo.aGuardar(filas).isEmpty())
    }

    @Test
    fun `solo aparecen las filas de lo que se leyo, mas el numero de parte`() {
        val filas = comparar(listOf(propuesta(Campo.LOTE, "A1")), LabelCaptureSnapshot(), partida(parte = "X"))

        assertEquals(listOf(Campo.NUMERO_PARTE, Campo.LOTE), filas.map { it.campo })
    }

    // ---- Número de parte: solo identifica, nunca se escribe ----

    @Test
    fun `el numero de parte nunca es escribible ni pendiente`() {
        val iguales = comparar(listOf(propuesta(Campo.NUMERO_PARTE, " ab-10 ")), LabelCaptureSnapshot(), partida(parte = "AB-10"))
        val distintos = comparar(listOf(propuesta(Campo.NUMERO_PARTE, "ZZ-99")), LabelCaptureSnapshot(), partida(parte = "AB-10"))

        assertEquals(Tipo.COINCIDE, fila(iguales, Campo.NUMERO_PARTE).tipo)
        assertEquals(Tipo.DIFERENTE, fila(distintos, Campo.NUMERO_PARTE).tipo)
        listOf(iguales, distintos).forEach {
            assertFalse(fila(it, Campo.NUMERO_PARTE).escribible)
            assertEquals(0, LabelCaptureCotejo.pendientes(it))
            assertTrue(LabelCaptureCotejo.aGuardar(it).isEmpty())
        }
    }

    @Test
    fun `el alias del cliente identifica la partida`() {
        val filas = comparar(listOf(propuesta(Campo.NUMERO_PARTE, "SUP-20")), LabelCaptureSnapshot(), partida(parte = "INT-10", alias = "SUP-20"))

        val parte = fila(filas, Campo.NUMERO_PARTE)
        assertEquals(Tipo.COINCIDE, parte.tipo)
        assertEquals("SUP-20", parte.declarado)
        assertEquals("Alias del cliente", parte.detalle)
    }

    @Test
    fun `una clave que solo coincide sin separadores es diferencia con aviso`() {
        val filas = comparar(listOf(propuesta(Campo.NUMERO_PARTE, "AB.12")), LabelCaptureSnapshot(), partida(parte = "AB-12"))

        val parte = fila(filas, Campo.NUMERO_PARTE)
        assertEquals(Tipo.DIFERENTE, parte.tipo)
        assertEquals("Solo coincide al quitar separadores", parte.detalle)
    }

    // ---- Cantidad: solo informa ----

    @Test
    fun `la cantidad de la etiqueta se muestra como referencia`() {
        val cantidad = LabelCaptureCotejo.cantidadEtiqueta(listOf(propuesta(Campo.CANTIDAD, "10", raw = "10 PCS PER BAG")))

        assertEquals("10 PCS PER BAG", cantidad?.texto)
        assertNull(cantidad?.bulto)
    }

    @Test
    fun `un bulto numerado da su numero y su total`() {
        val lectura = leida(Campo.CANTIDAD, "3", raw = "3-10").copy(
            quantityScope = LabelFieldExtractor.QuantityScope.PACKAGE_ORDINAL, packageTotal = "10"
        )
        val cantidad = LabelCaptureCotejo.cantidadEtiqueta(listOf(CampoPropuesto(lectura, false, false)))

        assertEquals("3", cantidad?.bulto)
        assertEquals("10", cantidad?.bultoTotal)
    }

    @Test
    fun `sin cantidad leida no hay renglon de cantidad`() {
        assertNull(LabelCaptureCotejo.cantidadEtiqueta(listOf(propuesta(Campo.LOTE, "A1"))))
    }

    // ---- Ceros a la izquierda (autorizado por Eduardo el 16-oct) ----

    @Test
    fun `la clave con ceros de la etiqueta coincide con la del catalogo y la identidad es exacta`() {
        val campos = listOf(propuesta(Campo.NUMERO_PARTE, "0049263333"))
        val partida = partida(parte = "49263333")

        val parte = fila(comparar(campos, LabelCaptureSnapshot(), partida), Campo.NUMERO_PARTE)

        assertEquals(Tipo.COINCIDE, parte.tipo)
        assertEquals("Sin los ceros de la etiqueta", parte.detalle)
        assertEquals(LabelCaptureCotejo.Identidad.CLAVE_EXACTA, LabelCaptureCotejo.identidad(campos, partida))
    }

    @Test
    fun `una clave igual de forma exacta no lleva aviso de ceros`() {
        val parte = fila(
            comparar(listOf(propuesta(Campo.NUMERO_PARTE, "49263333")), LabelCaptureSnapshot(), partida(parte = "49263333")),
            Campo.NUMERO_PARTE
        )

        assertEquals(Tipo.COINCIDE, parte.tipo)
        assertNull(parte.detalle)
    }

    @Test
    fun `los ceros del catalogo no se recortan y una clave distinta sigue siendo distinta`() {
        val conCerosEnCatalogo = fila(
            comparar(listOf(propuesta(Campo.NUMERO_PARTE, "12")), LabelCaptureSnapshot(), partida(parte = "00012")), Campo.NUMERO_PARTE
        )
        val otraClave = fila(
            comparar(listOf(propuesta(Campo.NUMERO_PARTE, "0049263334")), LabelCaptureSnapshot(), partida(parte = "49263333")),
            Campo.NUMERO_PARTE
        )

        assertEquals(Tipo.DIFERENTE, conCerosEnCatalogo.tipo)
        assertEquals(Tipo.DIFERENTE, otraClave.tipo)
    }

    // ---- Cantidad con unidades ----

    private fun cantidadDe(valor: String, raw: String, unidad: String?, cantidad: Double): Fila? = comparar(
        listOf(propuesta(Campo.CANTIDAD, valor, raw = raw).copy(escribible = false, aceptado = false)),
        LabelCaptureSnapshot(), partida(parte = "X", cantidad = cantidad, unidad = unidad)
    ).firstOrNull { it.campo == Campo.CANTIDAD }

    @Test
    fun `los cientos de la factura se pasan a piezas antes de compararlos con la bolsa`() {
        // Partida línea 33: 2.100 CNT son 210 piezas, y la bolsa dice 210 PC.
        val fila = cantidadDe("210", "210 PC", "CNT", 2.1)!!

        assertEquals(Tipo.COINCIDE, fila.tipo)
        assertEquals(LabelCaptureCotejo.ResultadoCantidad.IGUAL, fila.cantidad!!.resultado)
        assertEquals("pzas", fila.cantidad!!.unidadBase)
        assertEquals(210.0, fila.cantidad!!.declaradaEnBase, 0.0001)
        assertEquals("210", fila.declarado)
    }

    @Test
    fun `la cantidad que viene del codigo sin unidad se toma en piezas y puede ser parcial`() {
        // Caja de 600 piezas contra una partida de 26,400 PZA: faltan muchas cajas, no hay error.
        val fila = cantidadDe("00000600", "00000600", "PZA", 26400.0)!!

        assertEquals(Tipo.PARCIAL, fila.tipo)
        assertEquals(LabelCaptureCotejo.ResultadoCantidad.PARCIAL, fila.cantidad!!.resultado)
        assertEquals("600", fila.leido)
        assertEquals("26400", fila.declarado)
        assertFalse("un empaque parcial no bloquea nada", fila.pendiente)
    }

    @Test
    fun `una etiqueta con mas piezas que la factura se marca pero no bloquea`() {
        val fila = cantidadDe("300", "300 PCS", "CNT", 2.0)!!

        assertEquals(LabelCaptureCotejo.ResultadoCantidad.MAYOR, fila.cantidad!!.resultado)
        assertEquals(Tipo.DIFERENTE, fila.tipo)
        assertFalse(fila.pendiente)
        assertFalse(fila.seGuarda)
    }

    @Test
    fun `kilos de la etiqueta no se comparan con cientos de la factura`() {
        val fila = cantidadDe("200", "200 KG NET", "CNT", 2.0)!!

        assertEquals(LabelCaptureCotejo.ResultadoCantidad.NO_COMPARABLE, fila.cantidad!!.resultado)
        assertEquals(Tipo.SIN_LECTURA, fila.tipo)
    }

    @Test
    fun `las unidades que no son de piezas se comparan tal cual`() {
        val igual = cantidadDe("49.200", "0049.200", "KG", 49.2)!!
        val distinta = cantidadDe("77.6", "77.6 KG", "KG", 49.2)!!

        assertEquals(LabelCaptureCotejo.ResultadoCantidad.IGUAL, igual.cantidad!!.resultado)
        assertEquals("KG", igual.cantidad!!.unidadBase)
        assertEquals(LabelCaptureCotejo.ResultadoCantidad.MAYOR, distinta.cantidad!!.resultado)
    }

    @Test
    fun `los millares tambien se convierten a piezas`() {
        val fila = cantidadDe("1500", "1500 PC", "MLL", 1.5)!!

        assertEquals(LabelCaptureCotejo.ResultadoCantidad.IGUAL, fila.cantidad!!.resultado)
    }

    @Test
    fun `un numero de bulto el contenido por empaque o una factura sin unidad no son comparaciones`() {
        val ordinal = leida(Campo.CANTIDAD, "3", raw = "3-10").copy(
            quantityScope = LabelFieldExtractor.QuantityScope.PACKAGE_ORDINAL, packageTotal = "10"
        )
        val deOrdinal = comparar(listOf(CampoPropuesto(ordinal, false, false)), LabelCaptureSnapshot(), partida(parte = "X", unidad = "CNT"))
        val porEmpaque = comparar(
            listOf(propuesta(Campo.PIEZAS_POR_EMPAQUE, "10", raw = "10 PCS PER BAG")), LabelCaptureSnapshot(), partida(parte = "X", unidad = "CNT")
        )

        assertTrue(deOrdinal.none { it.campo == Campo.CANTIDAD })
        assertTrue(porEmpaque.none { it.campo == Campo.CANTIDAD })
        assertNull(cantidadDe("10", "10", null, 10.0))
    }

    @Test
    fun `la fila de cantidad nunca se escribe ni cuenta como pendiente`() {
        val filas = comparar(
            listOf(propuesta(Campo.CANTIDAD, "300", raw = "300 PCS").copy(escribible = false, aceptado = false), propuesta(Campo.LOTE, "A1")),
            LabelCaptureSnapshot(), partida(parte = "X", cantidad = 2.0, unidad = "CNT")
        )

        assertEquals(0, LabelCaptureCotejo.pendientes(filas))
        assertEquals(listOf(Campo.LOTE), LabelCaptureCotejo.aGuardar(filas))
        assertTrue(Campo.CANTIDAD !in LabelCaptureCotejo.sinCambio(filas))
    }

    @Test
    fun `los numeros se escriben sin ceros sobrantes`() {
        assertEquals("210", LabelCaptureCotejo.numero(210.0))
        assertEquals("2.1", LabelCaptureCotejo.numero(2.1))
        assertEquals("0.38", LabelCaptureCotejo.numero(0.38))
        assertEquals("1980", LabelCaptureCotejo.numero(1980.0))
        assertEquals("33.333", LabelCaptureCotejo.numero(33.3333))
    }

    // ---- Estado completo ----

    @Test
    fun `sin los datos actuales de la partida no hay cotejo`() {
        val estado = LabelCaptureViewModel.Estado(campos = listOf(propuesta(Campo.LOTE, "A1")), snapshot = null)

        assertTrue(LabelCaptureCotejo.comparar(estado).isEmpty())
    }

    @Test
    fun `el estado aplica las decisiones guardadas`() {
        val estado = LabelCaptureViewModel.Estado(
            campos = listOf(propuesta(Campo.MARCA, "DEMOTEC")), snapshot = LabelCaptureSnapshot(marca = "ALFAPARTS"),
            selectedPartida = partida(parte = "X"), decisiones = mapOf(Campo.MARCA to Decision.USAR)
        )

        assertEquals(listOf(Campo.MARCA), LabelCaptureCotejo.aGuardar(LabelCaptureCotejo.comparar(estado)))
    }

    // ---- Identidad de la partida ----

    @Test
    fun `la identidad sale de la clave impresa en la etiqueta`() {
        val partida = partida(parte = "AB-10")

        assertEquals(LabelCaptureCotejo.Identidad.CLAVE_EXACTA,
            LabelCaptureCotejo.identidad(listOf(propuesta(Campo.NUMERO_PARTE, "ab-10")), partida))
        assertEquals(LabelCaptureCotejo.Identidad.CLAVE_DISTINTA,
            LabelCaptureCotejo.identidad(listOf(propuesta(Campo.NUMERO_PARTE, "ZZ-99")), partida))
        assertEquals(LabelCaptureCotejo.Identidad.SIN_CLAVE,
            LabelCaptureCotejo.identidad(listOf(propuesta(Campo.LOTE, "A1")), partida))
    }

    // ---- Lo que viaja al servidor ----

    @Test
    fun `la peticion solo lleva lo nuevo y lo decidido`() {
        val estado = estadoListo(
            campos = listOf(
                propuesta(Campo.NUMERO_PARTE, "HX-4410-B"), propuesta(Campo.LOTE, "2604117-A"),
                propuesta(Campo.MARCA, "demo tec"), propuesta(Campo.PAIS_ORIGEN, "THAILAND", escribirComo = "THA", detalle = "THA · Tailandia"),
                propuesta(Campo.MODELO, "X2"), propuesta(Campo.NUMERO_SERIE, "SN-9"),
                propuesta(Campo.CANTIDAD, "10", raw = "10 PCS PER BAG").copy(escribible = false, aceptado = false)
            ),
            snapshot = LabelCaptureSnapshot(marca = "DEMOTEC", cPaisOrigen = "MEX", modelo = "X1", numSer = "SN-1"),
            decisiones = mapOf(Campo.PAIS_ORIGEN to Decision.CONSERVAR, Campo.MODELO to Decision.USAR)
        )

        val fields = LabelCaptureCotejo.armarPeticion(estado, "REF-1", markReviewed = false)!!.fields

        assertEquals("2604117-A", fields.numLote)
        assertEquals("X2", fields.modelo)
        assertNull("lo que coincide no se reescribe", fields.marca)
        assertNull("lo conservado no se manda", fields.cPaisOrigen)
        assertNull("una diferencia sin decidir no se manda", fields.numSer)
    }

    @Test
    fun `el hazmat solo lleva las piezas que cambian`() {
        val estado = estadoListo(
            campos = listOf(propuesta(Campo.UN_HAZMAT, "1866"), propuesta(Campo.CLASE_HAZMAT, "3"), propuesta(Campo.GRUPO_EMBALAJE, "III")),
            snapshot = LabelCaptureSnapshot(hazmatUn = "1866", hazmatClase = "", hazmatGrupo = "II"),
            decisiones = mapOf(Campo.GRUPO_EMBALAJE to Decision.USAR)
        )

        val hazmat = LabelCaptureCotejo.armarPeticion(estado, "REF-1", markReviewed = false)!!.hazmat!!

        assertNull(hazmat.un)
        assertEquals("3", hazmat.clase)
        assertEquals("III", hazmat.grupo)
    }

    @Test
    fun `si nada cambia no se manda hazmat ni campos pero la revision sigue su curso`() {
        val estado = estadoListo(
            campos = listOf(propuesta(Campo.MARCA, "DEMOTEC"), propuesta(Campo.UN_HAZMAT, "1866")),
            snapshot = LabelCaptureSnapshot(marca = "DEMOTEC", hazmatUn = "1866")
        )

        val peticion = LabelCaptureCotejo.armarPeticion(estado, "REF-1", markReviewed = true)!!

        assertNull(peticion.hazmat)
        assertEquals(LabelCaptureFields(), peticion.fields)
        assertTrue(peticion.markReviewed)
    }

    @Test
    fun `marcar revisada declara la revision fisica y guardar avance no`() {
        val estado = estadoListo(listOf(propuesta(Campo.LOTE, "A1")), LabelCaptureSnapshot())

        val revisada = LabelCaptureCotejo.armarPeticion(estado, "REF-1", markReviewed = true)!!
        val avance = LabelCaptureCotejo.armarPeticion(estado, "REF-1", markReviewed = false)!!

        assertTrue(revisada.markReviewed && revisada.physicalReviewConfirmed)
        assertFalse(avance.markReviewed || avance.physicalReviewConfirmed)
    }

    @Test
    fun `con una diferencia sin decidir se puede guardar avance pero no marcar revisada`() {
        val estado = estadoListo(listOf(propuesta(Campo.MARCA, "DEMOTEC")), LabelCaptureSnapshot(marca = "ALFAPARTS"))

        assertNull(LabelCaptureCotejo.armarPeticion(estado, "REF-1", markReviewed = true))
        val avance = LabelCaptureCotejo.armarPeticion(estado, "REF-1", markReviewed = false)!!
        assertNull("la diferencia sin decidir no se escribe", avance.fields.marca)
    }

    @Test
    fun `sin identidad aceptada o sin permiso no hay peticion`() {
        val campos = listOf(propuesta(Campo.LOTE, "A1"))

        assertNull(LabelCaptureCotejo.armarPeticion(estadoListo(campos, LabelCaptureSnapshot(), identidad = false), "REF-1", false))
        assertNull(LabelCaptureCotejo.armarPeticion(estadoListo(campos, LabelCaptureSnapshot(), puedeEditar = false), "REF-1", false))
        assertNull(LabelCaptureCotejo.armarPeticion(estadoListo(campos, LabelCaptureSnapshot(), puedeMarcar = false), "REF-1", true))
        assertTrue(LabelCaptureCotejo.armarPeticion(estadoListo(campos, LabelCaptureSnapshot(), puedeMarcar = false), "REF-1", false) != null)
    }

    @Test
    fun `la peticion lleva la evidencia con las lecturas aceptadas marcadas`() {
        val lote = leida(Campo.LOTE, "A1")
        val marca = leida(Campo.MARCA, "DEMOTEC")
        val modelo = leida(Campo.MODELO, "X2")
        val estado = estadoListo(
            campos = listOf(propuesta(Campo.LOTE, "A1"), propuesta(Campo.MARCA, "DEMOTEC"), propuesta(Campo.MODELO, "X2")),
            snapshot = LabelCaptureSnapshot(marca = "DEMOTEC", modelo = "X1"),
            decisiones = mapOf(Campo.MODELO to Decision.CONSERVAR),
            observaciones = listOf(lote, marca, modelo)
        )

        val observaciones = LabelCaptureCotejo.armarPeticion(estado, "REF-1", markReviewed = true)!!.observations.associateBy { it.field }

        assertTrue("lo que se guarda queda aceptado", observaciones.getValue("LOTE").accepted)
        assertTrue("lo que coincide con lo declarado queda aceptado", observaciones.getValue("MARCA").accepted)
        assertFalse("lo que el revisador no usó queda como lectura rechazada", observaciones.getValue("MODELO").accepted)
    }

    @Test
    fun `la peticion apunta a la partida y a los datos que el revisador vio`() {
        val snapshot = LabelCaptureSnapshot(marca = "DEMOTEC", revisada = false)
        val estado = estadoListo(listOf(propuesta(Campo.LOTE, "A1")), snapshot)

        val peticion = LabelCaptureCotejo.armarPeticion(estado, "REF-1", markReviewed = true)!!

        assertEquals("REF-1", peticion.crefAgrupada)
        assertEquals("captura-1", peticion.captureId)
        assertEquals(7, peticion.idRevdet)
        assertEquals(snapshot, peticion.expectedSnapshot)
    }

    // ---- Apoyos ----

    private fun estadoListo(
        campos: List<CampoPropuesto>,
        snapshot: LabelCaptureSnapshot,
        decisiones: Map<Campo, Decision> = emptyMap(),
        observaciones: List<LabelFieldExtractor.CampoLeido> = emptyList(),
        identidad: Boolean = true,
        puedeEditar: Boolean = true,
        puedeMarcar: Boolean = true
    ) = LabelCaptureViewModel.Estado(
        campos = campos, observations = observaciones, captureId = "captura-1",
        selectedPartida = partida(parte = "HX-4410-B", id = 7), snapshot = snapshot,
        canEdit = puedeEditar, canMarkReviewed = puedeMarcar, identityAccepted = identidad, decisiones = decisiones
    )

    private fun comparar(
        campos: List<CampoPropuesto>,
        snapshot: LabelCaptureSnapshot,
        partida: PartidaRevisionDto?,
        conflictos: List<LabelFieldExtractor.FieldConflict> = emptyList(),
        resueltos: Set<Campo> = emptySet(),
        decisiones: Map<Campo, Decision> = emptyMap()
    ): List<Fila> = LabelCaptureCotejo.comparar(campos, snapshot, partida, conflictos, resueltos, decisiones)

    private fun fila(filas: List<Fila>, campo: Campo): Fila = filas.first { it.campo == campo }

    private fun leida(campo: Campo, valor: String, raw: String = valor) =
        LabelFieldExtractor.CampoLeido(campo, valor, LabelFieldExtractor.Origen.ANCLA_MISMA_LINEA, "ANCLA", 90, rawValue = raw)

    private fun propuesta(
        campo: Campo,
        valor: String,
        escribible: Boolean = true,
        detalle: String? = null,
        escribirComo: String = valor,
        raw: String = valor
    ) = CampoPropuesto(
        leido = leida(campo, valor, raw), escribible = escribible, aceptado = escribible,
        destino = LabelCaptureViewModel.Destino.PARTIDA, valorAEscribir = escribirComo, detalle = detalle
    )

    private fun partida(parte: String, alias: String? = null, id: Int = 1, cantidad: Double = 10.0, unidad: String? = null) =
        PartidaRevisionDto(
            IdRevdet = id, Cnofact = "FACT-1", CnoParte = parte, cDescripcion = null, Marca = null, Modelo = null,
            NumSer = null, NumLote = null, nCantFactura = cantidad, nCantOut = null, cPaisOrigen = null,
            CptCveTmateria = null, CptDescTmateria = null, Revisada = false, Obsrevpartida = null,
            Mnotapartida = null, nPeso = null, Cclienteid = null, UnidadFactura = unidad, ParteCte = alias
        )
}
