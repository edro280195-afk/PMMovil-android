package com.example.pmmovil.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class JwtExpiryTest {

    // Un "ahora" fijo: ninguna prueba depende del reloj de la máquina.
    private val nowMillis = 1_760_000_000_000L
    private val nowSeconds = nowMillis / 1_000L

    @Test
    fun expiresAtMillis_readsTheExpClaimAsSecondsSinceEpoch() {
        val token = jwt("""{"exp":${nowSeconds + 500}}""")

        assertEquals(nowMillis + 500_000L, JwtExpiry.expiresAtMillis(token))
    }

    @Test
    fun isExpired_whenExpIsInThePast_returnsTrue() {
        val token = jwt("""{"exp":${nowSeconds - 1}}""")

        assertTrue(JwtExpiry.isExpired(token, nowMillis))
    }

    @Test
    fun isExpired_whenExpIsInTheFuture_returnsFalse() {
        val token = jwt("""{"exp":${nowSeconds + 1}}""")

        assertFalse(JwtExpiry.isExpired(token, nowMillis))
    }

    @Test
    fun isExpired_whenNowIsExactlyExp_returnsTrue() {
        // `exp` es el primer instante en que el token ya no sirve, no el último.
        val token = jwt("""{"exp":$nowSeconds}""")

        assertTrue(JwtExpiry.isExpired(token, nowMillis))
    }

    @Test
    fun isExpired_readsTheTokensTheApiIssues() {
        // Forma de los tokens de PMMovil.Api (AuthService): claims con una URL
        // por nombre, jti, issuer y audience, además del exp.
        val token = jwt(
            """
            {
              "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name": "operador.prueba",
              "Area": "SOFTWARE",
              "Lugar": "MTY",
              "jti": "3f2c9d1e-8b7a-4c55-9a10-0b6f1d2e7a41",
              "exp": ${nowSeconds - 3_600},
              "iss": "PMMovil.Api",
              "aud": "PMMovil.App"
            }
            """.trimIndent()
        )

        assertTrue(JwtExpiry.isExpired(token, nowMillis))
    }

    @Test
    fun expiresAtMillis_decodesBase64UrlWithAndWithoutPadding() {
        // Cinco bytes iguales seguidos garantizan al menos un grupo que sale
        // como '-' o '_' (en el base64 normal serían '+' y '/'). Los espacios
        // finales recorren las tres longitudes posibles del relleno.
        (0..2).forEach { spaces ->
            val token = jwt(
                """{"exp":${nowSeconds + 60},"a":"?????","b":"~~~~~"${" ".repeat(spaces)}}"""
            )
            val payload = token.split('.')[1]

            assertTrue("la prueba debe usar el alfabeto url-safe", '-' in payload && '_' in payload)
            assertFalse("el JWT no lleva relleno", '=' in payload)
            assertEquals(nowMillis + 60_000L, JwtExpiry.expiresAtMillis(token))
        }
    }

    @Test
    fun isExpired_whenTheTokenIsNotAJwt_returnsFalse() {
        val notJwts = listOf(
            "",
            "no-es-un-jwt",
            "a.b.c",
            "a..c",
            "a.%%%.c",
            "a.${base64Url("esto no es json")}.c",
            "a.${base64Url("[1,2,3]")}.c",
            "a.${base64Url("null")}.c"
        )

        notJwts.forEach { token ->
            assertNull("token='$token'", JwtExpiry.expiresAtMillis(token))
            assertFalse("token='$token'", JwtExpiry.isExpired(token, nowMillis))
        }
    }

    @Test
    fun isExpired_whenExpIsMissingOrUnusable_returnsFalse() {
        val payloads = listOf(
            """{"iss":"PMMovil.Api"}""",
            """{"exp":null}""",
            """{"exp":"mañana"}""",
            // Un NumericDate es un número JSON, no un texto con dígitos.
            """{"exp":"${nowSeconds - 1}"}""",
            """{"exp":{"segundos":1}}""",
            """{"exp":[1760000000]}""",
            // En milisegundos ya no cabe en un Long: no es una fecha real.
            """{"exp":${Long.MAX_VALUE / 1_000L + 1}}"""
        )

        payloads.forEach { payload ->
            val token = jwt(payload)
            assertNull("payload=$payload", JwtExpiry.expiresAtMillis(token))
            assertFalse("payload=$payload", JwtExpiry.isExpired(token, nowMillis))
        }
    }

    private fun jwt(payloadJson: String): String =
        "${base64Url("""{"alg":"HS256","typ":"JWT"}""")}.${base64Url(payloadJson)}.firma-de-prueba"

    private fun base64Url(text: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray(Charsets.UTF_8))
}
