package com.example.pmmovil.data.printing

import com.example.pmmovil.R

/**
 * Medidas en puntos del raster de cada etiqueta. La plantilla de entrada bodega
 * se lee horizontal (1800 x 1200) y la de caseta conserva su orientación física.
 */
data class ZebraLabelLayout(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val widthDots: Int = READING_WIDTH_DOTS,
    val heightDots: Int = READING_HEIGHT_DOTS,
    val rotate: Boolean = true,
    val logo: ZebraLabelElement = ZebraLabelElement(x = 65, y = 48, width = 460, height = 188),
    /** Estilo completo del código QR: forma de módulos y ojos, logo al centro, zona de silencio. */
    val qrStyle: QrStyleOptions = QrStyleOptions(),
    val qr: ZebraLabelElement = ZebraLabelElement(x = 1_180, y = 48, width = 520, height = 520, fontHeight = 24),
    val arrivalLabel: ZebraLabelElement = ZebraLabelElement(x = 65, y = 270, width = 290, height = 58, fontHeight = 44, fontWidth = 40),
    val arrivalDate: ZebraLabelElement = ZebraLabelElement(x = 390, y = 270, width = 390, height = 58, fontHeight = 48, fontWidth = 44),
    val reference: ZebraLabelElement = ZebraLabelElement(x = 65, y = 365, width = 900, height = 270, fontHeight = 250, fontWidth = 150),
    val customerLabel: ZebraLabelElement = ZebraLabelElement(x = 65, y = 735, width = 280, height = 58, fontHeight = 48, fontWidth = 42),
    val customerValue: ZebraLabelElement = ZebraLabelElement(x = 400, y = 735, width = 1_300, height = 58, fontHeight = 40, fontWidth = 34),
    val boxLabel: ZebraLabelElement = ZebraLabelElement(x = 65, y = 870, width = 220, height = 58, fontHeight = 48, fontWidth = 42),
    val boxValue: ZebraLabelElement = ZebraLabelElement(x = 300, y = 870, width = 520, height = 58, fontHeight = 44, fontWidth = 38),
    val guideLabel: ZebraLabelElement = ZebraLabelElement(x = 940, y = 870, width = 220, height = 58, fontHeight = 48, fontWidth = 42),
    val guideValue: ZebraLabelElement = ZebraLabelElement(x = 1_200, y = 870, width = 500, height = 58, fontHeight = 44, fontWidth = 38),
    val locationLabel: ZebraLabelElement = ZebraLabelElement(x = 940, y = 995, width = 250, height = 58, fontHeight = 48, fontWidth = 42),
    val locationValue: ZebraLabelElement = ZebraLabelElement(x = 1_200, y = 995, width = 250, height = 58, fontHeight = 44, fontWidth = 38),
    val counter: ZebraLabelElement = ZebraLabelElement(
        x = 1_190,
        y = 950,
        width = 560,
        height = 220,
        fontHeight = 220,
        fontWidth = 210,
        alignment = LabelAlignment.RIGHT
    ),
    val customTexts: List<ZebraCustomText> = emptyList(),
    val customImages: List<ZebraCustomImage> = emptyList()
) {
    fun element(id: ZebraLabelElementId): ZebraLabelElement = when (id) {
        ZebraLabelElementId.LOGO -> logo
        ZebraLabelElementId.QR -> qr
        ZebraLabelElementId.ARRIVAL_LABEL -> arrivalLabel
        ZebraLabelElementId.ARRIVAL_DATE -> arrivalDate
        ZebraLabelElementId.REFERENCE -> reference
        ZebraLabelElementId.CUSTOMER_LABEL -> customerLabel
        ZebraLabelElementId.CUSTOMER_VALUE -> customerValue
        ZebraLabelElementId.BOX_LABEL -> boxLabel
        ZebraLabelElementId.BOX_VALUE -> boxValue
        ZebraLabelElementId.GUIDE_LABEL -> guideLabel
        ZebraLabelElementId.GUIDE_VALUE -> guideValue
        ZebraLabelElementId.LOCATION_LABEL -> locationLabel
        ZebraLabelElementId.LOCATION_VALUE -> locationValue
        ZebraLabelElementId.COUNTER -> counter
    }

    fun replaceElement(id: ZebraLabelElementId, element: ZebraLabelElement): ZebraLabelLayout = when (id) {
        ZebraLabelElementId.LOGO -> copy(logo = element)
        ZebraLabelElementId.QR -> copy(qr = element)
        ZebraLabelElementId.ARRIVAL_LABEL -> copy(arrivalLabel = element)
        ZebraLabelElementId.ARRIVAL_DATE -> copy(arrivalDate = element)
        ZebraLabelElementId.REFERENCE -> copy(reference = element)
        ZebraLabelElementId.CUSTOMER_LABEL -> copy(customerLabel = element)
        ZebraLabelElementId.CUSTOMER_VALUE -> copy(customerValue = element)
        ZebraLabelElementId.BOX_LABEL -> copy(boxLabel = element)
        ZebraLabelElementId.BOX_VALUE -> copy(boxValue = element)
        ZebraLabelElementId.GUIDE_LABEL -> copy(guideLabel = element)
        ZebraLabelElementId.GUIDE_VALUE -> copy(guideValue = element)
        ZebraLabelElementId.LOCATION_LABEL -> copy(locationLabel = element)
        ZebraLabelElementId.LOCATION_VALUE -> copy(locationValue = element)
        ZebraLabelElementId.COUNTER -> copy(counter = element)
    }

    companion object {
        const val READING_WIDTH_DOTS = 1_800
        const val READING_HEIGHT_DOTS = 1_200
        // v4: qrLogoEnabled (Boolean suelto) pasó a ser el objeto qrStyle con forma de módulos,
        // ojos, placa y zona de silencio. La migración conserva el valor anterior del logo.
        const val CURRENT_SCHEMA_VERSION = 4

        fun reference(): ZebraLabelLayout = ZebraLabelLayout()

        fun compact(): ZebraLabelLayout = ZebraLabelLayout(
            qr = ZebraLabelElement(x = 1_140, y = 40, width = 620, height = 620),
            reference = ZebraLabelElement(x = 65, y = 340, width = 1_000, height = 240, fontHeight = 220, fontWidth = 140),
            counter = ZebraLabelElement(x = 1_140, y = 920, width = 620, height = 240, fontHeight = 240, fontWidth = 230, alignment = LabelAlignment.RIGHT)
        )

        fun giantReference(): ZebraLabelLayout = ZebraLabelLayout(
            reference = ZebraLabelElement(x = 65, y = 300, width = 1_050, height = 340, fontHeight = 300, fontWidth = 180),
            customerValue = ZebraLabelElement(x = 400, y = 735, width = 1_300, height = 70, fontHeight = 48, fontWidth = 40)
        )

        fun customerFocused(): ZebraLabelLayout = ZebraLabelLayout(
            customerLabel = ZebraLabelElement(x = 65, y = 680, width = 280, height = 65, fontHeight = 54, fontWidth = 48, inverted = true),
            customerValue = ZebraLabelElement(x = 360, y = 680, width = 1_380, height = 65, fontHeight = 52, fontWidth = 44, borderWidth = 2)
        )

        fun framedDividers(): ZebraLabelLayout = ZebraLabelLayout(
            arrivalLabel = ZebraLabelElement(x = 65, y = 270, width = 290, height = 58, fontHeight = 44, fontWidth = 40, inverted = true),
            customerLabel = ZebraLabelElement(x = 65, y = 735, width = 280, height = 58, fontHeight = 48, fontWidth = 42, inverted = true),
            boxLabel = ZebraLabelElement(x = 65, y = 870, width = 220, height = 58, fontHeight = 48, fontWidth = 42, inverted = true),
            guideLabel = ZebraLabelElement(x = 940, y = 870, width = 220, height = 58, fontHeight = 48, fontWidth = 42, inverted = true),
            locationLabel = ZebraLabelElement(x = 940, y = 995, width = 250, height = 58, fontHeight = 48, fontWidth = 42, inverted = true)
        )

        fun casetaCompact(): ZebraLabelLayout = ZebraLabelLayout(
            // 2.25" x 1.25" a 300 dpi. La plantilla anterior (457 x 254)
            // dejaba la impresion mas pequena y podia caer entre dos etiquetas.
            widthDots = 675,
            heightDots = 375,
            rotate = false,
            logo = ZebraLabelElement(x = 0, y = 0, width = 0, height = 0, visible = false),
            // El logo de IMEX va al centro del QR (ver qrStyle); esta placa suma un logo libre en
            // la esquina superior derecha, editable/movible desde la pestaña Imágenes.
            qrStyle = QrStyleOptions.premium().copy(logoEnabled = true),
            qr = ZebraLabelElement(x = 20, y = 38, width = 300, height = 300),
            reference = ZebraLabelElement(
                x = 340,
                y = 136,
                width = 315,
                height = 104,
                fontHeight = 76,
                fontWidth = 64,
                alignment = LabelAlignment.CENTER
            ),
            arrivalLabel = ZebraLabelElement(x = 0, y = 0, width = 0, height = 0, visible = false),
            arrivalDate = ZebraLabelElement(x = 0, y = 0, width = 0, height = 0, visible = false),
            customerLabel = ZebraLabelElement(x = 0, y = 0, width = 0, height = 0, visible = false),
            customerValue = ZebraLabelElement(x = 0, y = 0, width = 0, height = 0, visible = false),
            boxLabel = ZebraLabelElement(x = 0, y = 0, width = 0, height = 0, visible = false),
            boxValue = ZebraLabelElement(x = 0, y = 0, width = 0, height = 0, visible = false),
            guideLabel = ZebraLabelElement(x = 0, y = 0, width = 0, height = 0, visible = false),
            guideValue = ZebraLabelElement(x = 0, y = 0, width = 0, height = 0, visible = false),
            locationLabel = ZebraLabelElement(x = 0, y = 0, width = 0, height = 0, visible = false),
            locationValue = ZebraLabelElement(x = 0, y = 0, width = 0, height = 0, visible = false),
            counter = ZebraLabelElement(x = 0, y = 0, width = 0, height = 0, visible = false),
            customTexts = emptyList(),
            customImages = listOf(
                ZebraCustomImage(
                    id = "imex-logo",
                    assetId = LabelImageAsset.IMEX_LOGO.id,
                    element = ZebraLabelElement(x = 420, y = 14, width = 235, height = 96)
                )
            )
        )
    }
}

