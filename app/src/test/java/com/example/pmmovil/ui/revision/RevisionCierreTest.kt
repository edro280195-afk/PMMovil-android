package com.example.pmmovil.ui.revision

import com.example.pmmovil.data.model.TimerFinalizarResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RevisionCierreTest {

    @Test
    fun `una entrada completa queda marcada`() {
        val respuesta = TimerFinalizarResponse(success = true, revisionCompleta = true)
        assertTrue(RevisionCierre.entradaQuedoMarcada(respuesta))
    }

    @Test
    fun `un false explicito del servidor ya no se ignora`() {
        val respuesta = TimerFinalizarResponse(
            success = true,
            revisionCompleta = false,
            facturasConPartidasPendientes = 1,
            message = "La revisión se cerró, pero la entrada no quedó marcada como revisada físicamente."
        )
        assertFalse(RevisionCierre.entradaQuedoMarcada(respuesta))
    }

    @Test
    fun `un servidor anterior que no manda el campo se trata como siempre`() {
        val respuesta = TimerFinalizarResponse(success = true)
        assertTrue(RevisionCierre.entradaQuedoMarcada(respuesta))
    }

    @Test
    fun `el motivo del servidor se recorta y un motivo vacio es ausencia de motivo`() {
        assertEquals(
            "faltan partidas",
            RevisionCierre.motivoDelServidor(TimerFinalizarResponse(success = true, message = "  faltan partidas  "))
        )
        assertNull(RevisionCierre.motivoDelServidor(TimerFinalizarResponse(success = true, message = "   ")))
        assertNull(RevisionCierre.motivoDelServidor(TimerFinalizarResponse(success = true, message = null)))
    }

    @Test
    fun `el JSON que manda el API se lee completo`() {
        val json = """
            {"success":true,"estatusFinal":2,"revisionCompleta":false,
             "facturasIncompletas":0,"facturasConPartidasPendientes":1,
             "message":"La revisión se cerró, pero la entrada no quedó marcada como revisada físicamente: faltan 1 factura(s) con partidas sin revisar."}
        """.trimIndent()
        val respuesta = com.google.gson.Gson().fromJson(json, TimerFinalizarResponse::class.java)
        assertTrue(respuesta.success)
        assertEquals(2, respuesta.estatusFinal)
        assertEquals(false, respuesta.revisionCompleta)
        assertEquals(1, respuesta.facturasConPartidasPendientes)
        assertFalse(RevisionCierre.entradaQuedoMarcada(respuesta))
    }

    @Test
    fun `la peticion de factura lleva la entrada y omite el campo cuando no hay`() {
        val gson = com.google.gson.Gson()
        val conEntrada = gson.toJson(
            com.example.pmmovil.data.model.GuardarCampoFacturaRequest("90568", "status_rev", 1, "01C263616880000")
        )
        assertTrue(conEntrada.contains("\"crefAgrupada\":\"01C263616880000\""))
        val sinEntrada = gson.toJson(com.example.pmmovil.data.model.CnofactRequest("90568"))
        assertFalse(sinEntrada.contains("crefAgrupada"))
    }
}
