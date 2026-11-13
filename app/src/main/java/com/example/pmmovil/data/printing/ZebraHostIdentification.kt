package com.example.pmmovil.data.printing

/**
 * Identificación de la Zebra leída con `~HI` (ZPL II Programming Guide, "Host Identification"): una línea entre
 * STX/ETX con modelo, versión de firmware, puntos por milímetro de la cabeza y memoria, por ejemplo
 * `ZT410-300dpi,V75.20.14Z,12,4096KB`.
 *
 * Sirve de respaldo para conocer los dpi de la cabeza en Zebras que no contestan variables SGD por el puerto 9100.
 */
data class ZebraHostIdentification(
    val model: String,
    val firmware: String,
    /** Puntos por milímetro de la cabeza (6, 8, 12 o 24); null si el campo no vino o no se entendió. */
    val dotsPerMillimeter: Int?
) {

    /** dpi de la cabeza: 6→152, 8→203, 12→300, 24→600. */
    val printheadDpi: Int?
        get() = when (dotsPerMillimeter) {
            6 -> 152
            8 -> 203
            12 -> 300
            24 -> 600
            else -> null
        }
}

object ZebraHostIdentificationParser {

    private val MODEL_DPI = Regex("(\\d{3})\\s*dpi", RegexOption.IGNORE_CASE)

    /** Devuelve null si la respuesta no tiene la forma documentada. */
    fun parse(raw: String): ZebraHostIdentification? {
        val line = raw
            .split('\r', '\n')
            .map { piece -> piece.filter { it.code >= 0x20 }.trim() } // quita STX (0x02) y ETX (0x03)
            .firstOrNull { it.isNotEmpty() }
            ?: return null

        val fields = line.split(',').map(String::trim)
        if (fields.size < 3) return null

        val model = fields[0]
        val firmware = fields[1]
        var dotsPerMillimeter = fields[2].toIntOrNull()

        // Si el campo de puntos por mm no vino, el propio nombre del modelo suele decirlo («ZT410-300dpi»).
        if (dotsPerMillimeter == null) {
            dotsPerMillimeter = MODEL_DPI.find(model)?.groupValues?.get(1)?.toIntOrNull()?.let { dpi ->
                when (dpi) {
                    152 -> 6
                    203 -> 8
                    300 -> 12
                    600 -> 24
                    else -> null
                }
            }
        }

        if (model.isEmpty()) return null
        return ZebraHostIdentification(model, firmware, dotsPerMillimeter)
    }
}