data class ZebraLabelElement(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val fontHeight: Int = 0,
    val fontWidth: Int = 0,
    val fontFamily: LabelFontFamily = LabelFontFamily.SANS,
    val alignment: LabelAlignment = LabelAlignment.LEFT,
    val visible: Boolean = true,
    val inverted: Boolean = false,
    val borderWidth: Int = 0,
    val rotation: Int = 0,
    /** Default en true: antes de existir este campo TODO se imprimía en negritas salvo
     *  "Sans ligera" (ver migración en ZebraLabelLayoutPreferences para no cambiar etiquetas ya guardadas). */
    val bold: Boolean = true,
    val italic: Boolean = false,
    val underline: Boolean = false,
    /** Tracking en unidades "em" (mismo significado que Paint.letterSpacing): 0 = normal,
     *  negativo = letras más juntas, positivo = más separadas. */
    val letterSpacing: Float = 0f,
    val customText: String? = null
) {
    fun constrained(
        maxWidthDots: Int = ZebraLabelLayout.READING_WIDTH_DOTS,
        maxHeightDots: Int = ZebraLabelLayout.READING_HEIGHT_DOTS
    ): ZebraLabelElement {
        val safeWidth = width.coerceIn(80, maxWidthDots.coerceAtLeast(80))
        val safeHeight = height.coerceIn(32, maxHeightDots.coerceAtLeast(32))
        val safeBorder = borderWidth.coerceIn(0, 20)
        val safeRotation = when (rotation) {
            90 -> 90
            180 -> 180
            270 -> 270
            else -> 0
        }
        return copy(
            x = x.coerceIn(0, maxWidthDots.coerceAtLeast(safeWidth) - safeWidth),
            y = y.coerceIn(0, maxHeightDots.coerceAtLeast(safeHeight) - safeHeight),
            width = safeWidth,
            height = safeHeight,
            fontHeight = fontHeight.coerceIn(0, 300),
            fontWidth = fontWidth.coerceIn(0, 300),
            borderWidth = safeBorder,
            rotation = safeRotation,
            letterSpacing = letterSpacing.coerceIn(LETTER_SPACING_MIN, LETTER_SPACING_MAX)
        )
    }

    companion object {
        const val LETTER_SPACING_MIN = -0.05f
        const val LETTER_SPACING_MAX = 0.40f
    }
}

