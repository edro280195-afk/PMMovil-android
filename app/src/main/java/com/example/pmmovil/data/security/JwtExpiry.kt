package com.example.pmmovil.data.security

import com.google.gson.JsonParser
import java.util.Base64

/**
 * Lee el vencimiento (`exp`) de un JWT sin validar su firma.
 *
 * Sirve para decidir en el teléfono si vale la pena intentar algo con el token
 * guardado -- abrir el hub de SignalR, por ejemplo -- sin gastar un viaje al
 * servidor solo para enterarse de que ya no sirve.
 *
 * No es una validación de seguridad: quien decide si el token es bueno sigue
 * siendo el API. Por eso todo lo que aquí no se pueda leer cuenta como "no sé",
 * nunca como "vencido": equivocarse hacia ese lado dejaría al usuario sin
 * tiempo real aunque su sesión estuviera sana.
 */
object JwtExpiry {

    /**
     * Momento en que vence el token, en milisegundos desde epoch, o `null` si
     * no es un JWT legible o no trae `exp`.
     */
    fun expiresAtMillis(token: String): Long? {
        // Un JWT son tres tramos en base64url separados por punto; las claims
        // viajan en el de en medio.
        val payload = token.split('.').getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            val json = String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)
            val exp = JsonParser.parseString(json).asJsonObject.get("exp")
            // RFC 7519: `exp` es un NumericDate, o sea un número JSON. Un texto,
            // un arreglo o un objeto no se interpretan (Gson, por ejemplo, leería
            // [123] como 123).
            if (exp == null || !exp.isJsonPrimitive || !exp.asJsonPrimitive.isNumber) return null
            Math.multiplyExact(exp.asLong, MILLIS_PER_SECOND)
        } catch (e: Exception) {
            // Base64 inválido, JSON roto o un `exp` que no cabe en milisegundos.
            // El token sale de disco y puede venir dañado: no hay nada que
            // hacer más que ignorarlo.
            null
        }
    }

    /**
     * true si el token ya no sirve: `exp` es el instante a partir del cual el
     * JWT no debe aceptarse (RFC 7519), así que en ese mismo instante ya cuenta
     * como vencido. Se compara contra el reloj del teléfono; un token que no se
     * puede leer no se considera vencido.
     */
    fun isExpired(token: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val expiresAt = expiresAtMillis(token) ?: return false
        return nowMillis >= expiresAt
    }

    private const val MILLIS_PER_SECOND = 1_000L
}
