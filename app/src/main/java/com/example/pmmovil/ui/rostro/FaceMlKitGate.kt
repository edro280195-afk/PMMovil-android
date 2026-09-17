package com.example.pmmovil.ui.rostro

import android.graphics.Rect

/** Resultado rápido del filtro de rostro previo a YuNet/SFace. */
enum class FaceGateStatus {
    READY,
    NO_FACE,
    MULTIPLE_FACES,
    TOO_SMALL,
    TOO_LARGE,
    OUT_OF_FRAME,
    ERROR
}

/** Marco facial expresado en las coordenadas del cuadro ya orientado. */
data class FaceGateBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    companion object {
        fun from(rect: Rect): FaceGateBounds = FaceGateBounds(
            left = rect.left,
            top = rect.top,
            right = rect.right,
            bottom = rect.bottom
        )
    }
}

/** Datos mínimos que necesitamos del detector, aislados de la clase Face de ML Kit. */
data class FaceGateDetection(
    val bounds: FaceGateBounds,
    val pitch: Float,
    val yaw: Float,
    val roll: Float
)

data class FaceGateResult(
    val status: FaceGateStatus,
    val bounds: FaceGateBounds? = null,
    val pitch: Float = 0f,
    val yaw: Float = 0f,
    val roll: Float = 0f,
    val elapsedMs: Int = 0,
    val detail: String? = null
)

/** Cuadro que llegó al ViewModel después de pasar el filtro rápido. */
data class RostroFrame(
    val bitmap: android.graphics.Bitmap?,
    val gate: FaceGateResult,
    /**
     * Convierte el cuadro sólo si el consumidor decidió que vale la pena
     * ejecutar YuNet/SFace. Debe llamarse mientras ImageProxy sigue abierto.
     */
    val bitmapProvider: (() -> android.graphics.Bitmap?)? = null
)

/**
 * Reglas geométricas rápidas y deterministas para no gastar SFace en cuadros
 * que no pueden servir. El detector ML Kit ya decide cuántas caras hay; aquí
 * sólo usamos sus marcos y no intentamos reemplazar el embedding existente.
 */
object FaceMlKitGate {
    const val MIN_FACE_WIDTH_PX = 110

    /**
     * El analisis corre a 640 px de ancho, asi que 430 obligaba a dejar la cara
     * por debajo del 67 % del cuadro: con la tableta en la mano eso es estirar
     * el brazo hasta que incomoda. A 560 (87 %) la cara puede llenar el circulo
     * como en Face ID y a SFace le sobran pixeles.
     */
    const val MAX_FACE_WIDTH_PX = 560

    private const val MAX_CENTER_DISTANCE_X = 0.26f
    private const val MAX_CENTER_DISTANCE_Y = 0.30f

    fun evaluate(
        detections: List<FaceGateDetection>,
        imageWidth: Int,
        imageHeight: Int,
        elapsedMs: Int = 0
    ): FaceGateResult {
        if (detections.isEmpty()) {
            return FaceGateResult(FaceGateStatus.NO_FACE, elapsedMs = elapsedMs)
        }
        if (detections.size > 1) {
            return FaceGateResult(FaceGateStatus.MULTIPLE_FACES, elapsedMs = elapsedMs)
        }

        val detection = detections.first()
        val bounds = detection.bounds
        val width = imageWidth.coerceAtLeast(1)
        val height = imageHeight.coerceAtLeast(1)
        val normalizedCenterX = bounds.centerX / width
        val normalizedCenterY = bounds.centerY / height

        val base = FaceGateResult(
            status = FaceGateStatus.READY,
            bounds = bounds,
            pitch = detection.pitch,
            yaw = detection.yaw,
            roll = detection.roll,
            elapsedMs = elapsedMs
        )

        return when {
            bounds.width < MIN_FACE_WIDTH_PX -> base.copy(
                status = FaceGateStatus.TOO_SMALL,
                detail = "Acércate un poco"
            )
            bounds.width > MAX_FACE_WIDTH_PX -> base.copy(
                status = FaceGateStatus.TOO_LARGE,
                detail = "Aléjate un poco"
            )
            kotlin.math.abs(normalizedCenterX - 0.5f) > MAX_CENTER_DISTANCE_X ||
                kotlin.math.abs(normalizedCenterY - 0.5f) > MAX_CENTER_DISTANCE_Y -> base.copy(
                status = FaceGateStatus.OUT_OF_FRAME,
                detail = "Mantén el rostro dentro del círculo"
            )
            else -> base
        }
    }
}