data class ZebraCustomText(
    val id: String,
    val text: String,
    val element: ZebraLabelElement
)

/** Una imagen libre (p. ej. el logo de IMEX) colocada en la etiqueta, igual que un texto libre. */
data class ZebraCustomImage(
    val id: String,
    val assetId: String,
    val element: ZebraLabelElement
)

/**
 * Imágenes propias del proyecto que se pueden colocar libremente en la etiqueta desde el
 * diseñador visual, igual que los textos libres. Para agregar una imagen nueva al selector
 * basta con sumar la entrada aquí (y el drawable correspondiente en res/drawable).
 */
enum class LabelImageAsset(
    val id: String,
    val title: String,
    val drawableRes: Int,
    /** Ancho / alto original del recurso, para no deformarlo al redimensionar. */
    val aspectRatio: Float
) {
    IMEX_LOGO(id = "imex_logo", title = "Logo IMEX", drawableRes = R.drawable.imex_logo, aspectRatio = 1_866f / 764f);

    companion object {
        fun fromId(id: String?): LabelImageAsset? = values().firstOrNull { it.id == id }
    }
}

enum class ZebraLabelType(
    val title: String,
    val dimensionsText: String
) {
    ENTRADA_BODEGA("Entrada bodega", "4 × 6 in · 1800 × 1200 dots"),
    CASETA("Caseta", "2.25 × 1.25 in · 675 × 375 dots");

    fun defaultLayout(): ZebraLabelLayout = when (this) {
        ENTRADA_BODEGA -> ZebraLabelLayout.reference()
        CASETA -> ZebraLabelLayout.casetaCompact()
    }
}

