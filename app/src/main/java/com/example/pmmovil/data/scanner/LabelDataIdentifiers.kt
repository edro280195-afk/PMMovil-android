package com.example.pmmovil.data.scanner

import java.util.Locale

/**
 * Lee los **identificadores de dato** (ANSI MH10.8.2, los que usa la norma VDA 4902 de la industria
 * automotriz) vengan como vengan en la etiqueta:
 *
 *  - **Todos en un código**, pegados con `+`, como el PDF417 del compuesto químico de Freudenberg:
 *    `+30S0049379668+Q0073.500+H2612012010+2D10102027+2Y565310102026+P`
 *  - **Uno por código**, cada Code 39 con su identificador adentro, como la etiqueta de envío de
 *    Freudenberg-NOK: `P000000000075370203`, `Q20000`, `H5004029781`, `30S75370203`, `S279544448`…
 *
 * Son el mismo diccionario, así que un solo lector sirve para los dos y para cualquier proveedor que
 * siga la norma — que es medio sector automotriz — sin programar «el formato de tal cliente».
 *
 * **Por qué es conservador con los códigos sueltos:** un valor cualquiera que empiece con la letra de
 * un identificador se leería mal. Un lote `S12345` parecería un número de empaque. Por eso los códigos
 * sueltos solo se interpretan cuando **al menos [MINIMO_IDENTIFICADORES] de ellos** traen un
 * identificador conocido con un valor de forma válida: una etiqueta de este tipo trae seis u ocho, y
 * una etiqueta de valores sueltos (la bolsa de Taiwán) no llega a tres ni por casualidad. Quien llama
 * debe además comprobar que la clave leída sea la de una partida de la entrada, que es la red de
 * seguridad de verdad.
 */
object LabelDataIdentifiers {

    /** Con menos identificadores que esto, los códigos sueltos se dejan en paz. */
    const val MINIMO_IDENTIFICADORES = 3

    enum class Dato {
        /** `P`: número de parte del cliente. En Freudenberg viene relleno de ceros. */
        PARTE_CLIENTE,

        /** `30S` / `1P`: número de artículo del proveedor. */
        ARTICULO,

        /** `Q`: cantidad. La unidad no viaja en el código; se lee del renglón impreso. */
        CANTIDAD,

        /** `H`: lote. */
        LOTE,

        /** `S`: identificador del empaque, único por bulto. */
        ID_EMPAQUE,

        /** `V`: número del proveedor. */
        PROVEEDOR,

        /** `N`: número de la nota de entrega. */
        NOTA_ENTREGA,

        /** `2D`: fecha larga (caducidad o consumo preferente). */
        FECHA,

        /** `D`: fecha de embarque. */
        FECHA_EMBARQUE,

        /** `2X` / `2Y`: número de contrato. */
        CONTRATO
    }

    /**
     * Del identificador más largo al más corto, para que `30S` no se lea como `3` + resto y `2D` no se
     * lea como `2`. `2Y` existe porque el compuesto químico lo usa donde la caja usa `2X`: es el mismo
     * contrato con otra letra, y sin él ese segmento se perdía en silencio.
     */
    private val DICCIONARIO: List<Pair<String, Dato>> = listOf(
        "30S" to Dato.ARTICULO,
        "1P" to Dato.ARTICULO,
        "2D" to Dato.FECHA,
        "2X" to Dato.CONTRATO,
        "2Y" to Dato.CONTRATO,
        "P" to Dato.PARTE_CLIENTE,
        "Q" to Dato.CANTIDAD,
        "H" to Dato.LOTE,
        "S" to Dato.ID_EMPAQUE,
        "V" to Dato.PROVEEDOR,
        "N" to Dato.NOTA_ENTREGA,
        "D" to Dato.FECHA_EMBARQUE
    ).sortedByDescending { (identificador, _) -> identificador.length }

    data class Lectura(
        val valores: Map<Dato, String> = emptyMap(),
        /** Cuántos identificadores distintos se reconocieron. Sirve para medir la confianza. */
        val reconocidos: Int = 0,
        /** Cómo venía la etiqueta, para poder explicarlo en pantalla y en la bitácora. */
        val forma: Forma = Forma.NINGUNA
    ) {
        operator fun get(dato: Dato): String? = valores[dato]

        /** El número de parte: el del cliente si viene, si no el del proveedor. */
        val clave: String? get() = valores[Dato.PARTE_CLIENTE] ?: valores[Dato.ARTICULO]

        val vacia: Boolean get() = valores.isEmpty()

        companion object {
            val VACIA = Lectura()
        }
    }

    enum class Forma {
        NINGUNA,

        /** Un solo código con todos los campos pegados con `+`. */
        CONCATENADA,

        /** Un código por campo, cada uno con su identificador adentro. */
        POR_CAMPO
    }

