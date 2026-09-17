package com.example.pmmovil.data.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeModelsTest {

    private val gson = Gson()

    @Test
    fun estadoAcceptsLegacyDateObjectWithoutDroppingReadings() {
        val json = """
            {
              "success": true,
              "activa": true,
              "idSesion": 75,
              "fechaInicio": {
                "year": 2026,
                "month": 6,
                "day": 13,
                "hour": 12,
                "minute": 32,
                "second": 43
              },
              "lecturas": [
                {
                  "cidproduct": "105431",
                  "cantidad": 9,
                  "existe": true
                }
              ]
            }
        """.trimIndent()

        val response = gson.fromJson(json, EstadoBarcodeResponse::class.java)

        assertTrue(response.activa)
        assertTrue(response.fechaInicio?.isJsonObject == true)
        assertEquals(9, response.lecturas?.single()?.cantidad)
    }

    @Test
    fun estadoAcceptsIsoDateString() {
        val json = """
            {
              "success": true,
              "activa": true,
              "idSesion": 75,
              "fechaInicio": "2026-06-13T12:32:43",
              "lecturas": []
            }
        """.trimIndent()

        val response = gson.fromJson(json, EstadoBarcodeResponse::class.java)

        assertEquals(
            "2026-06-13T12:32:43",
            response.fechaInicio?.asString
        )
    }
}
