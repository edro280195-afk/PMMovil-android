package com.example.pmmovil.data.ocr

/**
 * La **etiqueta de envío VDA** de Freudenberg-NOK, el tercer formato del cliente (Eduardo la mandó el
 * 19-oct-2026). Es una cuadrícula: cada celda trae el rótulo chico arriba a la izquierda, el valor grande
 * a su derecha y el código de barras debajo.
 *
 * Lo que la distingue de los otros dos formatos del cliente:
 *  - Cada rótulo termina con su **identificador de dato entre paréntesis** — `(N)`, `(P)`, `(Q)`, `(V)`,
 *    `(S)`, `(H)`, `(30S)`, `(D)` — que son los mismos identificadores ANSI MH10 que el PDF417 de la caja
 *    trae concatenados (`+30S…+Q…+H…`). O sea: el rótulo impreso dice sin ambigüedad qué es cada valor.
 *  - El valor queda **a la derecha** del rótulo, no debajo.
 *  - La cantidad trae la unidad pegada: «20000 PC».
 *  - El país viene en **ISO-2** («TH»), no como nombre.
 *
 * Cotejo contra la base (respaldo local, 19-oct-2026): la clave `75370203` existe en 29 partidas, todas de
 * Freudenberg (CL8292), todas con origen `THA` y unidad `CNT`. La partida viva es la **3055981**
 * (cref `01I265089680000`, factura 2656195423, línea 10, 6000 CNT = 600 000 piezas, sin revisar): los
 * 20 000 PC de esta etiqueta son exactamente **1/30 de la partida**. El número de nota de entrega
 * (`81706661`) y el de proveedor (`0000000305`) no existen en ninguna tabla: son datos del proveedor.
 *
 * **Qué traen los códigos, ya confirmado en el Pixel el 19-oct** (prueba con la hoja impresa, sección 3.13 de
 * `docs/ar-qr-y-revision-por-etiquetas.md`): son **Code 39, uno por campo, y cada uno lleva su identificador
 * adentro** — `N` entrega, `P` parte del cliente, `Q` cantidad, `V` proveedor, `S` empaque, `H` lote,
 * `30S` referencia, `D` fecha. Es [codigosReales]. La variante sin identificador ([codigosSinIdentificador])
 * se conserva solo como contraste: así es la bolsa de Taiwán ([BolsaFreudenbergFixture]).
 */
object EnvioVdaFreudenbergFixture {

    /** Los valores impresos, tal como se leen en la foto. */
    const val NOTA_ENTREGA = "81706661"
    const val PARTE_CLIENTE = "000000000075370203"
    const val CLAVE = "75370203"
    const val CANTIDAD = "20000"
    const val PROVEEDOR = "0000000305"
    const val EMPAQUE = "279544448"
    const val LOTE = "5004029781"
    const val FECHA_EMBARQUE = "261012"
    const val PAIS = "TH"
    const val DESCRIPCION = "OR 0.296 0.139\" 70 NBR S1"

    /** Los ocho Code 39 de la etiqueta, cada uno con su identificador MH10 adentro. Confirmado en el Pixel. */
    val codigosReales = listOf(
        "N$NOTA_ENTREGA", "P$PARTE_CLIENTE", "Q$CANTIDAD", "V$PROVEEDOR",
        "S$EMPAQUE", "H$LOTE", "30S$CLAVE", "D$FECHA_EMBARQUE"
    )

    /** Los mismos valores sin identificador, como los entrega la bolsa de Taiwán. Solo para contrastar. */
    val codigosSinIdentificador = listOf(
        NOTA_ENTREGA, PARTE_CLIENTE, CANTIDAD, PROVEEDOR, EMPAQUE, LOTE, CLAVE, FECHA_EMBARQUE
    )

    /**
     * Lo que el **video** llegó a decodificar mal en la prueba del Pixel (640 × 480): el Code 39 se equivoca en
     * los caracteres repetidos. Sirve para exigir que un código se acepte solo si dos cuadros coinciden.
     */
    val lecturasMalasDelVideo = listOf(
        "S27954448", "S2795444448", "Q2000000", "V000000305", "V0000305", "FGS75370203",
        "TLN7BBO", "TTLTLTG", "J.000"
    )

    private fun linea(texto: String, x0: Float, y0: Float, x1: Float, y1: Float) =
        LineaDeTexto(
            texto,
            listOf(
                EsquinaDeTexto(x0, y0), EsquinaDeTexto(x1, y0),
                EsquinaDeTexto(x1, y1), EsquinaDeTexto(x0, y1)
            )
        )

