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

    /**
     * Orden de carga: el QR de la hoja que se escanea para iniciar una carga. Es la relación, un guion y la versión de
     * la hoja (`01I26106418-1`, impresa como «01-I-26-106418 Versión 1»).
     */
    val ordenDeCarga = Regex("^(\\d{2}[IEC]\\d{8})-(\\d{1,3})$")

    /**
     * Requisición de cruce: el QR de la hoja que se escanea en caseta para dar la salida. Empieza con `R-`, sigue la
     * relación y después trae más campos separados por espacios (`R-01I26106070  0000  101`). Lo único que se busca es la
     * relación: no se interpretan los demás campos.
     */
    val requisicionDeCruce = Regex("^R-(\\d{2}[IEC]\\d{8})(?: .*)?$")

    /**
     * Etiqueta de carpeta de siempre: la referencia de entrada de 15 caracteres seguida de un campo de ancho fijo con el
     * código del cliente (6 posiciones, rellenado con espacios) y un cierre `100`. Con un cliente corto queda un espacio
     * (`01C2635628400001454  100`) y con uno de 6 caracteres no queda ninguno (`01C263605020000CL7480100`). Solo cuenta
     * la entrada: son los primeros 15 caracteres, como los recorta desde siempre `Revision/buscar` en el API.
     *
     * La tercera posición es una letra en todas las entradas que existen (I, C, E, D, K, P, R), y eso evita tomar por
     * carpeta un código GS1, que empieza con dos dígitos y sigue con 14 dígitos. Lo que sigue a la entrada lleva solo
     * letras, dígitos y espacios: con un guion es la posición de un bulto (`… 3-12`), que se lee aparte.
     */
    val carpetaConCliente = Regex("^(\\d{2}[A-Z][A-Z0-9]{12})([A-Z0-9 ]{3,15})$")

    /** El QR del gafete trae `usuario;clave`: lleva una credencial y jamás se consulta, se guarda ni se registra. */
    fun esGafete(normalizado: String): Boolean = ';' in normalizado

    /** Dice si la lectura es de otro tipo de código que PMM usa y que no es una entrada. */
    fun noEsEntrada(normalizado: String): Boolean =
        esGafete(normalizado) ||
            normalizado.startsWith(PRE_REGISTRO_PREFIX) ||
            caja.matches(normalizado) ||
            relacion.matches(normalizado) ||
            // La orden de carga tiene forma de referencia (letras, dígitos y un guion): sin esto el AR la pedía como entrada.
            ordenDeCarga.matches(normalizado) ||
            requisicionDeCruce.matches(normalizado)
}
