package com.example.pmmovil.ui.scanner

/**
 * Resultado del analisis de calidad de un frame de la camara.
 *
 * Todas las metricas se calculan en una submuestra del plano Y (luminancia)
 * para que el costo sea despreciable. Se procesan a ~5 fps.
 *
 * @param avgLuminance luminancia promedio normalizada 0..1
 * @param variance dispersion de luminancia normalizada 0..1
 * @param edgeRatio proporcion de pixeles con borde 0..1
 * @param isUniform la imagen es uniforme (pared, cielo)
 * @param isTooDark luminancia muy baja, hace falta flash
 * @param isTooBright luminancia muy alta, imagen quemada
 * @param isBlurry bordes debiles, posible movimiento
 * @param hasContent hay zonas con suficiente varianza/bordes
 * @param contentCenterX centro X del contenido 0..1
 * @param contentCenterY centro Y del contenido 0..1
 * @param contentSize tamano relativo del bounding box del contenido 0..1
 */
data class FrameQuality(
    val avgLuminance: Double,
    val variance: Double,
    val edgeRatio: Double,
    val isUniform: Boolean,
    val isTooDark: Boolean,
    val isTooBright: Boolean,
    val isBlurry: Boolean,
    val hasContent: Boolean,
    val contentCenterX: Float,
    val contentCenterY: Float,
    val contentSize: Float
) {
    companion object {
        val Empty = FrameQuality(
            avgLuminance = 0.5,
            variance = 0.0,
            edgeRatio = 0.0,
            isUniform = true,
            isTooDark = false,
            isTooBright = false,
            isBlurry = false,
            hasContent = false,
            contentCenterX = 0.5f,
            contentCenterY = 0.5f,
            contentSize = 0f
        )
    }
}

/**
 * Estado perceptible del frame para la UI.
 */
enum class FrameState {
    Searching,
    AlmostThere,
    NoContent,
    TooDark,
    TooBright,
    Blurry,
    Sweeping
}

fun FrameQuality.toFrameState(): FrameState = when {
    isTooDark -> FrameState.TooDark
    isTooBright -> FrameState.TooBright
    isBlurry -> FrameState.Blurry
    !hasContent -> FrameState.NoContent
    edgeRatio in 0.02..0.4 && variance > 0.05 -> FrameState.AlmostThere
    else -> FrameState.Searching
}
