package com.example.pmmovil.data.printing

/**
 * Forma de los módulos de datos del QR (todo lo que no son los 3 ojos de esquina).
 *
 * El escáner solo distingue "hay tinta" / "no hay tinta" en el centro de cada módulo, así que
 * la forma es libre mientras el centro quede bien cubierto y el contraste se conserve. Aun así,
 * ningún estilo se imprime a ciegas: [StyledQrRenderer] decodifica de vuelta el QR ya dibujado
 * y degrada el estilo si no pasa la lectura.
 */
enum class QrModuleShape(val title: String, val description: String) {
    SQUARE("Cuadrado", "Módulos sólidos clásicos. El más robusto de todos."),
    ROUNDED("Redondeado", "Cuadrados con esquinas suaves, sin perder cuerpo."),
    DOT("Puntos", "Módulos circulares separados, estilo app moderna."),
    FLUID("Fluido", "Los módulos vecinos se funden en formas continuas.")
}

/**
 * Forma de los 3 ojos (finder patterns) de las esquinas. Se distingue el marco exterior (anillo
 * de 7x7) de la bola interior (3x3) porque combinarlos distinto es lo que más cambia el carácter
 * visual del QR sin tocar los datos.
 */
enum class QrEyeShape(val title: String) {
    SQUARE("Cuadrado"),
    ROUNDED("Redondeado"),
    CIRCLE("Círculo"),
    LEAF("Hoja")
}

/** Placa que separa el logo del ruido de módulos que lo rodea. */
enum class QrLogoPlate(val title: String, val description: String) {
    NONE("Sin placa", "El logo se funde con los módulos. Solo para logos muy sólidos."),
    TIGHT("Ajustada", "Rectángulo justo a la medida del logo."),
    ROUNDED("Redondeada", "Rectángulo con esquinas suaves alrededor del logo."),
    CIRCLE("Circular", "Disco blanco centrado. Requiere logos cuadrados o compactos.")
}

/**
 * Todo lo configurable del código QR de una etiqueta. Vive dentro de [ZebraLabelLayout], así que
 * se guarda, se exporta a JSON y participa del undo/redo igual que cualquier otro ajuste.
 */
data class QrStyleOptions(
    val moduleShape: QrModuleShape = QrModuleShape.SQUARE,
    val eyeFrameShape: QrEyeShape = QrEyeShape.SQUARE,
    val eyeBallShape: QrEyeShape = QrEyeShape.SQUARE,
    val logoEnabled: Boolean = false,
    /** Ancho del logo como fracción del lado del QR. El tope real lo impone la verificación de lectura. */
    val logoScale: Float = DEFAULT_LOGO_SCALE,
    val logoPlate: QrLogoPlate = QrLogoPlate.ROUNDED,
    /**
     * Quita los módulos que quedarían debajo del logo en vez de solo taparlos con la placa. Evita
     * que asomen medios módulos en el borde; la corrección de errores los reconstruye igual.
     */
    val clearModulesUnderLogo: Boolean = true,
    /** Margen blanco alrededor del QR, en módulos. El estándar recomienda 4; 1-2 basta impreso. */
    val quietZoneModules: Int = 1
) {
    fun sanitized(): QrStyleOptions = copy(
        logoScale = logoScale.coerceIn(LOGO_SCALE_MIN, LOGO_SCALE_MAX),
        quietZoneModules = quietZoneModules.coerceIn(QUIET_ZONE_MIN, QUIET_ZONE_MAX)
    )

    companion object {
        const val DEFAULT_LOGO_SCALE = 0.24f
        const val LOGO_SCALE_MIN = 0.12f
        const val LOGO_SCALE_MAX = 0.34f
        const val QUIET_ZONE_MIN = 1
        const val QUIET_ZONE_MAX = 4

        /** Preset conservador: idéntico a como se imprimía antes de existir los estilos. */
        fun classic(): QrStyleOptions = QrStyleOptions()

        /** Preset con el que se estrenó el estilo de puntos en caseta. */
        fun dots(): QrStyleOptions = QrStyleOptions(
            moduleShape = QrModuleShape.DOT,
            eyeFrameShape = QrEyeShape.SQUARE,
            eyeBallShape = QrEyeShape.SQUARE
        )

        /** Preset "premium": módulos fundidos y ojos redondeados, el más cercano a un QR de marca. */
        fun premium(): QrStyleOptions = QrStyleOptions(
            moduleShape = QrModuleShape.FLUID,
            eyeFrameShape = QrEyeShape.ROUNDED,
            eyeBallShape = QrEyeShape.ROUNDED
        )
    }
}

/** Presets de estilo de QR que el diseñador ofrece como punto de partida de un toque. */
enum class QrStylePreset(val title: String, val description: String) {
    CLASSIC("Clásico", "Cuadrados sólidos. Máxima robustez de lectura."),
    DOTS("Puntos", "Módulos circulares, ojos cuadrados."),
    SOFT("Suave", "Módulos redondeados y ojos con esquinas suaves."),
    PREMIUM("Premium", "Módulos fundidos y ojos redondeados."),
    ELEGANT("Elegante", "Módulos fundidos con ojos circulares.");

    fun apply(current: QrStyleOptions): QrStyleOptions = when (this) {
        CLASSIC -> current.copy(
            moduleShape = QrModuleShape.SQUARE,
            eyeFrameShape = QrEyeShape.SQUARE,
            eyeBallShape = QrEyeShape.SQUARE
        )
        DOTS -> current.copy(
            moduleShape = QrModuleShape.DOT,
            eyeFrameShape = QrEyeShape.SQUARE,
            eyeBallShape = QrEyeShape.SQUARE
        )
        SOFT -> current.copy(
            moduleShape = QrModuleShape.ROUNDED,
            eyeFrameShape = QrEyeShape.ROUNDED,
            eyeBallShape = QrEyeShape.ROUNDED
        )
        PREMIUM -> current.copy(
            moduleShape = QrModuleShape.FLUID,
            eyeFrameShape = QrEyeShape.ROUNDED,
            eyeBallShape = QrEyeShape.ROUNDED
        )
        ELEGANT -> current.copy(
            moduleShape = QrModuleShape.FLUID,
            eyeFrameShape = QrEyeShape.CIRCLE,
            eyeBallShape = QrEyeShape.CIRCLE
        )
    }

    fun matches(options: QrStyleOptions): Boolean = apply(options) == options
}

/**
 * Resultado de generar un QR: qué estilo terminó imprimiéndose realmente y si hubo que degradarlo
 * para que pasara la verificación de lectura. El diseñador lo muestra en vivo para que nadie
 * imprima un lote entero creyendo que salió con un estilo que en realidad no aguantó.
 */
data class QrRenderReport(
    val requested: QrStyleOptions,
    val applied: QrStyleOptions,
    val downgraded: Boolean,
    val verified: Boolean,
    val moduleCount: Int,
    val moduleSizeDots: Int,
    /** Nivel de corrección con el que se imprimió (`L`, `M`, `Q` o `H`); vacío si no se verificó. */
    val errorCorrection: String = ""
) {
    /** Mensaje corto listo para mostrar junto al lienzo del diseñador. */
    fun statusText(): String = when {
        !verified -> "No se pudo verificar la lectura de este QR"
        downgraded -> "Verificado, pero se ajustó el estilo para garantizar la lectura"
        else -> "Verificado: se decodifica correctamente"
    }
}
