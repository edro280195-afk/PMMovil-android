package com.example.pmmovil.ui.relacion

import com.example.pmmovil.data.model.SalidaEtiqueta
import com.example.pmmovil.data.model.SalidaRelacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelacionRulesTest {

    @Test
    fun `no permite autorizar una carga capturada por el mismo usuario`() {
        val relation = readyRelation(usucargaMcia = "supervisor01")

        assertFalse(RelacionRules.canAuthorize(relation, "SUPERVISOR01"))
        assertEquals(
            "NECESITA OTRO ENCARGADO",
            RelacionRules.statusLabel(relation, "supervisor01")
        )
    }

    @Test
    fun `permite autorizar una relacion cargada por otro usuario`() {
        val relation = readyRelation(usucargaMcia = "operador01")

        assertTrue(RelacionRules.canAuthorize(relation, "supervisor01"))
    }

    @Test
    fun `rechaza una relacion sin carga terminada`() {
        val relation = readyRelation(
            estatusCarga = "",
            usucargaMcia = "operador01"
        )

        assertFalse(RelacionRules.canAuthorize(relation, "supervisor01"))
        assertEquals(
            "NO HA SIDO CARGADA",
            RelacionRules.statusLabel(relation, "supervisor01")
        )
    }

    @Test
    fun `no permite autorizar una relacion que ya salio ni una con los totales ilegibles`() {
        val salida = readyRelation(estatusCarga = "SALIDA")
        assertFalse(RelacionRules.canAuthorize(salida, "supervisor01"))
        assertEquals("YA SALIÓ", RelacionRules.statusLabel(salida, "supervisor01"))

        // El estado del legacy trae la palabra ERROR, que la app ya bloquea.
        val sinTotales = readyRelation().copy(estatus = "ERROR EN INFORMACION (revisar si esta agrupado)")
        assertFalse(RelacionRules.canAuthorize(sinTotales, "supervisor01"))
    }

    @Test
    fun `detecta un bloqueo posterior a un desbloqueo`() {
        val label = SalidaEtiqueta(historial = "BLOQUEO, DESBLOQUEO, BLOQUEO")

        assertTrue(RelacionRules.isBlocked(label))
    }

    @Test
    fun `considera liberada una etiqueta cuyo ultimo evento es desbloqueo`() {
        val label = SalidaEtiqueta(historial = "BLOQUEO, DESBLOQUEO")

        assertFalse(RelacionRules.isBlocked(label))
    }

    private fun readyRelation(
        estatusCarga: String = "CARGADA",
        usucargaMcia: String = "operador01"
    ) = SalidaRelacion(
        relno = "REL-001",
        estatus = "LISTA",
        estatusCarga = estatusCarga,
        usucargaMcia = usucargaMcia
    )
}
