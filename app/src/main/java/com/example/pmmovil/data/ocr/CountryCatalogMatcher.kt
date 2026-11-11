package com.example.pmmovil.data.ocr

import java.text.Normalizer

/**
 * Convierte lo que dice la etiqueta ("COO: THAILAND", "MADE IN USA", "DEU") en el
 * codigo de 3 letras que espera `revdet.cpaisorigen`.
 *
 * Por que hace falta: el catalogo `paises` esta **en espanol** y con la forma
 * larga -- `TAILANDIA (REINO DE)`, `ALEMANIA (REPUBLICA FEDERAL DE)`,
 * `ESTADOS UNIDOS DE AMERICA` -- mientras que las etiquetas vienen en ingles. Sin
 * traduccion, el unico caso que emparejaria solo seria el de las etiquetas
 * mexicanas. El campo ademas es varchar(3): escribir "THAILAND" lo truncaria.
 *
 * El `calias` del catalogo NO sirve para esto: es varchar(12) y guarda la primera
 * palabra recortada, asi que Reino Unido queda como `REINO` y Estados Unidos como
 * `ESTADOS`.
 *
 * Orden de intentos, del mas seguro al menos:
 *  1. Codigo ISO-3 que ya existe en el catalogo (`DEU`, `THA`).
 *  2. Codigo ISO-2 de la etiqueta (`DE`, `TH`) traducido a ISO-3.
 *  3. Nombre en ingles (`THAILAND`, `GERMANY`, `P.R.C.`).
 *  4. Nombre en espanol contra el propio catalogo (`MEXICO`, `ALEMANIA`).
 *
 * Si nada empareja, o si el texto menciona dos paises distintos, devuelve null.
 * Preferimos no capturar a escribir un pais equivocado en el pedimento.
 */
object CountryCatalogMatcher {

    data class PaisCatalogo(val codigo: String, val nombre: String)

    data class Resultado(
        val codigo: String,
        val nombre: String,
        /** Como se resolvio, para poder mostrarlo en pantalla. */
        val via: Via
    )

    enum class Via { CODIGO_ISO3, CODIGO_ISO2, NOMBRE_INGLES, NOMBRE_CATALOGO }

    fun emparejar(textoEtiqueta: String, catalogo: List<PaisCatalogo>): Resultado? {
        if (catalogo.isEmpty()) return null
        val limpio = limpiar(textoEtiqueta)
        if (limpio.isBlank()) return null

        val porCodigo = catalogo.associateBy { normalizar(it.codigo) }

        // 1. El texto ya es el codigo ISO-3.
        porCodigo[limpio]?.let { return Resultado(it.codigo, it.nombre, Via.CODIGO_ISO3) }

        // 2. Codigo ISO-2 ("DE", "TH", "US").
        if (limpio.length == 2) {
            ISO2_A_ISO3[limpio]?.let { iso3 ->
                porCodigo[iso3]?.let { return Resultado(it.codigo, it.nombre, Via.CODIGO_ISO2) }
            }
        }

        // 3. Nombre en ingles. Se revisa el texto completo y tambien por renglon,
        //    porque el OCR a veces pega "MADE IN" con la linea de abajo.
        emparejarIngles(limpio)?.let { iso3 ->
            porCodigo[iso3]?.let { return Resultado(it.codigo, it.nombre, Via.NOMBRE_INGLES) }
        }

        // 4. Nombre en espanol contra el catalogo vivo. Se compara contra la parte
        //    ANTES del parentesis: asi "TAIWAN (REPUBLICA DE CHINA)" no se confunde
        //    con China, que es justo la colision mas facil de cometer aqui.
        val porNombre = HashMap<String, PaisCatalogo>()
        catalogo.forEach { p ->
            val corto = normalizar(p.nombre.substringBefore('(').trim())
            if (corto.isNotEmpty()) porNombre.putIfAbsent(corto, p)
        }
        porNombre[limpio]?.let { return Resultado(it.codigo, it.nombre, Via.NOMBRE_CATALOGO) }

        return null
    }

    /**
     * Quita las etiquetas que acompanan al pais y la puntuacion, para que
     * "COO: THAILAND", "Made in U.S.A." y "Pais de origen - MEXICO" lleguen igual.
     */
    private fun limpiar(bruto: String): String {
        var t = normalizar(bruto)
        PREFIJOS.forEach { p -> if (t.startsWith(p)) t = t.removePrefix(p).trim() }
        // Quita puntuacion de orilla y la que parte las siglas (U.S.A. -> USA).
        t = t.trim(':', '-', '.', ',', ';', ' ')
        if (t.count { it == '.' } >= 2 && t.length <= 8) t = t.replace(".", "")
        return t.trim()
    }

    private fun emparejarIngles(texto: String): String? {
        INGLES_A_ISO3[texto]?.let { return it }
        // El nombre puede venir con ruido alrededor ("PRODUCT OF THAILAND").
        // Se exige frontera de palabra para no pegar "INDIA" dentro de "INDIANA".
        val encontrados = INGLES_A_ISO3.entries
            .filter { (nombre, _) -> contienePalabraCompleta(texto, nombre) }
            .map { it.value }
            .distinct()
        // Dos paises distintos en la misma linea: no adivinamos.
        return encontrados.singleOrNull()
    }

    private fun contienePalabraCompleta(texto: String, termino: String): Boolean {
        if (termino.length < 4) return false
        var desde = 0
        while (true) {
            val i = texto.indexOf(termino, desde)
            if (i < 0) return false
            val antes = i == 0 || !texto[i - 1].isLetterOrDigit()
            val fin = i + termino.length
            val despues = fin == texto.length || !texto[fin].isLetterOrDigit()
            if (antes && despues) return true
            desde = i + 1
        }
    }

