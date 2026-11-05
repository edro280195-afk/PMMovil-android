package com.example.pmmovil.data.scanner

import com.example.pmmovil.data.model.MobileModuleIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanActionsTest {

    private val everything: (Int, String) -> Boolean = { _, _ -> true }
    private val nothing: (Int, String) -> Boolean = { _, _ -> false }

    private fun only(moduleId: Int, level: String = "Lectura"): (Int, String) -> Boolean =
        { module, requested -> module == moduleId && (requested == "Lectura" || requested == level) }

    @Test
    fun `a box offers to create the entry, give it exit and find it in the yard`() {
        val actions = ScanActionResolver.actionsFor(ScannedCode.Caja("E-017846/26"))

        assertEquals(
            listOf(ScanActionId.CreateEntry, ScanActionId.ExitGate, ScanActionId.FindInYard),
            actions.map { it.id }
        )
    }

    @Test
    fun `the box id travels escaped so its slash does not break the route`() {
        val create = ScanActionResolver.actionsFor(ScannedCode.Caja("E-017846/26")).first()

        assertEquals("entrada_nueva?barcode=E-017846%2F26", create.route)
    }

    @Test
    fun `a release offers to load it, open it or give exit`() {
        val actions = ScanActionResolver.actionsFor(ScannedCode.Relacion("01I26104546"))

        assertEquals(
            listOf(ScanActionId.LoadCargo, ScanActionId.OpenRelation, ScanActionId.ExitGate),
            actions.map { it.id }
        )
        assertEquals("salida?barcode=01I26104546", actions[0].route)
        assertEquals("relacion?barcode=01I26104546", actions[1].route)
        assertEquals("salida_caseta?barcode=01I26104546", actions[2].route)
    }

    @Test
    fun `loading goods needs the load module and not the gate`() {
        val load = ScanActionResolver.actionsFor(ScannedCode.Relacion("01I26104546")).first()

        assertEquals(listOf(MobileModuleIds.WAREHOUSE_EXIT), load.modules)
        assertEquals(ScanAction.LEVEL_READ, load.level)
    }

    @Test
    fun `a package and a folder both lead to the entry by its reference only`() {
        val bulto = ScanActionResolver.actionsFor(ScannedCode.Bulto("01C263582800000", 3, 12))
        val carpeta = ScanActionResolver.actionsFor(ScannedCode.Carpeta("01C263582800000"))

        assertEquals(bulto, carpeta)
        assertEquals(
            listOf(ScanActionId.ReviewEntry, ScanActionId.OpenEntry, ScanActionId.ShowOnMap),
            bulto.map { it.id }
        )
        assertEquals("detalle/01C263582800000", bulto.first().route)
        assertEquals("entrada_editar/01C263582800000", bulto[1].route)
    }

    @Test
    fun `a pre registration opens the gate entry with its code escaped`() {
        val action = ScanActionResolver.actionsFor(ScannedCode.PreRegistro("PMMPR|7K2Q-4481")).single()

        assertEquals(ScanActionId.RegisterPreRegistro, action.id)
        assertEquals("caseta_entrada?barcode=PMMPR%7C7K2Q-4481", action.route)
        assertEquals(ScanAction.LEVEL_ADD, action.level)
    }

    @Test
    fun `a badge and the unrecognised reads open nothing`() {
        assertTrue(ScanActionResolver.actionsFor(ScannedCode.Gafete).isEmpty())
        assertTrue(ScanActionResolver.actionsFor(ScannedCode.ProductoCliente("+30S77120045")).isEmpty())
        assertTrue(ScanActionResolver.actionsFor(ScannedCode.Candidato("ZX-0042")).isEmpty())
        assertTrue(ScanActionResolver.actionsFor(ScannedCode.Desconocido("???")).isEmpty())
    }

    @Test
    fun `a user with no module sees no action`() {
        assertTrue(ScanActionResolver.available(ScannedCode.Caja("E-017846/26"), nothing).isEmpty())
    }

    @Test
    fun `a user with everything sees every action and none is forced as primary`() {
        val actions = ScanActionResolver.available(ScannedCode.Caja("E-017846/26"), everything)

        assertEquals(3, actions.size)
        assertFalse(actions.any { it.primary })
    }

    @Test
    fun `a guard sees exit and the yard but not creating an entry`() {
        val guard: (Int, String) -> Boolean = { module, _ ->
            module == MobileModuleIds.GATE || module == MobileModuleIds.GATE_PHYSICAL_EXIT
        }

        val ids = ScanActionResolver.available(ScannedCode.Caja("E-017846/26"), guard).map { it.id }

        assertEquals(listOf(ScanActionId.ExitGate, ScanActionId.FindInYard), ids)
    }

    @Test
    fun `reading a box in warehouse operations is not enough to create an entry without add permission`() {
        val readOnly = only(MobileModuleIds.WAREHOUSE_OPERATIONS)

        assertTrue(ScanActionResolver.available(ScannedCode.Caja("E-017846/26"), readOnly).isEmpty())
    }

    @Test
    fun `when only one action is left it becomes the primary one`() {
        val reviewer = only(MobileModuleIds.REVIEW)

        val actions = ScanActionResolver.available(ScannedCode.Carpeta("01C263582800000"), reviewer)

        assertEquals(listOf(ScanActionId.ReviewEntry), actions.map { it.id })
        assertTrue(actions.single().primary)
    }

    @Test
    fun `a pending code keeps only the last one and gives it once`() {
        val pending = PendingCode()

        pending.hold("01I26104546")
        pending.hold("E-017846/26")

        assertEquals("E-017846/26", pending.take())
        assertNull(pending.take())
    }

    @Test
    fun `encoding keeps letters digits and dashes and escapes the rest`() {
        assertEquals("E-017846%2F26", ScanActionResolver.encode("E-017846/26"))
        assertEquals("01C263582800000%203-12", ScanActionResolver.encode("01C263582800000 3-12"))
    }
}
