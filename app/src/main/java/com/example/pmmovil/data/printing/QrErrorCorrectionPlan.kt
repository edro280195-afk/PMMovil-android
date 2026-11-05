package com.example.pmmovil.data.printing

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Qué niveles de corrección de errores probar, y en qué orden, para un mismo estilo de QR.
 *
 * Por qué existe: el verificador de [StyledQrRenderer] vuelve a leer cada QR con ZXing antes de dibujarlo, y ZXing
 * tiene falsos negativos propios. Medido en la PC con la misma versión de la app (3.5.3): alrededor de 0.25 % de los
 * valores del tipo `01C2636546600000 9-17` generan un QR perfecto (otro lector, OpenCV, lo lee) que ZXing no logra
 * decodificar, y le pasa a cada nivel de corrección por separado, pero NUNCA a los cuatro a la vez (0 de 20 000).
 * Cambiar de nivel cambia la versión y la máscara del QR, y eso basta para que ZXing lo lea.
 *
 * Antes se probaba un solo nivel por estilo; el estilo clásico (cuadrado, sin logo) solo tenía un candidato, y un falso
 * negativo dejaba la etiqueta sin QR. Así salió sin QR la etiqueta 9 de 17 de una entrada.
 */
object QrErrorCorrectionPlan {

    /**
     * @param preferred el nivel que pide el estilo (ver [StyledQrRenderer]).
     * @param hasLogo con logo al centro solo sirve el nivel alto: el logo tapa módulos y los otros niveles no los
     * reconstruirían con holgura aunque el verificador los diera por buenos. Si el logo no pasa, la cadena de
     * degradación del renderizador cae a un QR sin logo, que sí prueba todos los niveles.
     */
    fun levelsToTry(preferred: ErrorCorrectionLevel, hasLogo: Boolean): List<ErrorCorrectionLevel> {
        if (hasLogo) return listOf(ErrorCorrectionLevel.H)
        // Del más parecido al preferido al menos parecido: más corrección primero (agranda poco el QR) y el nivel
        // bajo al final, porque deja menos margen de lectura en la etiqueta impresa.
        val fallback = listOf(
            ErrorCorrectionLevel.Q,
            ErrorCorrectionLevel.H,
            ErrorCorrectionLevel.M,
            ErrorCorrectionLevel.L
        )
        return (listOf(preferred) + fallback).distinct()
    }
}