enum class LabelFontFamily(val title: String, val familyName: String) {
    SANS("Sans serif", "sans-serif"),
    SANS_MEDIUM("Sans medium", "sans-serif-medium"),
    SANS_LIGHT("Sans ligera", "sans-serif-light"),
    SANS_THIN("Sans ultra ligera", "sans-serif-thin"),
    SANS_BLACK("Sans extra negra", "sans-serif-black"),
    SANS_CONDENSED("Sans condensada", "sans-serif-condensed"),
    SERIF("Serif", "serif"),
    MONOSPACE("Monospace", "monospace"),
    // De cosecha propia: dos fuentes decorativas para textos libres tipo "GRACIAS" o sellos,
    // no pensadas para datos operativos (folio, cliente, etc.) por legibilidad a distancia.
    CASUAL("Informal", "casual"),
    CURSIVE("Manuscrita", "cursive")
}

enum class LabelAlignment(val title: String) {
    LEFT("Izquierda"),
    CENTER("Centro"),
    RIGHT("Derecha")
}

enum class ZebraLabelElementId(
    val title: String,
    val jsonKey: String,
    val supportsTypography: Boolean = true,
    val isImage: Boolean = false,
    val isSquare: Boolean = false
) {
    LOGO("Logo", "logo", supportsTypography = false, isImage = true),
    QR("Código QR", "qr", supportsTypography = false, isImage = true, isSquare = true),
    ARRIVAL_LABEL("Etiqueta de fecha", "arrivalLabel"),
    ARRIVAL_DATE("Fecha de arribo", "arrivalDate"),
    REFERENCE("Folio de entrada", "reference"),
    CUSTOMER_LABEL("Etiqueta cliente", "customerLabel"),
    CUSTOMER_VALUE("Cliente", "customerValue"),
    BOX_LABEL("Etiqueta caja", "boxLabel"),
    BOX_VALUE("Caja", "boxValue"),
    GUIDE_LABEL("Etiqueta guía", "guideLabel"),
    GUIDE_VALUE("Guía", "guideValue"),
    LOCATION_LABEL("Etiqueta ubicación", "locationLabel"),
    LOCATION_VALUE("Ubicación", "locationValue"),
    COUNTER("Contador", "counter")
}