    /**
     * Lee todos los códigos de una etiqueta. Primero intenta la forma concatenada, que es inequívoca
     * (el `+` al principio no deja lugar a dudas); si no la encuentra, prueba los códigos sueltos.
     */
    fun leer(codigos: List<String>): Lectura {
        val limpios = codigos
            .map(BarcodeValueNormalizer::normalizeLabel)
            .filter(String::isNotBlank)
            .distinct()
        if (limpios.isEmpty()) return Lectura.VACIA

        val concatenada = limpios.firstNotNullOfOrNull(::leerConcatenado)
        if (concatenada != null) return concatenada

        return leerPorCampo(limpios)
    }

    /**
     * Un solo código con los campos pegados: `+30S…+Q…+H…`. Devuelve null si no tiene esa forma o si
     * ningún segmento trae un identificador conocido.
     */
    fun leerConcatenado(codigo: String): Lectura? {
        val texto = BarcodeValueNormalizer.normalizeLabel(codigo)
        if (!texto.startsWith("+")) return null

        val segmentos = texto.split("+").filter(String::isNotBlank)
        if (segmentos.size < 2) return null

        val valores = mutableMapOf<Dato, String>()
        for (segmento in segmentos) {
            val (dato, valor) = separar(segmento) ?: continue
            // Dos segmentos que dicen cosas distintas del mismo campo: la etiqueta no se entiende y es
            // preferible no leerla a escribir el valor equivocado.
            val previo = valores[dato]
            if (previo != null && previo != valor) return null
            valores[dato] = valor
        }

        if (valores.isEmpty()) return null
        return Lectura(valores, valores.size, Forma.CONCATENADA)
    }

    /**
     * Varios códigos, cada uno con su identificador adentro. Solo se toma en serio con al menos
     * [MINIMO_IDENTIFICADORES] reconocidos; con menos devuelve vacío para que quien llama siga por el
     * camino de siempre.
     */
    fun leerPorCampo(codigos: List<String>): Lectura {
        val valores = mutableMapOf<Dato, String>()
        val contradictorios = mutableSetOf<Dato>()

        for (codigo in codigos) {
            if (codigo.startsWith("+")) continue
            val (dato, valor) = separar(codigo) ?: continue
            val previo = valores[dato]
            if (previo != null && previo != valor) {
                // Dos códigos distintos para el mismo campo: puede haber dos etiquetas en el cuadro.
                contradictorios += dato
                continue
            }
            valores[dato] = valor
        }

        contradictorios.forEach(valores::remove)
        if (valores.size < MINIMO_IDENTIFICADORES) return Lectura.VACIA
        return Lectura(valores, valores.size, Forma.POR_CAMPO)
    }

    /** Parte «30S0049379668» en ARTICULO + «0049379668», o null si no empieza con uno conocido. */
    private fun separar(segmento: String): Pair<Dato, String>? {
        val texto = segmento.trim()
        if (texto.isEmpty()) return null
        val mayusculas = texto.uppercase(Locale.ROOT)

        val (identificador, dato) = DICCIONARIO.firstOrNull { (identificador, _) ->
            mayusculas.startsWith(identificador)
        } ?: return null

        val valor = texto.substring(identificador.length).trim()
        if (valor.isEmpty()) return null
        return if (valido(dato, valor)) dato to valor else null
    }

    /**
     * Qué forma debe tener el valor de cada campo. Sin esto, un `SIN DATO` impreso se leería como
     * número de empaque «IN DATO».
     */
    private fun valido(dato: Dato, valor: String): Boolean = when (dato) {
        Dato.CANTIDAD ->
            valor.length <= 14 && valor.all { it.isDigit() || it == '.' } && valor.any(Char::isDigit) &&
                valor.toDoubleOrNull()?.let { it.isFinite() && it >= 0 } == true

        Dato.FECHA, Dato.FECHA_EMBARQUE ->
            valor.length in 4..8 && valor.all(Char::isDigit)

        Dato.ID_EMPAQUE, Dato.PROVEEDOR, Dato.NOTA_ENTREGA ->
            valor.length in 3..30 && valor.all { it.isLetterOrDigit() || it == '-' } && valor.any(Char::isDigit)

        Dato.PARTE_CLIENTE, Dato.ARTICULO, Dato.LOTE ->
            valor.length in 3..40 && valor.all { it.isLetterOrDigit() || it == '-' || it == '/' || it == '.' } &&
                valor.any(Char::isDigit)

        Dato.CONTRATO ->
            valor.length in 4..30 && valor.all { it.isLetterOrDigit() || it == '-' || it == '/' }
    }

    /**
     * «0049379668» y «49379668» son la misma clave: los ceros son relleno del proveedor y la base no
     * los guarda. Un valor que no sea solo dígitos se deja tal cual.
     */
    fun sinCerosALaIzquierda(valor: String): String =
        if (valor.length > 1 && valor.all { it in '0'..'9' }) valor.trimStart('0').ifEmpty { "0" } else valor
}
