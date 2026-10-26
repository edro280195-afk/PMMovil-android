package com.example.pmmovil.data.scanner

/**
 * Formas de los códigos que PMM imprime, ya normalizados (mayúsculas, sin controles ni espacios de más).
 *
 * Están aquí, y no dentro de cada pantalla, porque el mismo QR lo leen varias pantallas y el AR: antes cada una
 * decidía por su cuenta qué aceptaba, y el AR tomaba una caja o una relación por una referencia de entrada.
 * Las longitudes no se cruzan (relación 11, referencia de entrada 15, caja 10 u 11 con guion y diagonal), así que
 * se pueden distinguir sin consultar al servidor.
 */
object LabelCodeShapes {
    /** Prefijo del QR de pre-registro de un camión sin caja. */
    const val PRE_REGISTRO_PREFIX = "PMMPR|"

    /** Caja de caseta: el `casetaId` que imprime la etiqueta de caseta, p. ej. `E-017846/26`. */
    val caja = Regex("^E-\\d{3,6}/\\d{2}$")

    /**
     * Relación de salida: 11 caracteres, dos dígitos, una letra y ocho dígitos (`01I26104546`).
     * El tercero decide la tabla: `C` va a `relacion_imex`, cualquier otro a `relacion`.
     */
    val relacion = Regex("^\\d{2}[IEC]\\d{8}$")

    /** Referencia de entrada de 15 caracteres, la que imprime Entrada Bodega (`01C263582800000`). */
    val entrada = Regex("^\\d{2}[A-Z0-9]{13}$")

    /**
     * Cualquier referencia con la forma que acepta el AR. No tiene longitud fija (hay referencias viejas de 11 y 12
     * letras), así que no se recorta: la última palabra la tiene el servidor.
     */
    val referenciaGenerica = Regex("^[A-Z0-9][A-Z0-9_/-]{2,29}$")

    /** Etiqueta de bulto: la referencia más ` posición-total`, p. ej. `01C263582800000 3-12`. */
    val conPosicion = Regex("^(\\S+)\\s+(\\d{1,6})-(\\d{1,6})$")

    /** El QR del gafete trae `usuario;clave`: lleva una credencial y jamás se consulta, se guarda ni se registra. */
    fun esGafete(normalizado: String): Boolean = ';' in normalizado

    /** Dice si la lectura es de otro tipo de código que PMM usa y que no es una entrada. */
    fun noEsEntrada(normalizado: String): Boolean =
        esGafete(normalizado) ||
            normalizado.startsWith(PRE_REGISTRO_PREFIX) ||
            caja.matches(normalizado) ||
            relacion.matches(normalizado)
}