    /** Mayusculas, sin acentos y con espacios colapsados. */
    private fun normalizar(s: String): String =
        Normalizer.normalize(s.trim().uppercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("\\s+"), " ")

    private val PREFIJOS = listOf(
        "COUNTRY OF ORIGIN", "COUNTRY OF MANUFACTURE", "MADE IN", "PRODUCT OF",
        "ORIGIN", "COO", "PAIS DE ORIGEN", "HECHO EN", "FABRICADO EN", "ORIGEN"
    )

    /**
     * Nombres en ingles de los paises de los que de verdad llega mercancia. La
     * lista salio de contar `revdet.cpaisorigen` del ultimo ano: USA 57.8% y
     * China 21.7% son cuatro de cada cinco partidas, y los 22 primeros cubren el
     * 96%. Lo que no este aqui simplemente no se captura solo.
     */
    private val INGLES_A_ISO3: Map<String, String> = mapOf(
        "UNITED STATES" to "USA", "UNITED STATES OF AMERICA" to "USA", "USA" to "USA",
        "US" to "USA", "U S A" to "USA", "AMERICA" to "USA",
        "CHINA" to "CHN", "PRC" to "CHN", "P R C" to "CHN",
        "PEOPLES REPUBLIC OF CHINA" to "CHN", "MAINLAND CHINA" to "CHN",
        "TAIWAN" to "TWN", "ROC" to "TWN", "CHINESE TAIPEI" to "TWN",
        "INDIA" to "IND",
        "ITALY" to "ITA",
        "JAPAN" to "JPN",
        "VIETNAM" to "VNM", "VIET NAM" to "VNM",
        "MEXICO" to "MEX",
        "GERMANY" to "DEU",
        "THAILAND" to "THA",
        "TURKEY" to "TUR", "TURKIYE" to "TUR",
        "BRAZIL" to "BRA",
        "BELGIUM" to "BEL",
        "CANADA" to "CAN",
        "SPAIN" to "ESP",
        "FRANCE" to "FRA",
        "PORTUGAL" to "PRT",
        "CZECH REPUBLIC" to "CZE", "CZECHIA" to "CZE",
        "AUSTRIA" to "AUT",
        "INDONESIA" to "IDN",
        "SWEDEN" to "SWE",
        "UNITED KINGDOM" to "GBR", "GREAT BRITAIN" to "GBR", "ENGLAND" to "GBR",
        "SOUTH KOREA" to "KOR", "REPUBLIC OF KOREA" to "KOR", "KOREA" to "KOR",
        // Países Bajos es ZYA en el catálogo (no NLD, que es el ISO).
        "NETHERLANDS" to "ZYA", "HOLLAND" to "ZYA",
        "SWITZERLAND" to "CHE",
        "POLAND" to "POL",
        "MALAYSIA" to "MYS",
        "SINGAPORE" to "SGP",
        "PHILIPPINES" to "PHL",
        "ISRAEL" to "ISR",
        "AUSTRALIA" to "AUS",
        "DENMARK" to "DNK",
        "FINLAND" to "FIN",
        "NORWAY" to "NOR",
        "IRELAND" to "IRL",
        "HUNGARY" to "HUN",
        "SLOVAKIA" to "SVK",
        // Rumania es ROM en el catálogo (no ROU, que es el ISO).
        "ROMANIA" to "ROM",
        "ARGENTINA" to "ARG",
        "CHILE" to "CHL",
        "COLOMBIA" to "COL",
        "PERU" to "PER",
        "SOUTH AFRICA" to "ZAF",
        "NEW ZEALAND" to "NZL",
        "HONG KONG" to "HKG",
        "SRI LANKA" to "LKA",
        "BANGLADESH" to "BGD",
        "PAKISTAN" to "PAK",
        "EGYPT" to "EGY",
        "SAUDI ARABIA" to "SAU",
        "UNITED ARAB EMIRATES" to "ARE", "UAE" to "ARE",
        "RUSSIA" to "RUS"
    )

    /** ISO-2 de los mismos paises; el catalogo no lo trae. */
    private val ISO2_A_ISO3: Map<String, String> = mapOf(
        "US" to "USA", "CN" to "CHN", "TW" to "TWN", "IN" to "IND", "IT" to "ITA",
        "JP" to "JPN", "VN" to "VNM", "MX" to "MEX", "DE" to "DEU", "TH" to "THA",
        "TR" to "TUR", "BR" to "BRA", "BE" to "BEL", "CA" to "CAN", "ES" to "ESP",
        "FR" to "FRA", "PT" to "PRT", "CZ" to "CZE", "AT" to "AUT", "ID" to "IDN",
        // NL -> ZYA (Países Bajos) y RO -> ROM (Rumania): claves del catálogo, no ISO-3.
        "SE" to "SWE", "GB" to "GBR", "UK" to "GBR", "KR" to "KOR", "NL" to "ZYA",
        "CH" to "CHE", "PL" to "POL", "MY" to "MYS", "SG" to "SGP", "PH" to "PHL",
        "IL" to "ISR", "AU" to "AUS", "DK" to "DNK", "FI" to "FIN", "NO" to "NOR",
        "IE" to "IRL", "HU" to "HUN", "SK" to "SVK", "RO" to "ROM", "AR" to "ARG",
        "CL" to "CHL", "CO" to "COL", "PE" to "PER", "ZA" to "ZAF", "NZ" to "NZL",
        "HK" to "HKG", "LK" to "LKA", "BD" to "BGD", "PK" to "PAK", "EG" to "EGY",
        "SA" to "SAU", "AE" to "ARE", "RU" to "RUS"
    )
}