    /**
     * Los renglones con la geometría de la foto (1316 × 857), **con el rótulo y su valor separados**: es como
     * los entrega un lector que respeta el tamaño de letra, porque el rótulo es chico y el valor es grande.
     */
    val lineasSeparadas: List<LineaDeTexto> = listOf(
        linea("SHIP-TO PARTY", 36f, 32f, 196f, 52f),
        linea("Freudenberg - NOK Sealing Technologies de Mexico, S.", 36f, 82f, 775f, 112f),
        linea("Av de las Misiones 1 Int.", 36f, 124f, 310f, 152f),
        linea("76246 EL MARQUES, QUERETARO", 330f, 128f, 775f, 150f),
        linea("UNLOADING POINT STORAGE LOCATION", 800f, 34f, 1180f, 54f),
        linea("Freudenberg - NOK", 800f, 86f, 1158f, 122f),

        linea("DELIVERY NOTE NUMBER (N)", 36f, 196f, 336f, 216f),
        linea(NOTA_ENTREGA, 530f, 192f, 722f, 228f),
        linea("VENDOR ADDRESS", 800f, 192f, 960f, 212f),
        linea("Freudenberg - NOK General Partnership", 803f, 230f, 1258f, 254f),
        linea("NET WT KG", 810f, 272f, 918f, 292f),
        linea("8", 852f, 298f, 870f, 322f),
        linea("GROSS WT KG", 975f, 272f, 1110f, 292f),
        linea("9", 1030f, 298f, 1048f, 322f),
        linea("NO OF BOXES", 1130f, 272f, 1262f, 292f),
        linea("0", 1196f, 298f, 1214f, 322f),

        linea("PART NUMBER CUSTOMER (P)", 36f, 342f, 348f, 362f),
        linea(PARTE_CLIENTE, 520f, 338f, 896f, 374f),
        linea("COUNTRY OF", 1130f, 366f, 1246f, 386f),
        linea("ORIGIN", 1130f, 390f, 1200f, 410f),
        linea(PAIS, 1172f, 414f, 1220f, 448f),

        linea("QUANTITY (Q)", 36f, 468f, 180f, 488f),
        linea("$CANTIDAD PC", 528f, 474f, 690f, 510f),
        linea("DESCRIPTION", 718f, 470f, 848f, 490f),
        linea(DESCRIPCION, 718f, 494f, 1120f, 524f),
        linea("SUPPLIER REF. NUMBER (30S)", 718f, 550f, 1010f, 570f),
        linea(CLAVE, 1085f, 546f, 1222f, 578f),

        linea("VENDOR NUMBER (V)", 36f, 602f, 248f, 622f),
        linea(PROVEEDOR, 450f, 606f, 678f, 642f),
        linea("SHIP DATE", 718f, 668f, 820f, 688f),
        linea("D $FECHA_EMBARQUE", 718f, 692f, 870f, 718f),
        linea("MFG DATE", 920f, 668f, 1020f, 688f),
        linea("260624", 920f, 692f, 1048f, 718f),
        linea("ENG. CHANGE", 1130f, 668f, 1262f, 688f),

        linea("PACKAGE NO. (S)", 36f, 738f, 212f, 758f),
        linea(EMPAQUE, 435f, 740f, 650f, 776f),
        linea("BATCH NO. (H)", 718f, 740f, 872f, 760f),
        linea(LOTE, 1010f, 742f, 1198f, 778f)
    )

    /**
     * La misma etiqueta cuando el lector pega cada rótulo con su valor en un solo renglón, porque están a la
     * misma altura. Es el otro desenlace posible y conviene que las dos lecturas den lo mismo.
     */
    val lineasPegadas: List<LineaDeTexto> = listOf(
        linea("DELIVERY NOTE NUMBER (N) $NOTA_ENTREGA", 36f, 192f, 722f, 228f),
        linea("PART NUMBER CUSTOMER (P) $PARTE_CLIENTE", 36f, 338f, 896f, 374f),
        linea("QUANTITY (Q) $CANTIDAD PC", 36f, 468f, 690f, 510f),
        linea("VENDOR NUMBER (V) $PROVEEDOR", 36f, 602f, 678f, 642f),
        linea("PACKAGE NO. (S) $EMPAQUE", 36f, 738f, 650f, 776f),
        linea("SUPPLIER REF. NUMBER (30S) $CLAVE", 718f, 546f, 1222f, 578f),
        linea("BATCH NO. (H) $LOTE", 718f, 740f, 1198f, 778f),
        linea("SHIP DATE D $FECHA_EMBARQUE", 718f, 668f, 870f, 718f),
        linea("DESCRIPTION $DESCRIPCION", 718f, 470f, 1120f, 524f),
        linea("COUNTRY OF ORIGIN", 1130f, 366f, 1246f, 410f),
        linea(PAIS, 1172f, 414f, 1220f, 448f)
    )

    /** El texto plano que recibe [LabelFieldExtractor], en el orden en que vienen los renglones. */
    fun texto(lineas: List<LineaDeTexto>): String = lineas.joinToString("\n") { it.texto }
}
