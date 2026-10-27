package com.example.pmmovil.data.scanner

/** Qué tipo de pieza fue el último escaneo. Solo los que tienen ficha y no guardan ningún secreto. */
enum class LastScanKind { Caja, Relacion, Carpeta, Bulto }

/**
 * Lo único que se recuerda de un escaneo: qué código era, de quién y cuándo. La señal y los demás datos de la ficha NO se
 * guardan: cambian en minutos y se piden de nuevo cada vez que se muestra.
 */
data class LastScan(
    val code: String,
    val kind: LastScanKind,
    val user: String,
    val scannedAtMillis: Long
)

/** Hace cuánto fue el escaneo, en el redondeo que se le dice al usuario. */
sealed interface LastScanElapsed {
    /** Menos de un minuto. */
    data object JustNow : LastScanElapsed

    data class Minutes(val count: Int) : LastScanElapsed

    data class Hours(val count: Int) : LastScanElapsed
}

/**
 * Reglas del «Último escaneo» del inicio. Son puras (sin Android) para poder probarlas.
 *
 * Lo que NO se guarda nunca es tan importante como lo que sí: el gafete lleva una credencial, el pre-registro lleva un
 * token, y una etiqueta de producto o una referencia dudosa no es algo a lo que se quiera volver.
 */
object LastScanRules {
    /** Un turno. Pasado esto el escaneo ya no se enseña: nadie quiere volver a lo de ayer. */
    const val VALID_FOR_MILLIS = 12L * 60 * 60 * 1000

    private const val MINUTE_MILLIS = 60_000L
    private const val HOUR_MILLIS = 60 * MINUTE_MILLIS

    /** El recuerdo que deja leer [code], o `null` si esa lectura no debe dejar ninguno. */
    fun remember(code: ScannedCode, user: String?, nowMillis: Long): LastScan? {
        val owner = user?.trim().orEmpty()
        if (owner.isEmpty()) return null
        return when (code) {
            is ScannedCode.Caja -> LastScan(code.normalized, LastScanKind.Caja, owner, nowMillis)
            is ScannedCode.Relacion -> LastScan(code.normalized, LastScanKind.Relacion, owner, nowMillis)
            is ScannedCode.Carpeta -> LastScan(code.reference, LastScanKind.Carpeta, owner, nowMillis)
            is ScannedCode.Bulto -> LastScan(code.normalized, LastScanKind.Bulto, owner, nowMillis)

            is ScannedCode.PreRegistro,
            ScannedCode.Gafete,
            is ScannedCode.ProductoCliente,
            is ScannedCode.Candidato,
            is ScannedCode.Desconocido -> null
        }
    }

    /**
     * Lo guardado, solo si es de [user] (una tableta se comparte entre turnos) y todavía vale. Un reloj que va hacia atrás
     * no deja el escaneo «vigente» para siempre.
     */
    fun visibleTo(scan: LastScan?, user: String?, nowMillis: Long): LastScan? {
        if (scan == null) return null
        if (user.isNullOrBlank() || !scan.user.equals(user.trim(), ignoreCase = true)) return null
        val age = nowMillis - scan.scannedAtMillis
        return scan.takeIf { age in 0..VALID_FOR_MILLIS }
    }

    fun elapsed(scan: LastScan, nowMillis: Long): LastScanElapsed {
        val age = (nowMillis - scan.scannedAtMillis).coerceAtLeast(0)
        return when {
            age < MINUTE_MILLIS -> LastScanElapsed.JustNow
            age < HOUR_MILLIS -> LastScanElapsed.Minutes((age / MINUTE_MILLIS).toInt())
            else -> LastScanElapsed.Hours((age / HOUR_MILLIS).toInt())
        }
    }
}
