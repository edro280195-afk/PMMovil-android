package com.example.pmmovil.data.ocr

import java.util.Locale

/**
 * Comprueba que cada dato leído de un código de barras **aparezca también impreso** en la etiqueta.
 *
 * **Por qué existe.** Un decodificador que se equivoca no avisa: devuelve un valor con la misma
 * confianza que uno bueno. Medido en el Pixel el 20-oct-2026 leyendo la etiqueta de envío VDA de
 * Freudenberg, el código de la cantidad salió `Q20000`, `Q2000000` y `Q20000000` en lecturas distintas
 * de la **misma** etiqueta, donde lo impreso siempre dice 20000. Dos de esas tres veces el valor malo
 * salió igual en la foto y en el video, así que compararlos entre sí no lo detecta.
 *
 * Lo que sí lo detecta es que la etiqueta trae cada dato **dos veces**: dentro del código y escrito al
 * lado, en letra grande. Son lecturas independientes —otro algoritmo, otra zona de la imagen— y es muy
 * improbable que las dos se equivoquen igual. Si el código dice 2 000 000 y en la hoja dice 20 000,
 * la diferencia salta.
 *
 * **Lo que NO hace:** elegir cuál de los dos tiene razón. Con cero margen de error, un dato sin
 * corroborar no se escribe solo; se muestra y lo confirma una persona.
 */
object CorroboracionDeLectura {

    /**
     * ¿El valor leído de un código aparece impreso en [textoDeLaEtiqueta]?
     *
     * Se compara solo con dígitos y letras, porque lo impreso lleva separadores que el código no trae
     * («20000 PC», «5004 029 781»). La comparación es por **número completo**: si el código dice
     * `2000000` y la hoja dice `20000`, no corrobora, y al revés tampoco.
     */
    fun apareceImpreso(valor: String, textoDeLaEtiqueta: String): Boolean {
        val buscado = normalizar(valor)
        if (buscado.length < 3) return false
        val texto = normalizar(textoDeLaEtiqueta)
        if (buscado !in texto) return false

        // Frontera: el valor no puede ser un pedazo de un número más largo. Así «20000» no se da por
        // bueno dentro de «2000000», que es justo el error del decodificador.
        //
        // La frontera se mide solo contra DÍGITOS, no contra letras: al normalizar desaparecen los
        // separadores, así que el rótulo queda pegado al valor («QUANTITYQ20000PC») y exigir que al
        // lado no haya letras rechazaría todo lo que de verdad está impreso.
        var desde = 0
        while (true) {
            val i = texto.indexOf(buscado, desde)
            if (i < 0) return false
            val antes = i == 0 || !texto[i - 1].isDigit()
            val fin = i + buscado.length
            val despues = fin == texto.length || !texto[fin].isDigit()
            if (antes && despues) return true
            desde = i + 1
        }
    }

    /**
     * Lo mismo, admitiendo que lo impreso traiga los ceros de relleno del proveedor y el código no, o
     * al revés: `000000000075370203` y `75370203` son la misma clave.
     */
    fun apareceImpresoSinCeros(valor: String, textoDeLaEtiqueta: String): Boolean {
        if (apareceImpreso(valor, textoDeLaEtiqueta)) return true
        val sinCeros = valor.trimStart('0')
        return sinCeros.length >= 3 && sinCeros != valor && apareceImpreso(sinCeros, textoDeLaEtiqueta)
    }

    /**
     * Corrobora una **cantidad**, comparando por valor numérico y no por cadena.
     *
     * Hace falta porque el código la rellena con ceros y la hoja no: el PDF417 de la caja trae
     * `Q00000600` y debajo dice `600`; el del compuesto químico trae `Q0073.500` y dice `73.500`.
     * Comparar los dígitos tal cual rechazaría lecturas buenas.
     *
     * Sigue atrapando el error que importa: `2000000` no es igual a `20000` por mucho que se
     * normalicen los ceros.
     */
    fun cantidadApareceImpresa(valor: String, textoDeLaEtiqueta: String): Boolean {
        val buscado = aNumero(valor) ?: return false
        return NUMEROS.findAll(textoDeLaEtiqueta)
            .mapNotNull { aNumero(it.value) }
            .any { it.compareTo(buscado) == 0 }
    }

    /** Números con decimales por punto o por coma, tal como se imprimen. */
    private val NUMEROS = Regex("""\d+(?:[.,]\d+)?""")

    private fun aNumero(texto: String): java.math.BigDecimal? =
        texto.trim().replace(',', '.').toBigDecimalOrNull()?.stripTrailingZeros()

    /** Mayúsculas, solo letras y dígitos: los separadores de lo impreso no cuentan. */
    private fun normalizar(texto: String): String =
        texto.uppercase(Locale.ROOT).filter(Char::isLetterOrDigit)
}
